package com.iota.iri.network;

import com.iota.iri.TransactionValidator;
import com.iota.iri.network.handlers.RequestDropper;
import com.iota.iri.network.handlers.TransactionCacher;
import com.iota.iri.network.handlers.TransactionRequestHandler;
import com.iota.iri.utils.Converter;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final IOTAMessage.IOTAMessageEncoder messageCRC32Encoder;

    public IOTAProtocol(double pDropRequest, double pReplyRandomRequest, int MWM, long cacheSize) {
        requestDropper = new RequestDropper(pDropRequest);
        messageEncoder = new IOTAMessage.IOTAMessageEncoder(false);
        messageCRC32Encoder = new IOTAMessage.IOTAMessageEncoder(true);
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
                new FixedLengthFrameDecoder(protocol == Protocol.UDP ? IOTAMessage.MESSAGE_SIZE : (IOTAMessage.MESSAGE_SIZE + IOTAMessage.CRC32_LENGTH)),
                requestDropper,
                new IOTAMessage.IOTAMessageDecoder(),
                transactionRequestHandler,
                transactionCacher,
                messageVerifier,
                serverHandler
        };
    }

    public ChannelHandler[] getClientChannelHandlers(Protocol protocol) {
        return new ChannelHandler[]{
                protocol == Protocol.UDP ? messageEncoder : messageCRC32Encoder,
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
    static class MessageVerifier extends SimpleChannelInboundHandler<IOTAMessage> {
        private static final Logger LOG = LoggerFactory.getLogger(MessageVerifier.class);
        private final int MWM;

        public MessageVerifier(int mwm) {
            super(false);
            this.MWM = mwm;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, IOTAMessage msg) throws Exception {
            IOTAMessage.TransactionMessage txMsg = (IOTAMessage.TransactionMessage) msg;
            try {
                TransactionValidator.runValidation(txMsg.getTransaction(), MWM);
                ctx.fireChannelRead(msg);
            } catch (Exception e) {
                LOG.trace("Message failed validation: {}", e.getMessage());
                // FIXME increase neighbor stats for invalid tx.
                ReferenceCountUtil.release(msg);
            }

        }
    }
}
