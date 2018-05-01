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
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.proposal.storage.protectedstorage.ProposalPayload;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;
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

import lombok.extern.slf4j.Slf4j;

/**
 * Publishes proposal tx and payload to p2p network. Allow removal of proposal if in proposal phase.
 * Maintains myProposalList. Triggers republishing of my proposals at startup.
 */
@Slf4j
public class MyProposalListService implements PersistedDataHost {
    private final P2PService p2PService;
    private final StateService stateService;
    private final PeriodService periodService;
    private final WalletsManager walletsManager;
    private final Storage<ProposalList> storage;
    private final PublicKey signaturePubKey;

    private final ProposalList myProposalList = new ProposalList();
    private final ChangeListener<Number> numConnectedPeersListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyProposalListService(P2PService p2PService,
                                 StateService stateService,
                                 PeriodService periodService,
                                 WalletsManager walletsManager,
                                 Storage<ProposalList> storage,
                                 KeyRing keyRing) {
        this.p2PService = p2PService;
        this.stateService = stateService;
        this.periodService = periodService;
        this.walletsManager = walletsManager;
        this.storage = storage;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
        numConnectedPeersListener = (observable, oldValue, newValue) -> maybeRePublish();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        maybeRePublish();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            ProposalList persisted = storage.initAndGetPersisted(myProposalList, "MyProposalList", 20);
            if (persisted != null) {
                myProposalList.clear();
                myProposalList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Broadcast proposalTx and publish proposal to P2P network
    public void publishProposal(Proposal proposal, Transaction transaction, ResultHandler resultHandler,
                                ErrorMessageHandler errorMessageHandler) {
        walletsManager.publishAndCommitBsqTx(transaction, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
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
        // Inconsistently propagated proposals in the p2p network could have potentially worse effects.
        addProposalToP2PNetwork(proposal, errorMessageHandler);
    }

    public boolean removeMyProposal(Proposal proposal) {
        if (ProposalUtils.canRemoveProposal(proposal, stateService, periodService)) {
            boolean success = p2PService.removeData(createProposalPayload(proposal), true);
            if (!success)
                log.warn("Removal of proposal from p2p network failed. proposal={}", proposal);

            if (myProposalList.remove(proposal))
                persist();
            else
                log.warn("We called removeProposalFromList at a proposal which was not in our list");

            return success;
        } else {
            final String msg = "removeProposal called with a proposal which is outside of the proposal phase.";
            DevEnv.logErrorAndThrowIfDevMode(msg);
            return false;
        }
    }

    public List<Proposal> getMyProposals() {
        return myProposalList.getList();
    }

    public boolean isMyProposal(Proposal proposal) {
        return ProposalUtils.containsProposal(proposal, myProposalList.getList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addProposalToP2PNetwork(Proposal proposal, ErrorMessageHandler errorMessageHandler) {
        final boolean success = addToP2PNetwork(proposal);
        if (success) {
            log.info("We added a proposal to the P2P network. Proposal.txId=" + proposal.getTxId());
        } else {
            final String msg = "Adding of proposal to P2P network failed. proposal=" + proposal;
            log.error(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }

        if (!ProposalUtils.containsProposal(proposal, myProposalList.getList())) {
            myProposalList.add(proposal);
            persist();
        }
    }

    private void maybeRePublish() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false. Also better for production version
        // to avoid activity peaks at startup
        UserThread.runAfter(() -> {
            if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
                p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
                rePublishProposals();
            }
        }, 2);
    }

    private void rePublishProposals() {
        myProposalList.forEach(proposal -> {
            final String txId = proposal.getTxId();
            if (periodService.isTxInPhase(txId, DaoPhase.Phase.PROPOSAL) &&
                    periodService.isTxInCorrectCycle(txId, periodService.getChainHeight())) {
                if (!addToP2PNetwork(proposal))
                    log.warn("Adding of proposal to P2P network failed.\nproposal=" + proposal);
            }
        });
    }

    private boolean addToP2PNetwork(Proposal proposal) {
        return p2PService.addProtectedStorageEntry(createProposalPayload(proposal), true);
    }

    private ProtectedStoragePayload createProposalPayload(Proposal proposal) {
        return new ProposalPayload(proposal, signaturePubKey);
    }

    private void persist() {
        storage.queueUpForSave();
    }
}
