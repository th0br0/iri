package com.iota.iri.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Andreas C. Osowski
 */
public class NeighborManager {
    private static final Logger LOG = LoggerFactory.getLogger(NeighborManager.class);

    private final ConcurrentMap<Neighbor, Boolean> neighbors;

    public NeighborManager() {
        neighbors = new ConcurrentHashMap<>();
    }

    public Optional<Neighbor> getNeighborForAddress(Protocol protocol, InetSocketAddress address) {
        return neighbors.keySet().stream().filter(n -> n.getProtocol() == protocol
                && n.getPort() == address.getPort()
                && n.getAddress().get().equals(address.getAddress())).findFirst();
    }

    public List<Neighbor> getNeighbors() {
        return new ArrayList<>(neighbors.keySet());
    }

    public boolean addNeighbor(Neighbor neighbor) {
        LOG.trace("Adding neighbor: {}", neighbor);
        return neighbors.put(neighbor, true) == null;
    }

    public boolean removeNeighbor(Neighbor neighbor) {
        LOG.trace("Removing neighbor: {}", neighbor);
        return neighbors.remove(neighbor) != null;
    }

    public int getNeighborCount() {
        return neighbors.size();
    }
}
