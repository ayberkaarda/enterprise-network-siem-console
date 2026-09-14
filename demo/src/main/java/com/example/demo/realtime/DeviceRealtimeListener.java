package com.example.demo.realtime;

import com.example.demo.event.DeviceStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes every device status change to the subscribed consoles.
 *
 * <p>Kept separate from the audit listener so that the two can fail
 * independently: a console that is not listening must not cost an audit record,
 * and a database problem must not cost the live view.
 *
 * <p>This listener runs synchronously, unlike the incident one. The event
 * carries the live device object, which the scanning thread goes on to save; the
 * immutable snapshot therefore has to be taken on the publishing thread, while
 * the values still describe the probe that raised the event. Taking it is cheap,
 * and the send itself does not block — the message is handed to the broker's own
 * channel and delivered from there.
 */
@Component
public class DeviceRealtimeListener {

    private static final Logger log = LoggerFactory.getLogger(DeviceRealtimeListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    public DeviceRealtimeListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Registered at the lowest precedence. The correlation engine claims the
     * highest and depends on running before the latency baseline folds the new
     * sample in; pushing the change to a screen depends on nothing, so it takes
     * the back of the queue where it cannot disturb that ordering.
     */
    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onDeviceStatusChanged(DeviceStatusChangedEvent event) {
        if (event == null || event.getDevice() == null || event.getDevice().getId() == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(RealtimeTopics.DEVICES, DeviceRealtimeEvent.from(event));
        } catch (Exception ex) {
            // Losing a frame of the live view is not a reason to fail the scan
            // that produced it; the next scan will report the same state again.
            log.warn("Could not push a device status change to {}", RealtimeTopics.DEVICES, ex);
        }
    }
}
