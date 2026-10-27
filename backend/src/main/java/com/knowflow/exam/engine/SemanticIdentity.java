package com.knowflow.exam.engine;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 题目的稳定语义身份：用于组卷/AI 出卷时去重。
 *
 * <p>当前题库没有独立 canonical_key 列，采用
 * 「题型 + 归一化标题 + 归一化题干前 64 字」作为身份指纹：
 * 同母题的不同变体若标题高度一致会被判为同一题，避免一张卷内重复；
 * 真正的语义级去重在导入流水线中另有评审（见 content-studio 统一题库索引）。
 */
public final class SemanticIdentity {

    private SemanticIdentity() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        // 去除所有空白与常见标点噪声，使「HashMap 线程安全?」与「hashmap线程安全？」同一身份
        s = s.replaceAll("[\\s\\p{Punct}？?！!。.,，、;；:：\"'“”‘’（）()\\[\\]【】_-]", "");
        return s;
    }

    public static String fingerprint(String questionType, String title, String content) {
        String t = normalize(title);
        String c = normalize(content);
        if (c.length() > 64) {
            c = c.substring(0, 64);
        }
        return (questionType == null ? "" : questionType) + "|" + t + "|" + c;
    }

    public static String topicOf(String tags) {
        if (tags == null || tags.isBlank()) {
            return "";
        }
        String first = tags.split(",")[0];
        return first == null ? "" : first.trim().toUpperCase(Locale.ROOT);
    }
}
