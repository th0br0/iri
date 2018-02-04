package com.iota.iri.network.handlers;

import com.iota.iri.network.NettyTCPClient;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
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
        if (random.nextDouble() < P_DROP_PACKET) {
            LOG.trace("Dropping message.");
            return;
        }

        ctx.fireChannelRead(msg);
    }
}
