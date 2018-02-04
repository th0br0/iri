package com.iota.iri.network;

import com.google.common.util.concurrent.AbstractService;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TransactionBroadcaster extends AbstractService {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionBroadcaster.class);

    private final int MAX_QUEUE_SIZE;
    private final NeighborConnectionManager connectionManager;
    private final TransactionRequester transactionRequester;

    private PriorityBlockingQueue<TransactionViewModel> broadcastQueue;
    private ExecutorService eventExecutor;

    public TransactionBroadcaster(NeighborConnectionManager connectionManager, TransactionRequester transactionRequester, int MAX_QUEUE_SIZE) {
        this.connectionManager = connectionManager;
        this.MAX_QUEUE_SIZE = MAX_QUEUE_SIZE;
        this.transactionRequester = transactionRequester;
    }

    @Override
    protected void doStart() {
        LOG.debug("Starting up.");

        broadcastQueue = new PriorityBlockingQueue<>(MAX_QUEUE_SIZE, Comparator.comparingInt(o -> o.weightMagnitude));
        eventExecutor = Executors.newSingleThreadExecutor(NodeUtil.getNamedThreadFactory("TransactionBroadcaster"));

        eventExecutor.submit(this::broadcast);
        LOG.info("Startup complete.");
        notifyStarted();
    }

    @Override
    protected void doStop() {
        LOG.debug("Shutting down.");
        eventExecutor.shutdown();
        try {
            eventExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOG.info("Shutdown complete.");
        notifyStopped();
    }

    protected void broadcast() {
        while (true) {
            try {
                TransactionViewModel toBroadcast = broadcastQueue.poll(1, TimeUnit.SECONDS);

                if(toBroadcast != null) {
                    doBroadcast(toBroadcast);
                }
            } catch (InterruptedException e) {
                // No element yet.
            }
        }
    }

    protected void doBroadcast(TransactionViewModel toBroadcast) {
        connectionManager.getActiveClients().forEach((c) -> {
            Hash toRequest = Hash.NULL_HASH;
            try {
                toRequest = transactionRequester.transactionToRequest();
            } catch (Exception e) {
            }

            c.send(toBroadcast, toRequest);
        });
    }

    public void scheduleBroadcast(TransactionViewModel toBroadcast) {
        while (broadcastQueue.size() >= MAX_QUEUE_SIZE) {
            broadcastQueue.poll();
        }

        LOG.trace("Scheduling broadcast: {}", toBroadcast.getHash());
        broadcastQueue.offer(toBroadcast);
    }
}
