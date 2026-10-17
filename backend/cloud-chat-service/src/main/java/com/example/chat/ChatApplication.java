package com.example.chat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 聊天室服务启动类。
 *
 * <p>对外提供两类能力：HTTP 接口负责房间管理与历史记录查询（server.port），
 * Netty WebSocket 负责实时消息收发（cloud.chat.websocket-port）。
 * 额外扫描 cloud-common 中的在线状态存储与接口调用日志过滤器。
 */
@EnableDiscoveryClient
@MapperScan({"com.example.chat.mapper", "com.example.common.log.mapper"})
@ComponentScan({"com.example.chat", "com.example.common"})
@SpringBootApplication
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
