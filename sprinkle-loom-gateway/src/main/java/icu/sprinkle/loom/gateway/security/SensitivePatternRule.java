package icu.sprinkle.loom.gateway.security;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 敏感信息检测规则。检测输出中的 API Key、信用卡号、SSN 等。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class SensitivePatternRule implements OutputValidationRule {

    private final List<NamedPattern> patterns;

    public SensitivePatternRule() {
        this.patterns = defaultPatterns();
    }

    @Override
    public Optional<String> validate(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        for (NamedPattern np : patterns) {
            if (np.pattern.matcher(output).find()) {
                return Optional.of("Sensitive data detected: " + np.name);
            }
        }
        return Optional.empty();
    }

    private static List<NamedPattern> defaultPatterns() {
        return List.of(
                new NamedPattern("api_key",
                        Pattern.compile("(sk-[a-zA-Z0-9]{20,}|AKIA[A-Z0-9]{16})")),
                new NamedPattern("credit_card",
                        Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b")),
                new NamedPattern("ssn",
                        Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b")),
                new NamedPattern("private_key",
                        Pattern.compile("-----BEGIN\\s+(RSA\\s+)?PRIVATE\\s+KEY-----"))
        );
    }

    private record NamedPattern(String name, Pattern pattern) {
    }
}
