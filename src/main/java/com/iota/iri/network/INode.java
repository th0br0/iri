package com.iota.iri.network;

/**
 * @author Andreas C. Osowski
 */
public interface INode {
    int TRANSACTION_PACKET_SIZE = 1650;
    int REQUEST_HASH_SIZE = 46;

    void init() throws Exception;

    void shutdown() throws InterruptedException;

    int queuedTransactionsSize();

    int howManyNeighbors();

    int getBroadcastQueueSize();

    int getReceiveQueueSize();

    int getReplyQueueSize();
}
