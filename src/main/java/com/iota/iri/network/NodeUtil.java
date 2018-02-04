package com.iota.iri.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ThreadFactory;

/**
 * @author Andreas C. Osowski
 */
public class NodeUtil {
    private static final ThreadFactoryBuilder THREAD_FACTORY_BUILDER = new ThreadFactoryBuilder().setDaemon(true);

    public static ThreadFactory getNamedThreadFactory(String name) {
        return THREAD_FACTORY_BUILDER.setNameFormat(name + " Thread %d").build();
    }

    public static Neighbor neighborFromURI(URI uri) {
        if (!isUriValid(uri)) {
            throw new RuntimeException("Invalid Neighbor URI provided.");
        }

        Protocol proto = null;
        switch (uri.getScheme()) {
            case "tcp":
                proto = Protocol.TCP;
                break;
            case "udp":
                proto = Protocol.UDP;
                break;
        }

        return new Neighbor(proto, uri.getHost(), uri.getPort());
    }


    public static boolean isUriValid(URI uri) {
        if (uri != null) {
            if (uri.getScheme().equals("tcp") || uri.getScheme().equals("udp")) {
                if ((new InetSocketAddress(uri.getHost(), uri.getPort()).getAddress() != null)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }
}
