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

package bisq.core.dao.consensus.voteresult;

import bisq.core.dao.consensus.ballot.BallotList;
import bisq.core.dao.consensus.blindvote.BlindVote;
import bisq.core.dao.consensus.blindvote.BlindVoteService;
import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.consensus.state.blockchain.TxInput;
import bisq.core.dao.consensus.state.blockchain.TxOutput;
import bisq.core.dao.consensus.state.blockchain.TxOutputType;
import bisq.core.dao.consensus.state.blockchain.TxType;

import javax.inject.Inject;

import javax.crypto.SecretKey;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

public class DecryptedVoteHelper {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DecryptedVoteHelper() {
    }

    public Tx getVoteRevealTx(String voteRevealTxId, StateService stateService, PeriodService periodService,
                              int chainHeight)
            throws VoteResultException {
        Optional<Tx> optionalVoteRevealTx = stateService.getTx(voteRevealTxId);
        try {
            checkArgument(optionalVoteRevealTx.isPresent(), "voteRevealTx with txId " +
                    voteRevealTxId + "not found.");
            Tx voteRevealTx = optionalVoteRevealTx.get();
            Optional<TxType> optionalTxType = stateService.getTxType(voteRevealTx.getId());
            checkArgument(optionalTxType.isPresent(), "optionalTxType must be present");
            checkArgument(optionalTxType.get() == TxType.VOTE_REVEAL,
                    "voteRevealTx must have type VOTE_REVEAL");
            checkArgument(periodService.isTxInCorrectCycle(voteRevealTx.getBlockHeight(), chainHeight),
                    "voteRevealTx is not in correct cycle. chainHeight=" + chainHeight);
            return voteRevealTx;
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

    public TxOutput getBlindVoteStakeOutput(Tx voteRevealTx, StateService stateService)
            throws VoteResultException {
        try {
            // We use the stake output of the blind vote tx as first input
            final TxInput stakeIxInput = voteRevealTx.getInputs().get(0);
            Optional<TxOutput> optionalTxOutput = stateService.getConnectedTxOutput(stakeIxInput);
            checkArgument(optionalTxOutput.isPresent(), "blindVoteStakeOutput must not be present");
            final TxOutput txOutput = optionalTxOutput.get();
            checkArgument(stateService.getTxOutputType(txOutput) == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT,
                    "blindVoteStakeOutput must have type BLIND_VOTE_LOCK_STAKE_OUTPUT");
            return txOutput;
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

    public String getBlindVoteTxId(TxOutput blindVoteStakeOutput, StateService stateService,
                                   PeriodService periodService, int chainHeight)
            throws VoteResultException {
        try {
            String blindVoteTxId = blindVoteStakeOutput.getTxId();
            Optional<Tx> optionalBlindVoteTx = stateService.getTx(blindVoteTxId);
            checkArgument(optionalBlindVoteTx.isPresent(), "blindVoteTx with txId " +
                    blindVoteTxId + "not found.");
            Tx blindVoteTx = optionalBlindVoteTx.get();
            Optional<TxType> optionalTxType = stateService.getTxType(blindVoteTx.getId());
            checkArgument(optionalTxType.isPresent(), "optionalTxType must be present");
            checkArgument(optionalTxType.get() == TxType.BLIND_VOTE,
                    "blindVoteTx must have type BLIND_VOTE");
            checkArgument(periodService.isTxInCorrectCycle(blindVoteTx.getBlockHeight(), chainHeight),
                    "blindVoteTx is not in correct cycle. chainHeight=" + chainHeight);
            return blindVoteTxId;
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

    public long getStake(TxOutput blindVoteStakeOutput) {
        return blindVoteStakeOutput.getValue();
    }

    public BlindVote getBlindVote(String blindVoteTxId, BlindVoteService blindVoteService) throws VoteResultException {
        try {
            Optional<BlindVote> optionalBlindVote = blindVoteService.getBlindVoteList().stream()
                    .filter(blindVote -> blindVote.getTxId().equals(blindVoteTxId))
                    .findAny();

            checkArgument(optionalBlindVote.isPresent(), "blindVote with txId " + blindVoteTxId +
                    "not found in stateService.");
            return optionalBlindVote.get();
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

    public BallotList getBallotList(BlindVote blindVote, SecretKey secretKey) throws VoteResultException {
        try {
            final byte[] encryptedProposalList = blindVote.getEncryptedBallotList();
            final byte[] decrypted = VoteResultConsensus.decryptProposalList(encryptedProposalList, secretKey);
            return BallotList.parseBallotList(decrypted);
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

}
