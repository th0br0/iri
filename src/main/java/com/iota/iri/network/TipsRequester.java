package com.iota.iri.network;

import com.google.common.util.concurrent.AbstractService;
import com.iota.iri.Milestone;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TipsRequester extends AbstractService {
    private final static Logger LOG = LoggerFactory.getLogger(TipsRequester.class);

    private final Tangle tangle;
    private final NeighborConnectionManager connectionManager;
    private final Milestone milestone;
    private final ScheduledExecutorService threadPool;

    public TipsRequester(NeighborConnectionManager connectionManager, Tangle tangle, Milestone milestone) {
        this.connectionManager = connectionManager;
        this.tangle = tangle;
        this.milestone = milestone;

        this.threadPool = Executors.newSingleThreadScheduledExecutor(NodeUtil.getNamedThreadFactory("TipsRequester"));
    }

    @Override
    protected void doStart() {
        threadPool.scheduleAtFixedRate(this::requestTip, 0, 5, TimeUnit.SECONDS);
        LOG.info("Started up.");
        notifyStarted();
    }

    private void requestTip() {
        TransactionViewModel model;
        try {
            model = TransactionViewModel.fromHash(tangle, milestone.latestMilestone);
        } catch (Exception e) {
            LOG.debug("Failed to load latest milestone.");
            return;
        }

        connectionManager.getActiveClients().forEach((c) -> c.send(model, model.getHash()));
    }

    @Override
    protected void doStop() {
        LOG.info("Shutdown complete.");
        notifyStopped();
    }
}
