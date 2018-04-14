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
import bisq.core.dao.node.NodeExecutor;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens on the StateService for new txBlocks and for ProposalPayload from the P2P network.
 * We configure the P2P network listener thread context aware so we get our listeners called in the parser
 * thread created by the single threaded executor created in the NodeExecutor.
 * <p>
 * When the last block of the break after the proposal phase is parsed we will add all proposalPayloads we have received
 * from the P2P network to the stateChangeEvents and pass that back to the stateService where they get accumulated and be
 * included in that block's stateChangeEvents.
 * <p>
 * We maintain as well the openProposalList which gets persisted independently at the moment when the proposal arrives
 * and remove the proposal at the moment we put it to the stateChangeEvent.
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
    private final ProposalList openProposalList = new ProposalList();

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
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get called from the user thread at startup. It is not really required to map to parser thread here, but we want
    // to be consistent for all methods of that class to be executed in the parser thread.
    @Override
    public void readPersisted() {
        nodeExecutor.get().execute(() -> {
            if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
                ProposalList persisted = storage.initAndGetPersisted(openProposalList, 20);
                if (persisted != null) {
                    this.openProposalList.clear();
                    this.openProposalList.addAll(persisted.getList());
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get called from DaoSetup in the parser thread
    public void onAllServicesInitialized() {
        // We get called from stateService in the parser thread
        stateService.registerStateChangeEventsProvider(txBlock -> {
            Set<StateChangeEvent> stateChangeEvents = new HashSet<>();
            Set<ProposalPayload> toRemove = new HashSet<>();
            openProposalList.stream()
                    .map(Proposal::getProposalPayload)
                    .map(proposalPayload -> {
                        final Optional<StateChangeEvent> optional = getAddProposalPayloadEvent(proposalPayload, txBlock.getHeight());

                        // If we are in the correct block and we add a AddProposalPayloadEvent to the state we remove
                        // the proposalPayload from our list after we have completed iteration.
                        //TODO activate once we persist state
                       /* if (optional.isPresent())
                            toRemove.add(proposalPayload);*/

                        return optional;
                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(stateChangeEvents::add);

            // We remove those proposals we have just added to the state.
            toRemove.forEach(this::removeProposalFromList);

            return stateChangeEvents;
        });

        // We implement the getExecutor method in the HashMapChangedListener as we want to get called from
        // the parser thread.
        p2pDataStorage.addHashMapChangedListener(new HashMapChangedListener() {
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

        // We apply already existing protectedStorageEntries
        p2pDataStorage.getMap().values()
                .forEach(entry -> onAddedProtectedStorageEntry(entry, false));
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

    // We use the StateService not the TransactionConfidence from the wallet to not mix 2 different and possibly
    // out of sync data sources.
    public boolean isUnconfirmed(String txId) {
        return !stateService.getTx(txId).isPresent();
    }

    public void persist() {
        storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAddedProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            final ProposalPayload proposalPayload = (ProposalPayload) protectedStoragePayload;
            if (!listContains(proposalPayload)) {
                // For adding a proposal we need to be before the last block in BREAK1 as in the last block at BREAK1
                // we write our proposals to the state.
                if (isInToleratedBlockRange(stateService.getChainHeight())) {
                    log.info("We received a ProposalPayload from the P2P network. ProposalPayload.uid=" +
                            proposalPayload.getUid());
                    Proposal proposal = ProposalFactory.getProposalFromPayload(proposalPayload);
                    openProposalList.add(proposal);

                    if (storeLocally)
                        persist();
                } else {
                    log.warn("We are not in the tolerated phase anymore and ignore that " +
                                    "proposalPayload. proposalPayload={}, height={}", proposalPayload,
                            stateService.getChainHeight());
                }
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
                                stateService.getChainHeight())) {
                            removeProposalFromList(proposalPayload);
                        } else {
                            final String msg = "onRemoved called of a Proposal which is outside of the Request phase " +
                                    "is invalid and we ignore it.";
                            DevEnv.logErrorAndThrowIfDevMode(msg);
                        }
                    });
        }
    }

    // We add a AddProposalPayloadEvent if the tx is already available and proposal and tx are valid.
    // We only add it after the proposal phase to avoid handling of remove operation (user can remove a proposal
    // during the proposal phase).
    // We use the last block in the BREAK1 phase to set all proposals for that cycle.
    // If a proposal would arrive later it will be ignored.
    private Optional<StateChangeEvent> getAddProposalPayloadEvent(ProposalPayload proposalPayload, int height) {
        return stateService.getTx(proposalPayload.getTxId())
                .filter(tx -> isLastToleratedBlock(height))
                .filter(tx -> periodService.isTxInCorrectCycle(tx.getBlockHeight(), height))
                .filter(tx -> periodService.isInPhase(tx.getBlockHeight(), PeriodService.Phase.PROPOSAL))
                .filter(tx -> proposalPayloadValidator.isValid(proposalPayload))
                .map(tx -> new AddProposalPayloadEvent(proposalPayload, height));
    }

    private boolean isLastToleratedBlock(int height) {
        return height == periodService.getAbsoluteEndBlockOfPhase(height, PeriodService.Phase.BREAK1);
    }

    private boolean isInToleratedBlockRange(int height) {
        return height < periodService.getAbsoluteEndBlockOfPhase(height, PeriodService.Phase.BREAK1);
    }

    private void removeProposalFromList(ProposalPayload proposalPayload) {
        if (openProposalList.remove(proposalPayload))
            persist();
        else
            log.warn("We called removeProposalFromList at a proposalPayload which was not in our list");
    }

    private boolean listContains(ProposalPayload proposalPayload) {
        return findProposal(proposalPayload).isPresent();
    }

    private Optional<Proposal> findProposal(ProposalPayload proposalPayload) {
        return openProposalList.stream()
                .filter(proposal -> proposal.getProposalPayload().equals(proposalPayload))
                .findAny();
    }
}
