package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;

/**
 * @author Andreas C. Osowski
 */
public abstract class IOTAMessage {
    static final int MESSAGE_SIZE = 1650;
    static final int REQ_HASH_SIZE = 46;

    abstract ByteBuf write(ByteBufAllocator allocator) throws Exception;

    abstract void readFrom(ByteBuf buffer) throws Exception;


    static class IOTAMessageDecoder extends ByteToMessageDecoder {
        private byte[] byteBuf = new byte[MESSAGE_SIZE];
        private ByteBuf buffer = Unpooled.wrappedBuffer(byteBuf);

        public IOTAMessageDecoder() {
        }

        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf input, List<Object> list) throws Exception {
            buffer.clear();

            input.readBytes(buffer, MESSAGE_SIZE);
            // Old CRC32 values which we're discarding.
            if (channelHandlerContext.channel() instanceof SocketChannel) {
                input.skipBytes(16);
            }

            TransactionMessage msg = new TransactionMessage();
            msg.readFrom(buffer);
            msg.getTransaction().setArrivalTime(System.currentTimeMillis());
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

    public static class TransactionMessage extends IOTAMessage {
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
