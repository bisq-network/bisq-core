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
import bisq.core.dao.voting.ballot.proposal.Proposal;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import com.google.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains myBallotList. Triggers republishing of proposals from myBallotList at startup.
 */
@Slf4j
public class MyBallotListService implements PersistedDataHost {
    private final Storage<BallotList> storage;

    @Getter
    private final BallotList myBallotList = new BallotList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyBallotListService(Storage<BallotList> storage) {
        this.storage = storage;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            BallotList persisted = storage.initAndGetPersisted(myBallotList, "MyBallotList", 20);
            if (persisted != null) {
                myBallotList.clear();
                myBallotList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isMyProposal(Proposal proposal) {
        return BallotUtils.ballotListContainsProposal(proposal, myBallotList.getList());
    }

    public void storeBallot(Ballot ballot) {
        if (!BallotUtils.ballotListContainsProposal(ballot.getProposal(), myBallotList.getList())) {
            myBallotList.add(ballot);
            persist();
        }
    }

    public void removeBallot(Ballot ballot) {
        if (myBallotList.remove(ballot))
            persist();
        else
            log.warn("We called removeProposalFromList at a ballot which was not in our list");
    }

    public void persist() {
        storage.queueUpForSave();
    }

    public List<Proposal> getProposals() {
        return myBallotList.stream()
                .map(Ballot::getProposal)
                .collect(Collectors.toList());
    }
}
