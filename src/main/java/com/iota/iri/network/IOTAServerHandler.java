package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class IOTAServerHandler extends SimpleChannelInboundHandler<IOTAMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(IOTAServerHandler.class);
    private TransactionStorer transactionStorer;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        LOG.info("Channel active: " + ctx);

    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IOTAMessage iotaMessage) throws Exception {
        /*
            We have received a new IOTA Message.
         */

        IOTAMessage.TransactionMessage msg = (IOTAMessage.TransactionMessage) iotaMessage;
        LOG.info("Channel read: {} req: {}", msg.getTransaction().getHash(), msg.getReqHash());

        TransactionViewModel model = msg.getTransaction();
        transactionStorer.store(ctx.channel().attr(Neighbor.KEY).get(), model);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // Bad channel input or other exception.
        LOG.info("Channel {} died: {}", ctx.channel(), cause.getMessage());
        ctx.channel().close();
    }

    public void setTransactionStorer(TransactionStorer transactionStorer) {
        this.transactionStorer = transactionStorer;
    }
}
