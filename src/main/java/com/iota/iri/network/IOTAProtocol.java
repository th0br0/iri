package com.iota.iri.network;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.iota.iri.TransactionValidator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * @author Andreas C. Osowski
 */
public class IOTAProtocol {
    private final double P_DROP_REQUEST;
    private final IOTAMessage.IOTAMessageEncoder messageEncoder;
    private final MessageVerifier messageVerifier;
    private final TransactionCacher transactionCacher;

    public IOTAProtocol(NeighborManager neighborManager, double pDropRequest, int MWM, long cacheSize) {
        P_DROP_REQUEST = pDropRequest;
        messageEncoder = new IOTAMessage.IOTAMessageEncoder();
        transactionCacher = new TransactionCacher(cacheSize);
        messageVerifier = new MessageVerifier(MWM);
    }

    public ChannelHandler[] getServerChannelHandlers(Protocol protocol) {
        return new ChannelHandler[]{
                // FIXME discard CRC32 in old IRI
                new FixedLengthFrameDecoder(protocol == Protocol.UDP ? IOTAMessage.MESSAGE_SIZE : (IOTAMessage.MESSAGE_SIZE + 16)),
                // FIXME RequestDropper
                new IOTAMessage.IOTAMessageDecoder(),
                transactionCacher,
                messageVerifier,
                new IOTAServerHandler()
        };
    }

    public ChannelHandler[] getClientChannelHandlers() {
        return new ChannelHandler[]{
                messageEncoder,

                // Client doesn't receive.
                // new IOTAMessage.IOTAMessageDecoder(P_DROP_REQUEST),
                // transactionCacher,
                // messageVerifier,
                // clientHandler
        };
    }

    /**
     * Prevents processing of a transaction too often.
     */
    @ChannelHandler.Sharable
    static class TransactionCacher extends MessageToMessageDecoder<IOTAMessage> {
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

    @ChannelHandler.Sharable
    static class MessageVerifier extends MessageToMessageDecoder<IOTAMessage> {
        private static final Logger LOG = LoggerFactory.getLogger(MessageVerifier.class);
        private final int MWM;

        public MessageVerifier(int mwm) {
            this.MWM = mwm;
        }

        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, IOTAMessage iotaMessage, List<Object> list) throws Exception {
            IOTAMessage.TransactionMessage txMsg = (IOTAMessage.TransactionMessage) iotaMessage;
            try {
                TransactionValidator.runValidation(txMsg.getTransaction(), MWM);
                list.add(txMsg);
            } catch (Exception e) {
                LOG.trace("Message failed validation: {}", e.getMessage());
                // FIXME increase neighbor stats for invalid tx.
            }
        }
    }
}
