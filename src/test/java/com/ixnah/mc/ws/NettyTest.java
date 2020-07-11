package com.ixnah.mc.ws;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.ixnah.mc.ws.handler.HttpClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import org.junit.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.UUID;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderNames.AUTHORIZATION;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.NO_CACHE;
import static io.netty.handler.codec.http.HttpHeaderValues.NO_STORE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpScheme.HTTP;
import static io.netty.handler.codec.http.HttpScheme.HTTPS;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/7/10 8:31
 */
public class NettyTest {

    private static final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36 Edg/83.0.478.58";

//    @Test
    public void test() throws Throwable {
        URI uri = URI.create("ws://127.0.0.1/");
        InetAddress address = InetAddress.getByName(uri.getHost());
        boolean isSecure = uri.getScheme().equalsIgnoreCase("wss");
        int port = uri.getPort() != -1 ? uri.getPort() : isSecure ? HTTPS.port() : HTTP.port();

        HttpClientHandler httpClient = new HttpClientHandler();
        EventLoopGroup group = new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).build());
        Channel ch = new Bootstrap().group(group).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.config().setOption(ChannelOption.SO_KEEPALIVE, true);
                ch.pipeline()
                        .addLast("timeout", new ReadTimeoutHandler(30))
                        .addLast("HttpClientCodec", new HttpClientCodec())
                        .addLast("HttpObjectAggregator", new HttpObjectAggregator(8192))
                        .addLast("HttpClientHandler", httpClient);
            }
        }).channel(NioSocketChannel.class).connect(address, port).syncUninterruptibly().channel();
        System.out.println("afterConnect: " + ch.pipeline());
        HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, uri.getPath());
        request.headers()
                .add(HOST, uri.getHost())
                .add(USER_AGENT, userAgent)
                .add(PRAGMA, NO_CACHE)
                .add(CACHE_CONTROL, NO_STORE) // 设置CDN不缓存
                .add(CONNECTION, KEEP_ALIVE) // 设置长链接
                .add(AUTHORIZATION, UUID.randomUUID().toString().replace("-", "")); // 传递UUID
        Future<HttpResponse> responseFuture = httpClient.sendRequest(request).syncUninterruptibly();
        System.out.println("syncUninterruptibly: " + ch.pipeline());
        HttpResponse response = responseFuture.getNow();
        System.out.println("getNow: " + ch.pipeline());
//        System.out.println(response);
        ch.closeFuture().syncUninterruptibly();
    }
}
