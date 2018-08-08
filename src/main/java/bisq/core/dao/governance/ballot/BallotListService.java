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

package bisq.core.dao.governance.ballot;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.governance.ballot.vote.Vote;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.proposal.storage.appendonly.ProposalPayload;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import javafx.collections.ListChangeListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Takes the proposals from the append only store and makes Ballots out of it.
 * Applies voting on individual ballots and persist the list.
 * The BallotList contains all ballots of all cycles.
 */
@Slf4j
public class BallotListService implements PersistedDataHost {

    public interface BallotListChangeListener {
        void onListChanged(List<Ballot> list);
    }

    private final Storage<BallotList> storage;
    private final BallotList ballotList = new BallotList();
    private final List<BallotListChangeListener> listeners = new CopyOnWriteArrayList<>();

    @Inject
    public BallotListService(ProposalService proposalService,
                             Storage<BallotList> storage) {
        this.storage = storage;

        proposalService.getProposalPayloads().addListener((ListChangeListener<ProposalPayload>) c -> {
            c.next();
            if (c.wasAdded()) {
                List<? extends ProposalPayload> proposalPayloads = c.getAddedSubList();
                proposalPayloads.stream()
                        .map(ProposalPayload::getProposal)
                        .filter(proposal -> !BallotUtils.listContainsProposal(proposal, ballotList.getList()))
                        .forEach(proposal -> {
                            Ballot ballot = new Ballot(proposal);
                            if (ballotList.stream().noneMatch(e -> e.equals(ballot))) {
                                log.info("We add a proposal to a new ballot. Vote is null at that moment.proposalTxId={}",
                                        proposal.getTxId());
                                ballotList.add(ballot);
                                listeners.forEach(l -> l.onListChanged(ballotList.getList()));
                                persist();
                            }
                        });
            }
        });
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
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
    }

    public void setVote(Ballot ballot, @Nullable Vote vote) {
        ballot.setVote(vote);
        persist();
    }

    public void addListener(BallotListChangeListener listener) {
        listeners.add(listener);
    }

    public BallotList getBallotList() {
        return ballotList;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void persist() {
        storage.queueUpForSave();
    }
}
