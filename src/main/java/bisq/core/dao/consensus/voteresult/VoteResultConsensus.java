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
import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.consensus.state.blockchain.TxInput;
import bisq.core.dao.consensus.state.blockchain.TxOutput;
import bisq.core.dao.consensus.state.blockchain.TxOutputType;
import bisq.core.dao.consensus.state.blockchain.TxType;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.util.Utilities;

import javax.crypto.SecretKey;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class VoteResultConsensus {
    // Hash of the list of Blind votes is 20 bytes after version and type bytes
    public static byte[] getHashOfBlindVoteList(byte[] opReturnData) {
        return Arrays.copyOfRange(opReturnData, 2, 22);
    }

    public static byte[] decryptProposalList(byte[] encryptedProposalList, SecretKey secretKey) throws CryptoException {
        return Encryption.decrypt(encryptedProposalList, secretKey);
    }

    public static BallotList getDecryptedBallotList(byte[] encryptedBallotList, SecretKey secretKey) throws VoteResultException {
        try {
            final byte[] decrypted = VoteResultConsensus.decryptProposalList(encryptedBallotList, secretKey);
            return BallotList.getBallotListFromBytes(decrypted);
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

    //TODO add tests
    // We compare first by stake and in case we have multiple entries with same stake we use the
    // hex encoded hashOfProposalList for comparision
    @Nullable
    public static byte[] getMajorityHash(List<VoteResultService.HashWithStake> hashWithStakeList) {
        checkArgument(!hashWithStakeList.isEmpty(), "hashWithStakeList must not be empty");
        hashWithStakeList.sort(Comparator.comparingLong(VoteResultService.HashWithStake::getStake).reversed()
                .thenComparing(o -> Utilities.encodeToHex(o.getHashOfProposalList())));
        return hashWithStakeList.get(0).getHashOfProposalList();
    }

    // Key is stored after version and type bytes and list of Blind votes. It has 16 bytes
    public static SecretKey getSecretKey(byte[] opReturnData) {
        byte[] secretKeyAsBytes = Arrays.copyOfRange(opReturnData, 22, 38);
        return Encryption.getSecretKeyFromBytes(secretKeyAsBytes);
    }

    public static TxOutput getConnectedBlindVoteStakeOutput(Tx voteRevealTx, StateService stateService)
            throws VoteResultException {
        try {
            // We use the stake output of the blind vote tx as first input
            final TxInput stakeIxInput = voteRevealTx.getInputs().get(0);
            Optional<TxOutput> optionalBlindVoteStakeOutput = stateService.getConnectedTxOutput(stakeIxInput);
            checkArgument(optionalBlindVoteStakeOutput.isPresent(), "blindVoteStakeOutput must not be present");
            final TxOutput blindVoteStakeOutput = optionalBlindVoteStakeOutput.get();
            checkArgument(stateService.getTxOutputType(blindVoteStakeOutput) == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT,
                    "blindVoteStakeOutput must have type BLIND_VOTE_LOCK_STAKE_OUTPUT");
            return blindVoteStakeOutput;
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }


    public static Tx getBlindVoteTx(TxOutput blindVoteStakeOutput, StateService stateService,
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
                    "blindVoteTx is not in correct cycle. blindVoteTx.getBlockHeight()="
                            + blindVoteTx.getBlockHeight());
            checkArgument(periodService.isInPhase(blindVoteTx.getBlockHeight(), Phase.BLIND_VOTE),
                    "blindVoteTx is not in BLIND_VOTE phase. blindVoteTx.getBlockHeight()="
                            + blindVoteTx.getBlockHeight());
            return blindVoteTx;
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

}
