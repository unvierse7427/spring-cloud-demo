package com.example.rag.service;

import com.example.rag.config.RagProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * MinIO 对象存储：知识库文档的原件都保存在这里，向量化时再按需取回解析
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    /** MinIO 分片下限为 5MiB，这里统一取 10MiB，小文件会退化为单次 PUT */
    private static final long PART_SIZE = 10 * 1024 * 1024;

    /** 预览地址有效期（秒） */
    private static final int PREVIEW_EXPIRY_SECONDS = 600;

    private final MinioClient minioClient;

    private final RagProperties properties;

    public StorageService(MinioClient minioClient, RagProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /** 启动时确保存储桶存在，避免首次上传才报错 */
    @PostConstruct
    public void ensureBucket() {
        String bucket = bucket();
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建 MinIO 存储桶 {}", bucket);
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 初始化失败，请确认服务已启动且账号配置正确：" + e.getMessage(), e);
        }
    }

    public void upload(String objectKey, InputStream in, long size, String contentType) {
        try {
            // 已知长度时按实际大小上传，未知长度（size <= 0）由 SDK 按分片流式上传
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .stream(in, size > 0 ? size : -1, PART_SIZE);
            if (contentType != null && !contentType.isBlank()) {
                builder.contentType(contentType);
            }
            minioClient.putObject(builder.build());
        } catch (Exception e) {
            throw new IllegalStateException("文档上传 MinIO 失败：" + e.getMessage(), e);
        }
    }

    /** 返回的流由调用方负责关闭 */
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucket()).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("从 MinIO 读取文档失败：" + e.getMessage(), e);
        }
    }

    /**
     * 生成对象的临时访问地址，浏览器可直接打开（无需再带认证头）。
     * download 为 false 时按 inline 返回，浏览器内嵌预览；为 true 时按 attachment 返回，直接触发下载。
     */
    public String presignedUrl(String objectKey, String fileName, String contentType, boolean download) {
        String name = StringUtils.hasText(fileName) ? fileName : "document";
        Map<String, String> extraParams = new HashMap<>();
        // RFC 5987 格式，兼容中文文件名
        extraParams.put("response-content-disposition",
                (download ? "attachment" : "inline") + "; filename*=UTF-8''"
                        + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
        if (StringUtils.hasText(contentType)) {
            extraParams.put("response-content-type", contentType);
        }
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket())
                    .object(objectKey)
                    .expiry(PREVIEW_EXPIRY_SECONDS)
                    .extraQueryParams(extraParams)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("生成文档临时访问地址失败：" + e.getMessage(), e);
        }
    }

    public void remove(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket()).object(objectKey).build());
        } catch (Exception e) {
            // 删除失败不阻断业务，仅记录日志
            log.warn("删除 MinIO 对象 {} 失败：{}", objectKey, e.getMessage());
        }
    }

    private String bucket() {
        return properties.getMinio().getBucket();
    }
}
