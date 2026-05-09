package icu.sprinkle.loom.spring.autoconfigure;

import icu.sprinkle.loom.bootstrap.Loom;
import icu.sprinkle.loom.core.loop.event.AgentEvent;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoomFluxAdaptersTest {

    @Test
    void runFluxWrapsRunStreamingPublisher() {
        Loom loom = mock(Loom.class);
        when(loom.runStreaming("hello")).thenReturn(singleEventPublisher(AgentEvent.llmToken("hi", 0)));

        StepVerifier.create(LoomFluxAdapters.runFlux(loom, "hello"))
                .expectNextMatches(event -> event instanceof AgentEvent.LlmToken t && t.token().equals("hi"))
                .verifyComplete();
    }

    @Test
    void chatFluxWrapsChatStreamingPublisher() {
        Loom loom = mock(Loom.class);
        when(loom.chatStreaming("hello")).thenReturn(singleEventPublisher(
                new AgentEvent.IterationComplete(Instant.now(), 1, icu.sprinkle.loom.protocol.llm.StopReason.END_TURN)));

        StepVerifier.create(LoomFluxAdapters.chatFlux(loom, "hello"))
                .expectNextMatches(event -> event instanceof AgentEvent.IterationComplete)
                .verifyComplete();
    }

    @Test
    void resumeFluxWrapsResumeStreamingPublisher() {
        Loom loom = mock(Loom.class);
        when(loom.resumeStreaming("s1", "hello")).thenReturn(singleEventPublisher(
                new AgentEvent.SessionResumed(Instant.now(), "s1", 3)));

        StepVerifier.create(LoomFluxAdapters.resumeFlux(loom, "s1", "hello"))
                .expectNextMatches(event -> event instanceof AgentEvent.SessionResumed s && s.sessionId().equals("s1"))
                .verifyComplete();
    }

    @Test
    void fluxCancellationCancelsUnderlyingSubscription() {
        Loom loom = mock(Loom.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        when(loom.runStreaming("hello")).thenReturn(subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        }));

        StepVerifier.create(LoomFluxAdapters.runFlux(loom, "hello"))
                .thenCancel()
                .verify();

        org.assertj.core.api.Assertions.assertThat(cancelled).isTrue();
    }

    private static Flow.Publisher<AgentEvent> singleEventPublisher(AgentEvent event) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private boolean delivered;

            @Override
            public void request(long n) {
                if (!delivered && n > 0) {
                    delivered = true;
                    subscriber.onNext(event);
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                delivered = true;
            }
        });
    }
}
