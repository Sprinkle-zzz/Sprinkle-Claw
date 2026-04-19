package com.sprinkleclaw.gateway.security;

import java.util.Optional;

/**
 * 输出验证规则接口。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public interface OutputValidationRule {

    /**
     * 验证输出内容。
     *
     * @param output Agent 输出文本
     * @return 验证失败时返回描述，通过返回 empty
     */
    Optional<String> validate(String output);
}
