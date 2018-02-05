package com.iota.iri.network.handlers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.iota.iri.model.Hash;
import com.iota.iri.network.IOTAMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;

import java.nio.ByteBuffer;

/**
 * Prevents processing of a transaction too often.
 */
@ChannelHandler.Sharable
public class TransactionCacher extends SimpleChannelInboundHandler<IOTAMessage> {
    // Concurrent by default.
    private final Cache<ByteBuffer, Integer> cache;

    public TransactionCacher(long cacheSize) {
        super(false);
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheSize).build();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IOTAMessage iotaMessage) throws Exception {
        // FIXME runtime overhead of trits->bytes conversion
        IOTAMessage.TransactionMessage msg = (IOTAMessage.TransactionMessage) iotaMessage;

        if (msg.getTransaction().getHash().equals(Hash.NULL_HASH)) {
            ReferenceCountUtil.release(iotaMessage);
            return;
        }

        ByteBuffer hashBytes = ByteBuffer.wrap(msg.getTransaction().getHash().bytes());
        if (cache.getIfPresent(hashBytes) == null) {
            cache.put(hashBytes, 1);
            ctx.fireChannelRead(iotaMessage);
        } else {
            ReferenceCountUtil.release(iotaMessage);
            return;
        }

    }
}
