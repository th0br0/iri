package com.iota.iri.network;

import com.iota.iri.conf.Configuration;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

public class NettyTCPClient {

    private static final Logger LOG = LoggerFactory.getLogger(NettyTCPClient.class);
    private final Configuration config;

    private final int TCP_CLIENT_THREADS = 2;
    private final IOTAProtocol protocol;
    private final int TCP_PORT;
    private final String LISTEN_HOST;

    private Bootstrap bootstrap;
    private EventLoopGroup eventGroup;

    public NettyTCPClient(Configuration config, IOTAProtocol protocol) {
        this.config = config;
        this.protocol = protocol;

        TCP_PORT = config.integer(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT);
        LISTEN_HOST = config.string(Configuration.DefaultConfSettings.LISTEN_HOST);
    }

    public void init() {
        if (bootstrap != null) {
            throw new RuntimeException("Netty TCP client has already been initialized");
        }

        bootstrap = new Bootstrap();

        final String name = "IRI-TCP-Client";
        if (Epoll.isAvailable()) {
            eventGroup = new EpollEventLoopGroup(TCP_CLIENT_THREADS, NodeUtil.getNamedThreadFactory(name));
            bootstrap.group(eventGroup).channel(EpollSocketChannel.class);
        } else {
            eventGroup = new NioEventLoopGroup(TCP_CLIENT_THREADS, NodeUtil.getNamedThreadFactory(name));
            bootstrap.group(eventGroup).channel(NioSocketChannel.class);
        }

        bootstrap.option(ChannelOption.TCP_NODELAY, true);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5 * 1000);

        bootstrap.option(ChannelOption.SO_SNDBUF, 2 * INode.TRANSACTION_PACKET_SIZE);
        bootstrap.option(ChannelOption.SO_RCVBUF, 2 * INode.TRANSACTION_PACKET_SIZE);

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(protocol.getClientChannelHandlers());
            }
        });
    }

    Bootstrap getBootstrap() {
        return bootstrap;
    }

    void shutdown() {
        long start = System.currentTimeMillis();

        if (bootstrap != null) {
            if (eventGroup != null) {
                eventGroup.shutdownGracefully();
            }
            bootstrap = null;
        }

        long end = System.currentTimeMillis();
        LOG.info("Successful shutdown (took {} ms).", (end - start));
    }

    public ChannelFuture connect(SocketAddress serverSocketAddress) {
        if (bootstrap == null) {
            throw new RuntimeException("Client has not been initialized yet.");
        }

        return bootstrap.connect(serverSocketAddress);
    }
}
