package com.iota.iri.network;

import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionStorer {
    private final Logger LOG = LoggerFactory.getLogger(TransactionStorer.class);
    private final TransactionRequester transactionRequester;
    private final Tangle tangle;
    private final TransactionValidator transactionValidator;
    private final TransactionBroadcaster transactionBroadcaster;

    public TransactionStorer(Tangle tangle, TransactionRequester transactionRequester, TransactionValidator transactionValidator, TransactionBroadcaster transactionBroadcaster) {
        this.tangle = tangle;
        this.transactionRequester = transactionRequester;
        this.transactionValidator = transactionValidator;
        this.transactionBroadcaster = transactionBroadcaster;
    }

    public void store(Neighbor neighbor, TransactionViewModel model) {
        boolean stored = false;

        LOG.trace("Storing transaction: {}", model.getHash());
        try {
            stored = model.store(tangle);
        } catch (Exception e) {
            LOG.error("Error accessing persistence store.", e);
            //FIXME neighbor.incInvalidTransactions();
        }

        //if new, then broadcast to all neighbors
        if (stored) {
            LOG.trace("{} was new transaction.", model.getHash());
            try {
                transactionValidator.updateStatus(model);
                model.updateSender(neighbor.getAddress().toString());
                model.update(tangle, "arrivalTime|sender");
            } catch (Exception e) {
                LOG.error("Error updating transactions.", e);
            }
            // FIXME neighbor.incNewTransactions();

            transactionBroadcaster.scheduleBroadcast(model);
        }


    }
}
