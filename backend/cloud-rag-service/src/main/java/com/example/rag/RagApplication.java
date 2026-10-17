package com.example.rag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * RAG 知识库服务启动类。
 *
 * <p>注册到 Nacos 后由网关的 /api/rag/** 路由转发；
 * 额外扫描 cloud-common 中的在线状态存储与接口调用日志过滤器。
 */
@EnableDiscoveryClient
@ConfigurationPropertiesScan
@MapperScan({"com.example.rag.mapper", "com.example.common.log.mapper"})
@ComponentScan({"com.example.rag", "com.example.common"})
@SpringBootApplication
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
