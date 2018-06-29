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

package bisq.core.dao.voting.voteresult;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.blindvote.VoteWithProposalTxIdList;
import bisq.core.dao.voting.merit.MeritList;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.util.MathUtils;
import bisq.common.util.Utilities;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

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

    private static byte[] decryptVotes(byte[] encryptedVotes, SecretKey secretKey) throws CryptoException {
        return Encryption.decrypt(encryptedVotes, secretKey);
    }

    public static VoteWithProposalTxIdList getDecryptVotes(byte[] encryptedVotes, SecretKey secretKey) throws VoteResultException {
        try {
            final byte[] decrypted = decryptVotes(encryptedVotes, secretKey);
            return VoteWithProposalTxIdList.getVoteWithProposalTxIdListFromBytes(decrypted);
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

    public static MeritList getDecryptMeritList(byte[] encryptedMeritList, SecretKey secretKey) throws VoteResultException {
        try {
            final byte[] decrypted = Encryption.decrypt(encryptedMeritList, secretKey);
            return MeritList.getMeritListFromBytes(decrypted);
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

    public static long getWeightedMeritAmount(long amount, int issuanceHeight, int currentHeight, int blocksPerYear) {
        if (issuanceHeight > currentHeight)
            throw new IllegalArgumentException("issuanceHeight must not be larger than currentHeight");
        if (currentHeight < 0)
            throw new IllegalArgumentException("currentHeight must not be negative");
        if (amount < 0)
            throw new IllegalArgumentException("amount must not be negative");
        if (blocksPerYear < 0)
            throw new IllegalArgumentException("blocksPerYear must not be negative");

        // We use a linear function  to apply a factor for the issuance amount of 1 if the issuance was recent and 0
        // if the issuance was 2 years old or older.
        int maxAge = 2 * blocksPerYear;
        int age = Math.min(maxAge, currentHeight - issuanceHeight);

        double rel = MathUtils.roundDouble((double) age / (double) maxAge, 10);
        double factor = 1d - rel;
        long weightedAmount = MathUtils.roundDoubleToLong(amount * factor);
        log.info("getWeightedMeritAmount: amount={}, factor={}, weightedAmount={}, ", amount, factor, weightedAmount);
        return weightedAmount;
    }

    public static long getMeritStake(String blindVoteTxId, MeritList meritList, StateService stateService) {
        int blocksPerYear = 50_000; // 51264;
        int currentChainHeight = stateService.getChainHeight();
        return meritList.getList().stream()
                .filter(merit -> {
                    String pubKeyAsHex = merit.getIssuance().getPubKey();
                    if (pubKeyAsHex == null)
                        return false;

                    // TODO Check if a sig key was used multiple times for different voters
                    // At the moment we don't impl. that to not add too much complexity and as we consider that
                    // risk very low.

                    boolean result = false;
                    try {
                        ECKey pubKey = ECKey.fromPublicOnly(Utilities.decodeFromHex(pubKeyAsHex));
                        ECKey.ECDSASignature signature = ECKey.ECDSASignature.decodeFromDER(merit.getSignature()).toCanonicalised();
                        Sha256Hash data = Sha256Hash.wrap(blindVoteTxId);
                        result = pubKey.verify(data, signature);
                    } catch (Throwable t) {
                        log.error("Signature verification of issuance failed: " + t.toString());
                    }
                    log.debug("Signature verification result {}, txId={}", result, blindVoteTxId);
                    return result;
                })
                .mapToLong(merit -> {
                    return VoteResultConsensus.getWeightedMeritAmount(merit.getIssuance().getAmount(),
                            merit.getIssuance().getChainHeight(),
                            currentChainHeight,
                            blocksPerYear);
                })
                .sum();
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
            Optional<TxType> optionalTxType = stateService.getOptionalTxType(blindVoteTx.getId());
            checkArgument(optionalTxType.isPresent(), "optionalTxType must be present");
            checkArgument(optionalTxType.get() == TxType.BLIND_VOTE,
                    "blindVoteTx must have type BLIND_VOTE");
            checkArgument(periodService.isTxInCorrectCycle(blindVoteTx.getBlockHeight(), chainHeight),
                    "blindVoteTx is not in correct cycle. blindVoteTx.getBlockHeight()="
                            + blindVoteTx.getBlockHeight());
            checkArgument(periodService.isInPhase(blindVoteTx.getBlockHeight(), DaoPhase.Phase.BLIND_VOTE),
                    "blindVoteTx is not in BLIND_VOTE phase. blindVoteTx.getBlockHeight()="
                            + blindVoteTx.getBlockHeight());
            return blindVoteTx;
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }
}
