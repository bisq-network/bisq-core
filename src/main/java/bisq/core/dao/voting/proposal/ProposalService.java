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

import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalPayload;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalStorageService;
import bisq.core.dao.voting.proposal.storage.temp.TempProposalPayload;
import bisq.core.dao.voting.proposal.storage.temp.TempProposalStorageService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;

import com.google.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains protectedStoreList and appendOnlyStoreList.
 * Republishes protectedStoreList to append-only data store when entering the break before the blind vote phase.
 */
@Slf4j
public class ProposalService implements HashMapChangedListener, AppendOnlyDataStoreListener, BsqStateListener {

    private final P2PService p2PService;
    private final PeriodService periodService;
    private final BsqStateService bsqStateService;
    private final ProposalValidator proposalValidator;

    // Proposals we receive in the proposal phase. They can be removed in that phase. That list must not be used for
    // consensus critical code.
    @Getter
    private final ObservableList<Proposal> protectedStoreList = FXCollections.observableArrayList();

    // Proposals which got added to the append-only data store in the break before the blind vote phase.
    // They cannot be removed anymore. This list is used for consensus critical code. Different nodes might have
    // different data collections due the eventually consistency of the P2P network.
    @Getter
    private final ObservableList<ProposalPayload> appendOnlyStoreList = FXCollections.observableArrayList();

    private boolean parsingComplete;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalService(P2PService p2PService,
                           PeriodService periodService,
                           ProposalStorageService proposalStorageService,
                           TempProposalStorageService tempProposalStorageService,
                           AppendOnlyDataStoreService appendOnlyDataStoreService,
                           ProtectedDataStoreService protectedDataStoreService,
                           BsqStateService bsqStateService,
                           ProposalValidator proposalValidator) {
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.bsqStateService = bsqStateService;
        this.proposalValidator = proposalValidator;

        appendOnlyDataStoreService.addService(proposalStorageService);
        protectedDataStoreService.addService(tempProposalStorageService);

        bsqStateService.addBsqStateListener(this);

        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        fillListFromAppendOnlyDataStore();
        fillListFromProtectedStore();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        int heightForRepublishing = periodService.getFirstBlockOfPhase(bsqStateService.getChainHeight(), DaoPhase.Phase.BREAK1);
        if (block.getHeight() == heightForRepublishing) {
            // We only republish if we are not still parsing old blocks
            // TODO rethink that...
            if (parsingComplete)
                publishToAppendOnlyDataStore();

            fillListFromAppendOnlyDataStore();
        }
    }

    @Override
    public void onParseBlockChainComplete() {
        parsingComplete = true;

        // Fill the lists with the data we have collected in out stores.
        fillListFromProtectedStore();
        fillListFromAppendOnlyDataStore();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry entry) {
        onProtectedDataAdded(entry);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry entry) {
        onProtectedDataRemoved(entry);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        onAppendOnlyDataAdded(payload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillListFromProtectedStore() {
        p2PService.getDataMap().values().forEach(this::onProtectedDataAdded);
    }

    private void fillListFromAppendOnlyDataStore() {
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(this::onAppendOnlyDataAdded);
    }


    private void publishToAppendOnlyDataStore() {
        protectedStoreList.stream()
                .filter(proposalValidator::isValidAndConfirmed)
                .map(ProposalPayload::new)
                .forEach(proposalPayload -> {
                    boolean success = p2PService.addPersistableNetworkPayload(proposalPayload, true);
                    if (success)
                        log.info("We published a ProposalPayload to the P2P network as append-only data. proposalUid={}",
                                proposalPayload.getProposal().getUid());
                    else
                        log.warn("publishToAppendOnlyDataStore failed for proposal " + proposalPayload.getProposal());
                });
    }

    private void onProtectedDataAdded(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof TempProposalPayload) {
            final Proposal proposal = ((TempProposalPayload) protectedStoragePayload).getProposal();
            // We do not validate phase, cycle and confirmation yet as the tx might be not available/confirmed yet.
            // Though we check if we are in the proposal phase. During parsing we might miss proposals but we will
            // handle that in the node to request again the p2p network data so we get added potentially missed data
            if (!protectedStoreList.contains(proposal)) {
                if (proposalValidator.isValidOrUnconfirmed(proposal)) {
                    protectedStoreList.add(proposal);
                    log.info("We received a TempProposalPayload and store it to our protectedStoreList. proposalUid={}",
                            proposal.getUid());
                } else {
                    //TODO called at startup when we are not in cycle of proposal
                    log.debug("We received a invalid proposal from the P2P network. Proposal.txId={}, blockHeight={}",
                            proposal.getTxId(), bsqStateService.getChainHeight());
                }
            }
        }
    }

    private void onProtectedDataRemoved(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof TempProposalPayload) {
            final Proposal proposal = ((TempProposalPayload) protectedStoragePayload).getProposal();
            // We allow removal only if we are in the proposal phase.
            if (periodService.isInPhase(bsqStateService.getChainHeight(), DaoPhase.Phase.PROPOSAL)) {
                if (protectedStoreList.contains(proposal))
                    protectedStoreList.remove(proposal);
            } else {
                log.warn("We received a remove request outside the PROPOSAL phase. " +
                        "Proposal.txId={}, blockHeight={}", proposal.getTxId(), bsqStateService.getChainHeight());
            }
        }
    }

    private void onAppendOnlyDataAdded(PersistableNetworkPayload persistableNetworkPayload) {
        if (persistableNetworkPayload instanceof ProposalPayload) {
            ProposalPayload proposalPayload = (ProposalPayload) persistableNetworkPayload;
            if (!appendOnlyStoreList.contains(proposalPayload)) {
                Proposal proposal = proposalPayload.getProposal();
                if (proposalValidator.isValidAndConfirmed(proposal)) {
                    appendOnlyStoreList.add(proposalPayload);
                    log.info("We received a ProposalPayload and store it to our appendOnlyStoreList. proposalUid={}",
                            proposal.getUid());
                } else {
                    log.debug("We received a invalid append-only proposal from the P2P network. " +
                                    "Proposal.txId={}, blockHeight={}",
                            proposal.getTxId(), bsqStateService.getChainHeight());
                }
            }
        }
    }
}
