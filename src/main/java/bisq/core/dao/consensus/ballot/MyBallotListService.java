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

package bisq.core.dao.consensus.ballot;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.consensus.proposal.Proposal;
import bisq.core.dao.consensus.proposal.ProposalService;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import com.google.inject.Inject;

import javafx.beans.value.ChangeListener;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains persistable BallotList. Triggers republishing of proposals of myBallotList at startup.
 */
@Slf4j
public class MyBallotListService implements PersistedDataHost {
    private final P2PService p2PService;
    private final ProposalService proposalService;
    private final Storage<BallotList> storage;
    private ChangeListener<Number> numConnectedPeersListener;

    @Getter
    private final BallotList myBallotList = new BallotList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyBallotListService(P2PService p2PService,
                               ProposalService proposalService,
                               Storage<BallotList> storage) {
        this.p2PService = p2PService;
        this.proposalService = proposalService;
        this.storage = storage;

        numConnectedPeersListener = (observable, oldValue, newValue) -> maybeRePublish();
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        maybeRePublish();
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

    public boolean isMine(Proposal proposal) {
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeRePublish() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false. Also better for production version
        // to avoid activity peaks at startup
        UserThread.runAfter(() -> {
            if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
                p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
                proposalService.rePublishProposals(getProposals());
            }
        }, 2);
    }

    private List<Proposal> getProposals() {
        return myBallotList.stream()
                .map(Ballot::getProposal)
                .collect(Collectors.toList());
    }
}
