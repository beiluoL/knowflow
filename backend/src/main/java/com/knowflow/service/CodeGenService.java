package com.knowflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.dto.CodeGenDTO;
import com.knowflow.entity.OllamaConfig;
import com.knowflow.exception.BusinessException;
import com.knowflow.vo.CodeGenResultVO;
import com.knowflow.vo.GeneratedFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地代码生成服务（Ollama + deepseek-coder）。
 * <p>
 * 职责：把一句自然语言指令（如「替我写一个 html demo 案例」）交给本地 deepseek-coder 模型，
 * 并把模型返回的 Markdown 文本解析成一组可直接落盘的代码文件。
 * <p>
 * 与 {@link OllamaService} 的分工：OllamaService 负责连接与模型管理（列表/加载/删除），
 * 本类负责「生成 + 解析」这条业务链路。二者共用 Ollama 原生 API 与 baseUrl 规范化逻辑。
 * <p>
 * 关键设计：
 * <ul>
 *   <li>使用 /api/chat 非流式接口，一次拿到完整文本再解析，避免代码块被截断在 SSE 分片边界；</li>
 *   <li>用强约束的 system 提示词要求模型以「文件名 + 围栏代码块」的固定格式输出，提高可解析率；</li>
 *   <li>解析分三级降级：带文件名标注的代码块 → 裸代码块按语言推断文件名 → 整段疑似 HTML 兜底；</li>
 *   <li>文件名做路径穿越与非法字符校验，杜绝模型输出 ../../etc/passwd 之类的危险路径。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class CodeGenService {

    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 默认代码生成模型。 */
    public static final String DEFAULT_MODEL = "deepseek-coder:6.7b";

    /** 代码生成默认温度：低温度让输出更确定，减少无关发挥。 */
    private static final double DEFAULT_TEMPERATURE = 0.2D;

    /** 生成请求超时时间。本地 6.7B 模型首次加载较慢，给到 5 分钟。 */
    private static final Duration GENERATE_TIMEOUT = Duration.ofMinutes(5);

    /** 单个文件内容上限，防止模型异常输出撑爆前端与磁盘。 */
    private static final int MAX_FILE_CONTENT_LENGTH = 200_000;

    /** 单次生成允许落盘的最大文件数。 */
    private static final int MAX_FILE_COUNT = 20;

    /**
     * 约束模型输出格式的系统提示词。
     * <p>
     * 明确要求「每个文件一个围栏代码块，块前用 FILE: 标注文件名」，这是后续正则解析的契约。
     */
    private static final String SYSTEM_PROMPT = """
            你是一名资深前端与全栈工程师，负责根据用户的自然语言需求生成可直接运行的完整代码。

            输出规则（必须严格遵守）：
            1. 每个文件必须先单独一行写出文件名标注，格式为：FILE: 文件名，例如 FILE: index.html
            2. 紧接着用 Markdown 围栏代码块输出该文件的完整内容，并标注语言，例如 ```html
            3. 代码必须是完整可运行的，不要使用省略号或“此处省略”之类的占位内容。
            4. 结构拆分原则：当用户要的是「一个网页 + 独立脚本」这类前端功能（如番茄钟、计时器、小游戏、
               待办、表单等）时，必须把结构与行为分离为两个文件——一个 HTML 文件（含 CSS 内联，
               通过 <script src="app.js"></script> 引入脚本）和一个独立的 JavaScript 文件（app.js，
               承载全部交互逻辑，禁止把业务 JS 内联进 HTML）。文件名用 index.html 与 app.js。
               若用户只要一个简单静态页面（无交互或轻微交互），则退化为单个 index.html 内联 CSS/JS。
            5. 文件名只能是简单的相对文件名（可含一层子目录），禁止使用绝对路径或 .. 等上级目录符号。
            6. 代码块之外只允许写一小段简短的中文说明，不要重复粘贴代码。

            示例输出格式（带独立脚本的前端功能）：
            FILE: index.html
            ```html
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>...</head>
            <body>...</body>
            <script src="app.js"></script>
            </html>
            ```
            FILE: app.js
            ```javascript
            // 全部交互逻辑写在这里
            ```
            这是一个完整可运行的番茄钟，打开 index.html 即可使用。
            """;

    /**
     * 匹配「FILE: 文件名」+ 紧随其后的围栏代码块。
     * <p>
     * 兼容模型可能输出的 **FILE: x.html**、`FILE: x.html`、// FILE: x.html 等修饰写法。
     */
    private static final Pattern NAMED_BLOCK_PATTERN = Pattern.compile(
            "(?:^|\\n)[^\\S\\n]*(?://\\s*|#\\s*)?[*`_]{0,2}\\s*(?:FILE|文件|文件名)\\s*[:：]\\s*[*`_]{0,2}\\s*" +
                    "([^\\n*`]+?)[*`_]{0,2}[^\\S\\n]*\\n+```([A-Za-z0-9+#-]*)\\s*\\n(.*?)```",
            Pattern.DOTALL);

    /** 匹配任意围栏代码块（用于无文件名标注时的降级解析）。 */
    private static final Pattern ANY_BLOCK_PATTERN = Pattern.compile(
            "```([A-Za-z0-9+#-]*)\\s*\\n(.*?)```", Pattern.DOTALL);

    /** 合法文件名：字母数字中文下划线短横点，允许一层子目录。 */
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[\\w\\u4e00-\\u9fa5.-]+(/[\\w\\u4e00-\\u9fa5.-]+)?$");

    // ==================== 对外主流程 ====================

    /**
     * 执行一次代码生成。
     *
     * @param userId 当前用户，用于回退读取其 Ollama 配置
     * @param dto    生成请求
     * @return 解析后的文件列表与说明文字
     * @throws BusinessException 当 Ollama 不可达、模型未安装或模型无有效输出时抛出，由全局异常处理器转为友好提示
     */
    public CodeGenResultVO generate(Long userId, CodeGenDTO dto) {
        long start = System.currentTimeMillis();

        OllamaConfig config = ollamaService.getOrCreateConfig(userId);
        String baseUrl = OllamaService.normalizeBaseUrl(
                dto.getBaseUrl() != null && !dto.getBaseUrl().isBlank() ? dto.getBaseUrl() : config.getBaseUrl());
        String model = resolveModel(dto, config);
        double temperature = dto.getTemperature() != null ? dto.getTemperature() : DEFAULT_TEMPERATURE;

        String rawContent = callOllamaChat(baseUrl, model, dto.getPrompt(), temperature);
        if (rawContent == null || rawContent.isBlank()) {
            throw new BusinessException("模型没有返回任何内容，请稍后重试或更换模型");
        }

        CodeGenResultVO result = new CodeGenResultVO();
        result.setModel(model);
        result.setRawContent(rawContent);
        result.setFiles(parseFiles(rawContent));
        result.setExplanation(extractExplanation(rawContent));
        result.setElapsedMs(System.currentTimeMillis() - start);

        if (result.getFiles().isEmpty()) {
            log.warn("代码生成未解析到有效代码块: model={}, rawLength={}", model, rawContent.length());
        }
        return result;
    }

    /**
     * 选择生成模型：请求指定 > 用户默认模型（仅当它是 coder 类模型）> 内置 deepseek-coder。
     * <p>
     * 之所以对用户默认模型做 coder 判断，是因为用户的默认模型可能是聊天模型（如 qwen:chat），
     * 用它做代码生成效果明显更差，这里宁可回退到专用代码模型。
     */
    private String resolveModel(CodeGenDTO dto, OllamaConfig config) {
        if (dto.getModel() != null && !dto.getModel().isBlank()) {
            return dto.getModel().trim();
        }
        String defaultModel = config.getDefaultModel();
        if (defaultModel != null && !defaultModel.isBlank() && defaultModel.toLowerCase().contains("coder")) {
            return defaultModel.trim();
        }
        return DEFAULT_MODEL;
    }

    // ==================== Ollama 调用 ====================

    /**
     * 调用 Ollama /api/chat 非流式接口获取完整回复。
     * <p>
     * 对常见故障做了分类映射，把底层异常翻译成用户能看懂的处置建议。
     */
    private String callOllamaChat(String baseUrl, String model, String prompt, double temperature) {
        try {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("temperature", temperature);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("stream", false);
            body.put("options", options);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", prompt)));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .timeout(GENERATE_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 404) {
                // Ollama 对未安装模型返回 404，这里给出可直接执行的补救命令
                throw new BusinessException("本地未安装模型 " + model + "，请先执行：ollama pull " + model);
            }
            if (resp.statusCode() != 200) {
                throw new BusinessException("Ollama 返回错误 HTTP " + resp.statusCode() + "：" + truncate(resp.body(), 200));
            }

            Map<String, Object> json = objectMapper.readValue(resp.body(), Map.class);
            Map<String, Object> message = (Map<String, Object>) json.get("message");
            return message != null ? (String) message.get("content") : null;

        } catch (BusinessException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("Ollama 生成超时: model={}", model);
            throw new BusinessException("模型响应超时（超过 5 分钟），本地模型可能正在加载，请稍后重试");
        } catch (java.net.ConnectException e) {
            log.warn("Ollama 连接失败: baseUrl={}", baseUrl);
            throw new BusinessException("无法连接本地 Ollama 服务（" + baseUrl + "），请确认已执行 ollama serve 启动服务");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("代码生成被中断，请重试");
        } catch (Exception e) {
            log.error("Ollama 代码生成失败: model={}, err={}", model, e.getMessage(), e);
            throw new BusinessException("代码生成失败：" + e.getMessage());
        }
    }

    // ==================== 输出解析 ====================

    /**
     * 从模型输出中解析出待落盘文件，三级降级保证尽量不空手而归。
     */
    private List<GeneratedFileVO> parseFiles(String raw) {
        // 一级：带 FILE: 标注的代码块，信息最完整
        List<GeneratedFileVO> files = parseNamedBlocks(raw);
        if (!files.isEmpty()) {
            return files;
        }
        // 二级：裸代码块，按语言推断文件名
        files = parseAnonymousBlocks(raw);
        if (!files.isEmpty()) {
            return files;
        }
        // 三级：模型忘了套代码块，但整段本身就是 HTML
        return parseBareHtml(raw);
    }

    /** 一级解析：FILE: 文件名 + 代码块。 */
    private List<GeneratedFileVO> parseNamedBlocks(String raw) {
        List<GeneratedFileVO> files = new ArrayList<>();
        Matcher matcher = NAMED_BLOCK_PATTERN.matcher(raw);
        while (matcher.find() && files.size() < MAX_FILE_COUNT) {
            String fileName = sanitizeFileName(matcher.group(1));
            String language = normalizeLanguage(matcher.group(2));
            String content = matcher.group(3);
            if (fileName == null || content == null || content.isBlank()) {
                continue;
            }
            files.add(buildFile(fileName, language, content));
        }
        return files;
    }

    /** 二级解析：无文件名标注，按代码块语言与出现顺序推断文件名。 */
    private List<GeneratedFileVO> parseAnonymousBlocks(String raw) {
        List<GeneratedFileVO> files = new ArrayList<>();
        Matcher matcher = ANY_BLOCK_PATTERN.matcher(raw);
        int index = 0;
        while (matcher.find() && files.size() < MAX_FILE_COUNT) {
            String language = normalizeLanguage(matcher.group(1));
            String content = matcher.group(2);
            if (content == null || content.isBlank()) {
                continue;
            }
            // 同语言的第二个及以后的块加序号后缀，避免文件名互相覆盖
            String fileName = defaultFileName(language, content, index++);
            files.add(buildFile(fileName, language, content));
        }
        return files;
    }

    /** 三级解析：整段文本疑似 HTML 文档时，直接作为 index.html。 */
    private List<GeneratedFileVO> parseBareHtml(String raw) {
        List<GeneratedFileVO> files = new ArrayList<>();
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase();
        if (lower.contains("<!doctype html") || (lower.contains("<html") && lower.contains("</html>"))) {
            int start = Math.max(lower.indexOf("<!doctype html"), 0);
            if (lower.indexOf("<!doctype html") < 0) {
                start = lower.indexOf("<html");
            }
            int end = lower.lastIndexOf("</html>");
            String content = end > start ? trimmed.substring(start, end + "</html>".length()) : trimmed.substring(start);
            files.add(buildFile("index.html", "html", content));
        }
        return files;
    }

    /** 构造文件 VO，统一做内容裁剪与体积计算。 */
    private GeneratedFileVO buildFile(String fileName, String language, String content) {
        String body = content.strip();
        if (body.length() > MAX_FILE_CONTENT_LENGTH) {
            body = body.substring(0, MAX_FILE_CONTENT_LENGTH);
            log.warn("生成内容超长已截断: fileName={}", fileName);
        }
        return new GeneratedFileVO(fileName, language, body, body.getBytes(StandardCharsets.UTF_8).length);
    }

    /**
     * 提取代码块之外的说明文字，用于在对话区展示。
     */
    private String extractExplanation(String raw) {
        String text = ANY_BLOCK_PATTERN.matcher(raw).replaceAll("");
        // 去掉残留的 FILE: 标注行
        text = text.replaceAll("(?m)^[^\\S\\n]*(?://\\s*|#\\s*)?[*`_]{0,2}\\s*(?:FILE|文件|文件名)\\s*[:：].*$", "");
        text = text.replaceAll("\\n{3,}", "\n\n").strip();
        return truncate(text, 1000);
    }

    // ==================== 文件名处理 ====================

    /**
     * 清洗并校验模型给出的文件名，拒绝路径穿越与非法字符。
     *
     * @return 合法文件名；不合法时返回 null 由调用方跳过
     */
    private String sanitizeFileName(String rawName) {
        if (rawName == null) return null;
        String name = rawName.trim()
                .replaceAll("^[\"'`*]+", "")
                .replaceAll("[\"'`*]+$", "")
                .replace('\\', '/')
                .trim();
        // 去掉模型可能带上的前导 ./ 或绝对路径前缀
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.isBlank() || name.contains("..") || name.contains(":")) {
            log.warn("拒绝不安全的文件名: {}", rawName);
            return null;
        }
        if (!SAFE_FILE_NAME.matcher(name).matches()) {
            log.warn("拒绝非法字符的文件名: {}", rawName);
            return null;
        }
        // 文件名必须带扩展名，否则落盘后无法识别类型
        String lastSegment = name.substring(name.lastIndexOf('/') + 1);
        if (!lastSegment.contains(".")) {
            return null;
        }
        return name;
    }

    /** 归一化代码块语言标记。 */
    private String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) return "text";
        return switch (lang.trim().toLowerCase()) {
            case "js", "jsx", "javascript" -> "javascript";
            case "ts", "tsx", "typescript" -> "typescript";
            case "htm", "html" -> "html";
            case "py", "python" -> "python";
            default -> lang.trim().toLowerCase();
        };
    }

    /** 按语言给无名代码块生成默认文件名。 */
    private String defaultFileName(String language, String content, int index) {
        String suffix = index == 0 ? "" : String.valueOf(index + 1);
        return switch (language) {
            case "html" -> "index" + suffix + ".html";
            case "css" -> "style" + suffix + ".css";
            case "javascript" -> "script" + suffix + ".js";
            case "typescript" -> "script" + suffix + ".ts";
            case "python" -> "main" + suffix + ".py";
            case "java" -> "Main" + suffix + ".java";
            case "json" -> "data" + suffix + ".json";
            case "markdown", "md" -> "README" + suffix + ".md";
            default -> {
                // 未知语言但内容像 HTML 时仍归为 html，覆盖模型漏标语言的情况
                String lower = content.toLowerCase();
                if (lower.contains("<html") || lower.contains("<!doctype html")) {
                    yield "index" + suffix + ".html";
                }
                yield "file" + (index + 1) + ".txt";
            }
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
