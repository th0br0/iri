package com.iota.iri.network.handlers;

import com.iota.iri.network.IOTAMessage;
import com.iota.iri.network.NettyTCPClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;

@ChannelHandler.Sharable
public class RequestDropper extends SimpleChannelInboundHandler {
    private static final Logger LOG = LoggerFactory.getLogger(NettyTCPClient.class);
    private static final SecureRandom random = new SecureRandom();
    private final double P_DROP_PACKET;

    public RequestDropper(double p_DROP_PACKET) {
        super(false);

        P_DROP_PACKET = p_DROP_PACKET;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf && random.nextDouble() < P_DROP_PACKET) {
            LOG.trace("Dropping message.");

            if (ctx.channel() instanceof SocketChannel) {
                ((ByteBuf) msg).skipBytes(IOTAMessage.MESSAGE_SIZE + IOTAMessage.CRC32_LENGTH);
            }
            return;
        }

        ctx.fireChannelRead(msg);
    }
}
