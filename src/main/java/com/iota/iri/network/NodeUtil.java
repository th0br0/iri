package com.iota.iri.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.iota.iri.model.Hash;
import io.netty.buffer.ByteBuf;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;
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
            default:
                throw new IllegalArgumentException();
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

    public static ByteBuffer toNioBuffer(ByteBuf buffer) {
        if (buffer.isDirect()) {
            return buffer.nioBuffer();
        }
        final byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return ByteBuffer.wrap(bytes);
    }
}
