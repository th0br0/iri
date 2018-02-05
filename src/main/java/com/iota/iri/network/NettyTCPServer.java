package com.iota.iri.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * @author Andreas C. Osowski
 */
public class NettyTCPServer {
    private static final Logger LOG = LoggerFactory.getLogger(NettyTCPServer.class);

    private final int TCP_PORT;
    private final String LISTEN_HOST;
    private final IOTAProtocol protocol;
    private final NeighborManager neighborManager;

    private ServerBootstrap bootstrap;
    private ChannelFuture bindFuture;
    private EventLoopGroup eventGroup;
    private NeighborAcceptor neighborAcceptor;

    public NettyTCPServer(String listenHost, int port, IOTAProtocol protocol, NeighborManager neighborManager) {
        this.protocol = protocol;
        this.neighborManager = neighborManager;
        this.neighborAcceptor = new NeighborAcceptor(neighborManager);

        TCP_PORT = port;
        LISTEN_HOST = listenHost;
    }

    public void init() {
        bootstrap = new ServerBootstrap();

        final String name = "IRI-TCP" + " (" + TCP_PORT + ")";

        if (Epoll.isAvailable()) {
            eventGroup = new EpollEventLoopGroup(4, NodeUtil.getNamedThreadFactory(name));
            bootstrap.group(eventGroup).channel(EpollServerSocketChannel.class);
        } else {
            eventGroup = new NioEventLoopGroup(4, NodeUtil.getNamedThreadFactory(name));
            bootstrap.group(eventGroup).channel(NioServerSocketChannel.class);
        }

        bootstrap.localAddress(LISTEN_HOST, TCP_PORT);

        bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch)  {
                LOG.info("Accepted new TCP connection. " + ch);

                ch.pipeline().addLast(neighborAcceptor);
                ch.pipeline().addLast(protocol.getServerChannelHandlers(Protocol.TCP));
            }
        });

        bindFuture = bootstrap.bind(TCP_PORT).syncUninterruptibly();

        LOG.info("Successfully initialised Netty TCP server.");
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

        LOG.info("Successfully shut down Netty TCP server.");
    }


    /**
     * Verifies inbound TCP connection to come from known neighbor (as identified by TCP Receiver port)
     */
    @ChannelHandler.Sharable
    protected static class NeighborAcceptor extends ChannelInboundHandlerAdapter {
        private final static int PORT_BYTES = 10;

        private final NeighborManager neighborManager;

        public NeighborAcceptor(NeighborManager neighborManager) {
            this.neighborManager = neighborManager;
        }


        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf) {
                ByteBuf bb = (ByteBuf) msg;

                if (bb.readableBytes() < PORT_BYTES) {
                    throw new RuntimeException("Invalid receive port length: " + bb.readableBytes());
                }

                byte[] portBytes = new byte[PORT_BYTES];

                ((ByteBuf) msg).readBytes(portBytes, 0, PORT_BYTES);
                String portStr = new String(portBytes, Charset.defaultCharset());
                int port = Integer.parseInt(portStr);

                InetSocketAddress remoteAddress = new InetSocketAddress(((InetSocketAddress) ctx.channel().remoteAddress()).getAddress(), port);
                Optional<Neighbor> neighbor = neighborManager.getNeighborForAddress(Protocol.TCP, remoteAddress);

                if (!neighbor.isPresent()) {
                    LOG.error("Unknown neighbor: " + remoteAddress);
                    ctx.close();
                } else {
                    LOG.info("Neighbor connected: " + neighbor);
                    ctx.channel().attr(Neighbor.KEY).set(neighbor.get());
                }

                ctx.pipeline().remove(this);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
