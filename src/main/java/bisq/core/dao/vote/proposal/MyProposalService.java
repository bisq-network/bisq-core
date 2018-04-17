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

package bisq.core.dao.vote.proposal;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcastTimeoutException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.TxMalleabilityException;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.period.Phase;
import bisq.core.dao.state.StateService;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists, publishes and republishes own proposals.
 * Is accessed in user thread.
 */
@Slf4j
public class MyProposalService implements PersistedDataHost {
    private final P2PService p2PService;
    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final ProposalService proposalService;
    private final StateService stateService;
    private final Storage<ProposalList> storage;
    private ChangeListener<Number> numConnectedPeersListener;

    @Getter
    private final ObservableList<Proposal> observableList = FXCollections.observableArrayList();
    private final ProposalList proposalList = new ProposalList(observableList);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyProposalService(P2PService p2PService,
                             WalletsManager walletsManager,
                             BsqWalletService bsqWalletService,
                             ProposalService proposalService,
                             StateService stateService,
                             Storage<ProposalList> storage) {
        this.p2PService = p2PService;
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.proposalService = proposalService;
        this.stateService = stateService;

        this.storage = storage;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        // Republish my proposals once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> maybeRePublish();
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
        maybeRePublish();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            ProposalList persisted = storage.initAndGetPersisted(proposalList, "MyProposals", 20);
            if (persisted != null) {
                observableList.clear();
                observableList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void publishProposal(Proposal proposal, Transaction transaction, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        walletsManager.publishAndCommitBsqTx(transaction, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                final String txId = transaction.getHashAsString();
                if (proposal.getTxId().equals(txId)) {
                    final ProposalPayload proposalPayload = proposal.getProposalPayload();
                    if (addToP2PNetwork(proposalPayload)) {
                        log.info("We added a proposalPayload to the P2P network. ProposalPayload.uid=" + proposalPayload.getUid());
                        if (!listContains(proposal.getProposalPayload()))
                            observableList.add(proposal);
                        resultHandler.handleResult();
                    } else {
                        final String msg = "Adding of proposalPayload to P2P network failed.\n" +
                                "proposalPayload=" + proposalPayload;
                        log.error(msg);
                        errorMessageHandler.handleErrorMessage(msg);
                    }
                } else {
                    final String msg = "TxId of broadcasted transaction is different as the one in our " +
                            "proposalPayload. TxId of broadcasted transaction=" + txId;
                    log.error(msg);
                    errorMessageHandler.handleErrorMessage(msg);
                }
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
    }

    public boolean removeProposal(Proposal proposal) {
        final ProposalPayload proposalPayload = proposal.getProposalPayload();
        // We allow removal which are not confirmed yet or if it we are in the right phase
        if (proposalService.isInPhaseOrUnconfirmed(stateService.getTx(proposalPayload.getTxId()),
                proposalPayload.getTxId(),
                Phase.PROPOSAL,
                stateService.getChainHeight())) {
            boolean success = p2PService.removeData(proposalPayload, true);
            if (success) {
                if (observableList.remove(proposal))
                    persist();
                else
                    log.warn("We called removeProposalFromList at a proposal which was not in our list");
            } else {
                log.warn("Removal of proposal from p2p network failed. proposal={}", proposal);
            }
            return success;
        } else {
            final String msg = "removeProposal called with a Proposal which is outside of the Proposal phase.";
            DevEnv.logErrorAndThrowIfDevMode(msg);
            return false;
        }
    }

    public void persist() {
        storage.queueUpForSave();
    }

    private boolean listContains(ProposalPayload proposalPayload) {
        return findProposal(proposalPayload).isPresent();
    }

    private Optional<Proposal> findProposal(ProposalPayload proposalPayload) {
        return observableList.stream()
                .filter(proposal -> proposal.getProposalPayload().equals(proposalPayload))
                .findAny();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeRePublish() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false. Also better for production version
        // to avoid activity peaks at startup
        UserThread.runAfter(() -> {
            if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
                p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
                rePublish();
            }
        }, 2);
    }

    private void rePublish() {
        observableList.stream()
                .map(Proposal::getProposalPayload)
                .forEach(proposalPayload -> {
                    if (!addToP2PNetwork(proposalPayload))
                        log.warn("Adding of proposalPayload to P2P network failed.\nproposalPayload=" + proposalPayload);
                });
    }

    private boolean addToP2PNetwork(ProposalPayload proposalPayload) {
        return p2PService.addProtectedStorageEntry(proposalPayload, true);
    }
}
