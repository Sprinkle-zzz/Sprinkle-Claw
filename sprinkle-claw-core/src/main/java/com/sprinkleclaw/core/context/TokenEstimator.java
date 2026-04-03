package com.sprinkleclaw.core.context;

import com.sprinkleclaw.protocol.message.ContentBlock;
import com.sprinkleclaw.protocol.message.ContentBlock.*;
import com.sprinkleclaw.protocol.message.Message;
import com.sprinkleclaw.protocol.message.Message.*;

import java.util.List;
import java.util.Map;

/**
 * 基于字符数的快速 Token 估算器。
 *
 * <p>支持 CJK（中日韩）内容自适应估算：根据文本中 CJK 字符的占比动态调整估算因子。
 * 英文内容约 4 字符/token，中文内容约 2 字符/token，混合内容按比例插值。</p>
 *
 * <p>精确的 token 计数需要依赖具体 tokenizer（如 tiktoken），引入外部依赖且不同模型的
 * tokenizer 不同。当前采用字符估算满足压缩阈值判断的精度需求。
 * 更高精度的 tokenizer 方案（jtokkit）计划在 MVP3 实现。</p>
 *
 * @author sprinkle
 * @since 2026/3/23
 */
public final class TokenEstimator {

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
     * 估算纯文本的 token 数，支持 CJK 内容自适应。
     *
     * <p>根据文本中 CJK 字符的占比动态调整估算因子：</p>
     * <ul>
     *   <li>纯英文：约 4 字符/token（factor = 4.0）</li>
     *   <li>纯中文：约 2 字符/token（factor = 2.0）</li>
     *   <li>混合内容：按 CJK 比例线性插值</li>
     * </ul>
     *
     * @param text 要估算的文本
     * @return 估算 token 数
     */
    public int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjkCount = countCjkChars(text);
        double cjkRatio = (double) cjkCount / text.length();
        // CJK 多则因子低（~2），英文多则因子高（~4），混合按比例插值
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
