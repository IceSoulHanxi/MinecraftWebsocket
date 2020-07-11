package com.ixnah.mc.ws.util;

import com.ixnah.mc.ws.handler.HttpClientHandler;
import com.ixnah.mc.ws.handler.PacketToFrameHandler;
import com.ixnah.mc.ws.handler.WebSocketClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.*;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.CPacketLoginStart;
import net.minecraft.util.LazyLoadBase;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLException;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpScheme.HTTP;
import static io.netty.handler.codec.http.HttpScheme.HTTPS;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static net.minecraft.network.NetworkManager.CLIENT_EPOLL_EVENTLOOP;
import static net.minecraft.network.NetworkManager.CLIENT_NIO_EVENTLOOP;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/7/8 10:50
 */
@SuppressWarnings("unused")
@SideOnly(Side.CLIENT)
public class ConnectUtil {

    private static final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36 Edg/83.0.478.58";

    public static NetworkManager createConnection(ServerData serverData, boolean useNativeTransport) throws UnknownHostException {
        String address = serverData.serverIP;
        try {
            if (address.startsWith("http:") || address.startsWith("https:"))
                address = address.replaceFirst("http", "ws");
            URI uri = URI.create(address); // IllegalArgumentException
            if (Arrays.asList("ws", "wss").contains(uri.getScheme().toLowerCase())) {
                return createWebsocketConnection(uri, useNativeTransport); // Websocket
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(uri.getHost().trim()); // NullPointerException
                if (uri.getPort() != -1)
                    sb.append(":").append(uri.getPort());
                address = sb.toString();
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
        }
        ServerAddress serveraddress = ServerAddress.fromString(address);
        return NetworkManager.createNetworkManagerAndConnect(InetAddress.getByName(serveraddress.getIP()), serveraddress.getPort(), useNativeTransport);
    }

    public static NetworkManager createWebsocketConnection(URI uri, boolean useNativeTransport) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(uri.getHost());
        boolean isSecure = uri.getScheme().equalsIgnoreCase("wss");
        int port = uri.getPort() != -1 ? uri.getPort() : isSecure ? HTTPS.port() : HTTP.port();

        if (address instanceof Inet6Address) {
            System.setProperty("java.net.preferIPv4Stack", "false");
        }

        NetworkManager networkmanager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        Class<? extends Channel> oclass;
        LazyLoadBase<? extends EventLoopGroup> lazyloadbase;
        if (Epoll.isAvailable() && useNativeTransport) {
            oclass = EpollSocketChannel.class;
            lazyloadbase = CLIENT_EPOLL_EVENTLOOP;
        } else {
            oclass = NioSocketChannel.class;
            lazyloadbase = CLIENT_NIO_EVENTLOOP;
        }

        HttpClientHandler httpClient = new HttpClientHandler();
        Channel channel = (new Bootstrap()).group(lazyloadbase.getValue()).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel channel) throws SSLException {
                try {
                    channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                    channel.config().setOption(ChannelOption.SO_KEEPALIVE, true);
                } catch (ChannelException ignored) {
                }

                if (isSecure) {
                    SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                    channel.pipeline().addLast("SSL", sslCtx.newHandler(channel.alloc()));
                }

                channel.pipeline()
                        .addLast("HttpClientCodec", new HttpClientCodec())
                        .addLast("HttpObjectAggregator", new HttpObjectAggregator(8192))
                        .addLast("HttpClientHandler", httpClient);
            }
        }).channel(oclass).connect(address, port).syncUninterruptibly().channel();

        String playerId = Minecraft.getMinecraft().getSession().getPlayerID();
        HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, uri.getPath());
        request.headers()
                .add(HOST, uri.getHost())
                .add(USER_AGENT, userAgent)
                .add(PRAGMA, NO_CACHE)
                .add(CACHE_CONTROL, NO_STORE) // 设置CDN不缓存
                .add(CONNECTION, KEEP_ALIVE) // 设置长链接
                .add(AUTHORIZATION, playerId); // 传递UUID
        Future<HttpResponse> responseFuture = httpClient.sendRequest(request).syncUninterruptibly();
        if (!responseFuture.isSuccess()) {
            responseFuture.cause().printStackTrace();
            throw new RuntimeException(responseFuture.cause());
        }
        HttpResponse response = responseFuture.getNow();
        if (!response.status().equals(OK)) {
            RuntimeException exception = new RuntimeException(response.status().toString());
            exception.printStackTrace();
            throw exception;
        }

        String token = ((FullHttpResponse) response).content().toString(Charset.defaultCharset());
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add(AUTHORIZATION, token);
        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory
                .newHandshaker(uri, WebSocketVersion.V13, "Minecraft", true, headers);
        WebSocketClientHandler webSocketClientHandler = new WebSocketClientHandler(handshaker);
        channel.pipeline().replace("HttpClientHandler", "WebSocketClientHandler", webSocketClientHandler); // 发送WebSocket握手请求
        Future<?> handshakeFuture = webSocketClientHandler.handshakeFuture().syncUninterruptibly(); // 阻塞: 等待握手结束
        if (!handshakeFuture.isSuccess()) { // 握手失败 抛出异常退出连接
            handshakeFuture.cause().printStackTrace();
            throw new RuntimeException(handshakeFuture.cause());
        }

        channel.pipeline() // 添加MC网络处理器
                .addLast("timeout", new ReadTimeoutHandler(30))
                .addLast("PacketToFrameHandler", new PacketToFrameHandler())
                .addLast("splitter", new NettyVarint21FrameDecoder())
                .addLast("decoder", new NettyPacketDecoder(EnumPacketDirection.CLIENTBOUND))
                .addLast("prepender", new NettyVarint21FrameEncoder())
                .addLast("encoder", new NettyPacketEncoder(EnumPacketDirection.SERVERBOUND))
                .addLast("packet_handler", networkmanager);

        try {
            // 告知MC网络管理器 连接已建立
            networkmanager.channelActive(channel.pipeline().firstContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return networkmanager;
    }

    private static final AtomicInteger CONNECTION_ID = new AtomicInteger(0);
    private static final Logger LOGGER = LogManager.getLogger();

    // GuiConnecting.connect(String ip, int port)
    public static void connect(GuiConnecting connecting, ServerData serverData) {
        LOGGER.info("Connecting to {}", serverData.serverIP);
        new Thread("Server Connector #" + CONNECTION_ID.incrementAndGet()) {
            @Override
            public void run() {
                InetAddress inetaddress = null;
                int port = 25565;
                try {
                    if (connecting.cancel) return;

                    connecting.networkManager = createConnection(serverData, connecting.mc.gameSettings.isUsingNativeTransport());
                    InetSocketAddress socketAddress = (InetSocketAddress) connecting.networkManager.channel().remoteAddress();
                    inetaddress = socketAddress.getAddress();
                    port = socketAddress.getPort();
                    connecting.networkManager.setNetHandler(new NetHandlerLoginClient(connecting.networkManager, connecting.mc, connecting.previousGuiScreen));
                    connecting.networkManager.sendPacket(new C00Handshake(inetaddress.getHostName(), port, EnumConnectionState.LOGIN, true));
                    connecting.networkManager.sendPacket(new CPacketLoginStart(connecting.mc.getSession().getProfile()));
                } catch (UnknownHostException unknownhostexception) {
                    if (connecting.cancel) return;

                    LOGGER.error("Couldn't connect to server", unknownhostexception);
                    connecting.mc.displayGuiScreen(new GuiDisconnected(connecting.previousGuiScreen, "connect.failed", new TextComponentTranslation("disconnect.genericReason", new Object[]{"Unknown host"})));
                } catch (Exception exception) {
                    if (connecting.cancel) return;

                    LOGGER.error("Couldn't connect to server", exception);
                    String s = exception.toString();

                    if (inetaddress != null) {
                        String s1 = inetaddress + ":" + port;
                        s = s.replaceAll(s1, "");
                    }

                    connecting.mc.displayGuiScreen(new GuiDisconnected(connecting.previousGuiScreen, "connect.failed", new TextComponentTranslation("disconnect.genericReason", s)));
                }
            }
        }.start();
    }
}
