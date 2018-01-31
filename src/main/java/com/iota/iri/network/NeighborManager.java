package com.iota.iri.network;

/**
 * @author Andreas C. Osowski
 */
public class NeighborManager {

    private final NettyTCPClient tcpClient;

    public NeighborManager(NettyTCPClient tcpClient) {
        this.tcpClient = tcpClient;
    }

    public void addNeighbor() {
    }

    public void removeNeighbor() {
    }

    public int getNeighborCount() {
        return 0;
    }
}
