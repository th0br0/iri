package com.iota.iri.network;

import com.iota.iri.conf.Configuration;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyUDPClient {

    private static final Logger LOG = LoggerFactory.getLogger(NettyUDPClient.class);
    private final Configuration config;

    private final int UDP_CLIENT_THREADS = 2;
    private final IOTAProtocol protocol;
    private final String LISTEN_HOST;
    private final int UDP_PORT;

    private Bootstrap bootstrap;
    private ChannelFuture bindFuture;
    private EventLoopGroup eventGroup;

    public NettyUDPClient(Configuration config, IOTAProtocol protocol) {
        this.config = config;
        this.protocol = protocol;

        UDP_PORT = config.integer(Configuration.DefaultConfSettings.UDP_RECEIVER_PORT);
        LISTEN_HOST = config.string(Configuration.DefaultConfSettings.LISTEN_HOST);
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

        bootstrap.localAddress(LISTEN_HOST, UDP_PORT);
        bootstrap.option(ChannelOption.SO_BROADCAST, true);

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel channel) throws Exception {
                LOG.info("New UDP upstream connection. " + channel);
                channel.pipeline().addLast(protocol.getClientChannelHandlers());

            }
        });


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

    public ChannelFuture connect() {
        if (bootstrap == null) {
            throw new RuntimeException("Client has not been initialized yet.");
        }

        return bootstrap.bind(0).syncUninterruptibly();
    }
}
