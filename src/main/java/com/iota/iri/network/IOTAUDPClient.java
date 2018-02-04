package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class IOTAUDPClient extends IOTAClient {
    private static final Logger LOG = LoggerFactory.getLogger(IOTAUDPClient.class);
    private final InetSocketAddress localAddress;
    private final InetSocketAddress remoteSocketAddress;

    public IOTAUDPClient(Neighbor neighbor, Channel channel, InetAddress remoteAddress, InetSocketAddress localAddress) {
        super(neighbor, channel, remoteAddress);
        this.remoteSocketAddress = new InetSocketAddress(remoteAddress, neighbor.getPort());
        this.localAddress = localAddress;
    }

    @Override
    public void send(TransactionViewModel model, Hash toRequest) {
        IOTAMessage.TransactionMessage msg = new IOTAMessage.TransactionMessage();
        msg.setTransaction(model);
        msg.setReqHash(toRequest);

        try {
            ByteBuf bb = msg.write(channel.alloc());
            channel.writeAndFlush(new DatagramPacket(
                    bb,
                    remoteSocketAddress,
                    localAddress
            ));
        } catch (Exception e) {
            LOG.trace("Error sending UDP message: {}", e);
        }
    }

    @Override
    public void doClose() {
        // Can't close UDP client.
    }
}
