package com.iota.iri.network;

import com.google.common.util.concurrent.AbstractService;
import com.iota.iri.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Andreas C. Osowski
 */
public class NettyConnectionManager extends AbstractService {
    private final Logger LOG = LoggerFactory.getLogger(NettyConnectionManager.class);
    private final IOTAProtocol protocol;
    private final IOTATCPClientFactory clientFactory;

    private final NettyTCPServer tcpServer;
    private final NettyTCPClient tcpClient;
    private final NettyUDPServer udpServer;
    private final NettyUDPClient udpClient;

    public NettyConnectionManager(Configuration config, NeighborManager neighborManager) {
        int MWM;
        if (config.booling(Configuration.DefaultConfSettings.TESTNET)) {
            MWM = config.integer(Configuration.DefaultConfSettings.TESTNET_MWM);
        } else {
            MWM = config.integer(Configuration.DefaultConfSettings.MAINNET_MWM);
        }
        double dropTransaction = config.doubling(Configuration.DefaultConfSettings.P_DROP_TRANSACTION.name());
        double replyRandomRequest = config.doubling(Configuration.DefaultConfSettings.P_REPLY_RANDOM_TIP.name());

        int tcpPort = config.integer(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT);
        int udpPort = config.integer(Configuration.DefaultConfSettings.UDP_RECEIVER_PORT);
        String listenHost = config.string(Configuration.DefaultConfSettings.LISTEN_HOST);

        long cacheSize = 5000;

        this.protocol = new IOTAProtocol(dropTransaction, replyRandomRequest, MWM, cacheSize);

        this.tcpServer = new NettyTCPServer(listenHost, tcpPort, protocol, neighborManager);
        this.tcpClient = new NettyTCPClient(protocol);
        this.udpServer = new NettyUDPServer(listenHost, udpPort, protocol, neighborManager);
        this.udpClient = new NettyUDPClient(listenHost, udpPort, protocol);

        int tcpReceiverPort = config.integer(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT);
        this.clientFactory = new IOTATCPClientFactory(tcpClient, tcpReceiverPort);
    }

    public IOTAProtocol getProtocol() {
        return protocol;
    }

    public IOTAClient connect(Neighbor neighbor) throws IOException, InterruptedException {
        // FIXME - return UDP broadcast client here?
        if (neighbor.getProtocol() == Protocol.UDP) return null;

        return clientFactory.connectToNode(neighbor);
    }

    @Override
    protected void doStart() {
        tcpServer.init();
        tcpClient.init();
        udpServer.init();
        udpClient.init();
        LOG.info("Started up.");
        notifyStarted();
    }

    @Override
    protected void doStop() {
        tcpServer.shutdown();
        tcpClient.shutdown();
        udpServer.shutdown();
        udpClient.shutdown();
        LOG.info("Shutdown complete.");
        notifyStopped();
    }
}
