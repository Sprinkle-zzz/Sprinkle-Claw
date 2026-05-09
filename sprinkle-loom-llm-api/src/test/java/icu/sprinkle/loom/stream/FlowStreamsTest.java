package icu.sprinkle.loom.stream;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FlowStreamsTest {

    @Test
    void subscribeBuilderRegistersHandlersAndRequestsUnboundedByDefault() {
        List<String> events = new ArrayList<>();
        AtomicBoolean subscribed = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);

        FlowStreamSubscription subscription = FlowStreams.subscribe(singleValuePublisher("hello"))
                .onSubscribe(s -> subscribed.set(true))
                .onNext(events::add)
                .onComplete(() -> completed.set(true))
                .start();

        assertThat(subscribed).isTrue();
        assertThat(events).containsExactly("hello");
        assertThat(completed).isTrue();
        assertThat(subscription).isNotNull();
    }

    @Test
    void subscribeBuilderRoutesErrorsToOnError() {
        RuntimeException failure = new RuntimeException("boom");
        AtomicReference<Throwable> actual = new AtomicReference<>();

        FlowStreams.subscribe(errorPublisher(failure))
                .onError(actual::set)
                .start();

        assertThat(actual.get()).isSameAs(failure);
    }

    @Test
    void returnedSubscriptionCanCancelUnderlyingFlowSubscription() {
        AtomicBoolean cancelled = new AtomicBoolean(false);

        FlowStreamSubscription subscription = FlowStreams.subscribe(subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        })).start();

        subscription.cancel();

        assertThat(cancelled).isTrue();
    }

    @Test
    void customRequestAmountIsUsedWhenConfigured() {
        AtomicReference<Long> requested = new AtomicReference<>();

        FlowStreams.subscribe(subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                requested.set(n);
            }

            @Override
            public void cancel() {
            }
        })).request(3).start();

        assertThat(requested.get()).isEqualTo(3L);
    }

    private static Flow.Publisher<String> singleValuePublisher(String value) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private boolean done;

            @Override
            public void request(long n) {
                if (done || n <= 0) {
                    return;
                }
                done = true;
                subscriber.onNext(value);
                subscriber.onComplete();
            }

            @Override
            public void cancel() {
                done = true;
            }
        });
    }

    private static Flow.Publisher<String> errorPublisher(Throwable error) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                subscriber.onError(error);
            }

            @Override
            public void cancel() {
            }
        });
    }
}
