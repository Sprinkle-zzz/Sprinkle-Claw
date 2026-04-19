package com.sprinkleclaw.gateway.security;

import java.util.Optional;

/**
 * 提示注入检测 SPI。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public interface InjectionDetector {

    /**
     * 检测内容是否包含注入模式。
     *
     * @param content 待检测内容
     * @return 检测到注入时返回描述，否则 empty
     */
    Optional<String> detect(String content);
}
