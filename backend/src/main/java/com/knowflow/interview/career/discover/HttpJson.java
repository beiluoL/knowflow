package com.knowflow.interview.career.discover;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 外部岗位 API 的最小 HTTP 客户端。
 *
 * <p>硬边界：8 秒连接/12 秒请求超时、2MB 响应上限、只接受 2xx 与 JSON。
 * 任何失败都转成 {@link UpstreamException}（不含堆栈与原始响应体），由搜索服务降级处理，
 * 不允许把第三方异常原文透传给前端。</p>
 */
public class HttpJson {

    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper json;

    public HttpJson(ObjectMapper json) {
        this.json = json;
    }

    public JsonNode get(String url, String userAgent) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException invalid) {
            throw new UpstreamException("INVALID_URL", "岗位来源地址无效");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            throw new UpstreamException("INVALID_URL", "岗位来源地址协议不受支持");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                response.body().close();
                throw new UpstreamException("HTTP_" + code, "岗位来源暂时不可用");
            }
            byte[] bytes = readBounded(response.body());
            if (bytes.length == 0) throw new UpstreamException("EMPTY", "岗位来源返回空内容");
            return json.readTree(bytes);
        } catch (UpstreamException failure) {
            throw failure;
        } catch (java.io.IOException failure) {
            throw new UpstreamException("IO", "岗位来源无法访问");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("INTERRUPTED", "岗位来源请求被中断");
        }
    }

    private byte[] readBounded(InputStream input) throws java.io.IOException {
        var output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                input.close();
                throw new UpstreamException("TOO_LARGE", "岗位来源返回内容过大");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /** 上游异常：code 用于来源状态，message 是可直接展示的中文短句。 */
    public static class UpstreamException extends BusinessException {
        private final String code;

        public UpstreamException(String code, String message) {
            super(502, message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
