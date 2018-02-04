package com.iota.iri.network;

import com.google.common.util.concurrent.AbstractService;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class NeighborConnectionManager extends AbstractService {
    private static final Logger LOG = LoggerFactory.getLogger(NeighborConnectionManager.class);

    private final NeighborManager neighborManager;
    private final NettyConnectionManager connectionManager;

    private final ConcurrentHashMap<Neighbor, IOTAClient> neighborConnections;
    private EventLoopGroup eventGroup;

    private DnsNameResolver resolver;
    private ScheduledFuture<?> neighborCheckerFuture;

    public NeighborConnectionManager(NeighborManager neighborManager, NettyConnectionManager connectionManager) {
        this.neighborManager = neighborManager;
        this.connectionManager = connectionManager;

        neighborConnections = new ConcurrentHashMap<>();
    }

    @Override
    protected void doStart() {
        eventGroup = new NioEventLoopGroup(2, NodeUtil.getNamedThreadFactory("NeighborConnectionManager"));
        resolver = new DnsNameResolverBuilder(eventGroup.next())
                .channelType(NioDatagramChannel.class)
                .ttl(300, 300).build();

        neighborCheckerFuture = eventGroup.next().scheduleAtFixedRate(this::checkNeighborConnections, 0, 30, TimeUnit.SECONDS);
        notifyStarted();

    }

    @Override
    protected void doStop() {
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
        notifyStopped();
    }

    public List<IOTAClient> getActiveClients() {
        return neighborConnections.values().stream().filter((c) -> !c.isClosed()).collect(Collectors.toList());
    }

    public Optional<IOTAClient> getClientForNeighbor(Neighbor n) {
        IOTAClient c = neighborConnections.get(n);
        if (c == null || c.isClosed()) {
            return Optional.empty();
        } else {
            return Optional.of(c);
        }
    }

    protected void checkNeighborConnections() {
        List<Neighbor> neighbors = neighborManager.getNeighbors();
        LOG.info("Checking neighbor connections.");

        for (Neighbor neighbor : neighbors) {
            IOTAClient client = neighborConnections.get(neighbor);

            if (client != null && client.isClosed()) {
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

            if (client != null) {
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
                neighborConnections.remove(neighbor);
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
