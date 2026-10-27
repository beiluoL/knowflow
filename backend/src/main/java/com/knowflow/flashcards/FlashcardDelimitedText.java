package com.knowflow.flashcards;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded RFC4180-style CSV and quoted TSV. Lines are physical, starting at one. */
public final class FlashcardDelimitedText {
    public static final int MAX_BYTES = 1_048_576, MAX_ROWS = 1000, MAX_COLUMNS = 32, MAX_FIELD = 50_000;
    public record Row(int line, List<String> cells) {}
    private FlashcardDelimitedText() {}
    public static List<Row> parse(String source, char separator) {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("文件为空");
        if (source.length() > MAX_BYTES || source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("文件不能超过1MiB");
        if (source.indexOf('\0') >= 0 || source.indexOf('\ufffd') >= 0)
            throw new IllegalArgumentException("文件包含无效文本，请使用UTF-8");
        if (source.startsWith("\ufeff")) source = source.substring(1);
        List<Row> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        int state = 0, line = 1, startLine = 1; // 0 start, 1 unquoted, 2 quoted, 3 after closing quote
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (state == 2) {
                if (c == '"') state = 3;
                else { field.append(c); if (c == '\n' || c == '\r' && (i+1 == source.length() || source.charAt(i+1) != '\n')) line++; }
            } else if (state == 3 && c == '"') { field.append('"'); state = 2; }
            else if (c == separator || c == '\n' || c == '\r') {
                addField(cells, field, line); state = 0;
                if (c == '\n' || c == '\r') { addRow(rows, cells, startLine); cells = new ArrayList<>(); if(c == '\r' && i+1 < source.length() && source.charAt(i+1) == '\n') i++; line++; startLine = line; }
            } else if (c == '"' && state == 0) state = 2;
            else {
                if (c == '"' || state == 3) throw new IllegalArgumentException("第" + line + "行引号格式错误");
                field.append(c); state = 1;
            }
            if (field.length() > MAX_FIELD && !(field.length() == MAX_FIELD+1 && field.charAt(0) == '\'')) throw new IllegalArgumentException("第" + line + "行字段超过50000字符");
        }
        if (state == 2) throw new IllegalArgumentException("第" + startLine + "行引号未闭合");
        if (state != 0 || !cells.isEmpty() || field.length() > 0) { addField(cells, field, line); addRow(rows, cells, startLine); }
        if (rows.size() < 2) throw new IllegalArgumentException("文件需要表头与至少一行内容");
        return rows;
    }
    private static void addField(List<String> fields, StringBuilder value, int line) {
        if (fields.size() >= MAX_COLUMNS) throw new IllegalArgumentException("第" + line + "行超过32列");
        fields.add(value.toString()); value.setLength(0);
    }
    private static void addRow(List<Row> rows, List<String> cells, int line) {
        if (rows.size() >= MAX_ROWS + 1) throw new IllegalArgumentException("最多导入1000行内容");
        rows.add(new Row(line, List.copyOf(cells)));
    }
    /** Exact content export, always quoted; spreadsheet formula execution must be disabled by consumers. */
    public static String encode(List<List<String>> rows, char separator) {
        StringBuilder out = new StringBuilder();
        for (var row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(separator);
                out.append('"').append(Objects.toString(row.get(i), "").replace("\"", "\"\"")).append('"');
            }
            out.append("\r\n");
        }
        return out.toString();
    }
}
