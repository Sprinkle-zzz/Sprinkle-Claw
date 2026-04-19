package com.sprinkleclaw.gateway.security;

import com.sprinkleclaw.gateway.ErrorCode;
import com.sprinkleclaw.gateway.GatewayRequest;
import com.sprinkleclaw.gateway.GatewayResponse;
import com.sprinkleclaw.gateway.filter.FilterOrder;
import com.sprinkleclaw.gateway.filter.FilterResult;
import com.sprinkleclaw.gateway.filter.GatewayFilter;

import java.util.List;

/**
 * 输出验证过滤器（postFilter）。检测 Agent 输出中的敏感信息。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class OutputValidator implements GatewayFilter {

    private final List<OutputValidationRule> rules;

    public OutputValidator(OutputValidationRule... rules) {
        this.rules = List.of(rules);
    }

    public OutputValidator(List<OutputValidationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public int order() {
        return FilterOrder.OUTPUT_VALIDATOR;
    }

    @Override
    public FilterResult preFilter(GatewayRequest request) {
        return FilterResult.pass();
    }

    @Override
    public FilterResult postFilter(GatewayRequest request, GatewayResponse response) {
        String output = response.output();
        for (OutputValidationRule rule : rules) {
            var violation = rule.validate(output);
            if (violation.isPresent()) {
                return FilterResult.reject(ErrorCode.SEC_002, violation.get());
            }
        }
        return FilterResult.pass();
    }
}
