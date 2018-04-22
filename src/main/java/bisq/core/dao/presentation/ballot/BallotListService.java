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

package bisq.core.dao.presentation.ballot;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.consensus.ballot.Ballot;
import bisq.core.dao.consensus.ballot.BallotFactory;
import bisq.core.dao.consensus.ballot.BallotList;
import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.dao.consensus.proposal.Proposal;
import bisq.core.dao.consensus.proposal.ProposalPayload;
import bisq.core.dao.consensus.proposal.ProposalValidator;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.presentation.PresentationService;

import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.DevEnv;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Listens on the P2P network for new proposals and add valid proposals as new ballots to the list.
 *
 * Designed for user thread.
 * BallotList is accessed from parser thread so we add synchronized to any method using it.
 */
@Slf4j
public class BallotListService implements PersistedDataHost, PresentationService /*, StateChangeEventsProvider*/ {
    private final P2PDataStorage p2pDataStorage;
    private final PeriodService periodService;
    private final StateService stateService;
    private final ProposalValidator proposalValidator;
    private final Storage<BallotList> storage;
    private final BallotList ballotList = new BallotList();

    @Inject
    public BallotListService(P2PDataStorage p2pDataStorage,
                             PeriodService periodService,
                             StateService stateService,
                             ProposalValidator proposalValidator,
                             Storage<BallotList> storage) {
        this.p2pDataStorage = p2pDataStorage;
        this.periodService = periodService;
        this.stateService = stateService;
        this.proposalValidator = proposalValidator;
        this.storage = storage;

        // TODO remove
        //stateService.registerStateChangeEventsProvider(this);

        p2pDataStorage.addHashMapChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry entry) {
                onAddedProtectedStorageEntry(entry, true);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry entry) {
                onRemovedProtectedStorageEntry(entry);
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get called from the user thread at startup.
    @Override
    public synchronized void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            BallotList persisted = storage.initAndGetPersisted(getBallotList(), 20);
            if (persisted != null) {
                getBallotList().clear();
                getBallotList().addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        // We apply already existing protectedStorageEntries
        p2pDataStorage.getMap().values()
                .forEach(entry -> onAddedProtectedStorageEntry(entry, false));
    }


    // We use the StateService not the TransactionConfidence from the wallet to not mix 2 different and possibly
    // out of sync data sources.
    public boolean isUnconfirmed(String txId) {
        return !stateService.getTx(txId).isPresent();
    }

    public boolean isTxInPhaseAndCycle(Tx tx) {
        return periodService.isInPhase(tx.getBlockHeight(), Phase.PROPOSAL) &&
                periodService.isTxInCorrectCycle(tx.getBlockHeight(), periodService.getChainHeight());
    }

    public void persist() {
        storage.queueUpForSave();
    }

    public synchronized BallotList getBallotList() {
        return ballotList;
    }

    // Parser thread is accessing the ballotList.
    public synchronized ImmutableList<Ballot> getImmutableBallotList() {
        return ImmutableList.copyOf(getBallotList().getList());
    }


/*
    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateChangeEventsProvider
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Set<StateChangeEvent> provideStateChangeEvents(TxBlock txBlock) {

        // TODO remove
        Set<StateChangeEvent> stateChangeEvents = new HashSet<>();
        Set<Proposal> toRemove = new HashSet<>();
        ballotList.stream()
                .map(Ballot::getProposal)
                .map(proposalPayload -> {
                    final Optional<StateChangeEvent> optional = getAddProposalPayloadEvent(proposalPayload, txBlock.getHeight());

                    // If we are in the correct block and we add a ProposalEvent to the state we remove
                    // the proposal from our list after we have completed iteration.
                    //TODO activate once we persist state
                       */
/* if (optional.isPresent())
                            toRemove.add(proposal);*//*


                    return optional;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(stateChangeEvents::add);

        // We remove those proposals we have just added to the state.
        toRemove.forEach(this::removeProposalFromList);

        return stateChangeEvents;
    }
*/


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private synchronized void onAddedProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            final Proposal proposal = ((ProposalPayload) protectedStoragePayload).getProposal();
            if (isProposalValidToAddToList(proposal)) {
                log.info("We received a Proposal from the P2P network. Proposal.uid={}", proposal.getUid());
                Ballot ballot = BallotFactory.getBallotFromProposal(proposal);
                getBallotList().add(ballot);
                if (storeLocally) persist();
            }
        }
    }

    // We allow removal only if we are in the correct phase and cycle or the tx is unconfirmed
    private synchronized void onRemovedProtectedStorageEntry(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof Proposal) {
            final Proposal proposal = (Proposal) protectedStoragePayload;
            findProposalInBallotList(proposal, getBallotList().getList())
                    .filter(ballot -> {
                        if (canRemoveProposal(proposal)) {
                            return true;
                        } else {
                            final String msg = "onRemoved called of a Ballot which is outside of the proposal phase " +
                                    "is invalid and we ignore it.";
                            DevEnv.logErrorAndThrowIfDevMode(msg);
                            return false;
                        }
                    })
                    .ifPresent(ballot -> removeProposalFromList(ballot.getProposal()));
        }
    }

    // If unconfirmed or in correct phase/cycle we remove it
    boolean canRemoveProposal(Proposal proposal) {
        final Optional<Tx> optionalProposalTx = stateService.getTx(proposal.getTxId());
        return !optionalProposalTx.isPresent() || isTxInPhaseAndCycle(optionalProposalTx.get());
    }
/*

    // We add a ProposalEvent if the tx is already available and proposal and tx are valid.
    // We only add it after the proposal phase to avoid handling of remove operation (user can remove a proposal
    // during the proposal phase).
    // We use the last block in the BREAK1 phase to set all proposals for that cycle.
    // If a proposal would arrive later it will be ignored.
    private Optional<StateChangeEvent> getAddProposalPayloadEvent(Proposal proposal, int height) {
        return stateService.getTx(proposal.getTxId())
                .filter(tx -> isLastToleratedBlock(height))
                .filter(tx -> periodService.isTxInCorrectCycle(tx.getBlockHeight(), height))
                .filter(tx -> periodService.isInPhase(tx.getBlockHeight(), Phase.PROPOSAL))
                .filter(tx -> proposalValidator.isValid(proposal))
                .map(tx -> new ProposalEvent(proposal, height));
    }
*/

    private boolean isLastToleratedBlock(int height) {
        return height == periodService.getLastBlockOfPhase(height, Phase.BREAK1);
    }


    private synchronized void removeProposalFromList(Proposal proposal) {
        if (getBallotList().remove(proposal))
            persist();
        else
            log.warn("We called removeProposalFromList at a proposal which was not in our list");
    }

    boolean ballotListContainsProposal(Proposal proposal, List<Ballot> ballotList) {
        return findProposalInBallotList(proposal, ballotList).isPresent();
    }

    Optional<Ballot> findProposalInBallotList(Proposal proposal, List<Ballot> ballotList) {
        return ballotList.stream()
                .filter(ballot -> ballot.getProposal().equals(proposal))
                .findAny();
    }

    private synchronized boolean isProposalValidToAddToList(Proposal proposal) {
        if (ballotListContainsProposal(proposal, getBallotList().getList())) {
            log.warn("We have that proposalPayload already in our list. proposal={}", proposal);
            return false;
        }

        if (!proposalValidator.isValid(proposal)) {
            log.warn("proposal is invalid. proposal={}", proposal);
            return false;
        }

        final String txId = proposal.getTxId();
        Optional<Tx> optionalTx = stateService.getTx(txId);
        int chainHeight = stateService.getChainHeight();
        final boolean isTxConfirmed = optionalTx.isPresent();
        if (isTxConfirmed) {
            final int txHeight = optionalTx.get().getBlockHeight();
            if (!periodService.isTxInCorrectCycle(txHeight, chainHeight)) {
                log.warn("Tx is not in current cycle. proposal={}", proposal);
                return false;
            }
            if (!periodService.isInPhase(txHeight, Phase.PROPOSAL)) {
                log.warn("Tx is not in PROPOSAL phase. proposal={}", proposal);
                return false;
            }
        } else {
            if (!periodService.isInPhase(chainHeight, Phase.PROPOSAL)) {
                log.warn("We received an unconfirmed tx and are not in PROPOSAL phase anymore. proposal={}", proposal);
                return false;
            }
        }
        return true;
    }
}
