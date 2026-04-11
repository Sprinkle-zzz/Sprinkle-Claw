package com.sprinkleclaw.core.loop.approval;

/**
 * SDK 嵌入模式的审批回调接口。
 * <p>用户实现此接口自定义审批逻辑。Server 模式通过 REST 端点 resolve。</p>
 *
 * @author sprinkle
 * @since 2026/4/10
 */
@FunctionalInterface
public interface ApprovalCallback {

    /**
     * 当工具执行需要用户审批时调用。
     *
     * @param request 审批请求
     * @return 审批响应
     */
    ApprovalResponse onApprovalRequired(ApprovalRequest request);
}
