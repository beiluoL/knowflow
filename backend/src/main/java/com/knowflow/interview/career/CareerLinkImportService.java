package com.knowflow.interview.career;

import com.knowflow.exception.BusinessException;
import com.knowflow.knowledge.service.DocumentTextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 从公开岗位链接导入 JD 文本。
 *
 * <p>只接受公开 http/https 地址，拒绝本机、内网、链路本地、组播与受限端口，避免把
 * 服务端变成 SSRF 跳板；重定向逐跳复核。第三方登录、反爬或访问限制不会被绕过，
 * 抓取失败时前端回退为手动粘贴。</p>
 */
@Service
@RequiredArgsConstructor
public class CareerLinkImportService {

    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final CareerRecordService records;
    private final DocumentTextExtractor extractor;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public Map<String,Object> importUrl(Long userId, String rawUrl) {
        URI current = validate(rawUrl);
        HttpResponse<InputStream> response = null;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                var request = HttpRequest.newBuilder(current)
                        .GET()
                        .timeout(Duration.ofSeconds(12))
                        .header("User-Agent", "KnowFlowCareerWorkbench/1.0")
                        .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9")
                        .build();
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int code = response.statusCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    response.body().close();
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new BusinessException(422, "岗位链接重定向缺少目标地址"));
                    current = validate(current.resolve(location).toString());
                    continue;
                }
                if (code != 200) {
                    response.body().close();
                    throw new BusinessException(422, "岗位链接返回 " + code + "，请手动粘贴 JD");
                }
                return extract(userId, current, response);
            }
            throw new BusinessException(422, "岗位链接重定向次数过多，请手动粘贴 JD");
        } catch (BusinessException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new BusinessException(422, "岗位链接无法访问，请手动粘贴 JD");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "链接抓取被中断，请稍后重试或手动粘贴");
        }
    }

    private Map<String,Object> extract(Long userId, URI uri, HttpResponse<InputStream> response) throws IOException {
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
        if (!contentType.contains("text/html")
                && !contentType.contains("application/xhtml+xml")
                && !contentType.contains("text/plain")) {
            response.body().close();
            throw new BusinessException(422, "链接不是可解析的网页或文本，请手动粘贴 JD");
        }
        long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (declared > MAX_BYTES) {
            response.body().close();
            throw new BusinessException(422, "岗位链接内容超过2MB");
        }
        byte[] bytes;
        try (InputStream input = response.body()) {
            bytes = readBounded(input);
        }
        String text = extractor.extractText(new LinkFile(bytes, contentType));
        if (text == null || text.isBlank() || text.length() > 50000) {
            throw new BusinessException(422, "未从链接提取到可用 JD 文本，请手动粘贴");
        }
        String title = titleOf(bytes);
        if (title.isBlank()) title = uri.getHost();
        if (title.length() > 160) title = title.substring(0, 160);
        return records.create(userId, "JOB", new CareerRecordService.Edit(null, title, text, Map.of()));
    }

    private byte[] readBounded(InputStream input) throws IOException {
        var output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) throw new BusinessException(422, "岗位链接内容超过2MB");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String titleOf(byte[] bytes) {
        String html = new String(bytes, StandardCharsets.UTF_8);
        var matcher = TITLE.matcher(html);
        if (!matcher.find()) return "";
        String value = matcher.group(1)
                .replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    /** 只允许公开 http/https、80/443 端口，并拒绝解析到内网地址。 */
    URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > 2048) {
            throw new BusinessException(400, "岗位链接无效");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(400, "岗位链接格式无效");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException(400, "只支持 http 或 https 岗位链接");
        }
        if (uri.getUserInfo() != null) throw new BusinessException(400, "岗位链接不能包含账号信息");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new BusinessException(400, "岗位链接缺少域名");
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) throw new BusinessException(400, "岗位链接端口不受支持");
        rejectHost(host);
        return uri;
    }

    private void rejectHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost") || normalized.endsWith(".local")
                || normalized.endsWith(".internal") || normalized.endsWith(".localhost")) {
            throw new BusinessException(400, "不允许导入本机或内网地址");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress() || isUniqueLocal(address)
                        || isCarrierGradeNat(address)) {
                    throw new BusinessException(400, "不允许导入本机或内网地址");
                }
            }
        } catch (IOException unresolvable) {
            throw new BusinessException(422, "岗位链接域名无法解析，请手动粘贴 JD");
        }
    }

    private boolean isUniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && bytes[0] == 100 && (bytes[1] & 0xC0) == 64;
    }

    /** 不把用户链接交给解析器日志，也不写出任何公共文件。 */
    private record LinkFile(byte[] bytes, String contentType) implements MultipartFile {
        public String getName() { return "file"; }
        public String getOriginalFilename() { return "linked-job.html"; }
        public String getContentType() { return contentType; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes.clone(); }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        public void transferTo(File destination) {
            throw new UnsupportedOperationException("链接导入不写出公共文件");
        }
    }
}
