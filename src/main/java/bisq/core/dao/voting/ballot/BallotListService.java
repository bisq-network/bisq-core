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
import bisq.core.dao.voting.ballot.vote.Vote;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.ProposalValidator;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalAppendOnlyPayload;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Takes the proposal from the append only store and makes a Ballot out of it.
 * Applies the vote when user puts the vote in it and persist the list.
 */
@Slf4j
public class BallotListService implements PersistedDataHost, AppendOnlyDataStoreListener {
    public interface ListChangeListener {
        void onListChanged(List<Ballot> list);
    }

    private final P2PDataStorage p2pDataStorage;
    private ProposalValidator proposalValidator;
    private final Storage<BallotList> storage;
    @Getter
    private final BallotList ballotList = new BallotList();
    private final List<ListChangeListener> listeners = new ArrayList<>();

    @Inject
    public BallotListService(P2PDataStorage p2pDataStorage,
                             ProposalValidator proposalValidator,
                             Storage<BallotList> storage) {
        this.p2pDataStorage = p2pDataStorage;
        this.proposalValidator = proposalValidator;
        this.storage = storage;

        p2pDataStorage.addAppendOnlyDataStoreListener(this);
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
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        onPersistableNetworkPayloadAdded(payload, true);
    }

    private void onPersistableNetworkPayloadAdded(PersistableNetworkPayload payload, boolean storeLocally) {
        if (payload instanceof ProposalAppendOnlyPayload) {
            ProposalAppendOnlyPayload proposalAppendOnlyPayload = (ProposalAppendOnlyPayload) payload;
            final Proposal proposal = proposalAppendOnlyPayload.getProposal();
            if (!BallotUtils.ballotListContainsProposal(proposal, ballotList.getList()) &&
                    proposalValidator.isValidAndConfirmed(proposal)) {
                Ballot ballot = Ballot.createBallotFromProposal(proposal);
                ballotList.add(ballot);
                listeners.forEach(l -> l.onListChanged(ballotList.getList()));
                if (storeLocally)
                    persist();
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        p2pDataStorage.getAppendOnlyDataStoreMap().values()
                .forEach(payload -> onPersistableNetworkPayloadAdded(payload, false));
        persist();
    }

    public void setVote(Ballot ballot, @Nullable Vote vote) {
        ballot.setVote(vote);
        persist();
    }

    public void addListener(ListChangeListener listener) {
        listeners.add(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void persist() {
        storage.queueUpForSave();
    }
}
