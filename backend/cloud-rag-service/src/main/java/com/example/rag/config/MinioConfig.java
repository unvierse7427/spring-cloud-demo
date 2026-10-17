package com.example.rag.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置
 */
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(RagProperties properties) {
        RagProperties.Minio minio = properties.getMinio();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }
}
