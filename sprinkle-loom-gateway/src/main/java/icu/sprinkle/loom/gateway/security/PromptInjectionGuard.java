package icu.sprinkle.loom.gateway.security;

import icu.sprinkle.loom.gateway.ErrorCode;
import icu.sprinkle.loom.gateway.GatewayRequest;
import icu.sprinkle.loom.gateway.filter.FilterOrder;
import icu.sprinkle.loom.gateway.filter.FilterResult;
import icu.sprinkle.loom.gateway.filter.GatewayFilter;

/**
 * 提示注入检测过滤器。在 Agent 执行前检测用户消息。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class PromptInjectionGuard implements GatewayFilter {

    private final InjectionDetector detector;

    public PromptInjectionGuard(InjectionDetector detector) {
        this.detector = detector;
    }

    @Override
    public int order() {
        return FilterOrder.PROMPT_INJECTION_GUARD;
    }

    @Override
    public FilterResult preFilter(GatewayRequest request) {
        var detection = detector.detect(request.message());
        if (detection.isPresent()) {
            return FilterResult.reject(ErrorCode.SEC_001, detection.get());
        }
        return FilterResult.pass();
    }
}
