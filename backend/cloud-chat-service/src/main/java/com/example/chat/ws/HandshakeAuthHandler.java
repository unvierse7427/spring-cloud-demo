package com.example.chat.ws;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.util.List;

/**
 * 握手鉴权处理器：位于 WebSocket 协议处理器之前。
 *
 * <p>从升级请求的 query 中取出 token 并验签，通过则把用户身份写入 Channel 属性
 * 后继续握手，失败直接返回 401 并关闭连接。
 */
public class HandshakeAuthHandler extends ChannelInboundHandlerAdapter {

    /** 连接上的登录身份，供业务处理器读取 */
    public static final AttributeKey<JwtTokenVerifier.ChatUser> USER_KEY = AttributeKey.valueOf("chatUser");

    private static final String TOKEN_PARAM = "token";

    private static final String UNAUTHORIZED_BODY = "{\"code\":401,\"message\":\"令牌无效或已过期\"}";

    private final JwtTokenVerifier tokenVerifier;

    public HandshakeAuthHandler(JwtTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }

        FullHttpRequest request = (FullHttpRequest) msg;
        JwtTokenVerifier.ChatUser user = tokenVerifier.verify(resolveToken(request.uri()));
        if (user == null) {
            ReferenceCountUtil.release(request);
            writeUnauthorized(ctx);
            return;
        }

        ctx.channel().attr(USER_KEY).set(user);
        // 继续交给 WebSocketServerProtocolHandler 完成握手
        ctx.fireChannelRead(request);
    }

    private String resolveToken(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        List<String> values = decoder.parameters().get(TOKEN_PARAM);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private void writeUnauthorized(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.UNAUTHORIZED, Unpooled.copiedBuffer(UNAUTHORIZED_BODY, CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
