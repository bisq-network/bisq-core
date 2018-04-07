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
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.vote.BaseService;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.proposal.compensation.CompensationRequest;
import bisq.core.dao.vote.proposal.generic.GenericProposal;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Manages proposal collections.
 */
@Slf4j
public class ProposalService extends BaseService {
    private final Storage<ProposalList> storage;

    @Getter
    private final ObservableList<Proposal> observableList = FXCollections.observableArrayList();
    private final ProposalList proposalList = new ProposalList(observableList);
    @Getter
    private final FilteredList<Proposal> validProposals = new FilteredList<>(observableList);
    @Getter
    private final FilteredList<Proposal> validOrMyUnconfirmedProposals = new FilteredList<>(observableList);
    @Getter
    private final FilteredList<Proposal> closedProposals = new FilteredList<>(observableList);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalService(P2PService p2PService,
                           WalletsManager walletsManager,
                           BsqWalletService bsqWalletService,
                           PeriodService periodService,
                           ReadableBsqBlockChain readableBsqBlockChain,
                           KeyRing keyRing,
                           Storage<ProposalList> storage) {
        super(p2PService,
                walletsManager,
                bsqWalletService,
                periodService,
                readableBsqBlockChain,
                keyRing);
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            ProposalList persisted = storage.initAndGetPersisted(proposalList, 20);
            if (persisted != null) {
                this.observableList.clear();
                this.observableList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onRemoved(ProtectedStorageEntry data) {
        final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            findProposal((ProposalPayload) protectedStoragePayload)
                    .ifPresent(proposal -> {
                        if (isInPhaseOrUnconfirmed(proposal.getTxId(), PeriodService.Phase.PROPOSAL)) {
                            removeProposalFromList(proposal);
                        } else {
                            final String msg = "onRemoved called of a Proposal which is outside of the Request phase is invalid and we ignore it.";
                            DevEnv.logErrorAndThrowIfDevMode(msg);
                        }
                    });
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // BaseService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload)
            addProposal((ProposalPayload) protectedStoragePayload, storeLocally);
    }

    @Override
    protected void onBlockHeightChanged(int height) {
        // We display own unconfirmed proposals as there is no risk. Other proposals are only shown after they are in
        //the blockchain and verified
        validProposals.setPredicate(proposal -> isValid(proposal.getProposalPayload()));
        validOrMyUnconfirmedProposals.setPredicate(proposal -> (isUnconfirmed(proposal.getTxId()) &&
                isMine(proposal.getProposalPayload())) ||
                isValid(proposal.getProposalPayload()));
        closedProposals.setPredicate(proposal -> isValid(proposal.getProposalPayload()) &&
                periodService.isTxInPastCycle(proposal.getTxId()));
    }

    @Override
    protected List<ProtectedStoragePayload> getListForRepublishing() {
        return validProposals.stream()
                .map(Proposal::getProposalPayload)
                .filter(this::isMine)
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void publishProposal(Proposal proposal, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        final Transaction proposalTx = proposal.getTx();
        checkNotNull(proposalTx, "proposal.getTx() at publishProposal must not be null");
        walletsManager.publishAndCommitBsqTx(proposalTx, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                onTxBroadcasted(proposalTx, proposal, resultHandler, errorMessageHandler);
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

    private void onTxBroadcasted(Transaction proposalTx, Proposal proposal, ResultHandler resultHandler,
                                 ErrorMessageHandler errorMessageHandler) {
        final String txId = proposalTx.getHashAsString();
        final ProposalPayload proposalPayload = proposal.getProposalPayload();
        proposalPayload.setTxId(txId);
        if (addToP2PNetwork(proposalPayload)) {
            log.info("We added a proposalPayload to the P2P network. ProposalPayload.uid=" + proposalPayload.getUid());
            resultHandler.handleResult();
        } else {
            final String msg = "Adding of proposalPayload to P2P network failed.\n" +
                    "proposalPayload=" + proposalPayload;
            log.error(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
    }

    public boolean removeProposal(Proposal proposal) {
        final ProposalPayload proposalPayload = proposal.getProposalPayload();
        // We allow removal which are not confirmed yet or if it we are in the right phase
        if (isMine(proposal.getProposalPayload())) {
            if (isInPhaseOrUnconfirmed(proposalPayload.getTxId(), PeriodService.Phase.PROPOSAL)) {
                boolean success = p2PService.removeData(proposalPayload, true);
                if (success)
                    removeProposalFromList(proposal);
                else
                    log.warn("Removal of proposal from p2p network failed. proposal={}", proposal);

                return success;
            } else {
                final String msg = "removeProposal called with a Proposal which is outside of the Proposal phase.";
                DevEnv.logErrorAndThrowIfDevMode(msg);
                return false;
            }
        } else {
            final String msg = "removeProposal called for a Proposal which is not ours. That must not happen.";
            DevEnv.logErrorAndThrowIfDevMode(msg);
            return false;
        }
    }

    public void persist() {
        storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addProposal(ProposalPayload proposalPayload, boolean storeLocally) {
        if (!listContains(proposalPayload)) {
            log.info("We received a ProposalPayload from the P2P network. ProposalPayload.uid=" + proposalPayload.getUid());
            observableList.add(createSpecificProposal(proposalPayload));

            if (storeLocally)
                persist();

            onBlockHeightChanged(readableBsqBlockChain.getChainHeadHeight());
        } else {
            if (storeLocally && !isMine(proposalPayload))
                log.debug("We have that proposalPayload already in our list. proposalPayload={}", proposalPayload);
        }
    }


    private Proposal createSpecificProposal(ProposalPayload proposalPayload) {
        switch (proposalPayload.getType()) {
            case COMPENSATION_REQUEST:
                return new CompensationRequest(proposalPayload);
            case GENERIC:
                return new GenericProposal(proposalPayload);
            case CHANGE_PARAM:
                //TODO
                throw new RuntimeException("Not implemented yet");
            case REMOVE_ALTCOIN:
                //TODO
                throw new RuntimeException("Not implemented yet");
            default:
                final String msg = "Undefined ProposalType " + proposalPayload.getType();
                log.error(msg);
                throw new RuntimeException(msg);
        }
    }

    private boolean listContains(ProposalPayload proposalPayload) {
        return findProposal(proposalPayload).isPresent();
    }

    private Optional<Proposal> findProposal(ProposalPayload proposalPayload) {
        return observableList.stream()
                .filter(e -> e.getProposalPayload().equals(proposalPayload))
                .findAny();
    }

    private void removeProposalFromList(Proposal proposal) {
        if (observableList.remove(proposal))
            persist();
        else
            log.warn("We called removeProposalFromList at a proposal which was not in our list");
    }
}
