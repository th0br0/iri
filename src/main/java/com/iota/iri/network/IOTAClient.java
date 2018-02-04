package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class IOTAClient {
    protected final Channel channel;
    protected final Neighbor neighbor;
    protected final InetAddress remoteAddress;

    protected final AtomicBoolean channelClosed = new AtomicBoolean(false);

    public IOTAClient(Neighbor neighbor, Channel channel, InetAddress inetAddress) {
        this.neighbor = neighbor;
        this.channel = channel;
        this.remoteAddress = inetAddress;
    }

    public Neighbor getNeighbor() {
        return neighbor;
    }

    public InetAddress getRemoteAddress() {
        return remoteAddress;
    }

    public abstract void send(TransactionViewModel model, Hash toRequest);

    public abstract void doClose();

    public void close() {
        if (channelClosed.get()) {
            return;
        }

        channelClosed.set(true);
        doClose();
    }

    public ChannelFuture getCloseFuture() {
        return channel.closeFuture();
    }

    public boolean isClosed() {
        return channelClosed.get();
    }
}
