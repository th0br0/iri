package com.iota.iri.network;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.Hash;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

import static com.iota.iri.network.INode.TRANSACTION_PACKET_SIZE;

/**
 * @author Andreas C. Osowski
 */
public class NettyTCPServer {
    private static final Logger LOG = LoggerFactory.getLogger(NettyTCPServer.class);

    private final Configuration configuration;

    private final int TCP_PORT;
    private final String LISTEN_HOST;
    private final NettyProtocol protocol;

    private ServerBootstrap bootstrap;
    private ChannelFuture bindFuture;
    private EventLoopGroup eventGroup;

    public NettyTCPServer(Configuration configuration, NettyProtocol protocol) {
        this.configuration = configuration;
        this.protocol = protocol;

        TCP_PORT = configuration.integer(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT);
        LISTEN_HOST = configuration.string(Configuration.DefaultConfSettings.LISTEN_HOST);
    }

    public void init() {
        bootstrap = new ServerBootstrap();

        final String name = "IRI-TCP" + " (" + TCP_PORT + ")";

        if (Epoll.isAvailable()) {
            eventGroup = new EpollEventLoopGroup(2, NodeUtil.getNamedThreadFactory(name));
            bootstrap.group(eventGroup).channel(EpollServerSocketChannel.class);
        } else {
            eventGroup = new NioEventLoopGroup(2, NodeUtil.getNamedThreadFactory(name));
            bootstrap.group(eventGroup).channel(NioServerSocketChannel.class);
        }

        bootstrap.localAddress(LISTEN_HOST, TCP_PORT);

        // setup pooled allocator here if necessary

        bootstrap.childOption(ChannelOption.SO_RCVBUF, TRANSACTION_PACKET_SIZE);
        bootstrap.childOption(ChannelOption.SO_SNDBUF, TRANSACTION_PACKET_SIZE);

        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                LOG.info("Accepted new TCP connection. " + ch);
                ch.pipeline().addLast(protocol.getServerChannelHandlers());
            }
        });

        bindFuture = bootstrap.bind().syncUninterruptibly();

        LOG.info("Successfully initialised Netty TCP server.");
    }

    public void shutdown() {
        if (bindFuture != null) {
            bindFuture.channel().close().awaitUninterruptibly();
            bindFuture = null;
        }

        if (bootstrap != null) {
            if(eventGroup != null) {
                eventGroup.shutdownGracefully();
            }
            bootstrap = null;
        }

        LOG.info("Successfully shut down Netty TCP server.");
    }
}
