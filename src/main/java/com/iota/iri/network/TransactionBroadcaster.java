package com.iota.iri.network;

import com.google.common.util.concurrent.AbstractService;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionBroadcaster extends AbstractService {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionBroadcaster.class);

    private final NeighborConnectionManager connectionManager;
    private final int MAX_QUEUE_SIZE;
    private final TransactionRequester transactionRequester;

    private AtomicInteger queueSize = new AtomicInteger(0);
    private ThreadPoolExecutor eventExecutor;

    public TransactionBroadcaster(NeighborConnectionManager connectionManager, TransactionRequester transactionRequester, int MAX_QUEUE_SIZE) {
        this.connectionManager = connectionManager;
        this.MAX_QUEUE_SIZE = MAX_QUEUE_SIZE;
        this.transactionRequester = transactionRequester;
    }

    @Override
    protected void doStart() {
        LOG.debug("Starting up.");

        BlockingQueue<Runnable> queue = new PriorityBlockingQueue<>(MAX_QUEUE_SIZE);
        eventExecutor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, queue, NodeUtil.getNamedThreadFactory("TransactionBroadcaster"));
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

    protected void doBroadcast(final TransactionViewModel toBroadcast) {
        queueSize.decrementAndGet();
        connectionManager.getActiveClients().forEach((c) -> {
            Hash toRequest = Hash.NULL_HASH;
            try {
                toRequest = transactionRequester.transactionToRequest();
            } catch (Exception e) {
            }

            final Hash finalRequest = toRequest;

            c.send(toBroadcast, finalRequest);
        });
    }

    public void scheduleBroadcast(final TransactionViewModel toBroadcast) {
        if (queueSize.get() >= MAX_QUEUE_SIZE) {
            return;
        }
        LOG.trace("Scheduling broadcast: {}", toBroadcast.getHash());
        queueSize.incrementAndGet();
        eventExecutor.submit(new WeightedRunnable(toBroadcast));
    }

    class WeightedRunnable implements Runnable, Comparable {
        private final int trailingZeros;
        private final TransactionViewModel model;

        WeightedRunnable(TransactionViewModel model) {
            this.model = model;
            trailingZeros = model.getHash().trailingZeros();
        }

        @Override
        public void run() {
            doBroadcast(model);
        }

        @Override
        public int compareTo(Object o) {
            WeightedRunnable other = (WeightedRunnable) o;
            return Integer.compare(trailingZeros, other.trailingZeros);
        }
    }
}
