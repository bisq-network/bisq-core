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

package bisq.core.dao.voting.ballot;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.period.PeriodService;
import bisq.core.dao.state.StateService;
import bisq.core.dao.voting.ballot.proposal.Proposal;
import bisq.core.dao.voting.ballot.proposal.ProposalPayload;
import bisq.core.dao.voting.ballot.proposal.ProposalValidator;

import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.DevEnv;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens on the P2P network for new proposals and add valid proposals as new ballots to the list.
 */
@Slf4j
public class BallotListService implements PersistedDataHost {
    private final P2PDataStorage p2pDataStorage;
    private final PeriodService periodService;
    private final StateService stateService;
    private final ProposalValidator proposalValidator;
    private final Storage<BallotList> storage;
    @Getter
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

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            BallotList persisted = storage.initAndGetPersisted(ballotList, 20);
            if (persisted != null) {
                ballotList.clear();
                ballotList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        // We apply already existing protectedStorageEntries
        p2pDataStorage.getMap().values().forEach(entry -> onAddedProtectedStorageEntry(entry, false));
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
            final Proposal proposal = ((ProposalPayload) protectedStoragePayload).getProposal();
            if (!BallotUtils.ballotListContainsProposal(proposal, ballotList.getList()) &&
                    proposalValidator.isValid(proposal)) {
                log.info("We received a new proposal from the P2P network. Proposal.uid={}", proposal.getUid());
                Ballot ballot = Ballot.createBallotFromProposal(proposal);
                ballotList.add(ballot);
                if (storeLocally) persist();
            }
        }
    }

    // We allow removal only if we are in the correct phase and cycle or the tx is unconfirmed
    private void onRemovedProtectedStorageEntry(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof ProposalPayload) {
            final Proposal proposal = ((ProposalPayload) protectedStoragePayload).getProposal();
            BallotUtils.findProposalInBallotList(proposal, ballotList.getList())
                    .filter(ballot -> {
                        if (BallotUtils.canRemoveProposal(proposal, stateService, periodService)) {
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

    private void removeProposalFromList(Proposal proposal) {
        Optional<Ballot> optionalBallot = BallotUtils.findProposalInBallotList(proposal, ballotList.getList());
        if (optionalBallot.isPresent()) {
            if (ballotList.remove(optionalBallot.get())) {
                persist();
            } else {
                log.warn("Removal of ballot failed");
            }
        } else {
            log.warn("We called removeProposalFromList at a ballot which was not in our list");
        }
    }
}
