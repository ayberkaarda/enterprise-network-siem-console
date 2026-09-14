package com.example.demo.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.realtime.RealtimeTopics;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Proves the live channel is actually shut to anyone without a credential.
 *
 * <p>These tests speak the real protocol over a real socket on a real port
 * rather than calling the interceptor directly, because the thing being
 * asserted is not "the interceptor throws" — it is that a client which is
 * refused ends up with no session and therefore no way to read
 * {@code /topic/devices}. That only shows up end to end.
 *
 * <p>The client reaches the endpoint through SockJS's raw WebSocket transport
 * ({@code /ws-siem/websocket}) since the endpoint is registered
 * {@code withSockJS()}. The CONNECT frame carries the token in exactly the
 * header the browser console uses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class WebSocketStompAuthContractTest {

    private static final Duration AWAIT = Duration.ofSeconds(10);

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void aConnectWithoutAnAuthorizationHeaderIsRefused() throws Exception {
        RecordingSessionHandler handler = connect(new StompHeaders());

        assertThat(handler.awaitFailure())
                .as("an unauthenticated CONNECT must be answered with an error")
                .isTrue();
        assertThat(handler.connected())
                .as("no session may be established without a token")
                .isFalse();
        assertThat(handler.awaitErrorFrame())
                .as("the refusal must be a STOMP-level rejection, not a dropped transport")
                .isTrue();
    }

    @Test
    void aConnectCarryingSomethingThatIsNotAnAccessTokenIsRefused() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer not-a-real-jwt");

        RecordingSessionHandler handler = connect(connectHeaders);

        assertThat(handler.awaitFailure()).isTrue();
        assertThat(handler.connected()).isFalse();
        assertThat(handler.awaitErrorFrame()).isTrue();
    }

    /**
     * A refresh token is a valid signature over a valid subject, so this is the
     * case a naive check would let through: it is rejected because the token
     * type is verified, not merely the signature.
     */
    @Test
    void aConnectPresentingARefreshTokenInsteadOfAnAccessTokenIsRefused() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtService.generateRefreshToken("ws-contract-viewer"));

        RecordingSessionHandler handler = connect(connectHeaders);

        assertThat(handler.awaitFailure()).isTrue();
        assertThat(handler.connected()).isFalse();
        assertThat(handler.awaitErrorFrame()).isTrue();
    }

    @Test
    void aConnectWithAValidAccessTokenOpensASessionThatReceivesTheDeviceTopic() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(
                "Authorization", "Bearer " + jwtService.generateAccessToken("ws-contract-viewer", Role.VIEWER));

        RecordingSessionHandler handler = connect(connectHeaders);

        assertThat(handler.awaitConnected())
                .as("a valid access token must open the session")
                .isTrue();

        StompSession session = handler.session();
        assertThat(session.isConnected()).isTrue();
        assertThat(session.getSessionId()).isNotBlank();

        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe(RealtimeTopics.DEVICES, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });

        // The broker registers the subscription asynchronously, so the probe is
        // resent until it lands instead of being published once and hoped for.
        // Everything else on this topic (the background scan) is ignored by
        // matching on a marker unique to this run.
        String marker = "stomp-auth-probe-" + UUID.randomUUID();
        assertThat(awaitMarker(received, marker))
                .as("a subscribed session must receive what is pushed to " + RealtimeTopics.DEVICES)
                .isTrue();

        session.disconnect();
    }

    /**
     * The handshake itself, not the STOMP layer: the endpoint used to accept
     * every origin, and the SockJS {@code /info} probe is the cheapest place to
     * see whether it still does.
     */
    @Test
    void theHandshakeEndpointOnlyAnswersConfiguredOrigins() throws Exception {
        // http://localhost:4200 is one of the origins the h2 profile lists.
        assertThat(infoStatusForOrigin("http://localhost:4200")).isEqualTo(200);
        assertThat(infoStatusForOrigin("http://not-an-allowed-origin.example")).isEqualTo(403);
    }

    // ------------------------------------------------------------- helpers

    private RecordingSessionHandler connect(StompHeaders connectHeaders) {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // No heartbeats: they would need a TaskScheduler on the client and add
        // nothing to what these tests assert.
        stompClient.setDefaultHeartbeat(new long[] {0, 0});

        RecordingSessionHandler handler = new RecordingSessionHandler();
        stompClient.connectAsync(
                "ws://localhost:" + port + "/ws-siem/websocket", new WebSocketHttpHeaders(), connectHeaders, handler);
        return handler;
    }

    private boolean awaitMarker(BlockingQueue<String> received, String marker) throws InterruptedException {
        long deadline = System.nanoTime() + AWAIT.toNanos();
        while (System.nanoTime() < deadline) {
            messagingTemplate.convertAndSend(RealtimeTopics.DEVICES, marker);
            String payload = received.poll(200, TimeUnit.MILLISECONDS);
            while (payload != null) {
                if (payload.contains(marker)) {
                    return true;
                }
                payload = received.poll();
            }
        }
        return false;
    }

    private int infoStatusForOrigin(String origin) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/ws-siem/info"))
                    .header("Origin", origin)
                    .timeout(AWAIT)
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    /**
     * Records the outcome of a connection attempt. Both directions have to be
     * observable: that a session came up, and that one never did.
     */
    private static final class RecordingSessionHandler extends StompSessionHandlerAdapter {

        private final CountDownLatch connected = new CountDownLatch(1);
        private final CountDownLatch failed = new CountDownLatch(1);
        private final CountDownLatch errorFrame = new CountDownLatch(1);
        private volatile StompSession session;

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            this.session = session;
            connected.countDown();
        }

        /** An ERROR frame arrives here when there is no subscription to route it to. */
        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errorFrame.countDown();
            failed.countDown();
        }

        @Override
        public void handleException(
                StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
            failed.countDown();
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            failed.countDown();
        }

        boolean awaitConnected() throws InterruptedException {
            return connected.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS);
        }

        boolean awaitFailure() throws InterruptedException {
            return failed.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS);
        }

        boolean connected() {
            return connected.getCount() == 0;
        }

        /**
         * Whether the server answered with a STOMP ERROR frame. This is what
         * separates "the credential check refused the session" from "the socket
         * never came up": a handshake turned away for its origin, or a network
         * failure, produces a transport error and no frame at all. The frame's
         * own {@code message} header is not asserted on — Spring reports the
         * interceptor's exception wrapped in a {@code MessageDeliveryException}
         * naming the inbound channel, so the text is a framework detail rather
         * than part of this contract.
         */
        boolean awaitErrorFrame() throws InterruptedException {
            return errorFrame.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS);
        }

        StompSession session() {
            return session;
        }
    }
}
