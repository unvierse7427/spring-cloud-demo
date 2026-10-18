package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.dto.OcrText;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Python OCR 服务的 HTTP 客户端：以 multipart/form-data 上传图片，
 * 取回识别出的文本。服务由 PaddleOCR 驱动，详见 ocr-service/main.py。
 */
@Service
public class OcrClient {

    private static final Logger log = LoggerFactory.getLogger(OcrClient.class);

    private final HttpClient httpClient;

    private final RagProperties.Ocr ocr;

    private final ObjectMapper objectMapper;

    public OcrClient(RagProperties properties, ObjectMapper objectMapper) {
        this.ocr = properties.getOcr();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(ocr.getConnectTimeout()))
                .build();
    }

    /**
     * 识别图片中的文字。
     *
     * @param fileName    原始文件名，仅用于判断扩展名与日志
     * @param imageBytes  图片字节内容
     * @param contentType 图片 MIME 类型
     * @return 识别结果，图片为空或未识别出文字时 text 为空字符串
     */
    public OcrText recognize(String fileName, byte[] imageBytes, String contentType) {
        String boundary = "----RagOcrBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, fileName, contentType, imageBytes);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ocr.getBaseUrl() + "/ocr"))
                .timeout(Duration.ofMillis(ocr.getReadTimeout()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        long started = System.currentTimeMillis();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OCR 请求被中断：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("调用 OCR 服务失败（" + ocr.getBaseUrl() + "）：" + e.getMessage(), e);
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("OCR 服务返回内容无法解析：" + truncate(response.body()));
        }

        int code = node.path("code").asInt(response.statusCode());
        if (response.statusCode() != 200 || code != 200) {
            throw new IllegalStateException("OCR 识别失败：" + node.path("message").asText("HTTP " + response.statusCode()));
        }

        OcrText result = OcrText.of(node.path("text").asText(""), node.path("lineCount").asInt(0),
                node.path("avgScore").asDouble(0), node.path("elapsedMs").asLong(0));
        result.setMarkdown(node.path("markdown").asText(""));
        result.setTableDetected(node.path("tableDetected").asBoolean(false));
        result.setTableRows(node.path("tableRows").asInt(0));
        result.setTableCols(node.path("tableCols").asInt(0));
        log.info("OCR 识别完成：fileName={}, 行数={}, 表格={}, 往返耗时={}ms",
                fileName, result.getLineCount(),
                result.isTableDetected() ? result.getTableRows() + "行x" + result.getTableCols() + "列" : "无",
                System.currentTimeMillis() - started);
        return result;
    }

    /** 手工拼装 multipart 请求体：图片字段名为 file，与 Python 侧接口保持一致 */
    private byte[] buildMultipartBody(String boundary, String fileName, String contentType, byte[] imageBytes) {
        String safeName = asciiFileName(fileName);
        String type = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        ByteArrayOutputStream out = new ByteArrayOutputStream(imageBytes.length + 512);
        write(out, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: " + type + "\r\n\r\n");
        out.writeBytes(imageBytes);
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    /**
     * 把文件名压成纯 ASCII：中文文件名在不同 multipart 解析实现下编码不一致，
     * 而我们只需要扩展名，直接替换非 ASCII 字符可规避乱码问题。
     */
    private String asciiFileName(String fileName) {
        String name = (fileName == null || fileName.isBlank()) ? "image" : fileName;
        StringBuilder builder = new StringBuilder(name.length());
        for (char ch : name.toCharArray()) {
            builder.append(ch >= 0x20 && ch <= 0x7E && ch != '"' && ch != '\\' ? ch : '_');
        }
        return builder.toString();
    }

    private void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 200 ? value.substring(0, 200) : value;
    }
}
