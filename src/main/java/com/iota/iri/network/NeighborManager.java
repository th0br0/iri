package com.iota.iri.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * @author Andreas C. Osowski
 */
public class NeighborManager {
    private static final Logger LOG = LoggerFactory.getLogger(NeighborManager.class);

    private final Set<Neighbor> neighbors;

    public NeighborManager() {
        neighbors = Collections.synchronizedSet(new HashSet<>());
    }

    public Optional<Neighbor> getNeighborForAddress(Protocol protocol, InetSocketAddress address) {
        return neighbors.stream().filter(n -> n.getProtocol() == protocol
                && n.getPort() == address.getPort()
                && n.getAddress().get().equals(address.getAddress())).findFirst();
    }

    public Set<Neighbor> getNeighbors() {
        return new HashSet(neighbors);
    }

    public boolean addNeighbor(Neighbor neighbor) {
        LOG.trace("Adding neighbor: {}", neighbor);
        return neighbors.add(neighbor);
    }

    public boolean removeNeighbor(Neighbor neighbor) {
        LOG.trace("Removing neighbor: {}", neighbor);
        return neighbors.remove(neighbor);
    }

    public int getNeighborCount() {
        return neighbors.size();
    }
}
