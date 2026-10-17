package com.example.chat.ws;

import com.example.chat.config.ChatProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Netty WebSocket 服务端：随 Spring 容器启动与关闭。
 *
 * <p>管道结构：HTTP 编解码 -> 请求聚合 -> 握手鉴权 -> WebSocket 协议处理 -> 聊天业务处理，
 * 其中业务处理器被放到独立线程组，避免数据库操作阻塞 IO 线程。
 */
@Component
public class ChatWebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketServer.class);

    /** 单个 HTTP 聚合请求的最大字节数，握手报文远小于该值 */
    private static final int MAX_HTTP_CONTENT_LENGTH = 64 * 1024;

    /** WebSocket 单帧最大字节数 */
    private static final int MAX_FRAME_SIZE = 64 * 1024;

    /** 业务线程组大小 */
    private static final int BUSINESS_THREADS = 4;

    private final ChatProperties chatProperties;

    private final JwtTokenVerifier tokenVerifier;

    private final ChatWebSocketHandler chatHandler;

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    private DefaultEventExecutorGroup businessGroup;

    private Channel serverChannel;

    public ChatWebSocketServer(ChatProperties chatProperties,
                               JwtTokenVerifier tokenVerifier,
                               ChatWebSocketHandler chatHandler) {
        this.chatProperties = chatProperties;
        this.tokenVerifier = tokenVerifier;
        this.chatHandler = chatHandler;
    }

    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("chat-boss"));
        workerGroup = new NioEventLoopGroup(new DefaultThreadFactory("chat-worker"));
        businessGroup = new DefaultEventExecutorGroup(BUSINESS_THREADS, new DefaultThreadFactory("chat-biz"));

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(MAX_HTTP_CONTENT_LENGTH))
                                .addLast(new ChunkedWriteHandler())
                                .addLast(new HandshakeAuthHandler(tokenVerifier))
                                // 最后一个参数 checkStartsWith=true：
                                // 握手请求路径会带 ?token=xxx，必须用前缀匹配而非全等匹配
                                .addLast(new WebSocketServerProtocolHandler(
                                        chatProperties.getWebsocketPath(), null, true, MAX_FRAME_SIZE,
                                        false, true))
                                .addLast(businessGroup, chatHandler);
                    }
                });

        serverChannel = bootstrap.bind(chatProperties.getWebsocketPort()).sync().channel();
        log.info("Netty WebSocket 服务已启动：ws://0.0.0.0:{}{}",
                chatProperties.getWebsocketPort(), chatProperties.getWebsocketPath());
    }

    @PreDestroy
    public void stop() {
        log.info("正在关闭 Netty WebSocket 服务 ...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (businessGroup != null) {
            businessGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }
}
