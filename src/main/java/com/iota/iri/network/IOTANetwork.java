package com.iota.iri.network;

import com.google.common.util.concurrent.AbstractService;
import com.iota.iri.Iota;
import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IOTANetwork extends AbstractService {
    private static final Logger LOG = LoggerFactory.getLogger(IOTANetwork.class);
    private final NeighborManager neighborManager;
    private final NettyConnectionManager connectionManager;
    private final NeighborConnectionManager neighborConnectionManager;
    private final TransactionBroadcaster transactionBroadcaster;
    private final TransactionStorer transactionStorer;
    private final TipsRequester tipsRequester;

    public IOTANetwork(Iota iota) {
        neighborManager = new NeighborManager();
        connectionManager = new NettyConnectionManager(iota.configuration, neighborManager);

        neighborConnectionManager = new NeighborConnectionManager(neighborManager, connectionManager);
        neighborConnectionManager.loadNeighbors(iota.configuration.string(Configuration.DefaultConfSettings.NEIGHBORS));

        transactionBroadcaster = new TransactionBroadcaster(neighborConnectionManager, iota.transactionRequester, 1000);
        transactionStorer = new TransactionStorer(iota.tangle, iota.transactionRequester, iota.transactionValidator, transactionBroadcaster);
        tipsRequester = new TipsRequester(neighborConnectionManager, iota.tangle, iota.milestone);

        connectionManager.getProtocol().setTransactionStorer(transactionStorer);
    }

    @Override
    protected void doStart() {
        LOG.debug("Starting up.");
        connectionManager.startAsync().awaitRunning();
        neighborConnectionManager.startAsync().awaitRunning();
        transactionBroadcaster.startAsync().awaitRunning();
        tipsRequester.startAsync().awaitRunning();
        LOG.info("Startup complete.");
        notifyStarted();
    }

    @Override
    protected void doStop() {
        LOG.debug("Shutting down.");
        tipsRequester.stopAsync();
        transactionBroadcaster.stopAsync();
        neighborConnectionManager.stopAsync();
        connectionManager.stopAsync();

        tipsRequester.awaitTerminated();
        transactionBroadcaster.awaitTerminated();
        neighborConnectionManager.awaitTerminated();
        connectionManager.awaitTerminated();

        LOG.info("Shutdown complete.");
        notifyStopped();
    }

    public void broadcast(TransactionViewModel transactionViewModel) {
        transactionBroadcaster.scheduleBroadcast(transactionViewModel);
    }

    public NettyConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public NeighborConnectionManager getNeighborConnectionManager() {
        return neighborConnectionManager;
    }

    public NeighborManager getNeighborManager() {
        return neighborManager;
    }
}
