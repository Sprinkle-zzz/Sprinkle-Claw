package icu.sprinkle.loom.llm;

import icu.sprinkle.loom.protocol.llm.ChatRequest;
import icu.sprinkle.loom.protocol.llm.ChatResponse;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * 基于阻塞式 {@link LlmProvider#streamChat(ChatRequest, StreamCallback)}
 * 的冷发布者默认适配器。
 *
 * @author sprinkle
 * @since 2026/5/10
 */
final class BlockingStreamChatPublisher implements Flow.Publisher<LlmStreamEvent> {

    private final LlmProvider provider;
    private final ChatRequest request;

    BlockingStreamChatPublisher(LlmProvider provider, ChatRequest request) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.request = Objects.requireNonNull(request, "request");
    }

    @Override
    public void subscribe(Flow.Subscriber<? super LlmStreamEvent> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber");
        subscriber.onSubscribe(new SubscriptionImpl(subscriber));
    }

    private final class SubscriptionImpl implements Flow.Subscription {
        private final Flow.Subscriber<? super LlmStreamEvent> subscriber;
        private final Object lock = new Object();
        private long requested;
        private boolean started;
        private boolean cancelled;
        private boolean terminated;
        private Thread runner;

        private SubscriptionImpl(Flow.Subscriber<? super LlmStreamEvent> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                fail(new IllegalArgumentException("request amount must be positive"));
                return;
            }

            Thread toStart = null;
            synchronized (lock) {
                if (cancelled || terminated) {
                    return;
                }
                requested = addCap(requested, n);
                if (!started) {
                    started = true;
                    runner = Thread.ofVirtual().name("llm-stream-publisher").unstarted(this::runStream);
                    toStart = runner;
                }
                lock.notifyAll();
            }

            if (toStart != null) {
                toStart.start();
            }
        }

        @Override
        public void cancel() {
            Thread toInterrupt;
            synchronized (lock) {
                if (cancelled || terminated) {
                    return;
                }
                cancelled = true;
                toInterrupt = runner;
                lock.notifyAll();
            }
            if (toInterrupt != null) {
                toInterrupt.interrupt();
            }
        }

        private void runStream() {
            try {
                ChatResponse response = provider.streamChat(request, new StreamCallback() {
                    @Override
                    public void onToken(String token) {
                        emit(new LlmStreamEvent.Token(Instant.now(), token));
                    }

                    @Override
                    public void onThinkingToken(String token) {
                        emit(new LlmStreamEvent.ThinkingToken(Instant.now(), token));
                    }

                    @Override
                    public void onToolUseInput(String toolUseId, String toolName, String inputChunk) {
                        emit(new LlmStreamEvent.ToolInputChunk(
                                Instant.now(), toolUseId, toolName, inputChunk));
                    }

                    @Override
                    public void onContentBlockStart(int index, String type) {
                        emit(new LlmStreamEvent.ContentBlockStart(Instant.now(), index, type));
                    }

                    @Override
                    public void onContentBlockStop(int index) {
                        emit(new LlmStreamEvent.ContentBlockStop(Instant.now(), index));
                    }
                });
                emit(new LlmStreamEvent.Complete(Instant.now(), response));
                complete();
            } catch (Throwable t) {
                fail(t);
            }
        }

        private void emit(LlmStreamEvent event) {
            synchronized (lock) {
                while (!cancelled && !terminated && requested == 0) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        cancelled = true;
                        return;
                    }
                }
                if (cancelled || terminated) {
                    return;
                }
                if (requested != Long.MAX_VALUE) {
                    requested--;
                }
            }
            subscriber.onNext(event);
        }

        private void complete() {
            synchronized (lock) {
                if (cancelled || terminated) {
                    return;
                }
                terminated = true;
            }
            subscriber.onComplete();
        }

        private void fail(Throwable error) {
            synchronized (lock) {
                if (cancelled || terminated) {
                    return;
                }
                terminated = true;
                cancelled = true;
                lock.notifyAll();
            }
            subscriber.onError(error);
        }

        private long addCap(long current, long n) {
            long result = current + n;
            return result < 0 ? Long.MAX_VALUE : result;
        }
    }
}
