package com.iota.iri.network;

import io.netty.util.AttributeKey;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicReference;

public class Neighbor {
    public final static AttributeKey<Neighbor> KEY = AttributeKey.newInstance("neighbor");

    private Protocol protocol;
    private String host;
    private int port;

    private AtomicReference<InetAddress> address;

    public Neighbor(Protocol protocol, String hostname, int port) {
        this.protocol = protocol;
        this.host = hostname;
        this.port = port;

        // Temporary initialiser.
        address = new AtomicReference<>(InetAddress.getLoopbackAddress());
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public AtomicReference<InetAddress> getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return "Neighbor{" +
                "protocol=" + protocol +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", address=" + address +
                '}';
    }
}
