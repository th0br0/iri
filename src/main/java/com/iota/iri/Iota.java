package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.*;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.network.IOTANetwork;
import com.iota.iri.network.TransactionRequestResponder;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.TipsManager;
import com.iota.iri.storage.*;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Pair;
import com.iota.iri.zmq.MessageQ;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by paul on 5/19/17.
 */
public class Iota {
    public static final String MAINNET_COORDINATOR_ADDRESS = "KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU";
    public static final Hash MAINNET_COORDINATOR = new Hash(MAINNET_COORDINATOR_ADDRESS);
    public static final String TESTNET_COORDINATOR_ADDRESS = "XNZBYAST9BETSDNOVQKKTBECYIPMF9IPOZRWUPFQGVH9HJW9NDSQVIPVBWU9YKECRYGDSJXYMZGHZDXCA";
    public static final Hash TESTNET_COORDINATOR = new Hash(TESTNET_COORDINATOR_ADDRESS);
    private static final Logger log = LoggerFactory.getLogger(Iota.class);
    public final LedgerValidator ledgerValidator;
    public final Milestone milestone;
    public final Tangle tangle;
    public final TransactionValidator transactionValidator;
    public final TipsManager tipsManager;
    public final Configuration configuration;
    public final Hash coordinator;
    public final TipsViewModel tipsViewModel;
    public final MessageQ messageQ;
    public final boolean testnet;
    public final int maxPeers;
    public final int udpPort;
    public final int tcpPort;
    public final int maxTipSearchDepth;
    public final IOTANetwork network;
    public final TransactionRequester transactionRequester;
    public final TransactionRequestResponder transactionRequestResponder;

    public Iota(Configuration configuration) {
        this.configuration = configuration;
        testnet = configuration.booling(Configuration.DefaultConfSettings.TESTNET);
        maxPeers = configuration.integer(Configuration.DefaultConfSettings.MAX_PEERS);
        udpPort = configuration.integer(Configuration.DefaultConfSettings.UDP_RECEIVER_PORT);
        tcpPort = configuration.integer(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT);
        maxTipSearchDepth = configuration.integer(Configuration.DefaultConfSettings.MAX_DEPTH);
        if (testnet) {
            String coordinatorTrytes = configuration.string(Configuration.DefaultConfSettings.COORDINATOR);
            if (coordinatorTrytes != null) {
                coordinator = new Hash(coordinatorTrytes);
            } else {
                coordinator = TESTNET_COORDINATOR;
            }
        } else {
            coordinator = MAINNET_COORDINATOR;
        }
        tangle = new Tangle();
        messageQ = new MessageQ(configuration.integer(Configuration.DefaultConfSettings.ZMQ_PORT),
                configuration.string(Configuration.DefaultConfSettings.ZMQ_IPC),
                configuration.integer(Configuration.DefaultConfSettings.ZMQ_THREADS),
                configuration.booling(Configuration.DefaultConfSettings.ZMQ_ENABLED)
        );
        tipsViewModel = new TipsViewModel();

        transactionRequester = new TransactionRequester(tangle, messageQ, configuration.doubling(Configuration.DefaultConfSettings.P_SELECT_MILESTONE_CHILD.name()));
        transactionValidator = new TransactionValidator(tangle, tipsViewModel, transactionRequester, messageQ);
        milestone = new Milestone(tangle, coordinator, Snapshot.initialSnapshot.clone(), transactionValidator, testnet, messageQ);
        ledgerValidator = new LedgerValidator(tangle, milestone, transactionRequester, messageQ);
        tipsManager = new TipsManager(tangle, ledgerValidator, transactionValidator, tipsViewModel, milestone, maxTipSearchDepth, messageQ);

        network = new IOTANetwork(this);
        transactionRequestResponder = new TransactionRequestResponder(network.getNeighborConnectionManager(), network.getConnectionManager().getProtocol().getTransactionRequestHandler(), tangle,
                tipsViewModel, milestone, transactionRequester,
                configuration.doubling(Configuration.DefaultConfSettings.P_SEND_MILESTONE.name()),
                configuration.doubling(Configuration.DefaultConfSettings.P_PROPAGATE_REQUEST.name()));
    }

    public void init() throws Exception {
        initializeTangle();
        tangle.init();

        if (configuration.booling(Configuration.DefaultConfSettings.RESCAN_DB)) {
            rescan_db();
        }
        boolean revalidate = configuration.booling(Configuration.DefaultConfSettings.REVALIDATE);

        if (revalidate) {
            tangle.clearColumn(com.iota.iri.model.Milestone.class);
            tangle.clearColumn(com.iota.iri.model.StateDiff.class);
            tangle.clearMetadata(com.iota.iri.model.Transaction.class);
        }
        milestone.init(SpongeFactory.Mode.CURLP27, ledgerValidator, revalidate);
        transactionValidator.init(testnet, configuration.integer(Configuration.DefaultConfSettings.MAINNET_MWM), configuration.integer(Configuration.DefaultConfSettings.TESTNET_MWM));
        transactionRequester.init(configuration.doubling(Configuration.DefaultConfSettings.P_REMOVE_REQUEST.name()));
        tipsManager.init();

        network.startAsync().awaitRunning();
        transactionRequestResponder.startAsync().awaitRunning();
    }

    private void rescan_db() throws Exception {
        int counter = 0;
        //delete all Address , Bundle , Approvee & Tag
        AddressViewModel add = AddressViewModel.first(tangle);
        while (add != null) {
            if (++counter % 10000 == 0) {
                log.info("Clearing cache: {} Addresses", counter);
            }
            AddressViewModel NextAdd = add.next(tangle);
            add.delete(tangle);
            add = NextAdd;
        }
        counter = 0;
        BundleViewModel bn = BundleViewModel.first(tangle);
        while (bn != null) {
            if (++counter % 10000 == 0) {
                log.info("Clearing cache: {} Bundles", counter);
            }
            BundleViewModel NextBn = bn.next(tangle);
            bn.delete(tangle);
            bn = NextBn;
        }
        counter = 0;
        ApproveeViewModel app = ApproveeViewModel.first(tangle);
        while (app != null) {
            if (++counter % 10000 == 0) {
                log.info("Clearing cache: {} Approvees", counter);
            }
            ApproveeViewModel NextApp = app.next(tangle);
            app.delete(tangle);
            app = NextApp;
        }
        counter = 0;
        TagViewModel tag = TagViewModel.first(tangle);
        while (tag != null) {
            if (++counter % 10000 == 0) {
                log.info("Clearing cache: {} Tags", counter);
            }
            TagViewModel NextTag = tag.next(tangle);
            tag.delete(tangle);
            tag = NextTag;
        }

        //rescan all tx & refill the columns
        TransactionViewModel tx = TransactionViewModel.first(tangle);
        counter = 0;
        while (tx != null) {
            if (++counter % 10000 == 0) {
                log.info("Rescanned {} Transactions", counter);
            }
            List<Pair<Indexable, Persistable>> saveBatch = tx.getSaveBatch();
            saveBatch.remove(5);
            tangle.saveBatch(saveBatch);
            tx = tx.next(tangle);
        }
    }

    public void shutdown() throws Exception {
        transactionRequestResponder.stopAsync().awaitTerminated();
        network.stopAsync().awaitTerminated();
        milestone.shutDown();
        tipsManager.shutdown();
        transactionValidator.shutdown();
        tangle.shutdown();
    }

    private void initializeTangle() {
        String dbPath = configuration.string(Configuration.DefaultConfSettings.DB_PATH);
        if (testnet) {
            if (dbPath.isEmpty() || dbPath.equals("mainnetdb")) {
                // testnetusers must not use mainnetdb, overwrite it unless an explicit name is set.
                configuration.put(Configuration.DefaultConfSettings.DB_PATH.name(), "testnetdb");
                configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH.name(), "testnetdb.log");
            }
        } else {
            if (dbPath.isEmpty() || dbPath.equals("testnetdb")) {
                // mainnetusers must not use testnetdb, overwrite it unless an explicit name is set.
                configuration.put(Configuration.DefaultConfSettings.DB_PATH.name(), "mainnetdb");
                configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH.name(), "mainnetdb.log");
            }
        }
        switch (configuration.string(Configuration.DefaultConfSettings.MAIN_DB)) {
            case "rocksdb": {
                tangle.addPersistenceProvider(new RocksDBPersistenceProvider(
                        configuration.string(Configuration.DefaultConfSettings.DB_PATH),
                        configuration.string(Configuration.DefaultConfSettings.DB_LOG_PATH),
                        configuration.integer(Configuration.DefaultConfSettings.DB_CACHE_SIZE)));
                break;
            }
            default: {
                throw new NotImplementedException("No such database type.");
            }
        }
        if (configuration.booling(Configuration.DefaultConfSettings.EXPORT)) {
            tangle.addPersistenceProvider(new FileExportProvider());
        }
        if (configuration.booling(Configuration.DefaultConfSettings.ZMQ_ENABLED)) {
            tangle.addPersistenceProvider(new ZmqPublishProvider(messageQ));
        }
    }
}
