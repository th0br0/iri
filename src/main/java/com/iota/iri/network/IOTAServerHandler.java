package com.iota.iri.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IOTAServerHandler extends SimpleChannelInboundHandler<IOTAMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(IOTAServerHandler.class);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        LOG.info("Channel active: " + ctx);

    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, IOTAMessage iotaMessage) throws Exception {
        /*
            We have received a new IOTA Message.
         */

        IOTAMessage.TransactionMessage msg = (IOTAMessage.TransactionMessage) iotaMessage;
        LOG.info("Channel read: {} req: {}", msg.getTransaction().getHash(), msg.getReqHash());

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // Bad channel input or other excepiton.
        // FIXME we must propagate this
        LOG.info("Channel {} died: {}", ctx.channel(), cause.getMessage());
        ctx.channel().close();
    }
}
