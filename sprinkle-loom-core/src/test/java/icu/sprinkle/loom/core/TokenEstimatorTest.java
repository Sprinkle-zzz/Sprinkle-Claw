package icu.sprinkle.loom.core;

import icu.sprinkle.loom.core.context.TokenEstimator;
import icu.sprinkle.loom.protocol.message.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenEstimator 测试：字符估算与 CJK 自适应。
 *
 * @author sprinkle
 * @since 2026/4/4
 */
class TokenEstimatorTest {

    private final TokenEstimator estimator = new TokenEstimator();

    @Test
    void estimateText_english_approximatelyQuarterLength() {
        String text = "Hello world, this is a test message for token estimation.";
        int tokens = estimator.estimateText(text);
        // 英文约 4 chars/token
        assertThat(tokens).isBetween(text.length() / 5, text.length() / 3);
    }

    @Test
    void estimateText_chinese_approximatelyHalfLength() {
        String text = "这是一段中文测试文本用于验证中文字符的估算精度";
        int tokens = estimator.estimateText(text);
        // 中文约 2 chars/token
        assertThat(tokens).isBetween(text.length() / 3, text.length());
    }

    @Test
    void estimateText_nullOrEmpty_returnsZero() {
        assertThat(estimator.estimateText(null)).isZero();
        assertThat(estimator.estimateText("")).isZero();
    }

    @Test
    void estimate_messageList_sumsAll() {
        List<Message> messages = List.of(
                Message.UserMessage.of("hello"),
                Message.UserMessage.of("world")
        );
        int total = estimator.estimate(messages);
        assertThat(total).isPositive();
        assertThat(total).isGreaterThan(estimator.estimate(messages.get(0)));
    }

    @Test
    void forModel_noJtokkit_fallsBackToCharEstimation() {
        // jtokkit 可能不在 test classpath 中，应 fallback
        TokenEstimator modelEstimator = TokenEstimator.forModel("gpt-4");
        int tokens = modelEstimator.estimateText("test text for estimation");
        assertThat(tokens).isPositive();
    }
}
