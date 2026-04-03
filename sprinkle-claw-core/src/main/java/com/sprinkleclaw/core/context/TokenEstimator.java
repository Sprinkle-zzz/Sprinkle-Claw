package com.sprinkleclaw.core.context;

import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.ContentBlock.*;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.message.Message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Token 估算器，支持字符估算和 jtokkit 精确估算两种模式。
 *
 * <p>字符估算（默认）：根据文本中 CJK 字符的占比动态调整估算因子。
 * 英文内容约 4 字符/token，中文内容约 2 字符/token，混合内容按比例插值。</p>
 *
 * <p>jtokkit 精确估算（MVP3 新增）：当 classpath 中存在 jtokkit 库时，
 * 使用实际 tokenizer 进行精确估算。通过 {@link #forModel(String)} 工厂方法创建。
 * jtokkit 不可用时自动 fallback 到字符估算。</p>
 *
 * @author sprinkle
 * @since 2026/3/23
 */
public final class TokenEstimator {

    private static final Logger log = LoggerFactory.getLogger(TokenEstimator.class);

    /**
     * 角色标记和消息结构的固定开销（约 4 tokens）
     */
    private static final int ROLE_OVERHEAD = 4;

    /**
     * 工具调用 ID 的固定开销（约 10 tokens）
     */
    private static final int TOOL_CALL_ID_OVERHEAD = 10;

    /**
     * 工具名称的固定开销（约 5 tokens）
     */
    private static final int TOOL_NAME_OVERHEAD = 5;

    /**
     * 纯英文估算因子：约 4 字符/token
     */
    private static final double ENGLISH_FACTOR = 4.0;

    /**
     * 纯 CJK 估算因子：约 2 字符/token
     */
    private static final double CJK_FACTOR = 2.0;

    /**
     * jtokkit Encoding 实例（通过反射持有，null 表示使用字符估算）
     */
    private final Object jtokkitEncoding;

    /**
     * 创建默认的字符估算器。
     */
    public TokenEstimator() {
        this.jtokkitEncoding = null;
    }

    /**
     * 创建带 jtokkit encoding 的精确估算器（内部构造）。
     */
    private TokenEstimator(Object jtokkitEncoding) {
        this.jtokkitEncoding = jtokkitEncoding;
    }

    /**
     * 根据模型名称创建精确估算器。
     * <p>如果 classpath 中存在 jtokkit 库且模型受支持，返回精确估算器；
     * 否则 fallback 到字符估算器。</p>
     *
     * @param modelName 模型名称（如 "gpt-4", "claude-3-opus" 等）
     * @return TokenEstimator 实例
     */
    public static TokenEstimator forModel(String modelName) {
        try {
            Class<?> registryClass = Class.forName("com.knuddels.jtokkit.Encodings");
            Object registry = registryClass.getMethod("newDefaultEncodingRegistry").invoke(null);

            // 尝试使用 cl100k_base（GPT-4/ChatGPT 的默认编码，也适用于 Claude 近似估算）
            Class<?> encodingTypeClass = Class.forName("com.knuddels.jtokkit.api.EncodingType");
            Object cl100kBase = Enum.valueOf(
                    encodingTypeClass.asSubclass(Enum.class), "CL100K_BASE");
            Object encoding = registry.getClass()
                    .getMethod("getEncoding", encodingTypeClass)
                    .invoke(registry, cl100kBase);

            log.info("[TokenEstimator] 使用 jtokkit (cl100k_base) 精确估算器，模型: {}", modelName);
            return new TokenEstimator(encoding);
        } catch (ClassNotFoundException e) {
            log.debug("[TokenEstimator] jtokkit 不在 classpath 中，使用字符估算");
            return new TokenEstimator();
        } catch (Exception e) {
            log.warn("[TokenEstimator] jtokkit 初始化失败，使用字符估算: {}", e.getMessage());
            return new TokenEstimator();
        }
    }

    /**
     * 估算单条消息的 token 数。
     * <p>包含消息角色标记、内容、工具调用参数等所有序列化后的文本。</p>
     *
     * @param message 要估算的消息
     * @return 估算 token 数
     */
    public int estimate(Message message) {
        return switch (message) {
            case UserMessage um -> ROLE_OVERHEAD + estimateContentBlocks(um.content());
            case AssistantMessage am -> ROLE_OVERHEAD + estimateContentBlocks(am.content());
            case ToolResultMessage tr -> ROLE_OVERHEAD + TOOL_CALL_ID_OVERHEAD + estimateText(tr.content());
        };
    }

    /**
     * 估算消息列表的总 token 数。
     *
     * @param messages 消息列表
     * @return 总估算 token 数
     */
    public int estimate(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            total += estimate(msg);
        }
        return total;
    }

    /**
     * 估算纯文本的 token 数。
     * <p>如果 jtokkit 可用则使用精确估算，否则使用 CJK 自适应字符估算。</p>
     *
     * @param text 要估算的文本
     * @return 估算 token 数
     */
    public int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (jtokkitEncoding != null) {
            return jtokkitEstimate(text);
        }
        return charEstimate(text);
    }

    /**
     * 使用 jtokkit 进行精确 token 估算。
     */
    private int jtokkitEstimate(String text) {
        try {
            Object result = jtokkitEncoding.getClass()
                    .getMethod("countTokens", String.class)
                    .invoke(jtokkitEncoding, text);
            return (int) result;
        } catch (Exception e) {
            log.debug("[TokenEstimator] jtokkit 估算失败，fallback 到字符估算: {}", e.getMessage());
            return charEstimate(text);
        }
    }

    /**
     * 字符估算：基于 CJK 字符比例动态调整因子。
     *
     * <ul>
     *   <li>纯英文：约 4 字符/token（factor = 4.0）</li>
     *   <li>纯中文：约 2 字符/token（factor = 2.0）</li>
     *   <li>混合内容：按 CJK 比例线性插值</li>
     * </ul>
     */
    private int charEstimate(String text) {
        int cjkCount = countCjkChars(text);
        double cjkRatio = (double) cjkCount / text.length();
        double factor = ENGLISH_FACTOR - (cjkRatio * (ENGLISH_FACTOR - CJK_FACTOR));
        return (int) Math.ceil(text.length() / factor);
    }

    /**
     * 估算内容块列表的 token 数。
     */
    private int estimateContentBlocks(List<ContentBlock> blocks) {
        int total = 0;
        for (ContentBlock block : blocks) {
            total += switch (block) {
                case TextBlock tb -> estimateText(tb.text());
                case ToolUseBlock tu -> TOOL_NAME_OVERHEAD + TOOL_CALL_ID_OVERHEAD + estimateJson(tu.input());
                case ThinkingBlock tk -> estimateText(tk.thinking());
            };
        }
        return total;
    }

    /**
     * 估算 JSON Map 序列化后的 token 数。
     * <p>JSON 结构中大括号、引号等标点占 token，使用约 3 字符/token 估算。</p>
     */
    private int estimateJson(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            return 0;
        }
        String serialized = json.toString();
        if (jtokkitEncoding != null) {
            return jtokkitEstimate(serialized);
        }
        return (int) Math.ceil(serialized.length() / 3.0);
    }

    /**
     * 统计文本中的 CJK（中日韩）字符数量。
     */
    private static int countCjkChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isCjkChar(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断字符是否为 CJK 字符（中文、日文假名、韩文）。
     */
    private static boolean isCjkChar(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
