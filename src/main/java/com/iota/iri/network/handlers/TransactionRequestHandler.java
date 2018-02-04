package com.iota.iri.network.handlers;

import com.iota.iri.model.Hash;
import com.iota.iri.network.IOTAMessage;
import com.iota.iri.network.Neighbor;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.commons.lang3.tuple.Pair;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

@ChannelHandler.Sharable
public class TransactionRequestHandler extends SimpleChannelInboundHandler<IOTAMessage> {
    private static final SecureRandom random = new SecureRandom();
    private final long MAX_QUEUE_SIZE;
    private final double P_REPLY_RANDOM_REQUEST;
    private PriorityBlockingQueue<Pair<Hash, Neighbor>> queue;

    public TransactionRequestHandler(long cacheSize, double randomRequestReplyChance) {
        super(false);
        MAX_QUEUE_SIZE = cacheSize;
        P_REPLY_RANDOM_REQUEST = randomRequestReplyChance;
        queue = new PriorityBlockingQueue<Pair<Hash, Neighbor>>((int) MAX_QUEUE_SIZE, Comparator.comparingInt(o -> o.getLeft().trailingZeros()));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IOTAMessage iotaMessage) {
        IOTAMessage.TransactionMessage msg = (IOTAMessage.TransactionMessage) iotaMessage;

        Hash reqHash = msg.getReqHash();

        if (reqHash.equals(Hash.NULL_HASH) && random.nextDouble() < P_REPLY_RANDOM_REQUEST) {
            // Random transaction request
            Neighbor n = ctx.channel().attr(Neighbor.KEY).get();
            addRequest(Pair.of(Hash.NULL_HASH, n));
        } else {
            Neighbor n = ctx.channel().attr(Neighbor.KEY).get();

            addRequest(Pair.of(reqHash, n));
        }

        ctx.fireChannelRead(iotaMessage);
    }

    public Optional<Pair<Hash, Neighbor>> nextRequest(long count, TimeUnit unit) throws InterruptedException {
        return Optional.ofNullable(queue.poll(count, unit));
    }

    protected void addRequest(Pair<Hash, Neighbor> of) {
        if(!queue.contains(of)) {
            queue.add(of);
        }

        while (queue.size() > MAX_QUEUE_SIZE) {
            queue.poll();
        }
    }
}
