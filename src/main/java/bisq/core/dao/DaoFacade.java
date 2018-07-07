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
import bisq.core.dao.bonding.lockup.LockupService;
import bisq.core.dao.bonding.unlock.UnlockService;
import bisq.core.dao.state.BlockListener;
import bisq.core.dao.state.ChainHeightListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputKey;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ValidationException;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.BallotListService;
import bisq.core.dao.voting.ballot.FilteredBallotListService;
import bisq.core.dao.voting.ballot.vote.Vote;
import bisq.core.dao.voting.blindvote.MyBlindVoteListService;
import bisq.core.dao.voting.myvote.MyVote;
import bisq.core.dao.voting.myvote.MyVoteListService;
import bisq.core.dao.voting.proposal.FilteredProposalListService;
import bisq.core.dao.voting.proposal.MyProposalListService;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.ProposalConsensus;
import bisq.core.dao.voting.proposal.ProposalWithTransaction;
import bisq.core.dao.voting.proposal.compensation.CompensationProposalService;

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
    private final FilteredProposalListService filteredProposalListService;
    private final BallotListService ballotListService;
    private final FilteredBallotListService filteredBallotListService;
    private final MyProposalListService myProposalListService;
    private final StateService stateService;
    private final PeriodService periodService;
    private final MyBlindVoteListService myBlindVoteListService;
    private final MyVoteListService myVoteListService;
    private final CompensationProposalService compensationProposalService;
    private final LockupService lockupService;
    private final UnlockService unlockService;

    private final ObjectProperty<DaoPhase.Phase> phaseProperty = new SimpleObjectProperty<>(DaoPhase.Phase.UNDEFINED);

    @Inject
    public DaoFacade(MyProposalListService myProposalListService,
                     FilteredProposalListService filteredProposalListService,
                     BallotListService ballotListService,
                     FilteredBallotListService filteredBallotListService,
                     StateService stateService,
                     PeriodService periodService,
                     MyBlindVoteListService myBlindVoteListService,
                     MyVoteListService myVoteListService,
                     CompensationProposalService compensationProposalService,
                     LockupService lockupService,
                     UnlockService unlockService) {
        this.filteredProposalListService = filteredProposalListService;
        this.ballotListService = ballotListService;
        this.filteredBallotListService = filteredBallotListService;
        this.myProposalListService = myProposalListService;
        this.stateService = stateService;
        this.periodService = periodService;
        this.myBlindVoteListService = myBlindVoteListService;
        this.myVoteListService = myVoteListService;
        this.compensationProposalService = compensationProposalService;
        this.lockupService = lockupService;
        this.unlockService = unlockService;

        stateService.addChainHeightListener(chainHeight -> {
            if (chainHeight > 0 && periodService.getCurrentCycle() != null)
                periodService.getCurrentCycle().getPhaseForHeight(chainHeight).ifPresent(phaseProperty::set);
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    //
    // Phase: Proposal
    //
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Present lists
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Proposal> getActiveOrMyUnconfirmedProposals() {
        return filteredProposalListService.getActiveOrMyUnconfirmedProposals();
    }

    public ObservableList<Proposal> getClosedProposals() {
        return filteredProposalListService.getClosedProposals();
    }

    public List<Proposal> getMyProposals() {
        return myProposalListService.getList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Create proposal
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Creation of Proposal and proposalTransaction
    public ProposalWithTransaction getCompensationProposalWithTransaction(String name,
                                                                          String title,
                                                                          String description,
                                                                          String link,
                                                                          Coin requestedBsq,
                                                                          String bsqAddress)
            throws ValidationException, InsufficientMoneyException, IOException, TransactionVerificationException,
            WalletException {
        return compensationProposalService.createProposalWithTransaction(name,
                title,
                description,
                link,
                requestedBsq,
                bsqAddress);
    }

    // Show fee
    public Coin getProposalFee() {
        return ProposalConsensus.getFee(stateService, stateService.getChainHeight());
    }

    // Publish proposal tx, proposal payload and and persist it to myProposalList
    public void publishMyProposal(Proposal proposal, Transaction transaction, ResultHandler resultHandler,
                                  ErrorMessageHandler errorMessageHandler) {
        myProposalListService.publishTxAndPayload(proposal, transaction, resultHandler, errorMessageHandler);
    }

    // Check if it is my proposal
    public boolean isMyProposal(Proposal proposal) {
        return myProposalListService.isMine(proposal);
    }

    // Remove my proposal
    public boolean removeMyProposal(Proposal proposal) {
        return myProposalListService.remove(proposal);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //
    // Phase: Blind Vote
    //
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Present lists
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableList<Ballot> getActiveOrMyUnconfirmedBallots() {
        return filteredBallotListService.getValidAndConfirmedBallots();
    }

    public ObservableList<Ballot> getClosedBallots() {
        return filteredBallotListService.getClosedBallots();
    }

    public List<MyVote> getMyVoteList() {
        return myVoteListService.getMyVoteList().getList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Vote
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Vote on ballot
    public void setVote(Ballot ballot, @Nullable Vote vote) {
        ballotListService.setVote(ballot, vote);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Create blindVote
    ///////////////////////////////////////////////////////////////////////////////////////////

    // When creating blind vote we present fee
    public Coin getBlindVoteFeeForCycle() {
        return myBlindVoteListService.getBlindVoteFee();
    }

    // Used for mining fee estimation
    public Transaction getDummyBlindVoteTx(Coin stake, Coin blindVoteFee) throws WalletException, InsufficientMoneyException, TransactionVerificationException {
        return myBlindVoteListService.getDummyBlindVoteTx(stake, blindVoteFee);
    }

    // Publish blindVote tx and broadcast blindVote to p2p network and store to blindVoteList.
    public void publishBlindVote(Coin stake, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        myBlindVoteListService.publishBlindVote(stake, resultHandler, exceptionHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //
    // Generic
    //
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Presentation of phases
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addChainHeightListener(ChainHeightListener listener) {
        stateService.addChainHeightListener(listener);
    }

    public void removeChainHeightListener(ChainHeightListener listener) {
        stateService.removeChainHeightListener(listener);
    }

    public int getFirstBlockOfPhase(int height, DaoPhase.Phase phase) {
        return periodService.getFirstBlockOfPhase(height, phase);
    }

    public int getLastBlockOfPhase(int height, DaoPhase.Phase phase) {
        return periodService.getLastBlockOfPhase(height, phase);
    }

    public int getDurationForPhase(DaoPhase.Phase phase) {
        return periodService.getDurationForPhase(phase, stateService.getChainHeight());
    }

    // listeners for phase change
    public ReadOnlyObjectProperty<DaoPhase.Phase> phaseProperty() {
        return phaseProperty;
    }

    public int getChainHeight() {
        return stateService.getChainHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Use case: Bonding
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO maybe merge lockupService and unlockService as bondService?
    // Publish lockup tx
    public void publishLockupTx(Coin lockupAmount, int lockTime, ResultHandler resultHandler,
                                ExceptionHandler exceptionHandler) {
        lockupService.publishLockupTx(lockupAmount, lockTime, resultHandler, exceptionHandler);
    }

    // Publish unlock tx
    public void publishUnlockTx(String lockedTxId, ResultHandler resultHandler,
                                ExceptionHandler exceptionHandler) {
        unlockService.publishUnlockTx(lockedTxId, resultHandler, exceptionHandler);
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
        return stateService.getBurntFeeTxs();
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
        return stateService.getTotalIssuedAmount();
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

    public Optional<TxType> getOptionalTxType(String txId) {
        return stateService.getOptionalTxType(txId);
    }

    public TxType getTxType(String txId) {
        return stateService.getTx(txId).map(Tx::getTxType).orElse(TxType.UNDEFINED_TX_TYPE);
    }

    public boolean isTxOutputSpendable(String txId, int index) {
        return stateService.isTxOutputSpendable(new TxOutputKey(txId, index));
    }

    public Set<TxOutput> getLockedInBondOutputs() {
        return stateService.getLockupTxOutputs();
    }

    public boolean containsTx(String txId) {
        return stateService.containsTx(txId);
    }

    public boolean isBsqTxOutputType(TxOutput txOutput) {
        return stateService.isBsqTxOutputType(txOutput);
    }

    public boolean isInPhaseButNotLastBlock(DaoPhase.Phase phase) {
        return periodService.isInPhaseButNotLastBlock(phase);
    }
}
