package icu.sprinkle.loom.core.loop.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * HITL（Human-in-the-Loop）异步审批管理器。
 * <p>支持两种模式：
 * <ul>
 *   <li>SDK 嵌入模式：通过 {@link ApprovalCallback} 回调</li>
 *   <li>Server 模式：通过 AgentEvent 发布 + REST 端点 {@link #resolve} 完成</li>
 * </ul>
 * <p>审批请求阻塞当前虚拟线程直到收到响应或超时。</p>
 *
 * @author sprinkle
 * @since 2026/4/10
 */
public class ApprovalManager {

    private static final Logger log = LoggerFactory.getLogger(ApprovalManager.class);

    private final ConcurrentHashMap<String, PendingApproval> pending = new ConcurrentHashMap<>();
    private final ApprovalCallback callback;

    /**
     * 创建审批管理器（无回调，Server 模式）。
     */
    public ApprovalManager() {
        this(null);
    }

    /**
     * 创建审批管理器（SDK 嵌入模式）。
     *
     * @param callback 审批回调（null 表示 Server 模式）
     */
    public ApprovalManager(ApprovalCallback callback) {
        this.callback = callback;
    }

    /**
     * 请求审批。阻塞当前线程直到收到响应或超时。
     *
     * @param request 审批请求
     * @return 审批响应
     */
    public ApprovalResponse requestApproval(ApprovalRequest request) {
        var future = new CompletableFuture<ApprovalResponse>();
        pending.put(request.requestId(), new PendingApproval(request, future));

        try {
            if (callback != null) {
                // SDK 嵌入模式：在虚拟线程中回调
                Thread.ofVirtual().start(() -> {
                    try {
                        var response = callback.onApprovalRequired(request);
                        future.complete(response);
                    } catch (Exception e) {
                        log.warn("Approval callback failed for request {}", request.requestId(), e);
                        future.complete(ApprovalResponse.deny("Callback error: " + e.getMessage()));
                    }
                });
            }
            // Server 模式：等待外部 resolve()

            return future.get(request.timeout().toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Approval request {} timed out after {}s",
                    request.requestId(), request.timeout().toSeconds());
            return ApprovalResponse.timeout();
        } catch (Exception e) {
            log.error("Unexpected error waiting for approval {}", request.requestId(), e);
            return ApprovalResponse.deny("Internal error: " + e.getMessage());
        } finally {
            pending.remove(request.requestId());
        }
    }

    /**
     * 外部解决审批（REST 端点调用）。
     *
     * @param requestId 请求 ID
     * @param response  审批响应
     * @return true 如果成功解决（请求存在且未超时）
     */
    public boolean resolve(String requestId, ApprovalResponse response) {
        var pa = pending.get(requestId);
        if (pa == null) return false;
        return pa.future().complete(response);
    }

    /**
     * 获取待审批请求数量。
     */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * 检查指定请求是否待审批。
     */
    public boolean isPending(String requestId) {
        return pending.containsKey(requestId);
    }

    private record PendingApproval(ApprovalRequest request, CompletableFuture<ApprovalResponse> future) {
    }
}
