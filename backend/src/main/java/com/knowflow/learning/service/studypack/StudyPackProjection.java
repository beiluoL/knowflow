package com.knowflow.learning.service.studypack;

import com.knowflow.exception.BusinessException;
import com.knowflow.flashcards.dto.FlashcardSaveDTO;

import java.security.MessageDigest;
import java.util.*;

/** 仅提供学习包来源摘要与响应键投影。 */
final class StudyPackProjection {
    private StudyPackProjection() {}

    static String textHash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            Objects.requireNonNullElse(value, "")
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<String, Object> camel(Map<String, Object> raw) {
        var out = new LinkedHashMap<String, Object>();
        for (var entry : raw.entrySet()) {
            String key = entry.getKey();
            if (key.equals(key.toUpperCase(Locale.ROOT)) || key.contains("_"))
                key = key.toLowerCase(Locale.ROOT);
            StringBuilder name = new StringBuilder();
            boolean upper = false;
            for (char c : key.toCharArray()) {
                if (c == '_') {
                    upper = true;
                    continue;
                }
                name.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
            out.put(name.toString(), entry.getValue());
        }
        return out;
    }

    static String encode(com.fasterxml.jackson.databind.ObjectMapper json, Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static <T> T decode(
            com.fasterxml.jackson.databind.ObjectMapper json,
            Object raw,
            com.fasterxml.jackson.core.type.TypeReference<T> type) {
        try {
            return json.readValue(raw.toString(), type);
        } catch (Exception e) {
            throw new BusinessException(409, "学习包产物数据不可解析");
        }
    }

    static long num(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    static void validateCards(List<FlashcardSaveDTO> cards) {
        if (cards == null || cards.size() != 5) throw new BusinessException(400, "需要确认5张闪卡");
        var fronts = new HashSet<String>();
        for (var c : cards)
            if (c == null
                    || c.getFront() == null
                    || c.getFront().isBlank()
                    || c.getFront().length() > 200
                    || c.getBack() == null
                    || c.getBack().isBlank()
                    || c.getBack().length() > 2000
                    || !fronts.add(c.getFront().trim()))
                throw new BusinessException(400, "闪卡内容无效或问题重复");
    }
}
