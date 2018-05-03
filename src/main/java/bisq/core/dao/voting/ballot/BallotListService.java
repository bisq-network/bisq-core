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
import bisq.core.dao.state.ParseBlockChainListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.voting.ballot.vote.Vote;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalAppendOnlyPayload;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Takes the proposals from the append only store and makes Ballots out of it.
 * Applies voting on individual ballots and persist the list.
 * The BallotList contains all ballots of all cycles.
 */
@Slf4j
public class BallotListService implements ParseBlockChainListener, PersistedDataHost, AppendOnlyDataStoreListener {
    public interface ListChangeListener {
        void onListChanged(List<Ballot> list);
    }

    private final P2PDataStorage p2pDataStorage;
    private final Storage<BallotList> storage;
    @Getter
    private final BallotList ballotList = new BallotList();
    private final List<ListChangeListener> listeners = new CopyOnWriteArrayList<>();

    @Inject
    public BallotListService(P2PDataStorage p2pDataStorage,
                             StateService stateService,
                             Storage<BallotList> storage) {
        this.p2pDataStorage = p2pDataStorage;
        this.storage = storage;

        stateService.addParseBlockChainListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            BallotList persisted = storage.initAndGetPersisted(ballotList, 100);
            if (persisted != null) {
                ballotList.clear();
                ballotList.addAll(persisted.getList());
                listeners.forEach(l -> l.onListChanged(ballotList.getList()));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ParseBlockChainListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
        p2pDataStorage.addAppendOnlyDataStoreListener(this);
        p2pDataStorage.getAppendOnlyDataStoreMap().values().forEach(this::onPersistableNetworkPayloadAdded);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        onPersistableNetworkPayloadAdded(payload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
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

    private void onPersistableNetworkPayloadAdded(PersistableNetworkPayload payload) {
        if (payload instanceof ProposalAppendOnlyPayload) {
            ProposalAppendOnlyPayload proposalAppendOnlyPayload = (ProposalAppendOnlyPayload) payload;
            final Proposal proposal = proposalAppendOnlyPayload.getProposal();
            if (!BallotUtils.ballotListContainsProposal(proposal, ballotList.getList())) {
                Ballot ballot = new Ballot(proposal);
                ballotList.add(ballot);
                listeners.forEach(l -> l.onListChanged(ballotList.getList()));
                persist();
            }
        }
    }

    private void persist() {
        storage.queueUpForSave();
    }
}
