package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

public class IOTATCPClient extends IOTAClient {
    private static final Logger LOG = LoggerFactory.getLogger(IOTATCPClient.class);
    private final IOTATCPClientFactory clientFactory;

    public IOTATCPClient(Neighbor neighbor, Channel channel, InetAddress inetAddress, IOTATCPClientFactory clientFactory) {
        super(neighbor, channel, inetAddress);
        this.clientFactory = clientFactory;

        channel.closeFuture().addListener((ChannelFutureListener) future -> onClosed(future));
    }

    @Override
    public void send(TransactionViewModel model, Hash toRequest) {
        LOG.trace("Sending {} req {}", model.getHash(), toRequest);
        IOTAMessage.TransactionMessage msg = new IOTAMessage.TransactionMessage();
        msg.setTransaction(model);
        msg.setReqHash(toRequest);

        channel.writeAndFlush(msg);
    }

    @Override
    public void doClose() {
        channel.close();
    }

    // This method runs on the channel's event pool.
    public void onClosed(ChannelFuture future) {
        LOG.info("Channel is closed. {}", channel);
        channelClosed.set(true);
        clientFactory.destroyClient(neighbor, this);

        // FIXME notify owner of this client of its death
    }

    @Override
    public String toString() {
        return "IOTATCPClient{" +
                "channel=" + channel +
                ", neighbor=" + neighbor +
                ", remoteAddress=" + remoteAddress +
                '}';
    }
}
