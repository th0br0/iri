package com.iota.iri.network.stats;

/**
 * @author Andreas C. Osowski
 */
public interface NeighborStatistics {
    void incrAllTransactions();
    void incrNewTransactions();
    void incrInvalidTransactions();
    void incrRandomRequests();
    void incrSentTransactions();
}
