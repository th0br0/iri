package com.iota.iri.network;

import com.iota.iri.conf.Configuration;

/**
 * @author Andreas C. Osowski
 */
public class NettyConnectionManager {
    private final NettyTCPServer tcpServer;
    private final NettyTCPClient tcpClient;
    private final NettyUDPServer udpServer;
    private final NettyUDPClient udpClient;
    private final IOTAProtocol protocol;

    public NettyConnectionManager(Configuration config) {
        int MWM;
        if(config.booling(Configuration.DefaultConfSettings.TESTNET)) {
            MWM = config.integer(Configuration.DefaultConfSettings.TESTNET_MWM);
        } else {
            MWM = config.integer(Configuration.DefaultConfSettings.MAINNET_MWM);
        }
        double dropTransaction = config.doubling(Configuration.DefaultConfSettings.P_DROP_TRANSACTION.name());
        long cacheSize = 5000;

        this.protocol = new IOTAProtocol(dropTransaction, MWM, cacheSize);

        this.tcpServer = new NettyTCPServer(config, protocol);
        this.udpServer = new NettyUDPServer(config, protocol);

        this.udpClient = new NettyUDPClient(config, protocol);
        this.tcpClient = new NettyTCPClient(config, protocol);
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

    public int getDownstreamConnectionsCount() {
        return -1;
    }

    public int getUpstreamConnectionsCount() {
        return -1;
    }

}
