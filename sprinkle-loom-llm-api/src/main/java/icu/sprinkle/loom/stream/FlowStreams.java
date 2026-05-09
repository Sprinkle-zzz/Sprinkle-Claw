package icu.sprinkle.loom.stream;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * JDK {@link Flow.Publisher} 的轻量订阅辅助工具。
 *
 * @author sprinkle
 * @since 2026/5/10
 */
public final class FlowStreams {

    private FlowStreams() {
    }

    /**
     * 创建订阅构建器。
     *
     * @param publisher 事件发布者
     * @param <T>       事件类型
     * @return 订阅构建器
     */
    public static <T> SubscriberBuilder<T> subscribe(Flow.Publisher<T> publisher) {
        return new SubscriberBuilder<>(publisher);
    }

    /**
     * Flow 订阅构建器。
     *
     * @param <T> 事件类型
     */
    public static final class SubscriberBuilder<T> {
        private final Flow.Publisher<T> publisher;
        private Consumer<Flow.Subscription> onSubscribe = subscription -> {
        };
        private Consumer<T> onNext = item -> {
        };
        private Consumer<Throwable> onError = error -> {
        };
        private Runnable onComplete = () -> {
        };
        private long requestAmount = Long.MAX_VALUE;

        private SubscriberBuilder(Flow.Publisher<T> publisher) {
            this.publisher = Objects.requireNonNull(publisher, "publisher");
        }

        /**
         * 注册订阅建立回调。
         *
         * @param handler 回调处理器
         * @return 当前构建器
         */
        public SubscriberBuilder<T> onSubscribe(Consumer<Flow.Subscription> handler) {
            this.onSubscribe = Objects.requireNonNull(handler, "handler");
            return this;
        }

        /**
         * 注册事件回调。
         *
         * @param handler 回调处理器
         * @return 当前构建器
         */
        public SubscriberBuilder<T> onNext(Consumer<T> handler) {
            this.onNext = Objects.requireNonNull(handler, "handler");
            return this;
        }

        /**
         * 注册错误回调。
         *
         * @param handler 回调处理器
         * @return 当前构建器
         */
        public SubscriberBuilder<T> onError(Consumer<Throwable> handler) {
            this.onError = Objects.requireNonNull(handler, "handler");
            return this;
        }

        /**
         * 注册完成回调。
         *
         * @param handler 回调处理器
         * @return 当前构建器
         */
        public SubscriberBuilder<T> onComplete(Runnable handler) {
            this.onComplete = Objects.requireNonNull(handler, "handler");
            return this;
        }

        /**
         * 设置订阅时请求的事件数量。
         *
         * @param n 请求数量
         * @return 当前构建器
         */
        public SubscriberBuilder<T> request(long n) {
            if (n <= 0) {
                throw new IllegalArgumentException("request amount must be positive");
            }
            this.requestAmount = n;
            return this;
        }

        /**
         * 设置订阅时请求无限事件。
         *
         * @return 当前构建器
         */
        public SubscriberBuilder<T> requestUnbounded() {
            this.requestAmount = Long.MAX_VALUE;
            return this;
        }

        /**
         * 开始订阅。
         *
         * @return 订阅控制句柄
         */
        public FlowStreamSubscription start() {
            AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscriptionRef.set(subscription);
                    onSubscribe.accept(subscription);
                    subscription.request(requestAmount);
                }

                @Override
                public void onNext(T item) {
                    onNext.accept(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    onError.accept(throwable);
                }

                @Override
                public void onComplete() {
                    onComplete.run();
                }
            });
            return () -> {
                Flow.Subscription subscription = subscriptionRef.get();
                if (subscription != null) {
                    subscription.cancel();
                }
            };
        }
    }
}
