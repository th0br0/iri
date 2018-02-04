package com.iota.iri.network;

import com.iota.iri.TransactionValidator;
import com.iota.iri.network.handlers.RequestDropper;
import com.iota.iri.network.handlers.TransactionCacher;
import com.iota.iri.network.handlers.TransactionRequestHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Andreas C. Osowski
 */
public class IOTAProtocol {
    private final RequestDropper requestDropper;
    private final IOTAMessage.IOTAMessageEncoder messageEncoder;
    private final MessageVerifier messageVerifier;
    private final TransactionCacher transactionCacher;
    private final TransactionRequestHandler transactionRequestHandler;
    private final IOTAServerHandler serverHandler;

    public IOTAProtocol(double pDropRequest, double pReplyRandomRequest, int MWM, long cacheSize) {
        requestDropper = new RequestDropper(pDropRequest);
        messageEncoder = new IOTAMessage.IOTAMessageEncoder();
        transactionCacher = new TransactionCacher(cacheSize);
        transactionRequestHandler = new TransactionRequestHandler(cacheSize, pReplyRandomRequest);
        messageVerifier = new MessageVerifier(MWM);
        serverHandler = new IOTAServerHandler();
    }

    public TransactionRequestHandler getTransactionRequestHandler() {
        return transactionRequestHandler;
    }

    public ChannelHandler[] getServerChannelHandlers(Protocol protocol) {
        return new ChannelHandler[]{
                // FIXME discard CRC32 in old IRI
                new FixedLengthFrameDecoder(protocol == Protocol.UDP ? IOTAMessage.MESSAGE_SIZE : (IOTAMessage.MESSAGE_SIZE + 16)),
                requestDropper,
                new IOTAMessage.IOTAMessageDecoder(),
                transactionRequestHandler,
                transactionCacher,
                messageVerifier,
                serverHandler
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

    // NOT THREAD SAFE
    public void setTransactionStorer(TransactionStorer transactionStorer) {
        serverHandler.setTransactionStorer(transactionStorer);
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
