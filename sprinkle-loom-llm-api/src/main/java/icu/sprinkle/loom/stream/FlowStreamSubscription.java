package icu.sprinkle.loom.stream;

/**
 * 订阅 JDK Flow publisher 后返回的控制句柄。
 *
 * @author sprinkle
 * @since 2026/5/10
 */
public interface FlowStreamSubscription {

    /**
     * 取消底层 Flow 订阅。
     */
    void cancel();
}
