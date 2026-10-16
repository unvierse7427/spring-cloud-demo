package com.example.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 认证中心启动类。
 * 额外扫描 cloud-common 中的在线状态存储与接口调用日志过滤器。
 */
@EnableDiscoveryClient
@MapperScan({"com.example.auth.mapper", "com.example.common.log.mapper"})
@ComponentScan({"com.example.auth", "com.example.common"})
@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
