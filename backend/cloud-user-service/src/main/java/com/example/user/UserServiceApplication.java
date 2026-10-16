package com.example.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 用户服务启动类。
 * 额外扫描 cloud-common 中的在线状态存储与接口调用日志过滤器。
 */
@EnableDiscoveryClient
@MapperScan({"com.example.user.mapper", "com.example.common.log.mapper"})
@ComponentScan({"com.example.user", "com.example.common"})
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
