package icu.sprinkle.loom.llm;

import icu.sprinkle.loom.protocol.llm.ChatRequest;
import icu.sprinkle.loom.protocol.llm.ChatResponse;
import icu.sprinkle.loom.protocol.llm.StopReason;
import icu.sprinkle.loom.protocol.llm.Usage;
import icu.sprinkle.loom.protocol.message.ContentBlock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderPublisherTest {

    @Test
    void streamChatPublisher_startsOnlyAfterDemandAndPublishesStreamEvents() throws Exception {
        AtomicBoolean started = new AtomicBoolean(false);
        ChatResponse response = new ChatResponse(
                List.of(new ContentBlock.TextBlock("hello")),
                StopReason.END_TURN,
                new Usage(1, 1),
                "test-model");

        LlmProvider provider = new LlmProvider() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return response;
            }

            @Override
            public ChatResponse streamChat(ChatRequest request, StreamCallback callback) {
                started.set(true);
                callback.onContentBlockStart(0, "text");
                callback.onToken("he");
                callback.onThinkingToken("think");
                callback.onToolUseInput("toolu_1", "search", "{\"q\"");
                callback.onContentBlockStop(0);
                callback.onToken("llo");
                return response;
            }
        };

        RecordingSubscriber subscriber = new RecordingSubscriber();
        provider.streamChatPublisher(ChatRequest.builder().build()).subscribe(subscriber);

        assertThat(started).isFalse();

        subscriber.request(Long.MAX_VALUE);

        assertThat(subscriber.awaitComplete()).isTrue();
        assertThat(started).isTrue();
        assertThat(subscriber.error()).isNull();
        assertThat(subscriber.events()).hasSize(7);
        assertThat(subscriber.events().get(0)).isInstanceOf(LlmStreamEvent.ContentBlockStart.class);
        assertThat(((LlmStreamEvent.Token) subscriber.events().get(1)).token()).isEqualTo("he");
        assertThat(((LlmStreamEvent.ThinkingToken) subscriber.events().get(2)).token()).isEqualTo("think");
        assertThat(((LlmStreamEvent.ToolInputChunk) subscriber.events().get(3)).inputChunk()).isEqualTo("{\"q\"");
        assertThat(subscriber.events().get(4)).isInstanceOf(LlmStreamEvent.ContentBlockStop.class);
        assertThat(subscriber.events().get(5)).isInstanceOf(LlmStreamEvent.Token.class);
        assertThat(subscriber.events().get(6)).isInstanceOf(LlmStreamEvent.Complete.class);
        assertThat(((LlmStreamEvent.Complete) subscriber.events().get(6)).response()).isSameAs(response);
    }

    @Test
    void streamChatPublisher_reportsStreamChatFailuresViaOnError() throws Exception {
        RuntimeException failure = new RuntimeException("boom");
        LlmProvider provider = new LlmProvider() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw failure;
            }

            @Override
            public ChatResponse streamChat(ChatRequest request, StreamCallback callback) {
                callback.onToken("partial");
                throw failure;
            }
        };

        RecordingSubscriber subscriber = new RecordingSubscriber();
        provider.streamChatPublisher(ChatRequest.builder().build()).subscribe(subscriber);
        subscriber.request(Long.MAX_VALUE);

        assertThat(subscriber.awaitTerminal()).isTrue();
        assertThat(subscriber.error()).isSameAs(failure);
        assertThat(subscriber.completed()).isFalse();
        assertThat(subscriber.events()).hasSize(1);
        assertThat(subscriber.events().get(0)).isInstanceOf(LlmStreamEvent.Token.class);
    }

    @Test
    void streamChatPublisher_cancelStopsFurtherDelivery() throws Exception {
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        LlmProvider provider = new LlmProvider() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.empty(StopReason.END_TURN);
            }

            @Override
            public ChatResponse streamChat(ChatRequest request, StreamCallback callback) {
                callback.onToken("first");
                callbackEntered.countDown();
                try {
                    releaseCallback.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                callback.onToken("second");
                return ChatResponse.empty(StopReason.END_TURN);
            }
        };

        RecordingSubscriber subscriber = new RecordingSubscriber();
        provider.streamChatPublisher(ChatRequest.builder().build()).subscribe(subscriber);
        subscriber.request(Long.MAX_VALUE);

        assertThat(callbackEntered.await(2, TimeUnit.SECONDS)).isTrue();
        subscriber.cancel();
        releaseCallback.countDown();

        Thread.sleep(Duration.ofMillis(150));
        assertThat(subscriber.events())
                .extracting(event -> event instanceof LlmStreamEvent.Token t ? t.token() : event.getClass().getSimpleName())
                .containsExactly("first");
        assertThat(subscriber.completed()).isFalse();
    }

    private static final class RecordingSubscriber implements Flow.Subscriber<LlmStreamEvent> {
        private final List<LlmStreamEvent> events = new ArrayList<>();
        private final CountDownLatch terminal = new CountDownLatch(1);
        private Flow.Subscription subscription;
        private volatile Throwable error;
        private volatile boolean completed;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public synchronized void onNext(LlmStreamEvent item) {
            events.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            terminal.countDown();
        }

        @Override
        public void onComplete() {
            completed = true;
            terminal.countDown();
        }

        void request(long n) {
            subscription.request(n);
        }

        void cancel() {
            subscription.cancel();
        }

        synchronized List<LlmStreamEvent> events() {
            return List.copyOf(events);
        }

        Throwable error() {
            return error;
        }

        boolean completed() {
            return completed;
        }

        boolean awaitComplete() throws InterruptedException {
            return terminal.await(2, TimeUnit.SECONDS) && completed;
        }

        boolean awaitTerminal() throws InterruptedException {
            return terminal.await(2, TimeUnit.SECONDS);
        }
    }
}
