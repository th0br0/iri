package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;

/**
 * @author Andreas C. Osowski
 */
abstract class IOTAMessage {
    static final int MESSAGE_SIZE = 1650;
    static final int REQ_HASH_SIZE = 46;

    abstract ByteBuf write(ByteBufAllocator allocator) throws Exception;

    abstract void readFrom(ByteBuf buffer) throws Exception;


    static class IOTAMessageDecoder extends ByteToMessageDecoder {
        private static final SecureRandom random = new SecureRandom();
        private final double P_DROP_TRANSACTION;

        public IOTAMessageDecoder(double pDropTransaction) {
            P_DROP_TRANSACTION = pDropTransaction;
        }

        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf input, List<Object> list) throws Exception {
            if (random.nextDouble() < P_DROP_TRANSACTION) {
                return;
            }

            if(input.readableBytes() < MESSAGE_SIZE) {
                return;
            }

            // FIXME move allocation to constructor
            ByteBuf readBytes = channelHandlerContext.alloc().heapBuffer(MESSAGE_SIZE);

            input.readBytes(readBytes, MESSAGE_SIZE);
            TransactionMessage msg = new TransactionMessage();
            msg.readFrom(readBytes);
            list.add(msg);
        }
    }

    @ChannelHandler.Sharable
    static class IOTAMessageEncoder extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof IOTAMessage) {
                ByteBuf serialized = null;
                try {
                    serialized = ((IOTAMessage) msg).write(ctx.alloc());
                } catch (Throwable t) {
                    throw new IOException("Error while serializing message: " + msg, t);
                } finally {
                    if (serialized != null) {
                        ctx.write(serialized, promise);
                    }
                }
            } else {
                ctx.write(msg, promise);
            }
        }
    }

    static class TransactionMessage extends IOTAMessage {
        private TransactionViewModel transaction;
        private Hash reqHash;

        public TransactionViewModel getTransaction() {
            return transaction;
        }

        public void setTransaction(TransactionViewModel transaction) {
            this.transaction = transaction;
        }

        public Hash getReqHash() {
            return reqHash;
        }

        public void setReqHash(Hash reqHash) {
            this.reqHash = reqHash;
        }

        public boolean isRandomRequest() {
            return transaction.getHash().equals(reqHash);
        }

        @Override
        ByteBuf write(ByteBufAllocator allocator) throws Exception {
            final ByteBuf buffer = allocator.directBuffer(MESSAGE_SIZE);

            Converter.bytes(transaction.trits(), buffer, 0, TransactionViewModel.TRINARY_SIZE);
            Converter.bytes(reqHash.trits(), buffer, 0, REQ_HASH_SIZE * Converter.NUMBER_OF_TRITS_IN_A_BYTE);
            return buffer;
        }

        @Override
        void readFrom(ByteBuf buffer) throws Exception {
            byte[] data = buffer.array();
            int[] txTrits = new int[TransactionViewModel.TRINARY_SIZE];
            int[] reqTrits = new int[Hash.SIZE_IN_TRITS];

            Converter.getTrits(data, 0, txTrits);
            Hash txHash = Hash.calculate(SpongeFactory.Mode.CURLP81, txTrits);

            Converter.getTrits(data, TransactionViewModel.SIZE, reqTrits);

            transaction = new TransactionViewModel(txTrits, txHash);
            reqHash = new Hash(reqTrits, 0);
        }
    }


}
