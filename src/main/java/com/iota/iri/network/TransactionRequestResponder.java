package com.iota.iri.network;

import com.google.common.util.concurrent.AbstractService;
import com.iota.iri.Milestone;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.handlers.TransactionRequestHandler;
import com.iota.iri.storage.Tangle;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TransactionRequestResponder extends AbstractService {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionRequestResponder.class);

    private final SecureRandom random = new SecureRandom();

    private final TransactionRequestHandler requestHandler;
    private final NeighborConnectionManager connectionManager;
    private final Tangle tangle;
    private final TransactionRequester transactionRequester;
    private final Milestone milestone;
    private final TipsViewModel tipsViewModel;
    private final double P_SELECT_MILESTONE;

    private ExecutorService pool;

    public TransactionRequestResponder(NeighborConnectionManager neighborConnectionManager, TransactionRequestHandler transactionRequestHandler, Tangle tangle, TipsViewModel tipsViewModel, Milestone milestone, TransactionRequester transactionRequester, double pSelectMilestone) {
        this.P_SELECT_MILESTONE = pSelectMilestone;

        this.tangle = tangle;
        this.transactionRequester = transactionRequester;
        this.connectionManager = neighborConnectionManager;
        this.requestHandler = transactionRequestHandler;
        this.tipsViewModel = tipsViewModel;
        this.milestone = milestone;
    }

    @Override
    protected void doStart() {
        LOG.info("Starting up.");
        pool = Executors.newSingleThreadExecutor(NodeUtil.getNamedThreadFactory("TransactionRequestResponder"));
        pool.submit(this::processRequests);
        notifyStarted();
    }

    private void processRequests() {

        while (true) {
            Optional<Pair<Hash, Neighbor>> nextRequest = Optional.empty();
            try {
                nextRequest = requestHandler.nextRequest(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (nextRequest.isPresent()) {
                Pair<Hash, Neighbor> request = nextRequest.get();

                TransactionViewModel model;
                Hash toLoad = request.getLeft();
                Hash ourRequest;

                if (toLoad.equals(Hash.NULL_HASH)) {

                    if (random.nextDouble() < P_SELECT_MILESTONE) {
                        toLoad = milestone.latestMilestone;
                    } else {
                        toLoad = tipsViewModel.getRandomSolidTipHash();
                    }
                }

                try {
                    model = TransactionViewModel.fromHash(tangle, toLoad);
                } catch (Exception e) {
                    LOG.warn("Error occured during model loading {}: {}", toLoad, e.getMessage());
                    return;
                }

                try {
                    ourRequest = transactionRequester.transactionToRequest();
                } catch (Exception e) {
                    LOG.warn("Error occured when fetching new request: " + e.getMessage());
                    return;
                }
                connectionManager.getClientForNeighbor(request.getRight()).ifPresent((c) -> c.send(model, ourRequest));
            }
        }
    }

    @Override
    protected void doStop() {
        LOG.info("Shutting down.");
        try {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error("Error occured during shutdown: {}", e);
        }
        notifyStopped();
    }
}
