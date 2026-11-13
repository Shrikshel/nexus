package com.nexus.core.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDispatcherTest {

    private static class MockChannel implements OutboundNotificationChannel {
        private final String name;
        private final boolean enabled;
        final List<HostEvent> received = new CopyOnWriteArrayList<>();
        final CountDownLatch latch;

        MockChannel(String name, boolean enabled, CountDownLatch latch) {
            this.name = name;
            this.enabled = enabled;
            this.latch = latch;
        }

        @Override public String getChannelName() { return name; }
        @Override public boolean isEnabled() { return enabled; }
        @Override
        public void sendNotification(HostEvent event) {
            received.add(event);
            latch.countDown();
        }
    }

    @Test
    @DisplayName("NotificationDispatcher routes events only to enabled channels asynchronously")
    void testDispatching() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        MockChannel enabledChannel = new MockChannel("ActiveTelegram", true, latch);
        MockChannel disabledChannel = new MockChannel("DisabledSlack", false, latch);

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(enabledChannel, disabledChannel));

        HostEvent event = HostEvent.info("AGENT_ONLINE", "Nexus Started", "Agent version 1.0.0 is online");
        dispatcher.dispatch(event);

        boolean reached = latch.await(2, TimeUnit.SECONDS);
        assertThat(reached).isTrue();

        assertThat(enabledChannel.received).hasSize(1);
        assertThat(enabledChannel.received.get(0).title()).isEqualTo("Nexus Started");
        assertThat(disabledChannel.received).isEmpty();
    }
}
