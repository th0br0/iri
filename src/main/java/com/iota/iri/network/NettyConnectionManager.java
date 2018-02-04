package com.iota.iri.network;

import com.iota.iri.conf.Configuration;

import java.io.IOException;

/**
 * @author Andreas C. Osowski
 */
public class NettyConnectionManager {
    private final IOTAProtocol protocol;
    private final NeighborManager neighborManager;
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
        long cacheSize = 5000;

        this.protocol = new IOTAProtocol(neighborManager, dropTransaction, MWM, cacheSize);
        this.neighborManager = neighborManager;

        this.tcpServer = new NettyTCPServer(config, protocol, neighborManager);
        this.tcpClient = new NettyTCPClient(config, protocol);
        this.udpServer = new NettyUDPServer(config, protocol, neighborManager);
        this.udpClient = new NettyUDPClient(config, protocol);


        int tcpReceiverPort = config.integer(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT);
        this.clientFactory = new IOTATCPClientFactory(tcpClient, tcpReceiverPort);
    }

    public IOTAClient connect(Neighbor neighbor) throws IOException, InterruptedException {
        // FIXME - return UDP broadcast client here?
        if (neighbor.getProtocol() == Protocol.UDP) return null;

        return clientFactory.connectToNode(neighbor);
    }

    public void start() {
        tcpServer.init();
        tcpClient.init();
        udpServer.init();
        udpClient.init();
    }

    public void shutdown() {
        tcpServer.shutdown();
        tcpClient.shutdown();
        udpServer.shutdown();
        udpClient.shutdown();
    }
}
