package com.example.demo.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes opened and transitioned incidents to the subscribed consoles.
 *
 * <p>Two properties of this listener are deliberate:
 *
 * <ul>
 *   <li><strong>After commit.</strong> An analyst seeing an incident appear must
 *       be able to open it. If the push happened when the incident was merely
 *       written and the surrounding transaction then rolled back, the console
 *       would show a finding whose row does not exist — a false alarm produced
 *       by the alerting mechanism itself.</li>
 *   <li><strong>Off the caller's thread.</strong> Delivery is fan-out to whoever
 *       happens to be connected. Charging that to the thread that opened the
 *       incident would make an analyst's state change wait on the message
 *       broker. The payload is immutable and complete, so nothing is read here
 *       that could have moved on in the meantime.</li>
 * </ul>
 *
 * <p>{@code fallbackExecution} is enabled because incidents are also opened from
 * code paths that run outside a transaction, for example the automated probe
 * loop. Without it those incidents would be written and then silently never
 * reach a screen.
 */
@Component
public class IncidentRealtimeListener {

    private static final Logger log = LoggerFactory.getLogger(IncidentRealtimeListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    public IncidentRealtimeListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIncidentChanged(IncidentChangedEvent event) {
        if (event == null || event.payload() == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(RealtimeTopics.INCIDENTS, event.payload());
        } catch (Exception ex) {
            log.warn("Could not push incident {} to {}",
                    event.payload().id(), RealtimeTopics.INCIDENTS, ex);
        }
    }
}
