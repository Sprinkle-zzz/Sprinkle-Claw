package icu.sprinkle.loom.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 基于关键词/正则的提示注入检测器。内置 8 种常见注入模式。
 *
 * @author sprinkle
 * @since 0.7.0 (MVP6)
 */
public class KeywordInjectionDetector implements InjectionDetector {

    private final List<NamedPattern> patterns;

    public KeywordInjectionDetector() {
        this(List.of());
    }

    /**
     * @param customPatterns 自定义检测模式（追加到内置模式之后）
     */
    public KeywordInjectionDetector(List<Pattern> customPatterns) {
        this.patterns = new ArrayList<>(defaultPatterns());
        customPatterns.forEach(p -> this.patterns.add(new NamedPattern("custom", p)));
    }

    @Override
    public Optional<String> detect(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String lower = content.toLowerCase();
        for (NamedPattern np : patterns) {
            if (np.pattern.matcher(lower).find()) {
                return Optional.of("Injection pattern detected: " + np.name);
            }
        }
        return Optional.empty();
    }

    private static List<NamedPattern> defaultPatterns() {
        return List.of(
                new NamedPattern("ignore_instructions",
                        Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|prompts)")),
                new NamedPattern("system_prompt_override",
                        Pattern.compile("(new|override|replace)\\s+system\\s+(prompt|instructions)")),
                new NamedPattern("role_play_system",
                        Pattern.compile("you\\s+are\\s+now\\s+(a\\s+)?system")),
                new NamedPattern("jailbreak_dan",
                        Pattern.compile("(DAN|do\\s+anything\\s+now)")),
                new NamedPattern("prompt_leak",
                        Pattern.compile("(reveal|show|print|output)\\s+(your|the)\\s+(system\\s+)?(prompt|instructions)")),
                new NamedPattern("delimiter_injection",
                        Pattern.compile("```\\s*(system|assistant)\\s*\\n")),
                new NamedPattern("encoding_bypass",
                        Pattern.compile("(base64|rot13|hex)\\s+(decode|encrypt|encode)")),
                new NamedPattern("instruction_boundary",
                        Pattern.compile("(\\[INST\\]|<<SYS>>|<\\|im_start\\|>)"))
        );
    }

    private record NamedPattern(String name, Pattern pattern) {
    }
}
