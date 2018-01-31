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

public class NettyUDPClient {

    private static final Logger LOG = LoggerFactory.getLogger(NettyUDPClient.class);
    private final Configuration config;

    private final int UDP_CLIENT_THREADS = 2;
    private final NettyProtocol protocol;

    private Bootstrap bootstrap;
    private ChannelFuture bindFuture;
    private EventLoopGroup eventGroup;

    public NettyUDPClient(Configuration config, NettyProtocol protocol) {
        this.config = config;
        this.protocol = protocol;
    }

    public void init() {
        if (bootstrap != null) {
            throw new RuntimeException("Netty UDP client has already been initialized");
        }

        bootstrap = new Bootstrap();

        final String name = "IRI-UDP-Client";
        if (Epoll.isAvailable()) {
            eventGroup = new EpollEventLoopGroup(UDP_CLIENT_THREADS, NodeUtil.getNamedThreadFactory(name));
            bootstrap.group(eventGroup).channel(EpollSocketChannel.class);
        } else {
            eventGroup = new NioEventLoopGroup(UDP_CLIENT_THREADS, NodeUtil.getNamedThreadFactory(name));
            bootstrap.group(eventGroup).channel(NioSocketChannel.class);
        }

        bootstrap.option(ChannelOption.SO_BROADCAST, true);

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                LOG.info("New UDP upstream connection. " + channel);
                channel.pipeline().addLast(protocol.getClientChannelHandlers());
            }
        });

        bindFuture = bootstrap.bind(0).syncUninterruptibly();
    }

    Bootstrap getBootstrap() {
        return bootstrap;
    }

    void shutdown() {
        long start = System.currentTimeMillis();
        if (bindFuture != null) {
            bindFuture.channel().close().awaitUninterruptibly();
            bindFuture = null;
        }

        if (bootstrap != null) {
            if (eventGroup != null) {
                eventGroup.shutdownGracefully();
                eventGroup = null;
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
