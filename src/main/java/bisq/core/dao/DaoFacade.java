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

package bisq.core.dao;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.period.PeriodService;
import bisq.core.dao.period.Phase;
import bisq.core.dao.state.BlockListener;
import bisq.core.dao.state.ChainHeightListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.BallotListService;
import bisq.core.dao.voting.ballot.BallotWithTransaction;
import bisq.core.dao.voting.ballot.FilteredBallotListService;
import bisq.core.dao.voting.ballot.MyBallotListService;
import bisq.core.dao.voting.ballot.compensation.CompensationBallotService;
import bisq.core.dao.voting.blindvote.BlindVoteService;
import bisq.core.dao.voting.myvote.MyVote;
import bisq.core.dao.voting.myvote.MyVoteListService;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.ProposalConsensus;
import bisq.core.dao.voting.proposal.ProposalService;
import bisq.core.dao.voting.proposal.param.ChangeParamListService;
import bisq.core.dao.voting.vote.Vote;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ExceptionHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import javafx.collections.ObservableList;

import java.io.IOException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Provides a facade to interact with the Dao domain. Hides complexity and domain details to clients (e.g. UI or APIs)
 * by providing a reduced API and/or aggregating subroutines.
 */
public class DaoFacade {
    private final BallotListService ballotListService;
    private final FilteredBallotListService filteredBallotListService;
    private final MyBallotListService myBallotListService;
    private final ProposalService proposalService;
    private final StateService stateService;
    private final PeriodService periodService;
    private final BlindVoteService blindVoteService;
    private final MyVoteListService myVoteListService;
    private final ChangeParamListService changeParamListService;
    private final CompensationBallotService compensationBallotService;

    private final ObjectProperty<Phase> phaseProperty = new SimpleObjectProperty<>(Phase.UNDEFINED);

    @Inject
    public DaoFacade(BallotListService ballotListService,
                     FilteredBallotListService filteredBallotListService,
                     MyBallotListService myBallotListService,
                     ProposalService proposalService,
                     StateService stateService,
                     PeriodService periodService,
                     BlindVoteService blindVoteService,
                     MyVoteListService myVoteListService,
                     ChangeParamListService changeParamListService,
                     CompensationBallotService compensationBallotService) {
        this.ballotListService = ballotListService;
        this.filteredBallotListService = filteredBallotListService;
        this.myBallotListService = myBallotListService;
        this.proposalService = proposalService;
        this.stateService = stateService;
        this.periodService = periodService;
        this.blindVoteService = blindVoteService;
        this.myVoteListService = myVoteListService;
        this.changeParamListService = changeParamListService;
        this.compensationBallotService = compensationBallotService;

        periodService.addPeriodStateChangeListener(chainHeight -> {
            if (chainHeight > 0 && periodService.getCurrentCycle() != null)
                periodService.getCurrentCycle().getPhaseForHeight(chainHeight).ifPresent(phaseProperty::set);
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Present proposals/ballots
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Present proposals/ballots
    public ObservableList<Ballot> getActiveOrMyUnconfirmedBallots() {
        return filteredBallotListService.getActiveOrMyUnconfirmedBallots();
    }

    public ObservableList<Ballot> getClosedBallots() {
        return filteredBallotListService.getClosedBallots();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Create proposal/ballot
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Creation of Ballot and proposalTransaction
    public BallotWithTransaction getCompensationBallotWithTransaction(String name,
                                                                      String title,
                                                                      String description,
                                                                      String link,
                                                                      Coin requestedBsq,
                                                                      String bsqAddress)
            throws ValidationException, InsufficientMoneyException, IOException, TransactionVerificationException,
            WalletException {
        return compensationBallotService.createBallotWithTransaction(name,
                title,
                description,
                link,
                requestedBsq,
                bsqAddress);
    }

    // Present fee
    public Coin getProposalFee() {
        return ProposalConsensus.getFee(changeParamListService, stateService.getChainHeight());
    }

    // Publish proposal, store ballot
    public void publishBallot(Ballot ballot, Transaction transaction, ResultHandler resultHandler,
                              ErrorMessageHandler errorMessageHandler) {
        proposalService.publishBallot(ballot, transaction, resultHandler, errorMessageHandler);
    }

    // Allow remove if it is my proposal
    public boolean isMyProposal(Proposal proposal) {
        return myBallotListService.isMyProposal(proposal);
    }

    // Remove my ballot
    public boolean removeBallot(Ballot ballot) {
        myBallotListService.removeBallot(ballot);
        return proposalService.removeMyProposal(ballot);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Vote on proposal/ballot
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Vote on ballot
    public void setVote(Ballot ballot, @Nullable Vote vote) {
        ballot.setVote(vote);
        ballotListService.persist();
    }

    // Present myVotes
    public List<MyVote> getMyVoteList() {
        return myVoteListService.getMyVoteList().getList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Create blindVote
    ///////////////////////////////////////////////////////////////////////////////////////////

    // When creating blind vote we present fee
    public Coin getBlindVoteFeeForCycle() {
        return blindVoteService.getBlindVoteFee();
    }

    // Used for mining fee estimation
    public Transaction getDummyBlindVoteTx(Coin stake, Coin blindVoteFee) throws WalletException, InsufficientMoneyException, TransactionVerificationException {
        return blindVoteService.getDummyBlindVoteTx(stake, blindVoteFee);
    }

    // Publish blindVote tx and broadcast blindVote to p2p network and store to blindVoteList.
    public void publishBlindVote(Coin stake, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        blindVoteService.publishBlindVote(stake, resultHandler, exceptionHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Presentation of phases
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addPeriodStateChangeListener(ChainHeightListener listener) {
        periodService.addPeriodStateChangeListener(listener);
    }

    public void removePeriodStateChangeListener(ChainHeightListener listener) {
        periodService.removePeriodStateChangeListener(listener);
    }

    public int getFirstBlockOfPhase(int height, Phase phase) {
        return periodService.getFirstBlockOfPhase(height, phase);
    }

    public int getLastBlockOfPhase(int height, Phase phase) {
        return periodService.getLastBlockOfPhase(height, phase);
    }

    public int getDurationForPhase(Phase phase) {
        return periodService.getDurationForPhase(phase, stateService.getChainHeight());
    }

    // listeners for phase change
    public ReadOnlyObjectProperty<Phase> phaseProperty() {
        return phaseProperty;
    }

    public int getChainHeight() {
        return stateService.getChainHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Present transaction related state
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addBlockListener(BlockListener listener) {
        stateService.addBlockListener(listener);
    }

    public void removeBlockListener(BlockListener listener) {
        stateService.removeBlockListener(listener);
    }

    public Optional<Tx> getTx(String txId) {
        return stateService.getTx(txId);
    }

    public Set<TxOutput> getUnspentBlindVoteStakeTxOutputs() {
        return stateService.getUnspentBlindVoteStakeTxOutputs();
    }

    public int getGenesisBlockHeight() {
        return stateService.getGenesisBlockHeight();
    }

    public String getGenesisTxId() {
        return stateService.getGenesisTxId();
    }

    public Coin getGenesisTotalSupply() {
        return stateService.getGenesisTotalSupply();
    }

    public Set<Tx> getFeeTxs() {
        return stateService.getFeeTxs();
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return stateService.getUnspentTxOutputs();
    }

    public Set<Tx> getTxs() {
        return stateService.getTxs();
    }

    public long getTotalBurntFee() {
        return stateService.getTotalBurntFee();
    }

    public long getTotalIssuedAmountFromCompRequests() {
        return stateService.getTotalIssuedAmountFromCompRequests();
    }

    public long getBlockTime(int issuanceBlockHeight) {
        return stateService.getBlockTime(issuanceBlockHeight);
    }

    public int getIssuanceBlockHeight(String txId) {
        return stateService.getIssuanceBlockHeight(txId);
    }

    public boolean isIssuanceTx(String txId) {
        return stateService.isIssuanceTx(txId);
    }

    public boolean hasTxBurntFee(String hashAsString) {
        return stateService.hasTxBurntFee(hashAsString);
    }

    public Optional<TxType> getTxType(String txId) {
        return stateService.getTxType(txId);
    }
}
