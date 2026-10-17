package com.example.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 聊天室相关配置，对应 application.yml 中的 cloud.chat
 */
@Component
@ConfigurationProperties(prefix = "cloud.chat")
public class ChatProperties {

    /** Netty WebSocket 监听端口 */
    private int websocketPort = 9001;

    /** Netty WebSocket 握手路径 */
    private String websocketPath = "/ws/chat";

    /** 加入房间时下发的历史消息条数 */
    private int historyLimit = 50;

    /** 单条聊天消息最大长度 */
    private int maxContentLength = 1000;

    public int getWebsocketPort() {
        return websocketPort;
    }

    public void setWebsocketPort(int websocketPort) {
        this.websocketPort = websocketPort;
    }

    public String getWebsocketPath() {
        return websocketPath;
    }

    public void setWebsocketPath(String websocketPath) {
        this.websocketPath = websocketPath;
    }

    public int getHistoryLimit() {
        return historyLimit;
    }

    public void setHistoryLimit(int historyLimit) {
        this.historyLimit = historyLimit;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }
}
