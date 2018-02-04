package com.iota.iri.network;

import com.iota.iri.conf.Configuration;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author Andreas C. Osowski
 */
public class NettyUDPServer {
    private static final Logger LOG = LoggerFactory.getLogger(NettyUDPServer.class);
    private static final int NUM_UDP_SERVER_THREAD = 2;

    private final Configuration configuration;

    private final int UDP_PORT;
    private final String LISTEN_HOST;
    private final IOTAProtocol protocol;
    private final NeighborManager neighborManager;

    private Bootstrap bootstrap;
    private ChannelFuture bindFuture;
    private EventLoopGroup eventGroup;

    public NettyUDPServer(Configuration configuration, IOTAProtocol protocol, NeighborManager neighborManager) {
        this.configuration = configuration;
        this.protocol = protocol;
        this.neighborManager = neighborManager;

        UDP_PORT = configuration.integer(Configuration.DefaultConfSettings.UDP_RECEIVER_PORT);
        LISTEN_HOST = configuration.string(Configuration.DefaultConfSettings.LISTEN_HOST);
    }

    public void init() {
        bootstrap = new Bootstrap();

        final String name = "IRI-UDP (" + UDP_PORT + ")";
        LOG.info("Booting up: " + name);

        if (Epoll.isAvailable()) {
            eventGroup = new EpollEventLoopGroup(NUM_UDP_SERVER_THREAD, NodeUtil.getNamedThreadFactory(name));
            bootstrap.group(eventGroup).channel(EpollDatagramChannel.class);
        } else {
            eventGroup = new NioEventLoopGroup(NUM_UDP_SERVER_THREAD, NodeUtil.getNamedThreadFactory(name));
            bootstrap.group(eventGroup).channel(NioDatagramChannel.class);
        }

        bootstrap.localAddress(LISTEN_HOST, UDP_PORT);
        bootstrap.option(ChannelOption.SO_BROADCAST, true);

        bootstrap.handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(DatagramChannel ch) throws Exception {
                LOG.info("Accepted new UDP connection: " + ch);

                ch.pipeline().addLast(new NeighborFilter(neighborManager));
            }
        });

        bindFuture = bootstrap.bind(UDP_PORT).syncUninterruptibly();

        LOG.info("Successfully initialised Netty UDP server.");
    }

    public void shutdown() {
        if (bindFuture != null) {
            bindFuture.channel().close().awaitUninterruptibly();
            bindFuture = null;
        }

        if (bootstrap != null) {
            if (eventGroup != null) {
                eventGroup.shutdownGracefully();
            }
            bootstrap = null;
        }

        LOG.info("Successfully shut down Netty UDP server.");
    }


    @ChannelHandler.Sharable
    static class NeighborFilter extends ChannelInboundHandlerAdapter {
        private final NeighborManager neighborManager;

        public NeighborFilter(NeighborManager neighborManager) {
            this.neighborManager = neighborManager;
        }


        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if(msg instanceof DatagramPacket) {
                System.err.println(msg);
                if (neighborManager.getNeighborForAddress(Protocol.UDP, (InetSocketAddress) ((DatagramPacket) msg).sender()).isPresent()) {
                    ctx.fireChannelRead(msg);
                }
            }
        }
    }
}
