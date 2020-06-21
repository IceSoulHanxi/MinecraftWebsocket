package com.ixnah.mc.ws;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.ixnah.mc.ws.handler.PacketToFrameHandler;
import com.ixnah.mc.ws.handler.WebSocketClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.Promise;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.MessageDeserializer;
import net.minecraft.util.MessageDeserializer2;
import net.minecraft.util.MessageSerializer;
import net.minecraft.util.MessageSerializer2;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/29 20:41
 */
public class WebSocket {

    @Getter
    public static Bootstrap bootstrap;
    @Getter
    public static Channel channel;

    private static final Cache<InetSocketAddress, String> tokens = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES).build();

    private static final List<String> wsPort = Lists.newArrayList();
    private static final List<String> wssPort = Lists.newArrayList();

    static {
        Properties props = new Properties();
        try {
            props.load(WebSocket.class.getResourceAsStream("/websocket.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        wsPort.addAll(Arrays.asList(props.getProperty("com/ixnah/mc/ws", "80").split(",")));
        wssPort.addAll(Arrays.asList(props.getProperty("wss", "443").split(",")));
    }

    public static NetworkManager provideLanClient(InetAddress address, int port) {
        NetworkManager networkmanager = new NetworkManager(true);

        boolean isWs = wsPort.contains("" + port);
        boolean isWss = wssPort.contains("" + port);

        HttpHeaders headers = new DefaultHttpHeaders();
        if (isWs || isWss) {
            try (CloseableHttpClient httpClient = createHttpClient(isWss)) {
                String token = tokens.get(new InetSocketAddress(address, port), () -> {
                    String playerId = Minecraft.getMinecraft().getSession().getPlayerID();
                    HttpGet get = new HttpGet(URI.create(isWss ? "https://" : "http://" + address.getHostName() + ":" +
                            port + "?code=" + Integer.toHexString(playerId.hashCode()) + Long.toHexString(System.currentTimeMillis())));
                    get.addHeader(HttpHeaders.Names.AUTHORIZATION, playerId);
                    HttpResponse httpResponse = httpClient.execute(get);
                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    if (statusCode != HttpResponseStatus.OK.code())
                        throw new IllegalStateException(HttpResponseStatus.valueOf(statusCode).toString());
                    return EntityUtils.toString(httpResponse.getEntity());
                });
                headers.add(HttpHeaders.Names.AUTHORIZATION, token);
            } catch (Throwable t) {
                t.printStackTrace();
                throw new RuntimeException(t);
            }
        }

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory
                .newHandshaker(URI.create(isWss ? "wss://" : "ws://" + address.getHostName() + ":" + port),
                        WebSocketVersion.V13, null, true, headers);
        WebSocketClientHandler handler = new WebSocketClientHandler(handshaker);

        bootstrap = new Bootstrap();
        channel = bootstrap.group(NetworkManager.eventLoops).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                try {
                    channel.config().setOption(ChannelOption.IP_TOS, 24);
                } catch (ChannelException ignored) {
                }
                try {
                    channel.config().setOption(ChannelOption.TCP_NODELAY, false);
                } catch (ChannelException ignored) {
                }

                if (isWss) {
                    channel.pipeline().addLast("ssl", new SslHandler(prepareEngine(address.getHostName(), port)));
                }

                if (isWs || isWss) {
                    channel.pipeline()
                            .addLast("HttpClientCodec", new HttpClientCodec())
                            .addLast("HttpObjectAggregator", new HttpObjectAggregator(8192))
                            .addLast("WebSocketClientHandler", handler)
                            .addLast("timeout", new ReadTimeoutHandler(20))
                            .addLast("splitter", new MessageDeserializer2())
                            .addLast("decoder", new MessageDeserializer(NetworkManager.field_152462_h))
                            .addLast("packet_handler", networkmanager);
                } else {
                    channel.pipeline()
                            .addLast("timeout", new ReadTimeoutHandler(20))
                            .addLast("splitter", new MessageDeserializer2())
                            .addLast("decoder", new MessageDeserializer(NetworkManager.field_152462_h))
                            .addLast("prepender", new MessageSerializer2())
                            .addLast("encoder", new MessageSerializer(NetworkManager.field_152462_h))
                            .addLast("packet_handler", networkmanager);
                }
            }
        }).channel(NioSocketChannel.class).connect(address, port).syncUninterruptibly().channel();
        if (isWs || isWss) {
            Promise<Void> promise = handler.handshakeFuture().syncUninterruptibly();
            if (!promise.isSuccess()) {
                tokens.invalidate(new InetSocketAddress(address, port));
                throw new IllegalStateException(handler.handshakeFuture().cause());
            }
            channel.pipeline()
                    .addBefore("timeout", "PacketToFrameHandler", new PacketToFrameHandler())
                    .addBefore("packet_handler", "prepender", new MessageSerializer2())
                    .addBefore("packet_handler", "encoder", new MessageSerializer(NetworkManager.field_152462_h));
        }
        return networkmanager;
    }

    @SneakyThrows
    private static SSLEngine prepareEngine(String host, int port) {
        char[] passphrase = "changeit".toCharArray();

        SSLContext ctx = SSLContext.getInstance("TLSv1.2");
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }
        KeyManagerFactory factory = KeyManagerFactory.getInstance(algorithm);
        KeyStore keyStore = KeyStore.getInstance("JKS");

        String JAVA_HOME = System.getProperty("java.home");
        File cacertsFile = new File(JAVA_HOME + "/lib/security/cacerts");
        if (!cacertsFile.exists())
            cacertsFile = new File(JAVA_HOME + "/jre/lib/security/cacerts");
        keyStore.load(new FileInputStream(cacertsFile), passphrase);

        factory.init(keyStore, passphrase);
        ctx.init(factory.getKeyManagers(), null, null);
        SSLEngine sslEngine = ctx.createSSLEngine(host, port);
        sslEngine.setUseClientMode(true);

        return sslEngine;
    }

    @SneakyThrows
    private static CloseableHttpClient createHttpClient(boolean isSSL) {
        HttpClientBuilder builder = HttpClients.custom();
        if (isSSL) {
            SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(null, (certificate, authType) -> true).build();
            builder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext));
        }
        return builder.build();
    }
}
