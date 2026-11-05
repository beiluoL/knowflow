package com.knowflow.interview.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Consumer;

/** 仅提取根 followupQuestion 字符串作为临时文本，不输出评价 JSON。 */
final class FollowupQuestionDelta {
    private static final int MAX_DOCUMENT_CHARS = 120000;
    private final ObjectMapper json;
    private final Consumer<String> consumer;
    private final StringBuilder document = new StringBuilder();
    private String emitted = "";

    FollowupQuestionDelta(ObjectMapper json, Consumer<String> consumer) {
        this.json = json;
        this.consumer = consumer;
    }

    void accept(String delta) {
        if (delta.length() > MAX_DOCUMENT_CHARS - document.length()) { throw new IllegalArgumentException("Stream limit"); }
        document.append(delta);
        String text = document.toString();
        int depth = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '{' || current == '[') { depth++; }
            else if (current == '}' || current == ']') { depth--; }
            else if (current == '"') {
                int end = stringEnd(text, index + 1);
                if (end < 0) { return; }
                String key = text.substring(index + 1, end);
                int next = skipSpace(text, end + 1);
                if (depth == 1 && "followupQuestion".equals(key) && next < text.length() && text.charAt(next) == ':') {
                    next = skipSpace(text, next + 1);
                    if (next >= text.length() || text.charAt(next) != '"') { return; }
                    emitPrefix(text, next + 1);
                    return;
                }
                index = end;
            }
        }
    }

    private void emitPrefix(String text, int start) {
        int end = stringEnd(text, start);
        String raw = text.substring(start, end < 0 ? text.length() : end);
        // 令牌边界处未完成的转义或 Unicode 序列等待下一个真实增量。
        for (int trim = 0; trim <= Math.min(6, raw.length()); trim++) {
            try {
                String decoded = json.readValue("\"" + raw.substring(0, raw.length() - trim) + "\"", String.class);
                if (!decoded.isEmpty() && Character.isHighSurrogate(decoded.charAt(decoded.length() - 1))) {
                    decoded = decoded.substring(0, decoded.length() - 1);
                }
                if (!decoded.startsWith(emitted)) { throw new IllegalArgumentException("Stream prefix changed"); }
                if (decoded.length() > emitted.length()) {
                    String added = decoded.substring(emitted.length());
                    emitted = decoded;
                    consumer.accept(added);
                }
                return;
            } catch (com.fasterxml.jackson.core.JsonProcessingException incomplete) {
                // 仅允许末尾有界转义处于未完成状态；最终 JSON 仍须完整校验。
            }
        }
    }

    private static int stringEnd(String text, int start) {
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char current = text.charAt(index);
            if (escaped) { escaped = false; }
            else if (current == '\\') { escaped = true; }
            else if (current == '"') { return index; }
        }
        return -1;
    }

    private static int skipSpace(String text, int start) {
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) { start++; }
        return start;
    }
}
