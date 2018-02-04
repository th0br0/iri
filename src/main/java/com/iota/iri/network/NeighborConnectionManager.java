package com.iota.iri.network;

import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NeighborConnectionManager {
    private static final Logger LOG = LoggerFactory.getLogger(NeighborConnectionManager.class);

    private final NeighborManager neighborManager;
    private final NettyConnectionManager connectionManager;

    private final ConcurrentHashMap<Neighbor, IOTAClient> neighborConnections;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private EventLoopGroup eventGroup;
    private DnsNameResolver resolver;
    private ScheduledFuture<?> neighborCheckerFuture;

    public NeighborConnectionManager(NeighborManager neighborManager, NettyConnectionManager connectionManager) {
        this.neighborManager = neighborManager;
        this.connectionManager = connectionManager;

        neighborConnections = new ConcurrentHashMap<>();
    }

    public void start() {
        if (isRunning.get()) {
            LOG.warn("Already started.");
            return;
        }
        isRunning.set(true);

        eventGroup = new NioEventLoopGroup(4, NodeUtil.getNamedThreadFactory("NeighborConnectionManager"));
        resolver = new DnsNameResolverBuilder(eventGroup.next())
                .channelType(NioDatagramChannel.class)
                .ttl(300, 300).build();

        neighborCheckerFuture = eventGroup.scheduleAtFixedRate(this::checkNeighborConnections, 0, 1, TimeUnit.MINUTES);
    }

    public void shutdown() {
        if (!isRunning.get()) {
            LOG.warn("Tried to stop but wasn't started.");
            return;
        }
        LOG.info("Starting shutdown.");

        neighborCheckerFuture.cancel(true);
        neighborCheckerFuture = null;

        for (Map.Entry<Neighbor, IOTAClient> entry : neighborConnections.entrySet()) {
            LOG.info("Stopping connection: {}", entry.getKey());
            ChannelFuture future = entry.getValue().getCloseFuture();
            entry.getValue().close();

            future.syncUninterruptibly();
        }

        neighborConnections.clear();

        eventGroup.shutdownGracefully().syncUninterruptibly();

        LOG.info("Shutdown completed.");
    }

    protected void checkNeighborConnections() {
        Set<Neighbor> neighbors = neighborManager.getNeighbors();
        LOG.info("Checking neighbor connections.");

        for (Neighbor neighbor : neighbors) {
            IOTAClient client = neighborConnections.get(neighbor);

            if(client != null && client.isClosed()) {
                client = null;
            }

            Future<InetAddress> futureAddress = resolver.resolve(neighbor.getHost()).syncUninterruptibly();
            InetAddress address;

            try {
                address = futureAddress.get();
            } catch (Exception e) {
                LOG.info("Neighbor DNS resolution failed: " + neighbor);
                continue;
            }

            neighbor.getAddress().set(address);

            if(client != null) {
                boolean dnsChanged = !client.getRemoteAddress().equals(address);

                if (!dnsChanged) {
                    continue;
                }
                client.close();
            }

            try {
                neighborConnections.put(neighbor, connectionManager.connect(neighbor));
            } catch (Exception e) {
                LOG.info("Failed to connect to {}: {}", neighbor, e.getMessage());
            }
        }
    }

    public void loadNeighbors(String neighborStr) {
        Arrays.stream(neighborStr.split(" ")).distinct()
                .filter(s -> !s.isEmpty())
                .map(URI::create)
                .filter(NodeUtil::isUriValid)
                .map(NodeUtil::neighborFromURI)
                .forEach(neighborManager::addNeighbor);
    }
}
