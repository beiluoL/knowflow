package com.knowflow.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.FlashcardSaveDTO;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Anki .apkg 文件解析器。
 * <p>
 * .apkg 本质是 ZIP 压缩包，解压后含：
 * <ul>
 *   <li>collection.anki2 / collection.anki21b（SQLite 数据库，存 notes/cards/col 表）</li>
 *   <li>media（JSON 文本文件，记录 {"数字ID": "原始文件名"} 映射）</li>
 *   <li>0, 1, 2...（媒体二进制文件，数字命名，对应 media 映射的 key）</li>
 * </ul>
 * 本解析器提取 notes/cards 文字内容，同时落盘媒体文件并把卡片中的媒体引用改写为可访问 URL。
 */
public final class AnkiApkgParser {

    /** Anki 字段分隔符（0x1f） */
    private static final char FLD_SEPARATOR = '\u001f';

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 匹配 <img src="xxx"> 标签 */
    private static final Pattern IMG_PATTERN = Pattern.compile(
            "<img\\s+[^>]*src=[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);

    /** 匹配 [sound:xxx] 引用 */
    private static final Pattern SOUND_PATTERN = Pattern.compile(
            "\\[sound:([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);

    private AnkiApkgParser() {
    }

    /**
     * 解析 .apkg 字节流为闪卡保存 DTO 列表（含媒体 URL 改写）。
     *
     * @param apkgData .apkg 文件字节数组
     * @param uploadDir 媒体文件落盘根目录
     * @return 已解析的闪卡列表（front/back/category/tags 已填充，sourceType 不在这里设置）
     */
    public static List<FlashcardSaveDTO> parse(byte[] apkgData, String uploadDir) {
        if (apkgData == null || apkgData.length == 0) {
            throw new RuntimeException("Anki 导入失败：文件内容为空");
        }
        // 1. 解压 .apkg，收集 collection 数据库 + media 映射 + 媒体文件字节
        ApkgContent content = extractApkgContent(apkgData);
        if (content.dbBytes == null || content.dbBytes.length == 0) {
            throw new RuntimeException("Anki 导入失败：压缩包内未找到 collection.anki2 或 collection.anki21b");
        }
        // 2. 落盘媒体文件，建立 原始文件名 -> URL 映射
        Map<String, String> mediaUrlMap = persistMediaFiles(content, uploadDir);
        // 3. 用 SQLite 解析 cards/notes，改写媒体引用
        return parseSqlite(content.dbBytes, mediaUrlMap);
    }

    /**
     * 从 .apkg（ZIP）中一次性收集 collection 数据库字节、media 映射、媒体文件字节。
     */
    private static ApkgContent extractApkgContent(byte[] apkgData) {
        ApkgContent content = new ApkgContent();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(apkgData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null) {
                    continue;
                }
                String baseName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;

                // collection 数据库文件
                if ("collection.anki2".equals(baseName) || "collection.anki21b".equals(baseName)
                        || baseName.startsWith("collection.anki2")) {
                    if (content.dbBytes == null) {
                        content.dbBytes = readAll(zis);
                    }
                    continue;
                }
                // media 映射 JSON 文件
                if ("media".equals(baseName)) {
                    byte[] mediaJsonBytes = readAll(zis);
                    content.mediaMap = parseMediaMap(new String(mediaJsonBytes, java.nio.charset.StandardCharsets.UTF_8));
                    continue;
                }
                // 数字命名的媒体文件（如 "0"、"1"、"2"...）
                if (isNumeric(baseName)) {
                    content.mediaFiles.put(baseName, readAll(zis));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Anki 导入失败：解压 .apkg 失败 - " + safeMsg(e));
        }
        return content;
    }

    /** 读取 ZipInputStream 当前 entry 的全部字节。 */
    private static byte[] readAll(ZipInputStream zis) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** 判断字符串是否为纯数字（用于识别 Anki 媒体文件名）。 */
    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析 media 文件 JSON 为 {数字ID: 原始文件名} 映射。
     * JSON 结构：{"0": "image1.png", "1": "audio.mp3"}
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> parseMediaMap(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) {
            return map;
        }
        try {
            Map<String, Object> raw = OBJECT_MAPPER.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (raw == null) {
                return map;
            }
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() instanceof String) {
                    map.put(e.getKey(), (String) e.getValue());
                }
            }
        } catch (Exception ignored) {
            // media 映射解析失败时降级为空映射，媒体引用将无法改写但不会中断导入
        }
        return map;
    }

    /**
     * 落盘所有媒体文件，返回 {原始文件名 -> /uploads/anki/.../uuid.ext} 映射。
     */
    private static Map<String, String> persistMediaFiles(ApkgContent content, String uploadDir) {
        Map<String, String> mediaUrlMap = new HashMap<>();
        if (content.mediaFiles.isEmpty() || uploadDir == null || uploadDir.isEmpty()) {
            return mediaUrlMap;
        }
        for (Map.Entry<String, byte[]> e : content.mediaFiles.entrySet()) {
            String numericId = e.getKey();
            byte[] fileData = e.getValue();
            // 从 mediaMap 取原始文件名；若映射缺失则用数字 ID 作为文件名（无扩展名）
            String originalName = content.mediaMap.getOrDefault(numericId, numericId);
            try {
                String url = UploadHelper.saveBytes(fileData, originalName, uploadDir);
                mediaUrlMap.put(originalName, url);
            } catch (IOException ex) {
                // 单个媒体文件落盘失败不中断整体导入，该引用将保持原样（前端可能显示破图）
            }
        }
        return mediaUrlMap;
    }

    /**
     * 用 SQLite JDBC 临时文件方式连接解压出的字节，查询 decks / cards / notes 构造闪卡。
     */
    private static List<FlashcardSaveDTO> parseSqlite(byte[] sqliteBytes, Map<String, String> mediaUrlMap) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Anki 导入失败：未找到 SQLite JDBC 驱动 - " + safeMsg(e));
        }

        java.io.File tmp = null;
        try {
            tmp = java.io.File.createTempFile("anki-collection-", ".anki2");
            tmp.deleteOnExit();
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                fos.write(sqliteBytes);
            }
            String url = "jdbc:sqlite:" + tmp.getAbsolutePath();
            return doParse(url, mediaUrlMap);
        } catch (Exception e) {
            throw new RuntimeException("Anki 导入失败：解析 SQLite 数据库失败 - " + safeMsg(e));
        } finally {
            if (tmp != null && tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    /** 实际执行 SQL 查询，将 cards/notes/decks 组装为 FlashcardSaveDTO 列表（含媒体 URL 改写）。 */
    private static List<FlashcardSaveDTO> doParse(String url, Map<String, String> mediaUrlMap) throws Exception {
        Map<String, String> deckNameById = new HashMap<>();
        List<FlashcardSaveDTO> result = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // 1. 查询 col 表获取 decks JSON
            try (ResultSet rs = stmt.executeQuery("SELECT decks FROM col LIMIT 1")) {
                if (rs.next()) {
                    String decksJson = rs.getString("decks");
                    if (decksJson != null && !decksJson.isEmpty()) {
                        deckNameById = parseDecks(decksJson);
                    }
                }
            }

            // 2. 查询 cards 关联 notes：flds 按 0x1f 分隔，第一段为 front，第二段为 back
            String sql = "SELECT c.did, n.flds, n.tags FROM cards c JOIN notes n ON c.nid = n.id";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long did = rs.getLong("did");
                    String flds = rs.getString("flds");
                    String tags = rs.getString("tags");

                    String[] parts = flds == null ? new String[0] : flds.split(String.valueOf(FLD_SEPARATOR), -1);
                    // Anki 牌组字段数不固定（单词/音标/释义/图片/例句...）：
                    // front 取第 1 段（通常是单词/问题），back 拼接剩余所有字段（含图片/音频/例句），
                    // 确保多字段模板中的媒体引用不被丢弃。
                    String front = parts.length > 0 ? rewriteMedia(parts[0], mediaUrlMap) : "";
                    StringBuilder backBuilder = new StringBuilder();
                    for (int i = 1; i < parts.length; i++) {
                        if (i > 1) {
                            backBuilder.append("<br>");
                        }
                        backBuilder.append(rewriteMedia(parts[i], mediaUrlMap));
                    }
                    String back = backBuilder.toString();
                    if (front.isEmpty() && back.isEmpty()) {
                        continue;
                    }
                    if (front.isEmpty()) {
                        front = back;
                    }

                    FlashcardSaveDTO dto = new FlashcardSaveDTO();
                    dto.setFront(front);
                    dto.setBack(back);
                    dto.setCategory(deckNameById.getOrDefault(String.valueOf(did), "Anki 导入"));
                    dto.setTags(tagsToCsv(tags));
                    result.add(dto);
                }
            }
        }
        return result;
    }

    /**
     * 解析 col 表的 decks JSON 为 {deckId -> deckName} 映射。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> parseDecks(String decksJson) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            Map<String, Object> raw = OBJECT_MAPPER.readValue(decksJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (raw == null) {
                return map;
            }
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getValue() instanceof Map) {
                    Object name = ((Map<String, Object>) e.getValue()).get("name");
                    if (name != null) {
                        map.put(e.getKey(), String.valueOf(name));
                    }
                }
            }
        } catch (Exception ignored) {
            // decks JSON 解析失败时降级为空映射
        }
        return map;
    }

    /**
     * 改写卡片内容中的媒体引用为可访问 URL，并做初步 XSS 清理。
     * <p>
     * 处理：
     * 1. &nbsp; -> 空格
     * 2. 剥离 script/iframe/style 标签（防 XSS）
     * 3. 剥离 on* 事件属性（如 onclick、onerror）
     * 4. &lt;img src="xxx"&gt; 中的 xxx 替换为 URL 映射中的值
     * 5. [sound:xxx] 转为 &lt;audio controls src="URL"&gt;&lt;/audio&gt;
     * 6. 保留 br/div/b/i 等格式标签
     */
    public static String rewriteMedia(String html, Map<String, String> mediaUrlMap) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String s = html;
        // &nbsp; 转空格
        s = s.replace("&nbsp;", " ");

        // 剥离危险标签：script/iframe/style（含内容）
        s = s.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        s = s.replaceAll("(?is)<iframe[^>]*>.*?</iframe>", "");
        s = s.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        // 剥离所有 on* 事件属性（onclick/onerror/onload 等）
        s = s.replaceAll("(?i)\\son\\w+\\s*=\\s*[\"'][^\"']*[\"']", "");

        if (mediaUrlMap != null && !mediaUrlMap.isEmpty()) {
            // 替换 <img src="xxx"> 中的 src
            Matcher imgMatcher = IMG_PATTERN.matcher(s);
            StringBuffer sb = new StringBuffer();
            while (imgMatcher.find()) {
                String src = imgMatcher.group(1);
                String url = mediaUrlMap.get(src);
                if (url != null) {
                    // 改写为带 max-width 样式的 img 标签
                    imgMatcher.appendReplacement(sb, "<img src=\"" + url + "\" style=\"max-width:100%\" />");
                } else {
                    imgMatcher.appendReplacement(sb, imgMatcher.group(0));
                }
            }
            imgMatcher.appendTail(sb);
            s = sb.toString();

            // 替换 [sound:xxx] 为 <audio controls>
            Matcher soundMatcher = SOUND_PATTERN.matcher(s);
            sb = new StringBuffer();
            while (soundMatcher.find()) {
                String src = soundMatcher.group(1);
                String url = mediaUrlMap.get(src);
                if (url != null) {
                    soundMatcher.appendReplacement(sb, "<audio controls src=\"" + url + "\"></audio>");
                } else {
                    soundMatcher.appendReplacement(sb, soundMatcher.group(0));
                }
            }
            soundMatcher.appendTail(sb);
            s = sb.toString();
        }

        // 连续 3+ 换行压缩为 2 个
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    /**
     * 清理 Anki 卡片中常见的 HTML 标签，转为纯文本（保留用于列表预览等纯文本场景）。
     * 处理：&nbsp; -> 空格、&lt;br&gt; -> 换行、剥离其余标签、解码常见实体、压缩空白。
     */
    public static String cleanHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String s = html;
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(div|p|li|h[1-6]|tr)>", "\n");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    /** Anki tags 字段以空格分隔，转为逗号分隔以匹配项目约定。 */
    private static String tagsToCsv(String tags) {
        if (tags == null || tags.trim().isEmpty()) {
            return null;
        }
        String[] arr = tags.trim().split("\\s+");
        List<String> cleaned = new ArrayList<>(arr.length);
        for (String t : arr) {
            if (!t.isEmpty()) {
                cleaned.add(t);
            }
        }
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    /** 安全提取异常 message。 */
    private static String safeMsg(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    /** .apkg 解压产物容器。 */
    private static class ApkgContent {
        byte[] dbBytes;
        Map<String, String> mediaMap = new LinkedHashMap<>();
        Map<String, byte[]> mediaFiles = new LinkedHashMap<>();
    }
}
