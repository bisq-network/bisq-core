/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.consensus.myvote;

import bisq.core.dao.consensus.ballot.Ballot;
import bisq.core.dao.consensus.ballot.BallotList;
import bisq.core.dao.consensus.blindvote.BlindVoteConsensus;
import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.PeriodStateChangeListener;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.dao.consensus.proposal.param.ChangeParamService;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.presentation.proposal.BallotListService;

import bisq.common.ThreadAwareListener;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and published blind vote and blind vote tx. After broadcast it creates myVote which gets persisted and holds
 * all proposals with the votes.
 * Republished all my active myVotes at startup and applies the revealTxId to myVote once the reveal tx is published.
 * <p>
 * Executed from the user tread.
 */
@Slf4j
public class MyBlindVoteService {

    public interface Listener extends ThreadAwareListener {
        void onSortedBallotList(BallotList sortedBallotList);

        void onBlindVoteFee(Coin blindVoteFee);
    }

    private final PeriodService periodService;
    private final BallotListService ballotListService;
    private final StateService stateService;
    private final ChangeParamService changeParamService;

    @Getter
    private final BallotList sortedBallotList = new BallotList();
    @Getter
    private Coin blindVoteFee;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyBlindVoteService(PeriodService periodService,
                              BallotListService ballotListService,
                              StateService stateService,
                              ChangeParamService changeParamService) {
        this.periodService = periodService;
        this.ballotListService = ballotListService;
        this.stateService = stateService;
        this.changeParamService = changeParamService;

        periodService.addPeriodStateChangeListener(new PeriodStateChangeListener() {
            @Override
            public boolean executeOnUserThread() {
                return false;
            }

            @Override
            public void onPreParserChainHeightChanged(int chainHeight) {
                makeSortedBallotListSnapshot();
                makeBlindVoteFeeSnapshot();
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        makeSortedBallotListSnapshot();
        makeBlindVoteFeeSnapshot();
    }

    //TODO
    public void applyRevealTxId(MyVote myVote, String voteRevealTxId) {
        myVote.setRevealTxId(voteRevealTxId);
        log.info("Applied revealTxId to myVote.\nmyVote={}\nvoteRevealTxId={}", myVote, voteRevealTxId);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void makeBlindVoteFeeSnapshot() {
        int chainHeight = periodService.getChainHeight();
        if (periodService.getFirstBlockOfPhase(chainHeight, Phase.PROPOSAL) == chainHeight) {
            blindVoteFee = BlindVoteConsensus.getFee(changeParamService, stateService.getChainHeight());

            // map to user thread
            listeners.forEach(l -> l.execute(() -> l.onBlindVoteFee(blindVoteFee)));
        }
    }

    private void makeSortedBallotListSnapshot() {
        // If we enter the blind vote phase before actual parsing we copy the ballots for that vote phase
        // from the user thread domain to a local copy.
        int chainHeight = periodService.getChainHeight();
        if (periodService.getFirstBlockOfPhase(chainHeight, Phase.BLIND_VOTE) == chainHeight) {
            sortedBallotList.clear();
            final ImmutableList<Ballot> immutableList = ballotListService.getImmutableBallotList();
            final List<Ballot> ballots = immutableList.stream()
                    .filter(ballot -> stateService.getTx(ballot.getTxId()).isPresent())
                    .filter(ballot -> isTxInPhaseAndCycle(stateService.getTx(ballot.getTxId()).get()))
                    .collect(Collectors.toList());
            BlindVoteConsensus.sortProposalList(ballots);
            sortedBallotList.addAll(ballots);

            // map to user thread
            listeners.forEach(l -> l.execute(() -> l.onSortedBallotList(sortedBallotList)));
        }
    }

    public boolean isTxInPhaseAndCycle(Tx tx) {
        return periodService.isInPhase(tx.getBlockHeight(), Phase.PROPOSAL) &&
                periodService.isTxInCorrectCycle(tx.getBlockHeight(), periodService.getChainHeight());
    }

}
