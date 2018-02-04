package com.iota.iri.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class IOTATCPClientFactory {
    private static final Logger LOG = LoggerFactory.getLogger(IOTATCPClientFactory.class);

    private final ConcurrentMap<Neighbor, Object> clients = new ConcurrentHashMap<>();
    private final int tcpReceiverPort;
    private NettyTCPClient tcpClient;

    public IOTATCPClientFactory(NettyTCPClient tcpClient, int tcpReceiverPort) {
        this.tcpClient = tcpClient;
        this.tcpReceiverPort = tcpReceiverPort;
    }

    public IOTAClient connectToNode(Neighbor neighbor) throws InterruptedException, IOException {
        Object entry;
        IOTAClient client = null;

        while (client == null) {
            entry = clients.get(neighbor);
            if (entry != null) {
                if (entry instanceof IOTATCPClient) {
                    client = (IOTAClient) entry;

                    if (!client.getRemoteAddress().equals(neighbor.getAddress().get())) {
                        client.close();
                        client = null;
                    }
                } else {
                    // in process of connecting
                    ConnectingChannel future = (ConnectingChannel) entry;
                    client = future.waitForChannel();

                    clients.replace(neighbor, future, client);
                }
            } else {
                ConnectingChannel connectingChannel = new ConnectingChannel(neighbor, this);
                Object old = clients.putIfAbsent(neighbor, connectingChannel);

                if (old == null) {
                    tcpClient.connect(new InetSocketAddress(neighbor.getAddress().get(), neighbor.getPort()))
                            .addListener(connectingChannel);
                    client = connectingChannel.waitForChannel();
                    clients.replace(neighbor, connectingChannel, client);

                } else if (old instanceof ConnectingChannel) {
                    client = ((ConnectingChannel) old).waitForChannel();
                    clients.replace(neighbor, connectingChannel, client);
                } else {
                    client = (IOTAClient) old;
                }
            }
        }
        return client;
    }

    public void closeOpenChannelConnections(Neighbor neighbor) {
        Object entry = clients.get(neighbor);

        if (entry instanceof ConnectingChannel) {
            ConnectingChannel channel = (ConnectingChannel) entry;

            if (channel.dispose()) {
                clients.remove(neighbor, channel);
            }
        }
    }

    public int getNumberOfActiveClients() {
        return clients.size();
    }

    public void destroyClient(Neighbor neighbor, IOTAClient client) {
        LOG.info("Destroying client: {}", client);
        clients.remove(neighbor, client);
    }


    private static final class ConnectingChannel implements ChannelFutureListener {

        private final Object connectLock = new Object();

        private final Neighbor neighbor;
        private final IOTATCPClientFactory clientFactory;

        private boolean disposeRequestClient = false;
        private volatile Throwable error;

        private IOTAClient iotaClient = null;

        public ConnectingChannel(Neighbor neighbor, IOTATCPClientFactory clientFactory) {
            this.neighbor = neighbor;
            this.clientFactory = clientFactory;
        }

        private boolean dispose() {
            boolean result;
            synchronized (connectLock) {
                disposeRequestClient = true;
                result = true;

                connectLock.notifyAll();
            }

            return result;
        }

        private void handInChannel(Channel channel) {
            synchronized (connectLock) {
                try {
                    // First message on channel has to be our receiver port.
                    String portStr = StringUtils.leftPad(Integer.toString(clientFactory.tcpReceiverPort), 10, '0');
                    ByteBuf buf = Unpooled.wrappedBuffer(portStr.getBytes());
                    channel.writeAndFlush(buf);
                    channel.attr(Neighbor.KEY).set(neighbor);

                    this.iotaClient = new IOTATCPClient(neighbor, channel, neighbor.getAddress().get(), clientFactory);
                    connectLock.notifyAll();
                } catch (Throwable t) {
                    notifyOfError(t);
                }
            }
        }

        private IOTAClient waitForChannel() throws IOException, InterruptedException {
            synchronized (connectLock) {
                while (error == null && iotaClient == null) {
                    connectLock.wait(2000);
                }
            }

            if (error != null) {
                throw new IOException("Connecting the channel failed: " + error.getMessage(), error);
            }

            return iotaClient;
        }

        private void notifyOfError(Throwable error) {
            synchronized (connectLock) {
                this.error = error;
                connectLock.notifyAll();
            }
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            if (future.isSuccess()) {
                handInChannel(future.channel());
            } else if (future.cause() != null) {
                notifyOfError(future.cause());
            }
        }
    }
}
