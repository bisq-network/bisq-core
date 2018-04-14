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

package bisq.core.dao.vote.result;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.blindvote.BlindVote;
import bisq.core.dao.vote.blindvote.BlindVoteService;
import bisq.core.dao.vote.proposal.ProposalList;

import javax.crypto.SecretKey;

import java.util.Optional;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Holds all data from a decrypted vote item.
 */

@Slf4j
@Value
public class DecryptedVote {
    private final byte[] hashOfBlindVoteList;
    private final String voteRevealTxId;
    private final String blindVoteTxId;
    private final long stake;
    private final ProposalList proposalListUsedForVoting;

    public DecryptedVote(byte[] opReturnData, String voteRevealTxId,
                         StateService stateService, BlindVoteService blindVoteService,
                         PeriodService periodService, int chainHeight)
            throws VoteResultException {
        hashOfBlindVoteList = VoteResultConsensus.getHashOfBlindVoteList(opReturnData);
        this.voteRevealTxId = voteRevealTxId;

        SecretKey secretKey = VoteResultConsensus.getSecretKey(opReturnData);
        Tx voteRevealTx = getVoteRevealTx(voteRevealTxId, stateService, periodService, chainHeight);
        TxOutput blindVoteStakeOutput = getBlindVoteStakeOutput(voteRevealTx, stateService);
        blindVoteTxId = getBlindVoteTxId(blindVoteStakeOutput, stateService, periodService, chainHeight);
        stake = getStake(blindVoteStakeOutput);
        BlindVote blindVote = getBlindVote(stateService);
        proposalListUsedForVoting = getProposalList(blindVote, secretKey);
    }

    private Tx getVoteRevealTx(String voteRevealTxId, StateService stateService,
                               PeriodService periodService, int chainHeight)
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

    private TxOutput getBlindVoteStakeOutput(Tx voteRevealTx, StateService stateService)
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

    private String getBlindVoteTxId(TxOutput blindVoteStakeOutput, StateService stateService,
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

    private long getStake(TxOutput blindVoteStakeOutput) {
        return blindVoteStakeOutput.getValue();
    }

    private BlindVote getBlindVote(StateService stateService) throws VoteResultException {
        try {
            Optional<BlindVote> optionalBlindVote = stateService.getBlindVotes().stream()
                    .filter(blindVote -> blindVote.getTxId().equals(blindVoteTxId))
                    .findAny();
            checkArgument(optionalBlindVote.isPresent(), "blindVote with txId " + blindVoteTxId +
                    "not found in stateService.");
            return optionalBlindVote.get();
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

    private ProposalList getProposalList(BlindVote blindVote, SecretKey secretKey) throws VoteResultException {
        try {
            final byte[] encryptedProposalList = blindVote.getEncryptedProposalList();
            final byte[] decrypted = VoteResultConsensus.decryptProposalList(encryptedProposalList, secretKey);
            return ProposalList.parseProposalList(decrypted);
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }
}
