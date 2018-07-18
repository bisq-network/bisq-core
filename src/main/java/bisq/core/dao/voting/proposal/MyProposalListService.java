/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.voting.proposal;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcastTimeoutException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.TxMalleabilityException;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.proposal.storage.temp.TempProposalPayload;

import bisq.network.p2p.P2PService;

import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import javafx.beans.value.ChangeListener;

import java.security.PublicKey;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

/**
 * Publishes proposal tx and proposalPayload to p2p network.
 * Allow removal of proposal if in proposal phase.
 * Maintains myProposalList for own proposals.
 * Triggers republishing of my proposals at startup.
 */
@Slf4j
public class MyProposalListService implements PersistedDataHost, BsqStateListener {
    public interface Listener {
        void onListChanged(List<Proposal> list);
    }

    private final P2PService p2PService;
    private final StateService stateService;
    private final PeriodService periodService;
    private final WalletsManager walletsManager;
    private final Storage<MyProposalList> storage;
    private final PublicKey signaturePubKey;

    private final MyProposalList myProposalList = new MyProposalList();
    private ChangeListener<Number> numConnectedPeersListener;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyProposalListService(P2PService p2PService,
                                 StateService stateService,
                                 PeriodService periodService,
                                 WalletsManager walletsManager,
                                 Storage<MyProposalList> storage,
                                 KeyRing keyRing) {
        this.p2PService = p2PService;
        this.stateService = stateService;
        this.periodService = periodService;
        this.walletsManager = walletsManager;
        this.storage = storage;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();

        numConnectedPeersListener = (observable, oldValue, newValue) -> rePublishOnceWellConnected();
        stateService.addBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            MyProposalList persisted = storage.initAndGetPersisted(myProposalList, 100);
            if (persisted != null) {
                myProposalList.clear();
                myProposalList.addAll(persisted.getList());
                listeners.forEach(l -> l.onListChanged(getList()));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onEmptyBlockAdded(Block block) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
    }

    @Override
    public void onParseBlockChainComplete() {
        rePublishOnceWellConnected();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
    }

    // Broadcast tx and publish proposal to P2P network
    public void publishTxAndPayload(Proposal proposal, Transaction transaction, ResultHandler resultHandler,
                                    ErrorMessageHandler errorMessageHandler) {
        walletsManager.publishAndCommitBsqTx(transaction, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                log.info("Proposal tx has been published. TxId={}, proposalUid={}",
                        transaction.getHashAsString(), proposal.getUid());
                resultHandler.handleResult();
            }

            @Override
            public void onTimeout(TxBroadcastTimeoutException exception) {
                // TODO handle
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }

            @Override
            public void onTxMalleability(TxMalleabilityException exception) {
                // TODO handle
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                // TODO handle
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }
        });

        // We prefer to not wait for the tx broadcast as if the tx broadcast would fail we still prefer to have our
        // proposal stored and broadcasted to the p2p network. The tx might get re-broadcasted at a restart and
        // in worst case if it does not succeed the proposal will be ignored anyway.
        // Inconsistently propagated payloads in the p2p network could have potentially worse effects.
        addToP2PNetworkAsProtectedData(proposal, errorMessageHandler);

        addToList(proposal);
    }

    public boolean remove(Proposal proposal) {
        if (ProposalUtils.canRemoveProposal(proposal, stateService, periodService)) {
            boolean success = p2PService.removeData(new TempProposalPayload(proposal, signaturePubKey), true);
            if (!success)
                log.warn("Removal of proposal from p2p network failed. proposal={}", proposal);

            if (myProposalList.remove(proposal)) {
                listeners.forEach(l -> l.onListChanged(getList()));
                persist();
            } else {
                log.warn("We called remove at a proposal which was not in our list");
            }
            return success;
        } else {
            final String msg = "remove called with a proposal which is outside of the proposal phase.";
            DevEnv.logErrorAndThrowIfDevMode(msg);
            return false;
        }
    }

    public boolean isMine(Proposal proposal) {
        return ProposalUtils.containsProposal(proposal, getList());
    }

    public List<Proposal> getList() {
        return myProposalList.getList();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addToP2PNetworkAsProtectedData(Proposal proposal, ErrorMessageHandler errorMessageHandler) {
        final boolean success = addToP2PNetworkAsProtectedData(proposal);
        if (success) {
            log.info("TempProposalPayload has been added to P2P network. ProposalUid={}", proposal.getUid());
        } else {
            final String msg = "Adding of proposal to P2P network failed. proposal=" + proposal;
            log.error(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
    }

    private boolean addToP2PNetworkAsProtectedData(Proposal proposal) {
        return p2PService.addProtectedStorageEntry(new TempProposalPayload(proposal, signaturePubKey), true);
    }

    private void addToList(Proposal proposal) {
        if (!ProposalUtils.containsProposal(proposal, getList())) {
            myProposalList.add(proposal);
            listeners.forEach(l -> l.onListChanged(getList()));
            persist();
        }
    }

    private void rePublishOnceWellConnected() {
        if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped())) {
            p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
            rePublish();
        }
    }

    private void rePublish() {
        myProposalList.forEach(proposal -> {
            final String txId = proposal.getTxId();
            if (periodService.isTxInPhase(txId, DaoPhase.Phase.PROPOSAL) &&
                    periodService.isTxInCorrectCycle(txId, periodService.getChainHeight())) {
                if (!addToP2PNetworkAsProtectedData(proposal))
                    log.warn("Adding of proposal to P2P network failed.\nproposal=" + proposal);
            }
        });
    }

    private void persist() {
        storage.queueUpForSave();
    }
}
