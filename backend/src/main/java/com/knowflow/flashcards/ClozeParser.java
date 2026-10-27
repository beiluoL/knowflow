package com.knowflow.flashcards;

import java.util.*;

/**
 * Plain source scanner: never executes HTML, Markdown or code. Rendering must sanitize separately.
 */
public final class ClozeParser {
    private ClozeParser() {}

    public record CardText(String front, String back) {}

    private record Part(String text, Integer ordinal, String hint) {}

    public static SortedMap<Integer, CardText> cards(String source, String explanation) {
        if (source == null || source.length() > 100_000)
            throw new IllegalArgumentException("Cloze 内容为空或过长");
        List<Part> parts = new ArrayList<>();
        SortedSet<Integer> ordinals = new TreeSet<>();
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < source.length(); ) {
            if (source.charAt(i) == '\\' && source.startsWith("{{", i + 1)) {
                plain.append("{{");
                i += 3;
                continue;
            }
            if (!source.startsWith("{{c", i)
                    || i + 3 >= source.length()
                    || !Character.isDigit(source.charAt(i + 3))) {
                plain.append(source.charAt(i++));
                continue;
            }
            int start = i, cursor = i + 3;
            while (cursor < source.length() && Character.isDigit(source.charAt(cursor))) cursor++;
            if (cursor == i + 3 || !source.startsWith("::", cursor))
                throw new IllegalArgumentException("无效 Cloze 标记");
            int ordinal;
            try {
                ordinal = Integer.parseInt(source.substring(i + 3, cursor));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Cloze 编号过大");
            }
            if (ordinal < 1 || ordinal > 999) throw new IllegalArgumentException("Cloze 编号应为1–999");
            if (!plain.isEmpty()) {
                parts.add(new Part(plain.toString(), null, null));
                plain.setLength(0);
            }
            cursor += 2;
            int contentStart = cursor, depth = 1, hintStart = -1;
            while (cursor < source.length()) {
                if (source.charAt(cursor) == '\\' && cursor + 1 < source.length()) {
                    cursor += 2;
                    continue;
                }
                if (source.startsWith("{{c", cursor)
                        && cursor + 3 < source.length()
                        && Character.isDigit(source.charAt(cursor + 3)))
                    throw new IllegalArgumentException("嵌套 Cloze 暂不支持，请使用并列编号");
                if (source.startsWith("{{", cursor)) {
                    depth++;
                    cursor += 2;
                    continue;
                }
                if (source.startsWith("}}", cursor)) {
                    if (--depth == 0) break;
                    cursor += 2;
                    continue;
                }
                if (depth == 1 && hintStart < 0 && source.startsWith("::", cursor)) {
                    hintStart = cursor;
                    cursor += 2;
                    continue;
                }
                cursor++;
            }
            if (depth != 0) throw new IllegalArgumentException("未闭合 Cloze 标记，位置 " + start);
            String answer = source.substring(contentStart, hintStart < 0 ? cursor : hintStart);
            if (answer.isBlank()) throw new IllegalArgumentException("Cloze 答案不能为空");
            parts.add(
                    new Part(
                            answer,
                            ordinal,
                            hintStart < 0 ? null : source.substring(hintStart + 2, cursor)));
            ordinals.add(ordinal);
            i = cursor + 2;
        }
        if (!plain.isEmpty()) parts.add(new Part(plain.toString(), null, null));
        if (ordinals.isEmpty() || ordinals.size() > 100)
            throw new IllegalArgumentException("需要1–100个不同 Cloze 编号");
        SortedMap<Integer, CardText> cards = new TreeMap<>();
        for (int ordinal : ordinals) {
            StringBuilder front = new StringBuilder(), back = new StringBuilder();
            for (Part p : parts) {
                back.append(p.text());
                if (Objects.equals(p.ordinal(), ordinal))
                    front.append(p.hint() == null ? "[ … ]" : "[" + p.hint() + "]");
                else front.append(p.text());
            }
            if (explanation != null && !explanation.isBlank())
                back.append("\n\n").append(explanation);
            cards.put(ordinal, new CardText(front.toString(), back.toString()));
        }
        return cards;
    }
}
