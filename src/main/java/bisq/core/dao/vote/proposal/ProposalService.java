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
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.node.NodeExecutor;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.events.AddProposalPayloadEvent;
import bisq.core.dao.state.events.StateChangeEvent;
import bisq.core.dao.vote.PeriodService;

import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens on the StateService for new blocks and for ProposalPayload from the P2P network.
 * We configure both listeners thread context aware so we get our listeners called in the parser
 * thread created by the single threaded executor created in the NodeExecutor.
 * <p>
 * We put all ProposalPayload data into the Tx to store it with the BsqBlockchain data structure.
 * ProposalPayload data will be taken in later periods from the BsqBlockchain only and it is guaranteed that it is
 * already validated and in the right period and cycle.
 * <p>
 * We maintain as well the ProposalList which gets persisted independently.
 * <p>
 * We could consider to not persist the ProposalPayloads but keep it in memory only with a long TTL (about 30 days).
 * But to persist it gives us more reliability that the data will be available.
 */
@Slf4j
public class ProposalService implements PersistedDataHost {
    private final NodeExecutor nodeExecutor;
    private final P2PDataStorage p2pDataStorage;
    private final PeriodService periodService;
    private final PublicKey signaturePubKey;
    private final StateService stateService;
    private final ProposalPayloadValidator proposalPayloadValidator;
    private final Storage<ProposalList> storage;

    @Getter
    private final List<Proposal> proposals = new ArrayList<>();
    private final ProposalList proposalList = new ProposalList(proposals);

    @Inject
    public ProposalService(NodeExecutor nodeExecutor,
                           P2PDataStorage p2pDataStorage,
                           PeriodService periodService,
                           StateService stateService,
                           ProposalPayloadValidator proposalPayloadValidator,
                           KeyRing keyRing,
                           Storage<ProposalList> storage) {
        this.nodeExecutor = nodeExecutor;
        this.p2pDataStorage = p2pDataStorage;
        this.periodService = periodService;
        this.stateService = stateService;
        this.proposalPayloadValidator = proposalPayloadValidator;
        this.storage = storage;
        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        stateService.addListener(new StateService.Listener() {
            // We set the nodeExecutor as we want to get called in the context of the parser thread
            @Override
            public Executor getExecutor() {
                return nodeExecutor.get();
            }

            @Override
            public void onBlockAdded(BsqBlock bsqBlock) {
                // We iterate all proposals and if all valid we store it at the Tx
                proposalList.forEach(proposal -> maybeAddProposalToTx(proposal.getProposalPayload(), bsqBlock.getHeight()));
            }

            @Override
            public void onStateChange(StateChangeEvent stateChangeEvent) {
                if (stateChangeEvent instanceof AddProposalPayloadEvent) {
                    ProposalService.this.onAddProposalPayloadEvent((AddProposalPayloadEvent) stateChangeEvent);
                }
            }
        });

        p2pDataStorage.addHashMapChangedListener(new HashMapChangedListener() { // User thread context
            // We set the nodeExecutor as we want to get called in the context of the parser thread
            @Override
            public Executor getExecutor() {
                return nodeExecutor.get();
            }

            @Override
            public void onAdded(ProtectedStorageEntry entry) {
                onAddedProtectedStorageEntry(entry, true);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry entry) {
                onRemovedProtectedStorageEntry(entry);
            }
        });
        p2pDataStorage.getMap().values().forEach(entry -> onAddedProtectedStorageEntry(entry, false));
    }

    public void shutDown() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            ProposalList persisted = storage.initAndGetPersisted(proposalList, 20);
            if (persisted != null) {
                this.proposalList.clear();
                this.proposalList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAddedProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            final ProposalPayload proposalPayload = (ProposalPayload) protectedStoragePayload;
            if (!listContains(proposalPayload)) {
                log.info("We received a ProposalPayload from the P2P network. ProposalPayload.uid=" +
                        proposalPayload.getUid());
                Proposal proposal = ProposalFactory.getProposalFromPayload(proposalPayload);
                proposalList.add(proposal);

                maybeAddProposalToTx(proposalPayload, stateService.getChainHeadHeight());

                if (storeLocally)
                    persist();
            } else {
                if (storeLocally && !isMine(proposalPayload))
                    log.debug("We have that proposalPayload already in our list. proposalPayload={}", proposalPayload);
            }
        }
    }

    // We allow removal only if we are in the correct phase and cycle or the tx is unconfirmed
    private void onRemovedProtectedStorageEntry(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            final ProposalPayload proposalPayload = (ProposalPayload) protectedStoragePayload;
            findProposal(proposalPayload)
                    .ifPresent(payload -> {
                        if (isInPhaseOrUnconfirmed(stateService.getTx(payload.getTxId()), payload.getTxId(),
                                PeriodService.Phase.PROPOSAL,
                                stateService.getChainHeadHeight())) {
                            removeProposalFromList(proposalPayload);
                        } else {
                            final String msg = "onRemoved called of a Proposal which is outside of the Request phase " +
                                    "is invalid and we ignore it.";
                            DevEnv.logErrorAndThrowIfDevMode(msg);
                        }
                    });
        }
    }

    // We fire a AddProposalPayloadEvent if the tx is already available and proposal and tx are valid.
    // We only add it after the proposal phase to avoid handling of remove operation (user can remove a proposal
    // during the proposal phase).
    private void maybeAddProposalToTx(ProposalPayload proposalPayload, int chainHeight) {
        getTxForProposal(proposalPayload)
                .filter(tx -> !periodService.isInPhase(chainHeight, PeriodService.Phase.PROPOSAL))
                .ifPresent(tx -> stateService.addStateChangeEvent(new AddProposalPayloadEvent(proposalPayload, chainHeight)));
    }

    private void onAddProposalPayloadEvent(AddProposalPayloadEvent event) {
        ProposalPayload proposalPayload = (ProposalPayload) event.getPayload();
        getTxForProposal(proposalPayload)
                .filter(tx -> !periodService.isInPhase(event.getChainHeight(), PeriodService.Phase.PROPOSAL))
                .ifPresent(tx -> stateService.putProposalPayload(tx.getId(), proposalPayload));
    }


    private Optional<Tx> getTxForProposal(ProposalPayload proposalPayload) {
        return stateService.getTx(proposalPayload.getTxId())
                .filter(tx -> isValid(tx, proposalPayload));
    }

    private boolean listContains(ProposalPayload proposalPayload) {
        return findProposal(proposalPayload).isPresent();
    }

    private Optional<Proposal> findProposal(ProposalPayload proposalPayload) {
        return proposalList.stream()
                .filter(proposal -> proposal.getProposalPayload().equals(proposalPayload))
                .findAny();
    }

    private void removeProposalFromList(ProposalPayload proposalPayload) {
        if (proposalList.remove(proposalPayload))
            persist();
        else
            log.warn("We called removeProposalFromList at a proposalPayload which was not in our list");
    }

    // We use the StateService not the TransactionConfidence from the wallet to not mix 2 different and possibly
    // out of sync data sources.
    public boolean isUnconfirmed(String txId) {
        return !stateService.getTx(txId).isPresent();
    }

    public boolean isMine(ProtectedStoragePayload protectedStoragePayload) {
        return signaturePubKey.equals(protectedStoragePayload.getOwnerPubKey());
    }

    public boolean isInPhaseOrUnconfirmed(Optional<Tx> optionalProposalTx, String txId, PeriodService.Phase phase,
                                          int blockHeight) {
        return isUnconfirmed(txId) ||
                optionalProposalTx.filter(tx -> periodService.isTxInPhase(txId, phase))
                        .filter(tx -> periodService.isTxInCorrectCycle(tx.getBlockHeight(), blockHeight))
                        .isPresent();
    }

    public boolean isValid(Tx tx, ProposalPayload proposalPayload) {
        try {
            proposalPayloadValidator.validate(proposalPayload, tx);
            return true;
        } catch (ValidationException e) {
            log.warn("ProposalPayload validation failed. txId={}, proposalPayload={}, validationException={}",
                    tx.getId(), proposalPayload, e.toString());
            return false;
        }
    }

    public void persist() {
        storage.queueUpForSave();
    }
}
