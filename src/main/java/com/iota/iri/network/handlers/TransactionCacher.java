package com.iota.iri.network.handlers;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.iota.iri.network.IOTAMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Prevents processing of a transaction too often.
 */
@ChannelHandler.Sharable
public class TransactionCacher extends MessageToMessageDecoder<IOTAMessage> {
    // Concurrent by default.
    private final Cache<ByteBuffer, Integer> cache;

    public TransactionCacher(long cacheSize) {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(cacheSize).build();
    }

    void process(IOTAMessage.TransactionMessage msg, List<Object> list) {
        // FIXME runtime overhead of trits->bytes conversion
        ByteBuffer hashBytes = ByteBuffer.wrap(msg.getTransaction().getHash().bytes());
        if (cache.getIfPresent(hashBytes) == null) {
            cache.put(hashBytes, 1);
            list.add(msg);
        } else {
            return;
        }
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, IOTAMessage iotaMessage, List<Object> list) throws Exception {
        process((IOTAMessage.TransactionMessage) iotaMessage, list);
    }
}
