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

package bisq.core.dao.governance.voteresult;

import bisq.core.dao.governance.blindvote.VoteWithProposalTxIdList;
import bisq.core.dao.governance.merit.MeritList;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.governance.Issuance;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

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
    private static final int BLOCKS_PER_YEAR = 50_000; // 51264;

    // Hash of the list of Blind votes is 20 bytes after version and type bytes
    public static byte[] getHashOfBlindVoteList(byte[] opReturnData) {
        return Arrays.copyOfRange(opReturnData, 2, 22);
    }

    private static byte[] decryptVotes(byte[] encryptedVotes, SecretKey secretKey) throws CryptoException {
        return Encryption.decrypt(encryptedVotes, secretKey);
    }

    public static VoteWithProposalTxIdList getDecryptedVotes(byte[] encryptedVotes, SecretKey secretKey) throws VoteResultException {
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

    public static long getWeightedMeritAmount(long amount, int issuanceHeight, int blockHeight, int blocksPerYear) {
        if (issuanceHeight > blockHeight)
            throw new IllegalArgumentException("issuanceHeight must not be larger than blockHeight");
        if (blockHeight < 0)
            throw new IllegalArgumentException("blockHeight must not be negative");
        if (amount < 0)
            throw new IllegalArgumentException("amount must not be negative");
        if (blocksPerYear < 0)
            throw new IllegalArgumentException("blocksPerYear must not be negative");

        // We use a linear function  to apply a factor for the issuance amount of 1 if the issuance was recent and 0
        // if the issuance was 2 years old or older.
        int maxAge = 2 * blocksPerYear;
        int age = Math.min(maxAge, blockHeight - issuanceHeight);

        double rel = MathUtils.roundDouble((double) age / (double) maxAge, 10);
        double factor = 1d - rel;
        long weightedAmount = MathUtils.roundDoubleToLong(amount * factor);
        log.debug("getWeightedMeritAmount: amount={}, factor={}, weightedAmount={}, ", amount, factor, weightedAmount);
        return weightedAmount;
    }

    public static long getMeritStake(String blindVoteTxId, MeritList meritList, BsqStateService bsqStateService) {
        // We need to take the chain height when the blindVoteTx got published so we get the same merit for the vote even at
        // later blocks (merit decreases with each block).
        int txChainHeight = bsqStateService.getTx(blindVoteTxId).map(Tx::getBlockHeight).orElse(0);
        if (txChainHeight == 0) {
            log.error("Error at getMeritStake: blindVoteTx not found in bsqStateService. blindVoteTxId=" + blindVoteTxId);
            return 0;
        }

        return meritList.getList().stream()
                .filter(merit -> {
                    // We verify if signature of hash of blindVoteTxId is correct. EC key from first input for blind vote tx is
                    // used for signature.
                    String pubKeyAsHex = merit.getIssuance().getPubKey();
                    if (pubKeyAsHex == null) {
                        log.error("Error at getMeritStake: pubKeyAsHex is null");
                        return false;
                    }

                    // TODO Check if a sig key was used multiple times for different voters
                    // At the moment we don't impl. that to not add too much complexity and as we consider that
                    // risk very low.

                    boolean result = false;
                    try {
                        ECKey pubKey = ECKey.fromPublicOnly(Utilities.decodeFromHex(pubKeyAsHex));
                        ECKey.ECDSASignature signature = ECKey.ECDSASignature.decodeFromDER(merit.getSignature()).toCanonicalised();
                        Sha256Hash msg = Sha256Hash.wrap(blindVoteTxId);
                        result = pubKey.verify(msg, signature);
                    } catch (Throwable t) {
                        log.error("Signature verification of issuance failed: " + t.toString());
                    }
                    if (!result) {
                        log.error("Signature verification of issuance failed: blindVoteTxId={}, pubKeyAsHex={}",
                                blindVoteTxId, pubKeyAsHex);
                    }
                    return result;
                })
                .mapToLong(merit -> {
                    try {
                        int issuanceHeight = merit.getIssuance().getChainHeight();
                        if (issuanceHeight <= txChainHeight) {
                            return getWeightedMeritAmount(merit.getIssuance().getAmount(),
                                    issuanceHeight,
                                    txChainHeight,
                                    BLOCKS_PER_YEAR);
                        } else {
                            return 0;
                        }
                    } catch (Throwable t) {
                        log.error("Error at getMeritStake: " + t.toString());
                        return 0;
                    }
                })
                .sum();
    }

    // Used to get the currently available merit before we have made the blind vote.
    public static long getCurrentlyAvailableMerit(MeritList meritList, int currentChainHeight) {
        // We need to take the chain height when the blindVoteTx got published so we get the same merit for the vote even at
        // later blocks (merit decreases with each block).
        return meritList.getList().stream()
                .mapToLong(merit -> {
                    try {
                        Issuance issuance = merit.getIssuance();
                        int issuanceHeight = issuance.getChainHeight();
                        checkArgument(issuanceHeight <= currentChainHeight,
                                "issuanceHeight must not be larger as currentChainHeight");
                        return getWeightedMeritAmount(issuance.getAmount(),
                                issuanceHeight,
                                currentChainHeight,
                                BLOCKS_PER_YEAR);
                    } catch (Throwable t) {
                        log.error("Error at getCurrentlyAvailableMerit: " + t.toString());
                        return 0;
                    }
                })
                .sum();
    }

    //TODO add tests
    // We compare first by stake and in case we have multiple entries with same stake we use the
    // hex encoded hashOfProposalList for comparision
    @Nullable
    public static byte[] getMajorityHash(List<VoteResultService.HashWithStake> hashWithStakeList) throws VoteResultException {
        checkArgument(!hashWithStakeList.isEmpty(), "hashWithStakeList must not be empty");
        hashWithStakeList.sort(Comparator.comparingLong(VoteResultService.HashWithStake::getStake).reversed()
                .thenComparing(o -> Utilities.encodeToHex(o.getHash())));

        // If there are conflicting data views (multiple hashes) we only consider the voting round as valid if
        // the majority is a super majority with > 80%.
        if (hashWithStakeList.size() > 1) {
            long stakeOfAll = hashWithStakeList.stream().mapToLong(VoteResultService.HashWithStake::getStake).sum();
            long stakeOfFirst = hashWithStakeList.get(0).getStake();
            if ((double) stakeOfFirst / (double) stakeOfAll < 0.8) {
                throw new VoteResultException("The winning data view has less then 80% of the total stake of " +
                        "all data views. We consider the voting cycle as invalid if the winning data view does not " +
                        "reach a super majority.");
            }
        }
        return hashWithStakeList.get(0).getHash();
    }

    // Key is stored after version and type bytes and list of Blind votes. It has 16 bytes
    public static SecretKey getSecretKey(byte[] opReturnData) {
        byte[] secretKeyAsBytes = Arrays.copyOfRange(opReturnData, 22, 38);
        return Encryption.getSecretKeyFromBytes(secretKeyAsBytes);
    }

    public static TxOutput getConnectedBlindVoteStakeOutput(Tx voteRevealTx, BsqStateService bsqStateService)
            throws VoteResultException {
        try {
            // We use the stake output of the blind vote tx as first input
            final TxInput stakeTxInput = voteRevealTx.getTxInputs().get(0);
            Optional<TxOutput> optionalBlindVoteStakeOutput = bsqStateService.getConnectedTxOutput(stakeTxInput);
            checkArgument(optionalBlindVoteStakeOutput.isPresent(), "blindVoteStakeOutput must not be present");
            final TxOutput blindVoteStakeOutput = optionalBlindVoteStakeOutput.get();
            checkArgument(blindVoteStakeOutput.getTxOutputType() == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT,
                    "blindVoteStakeOutput must have type BLIND_VOTE_LOCK_STAKE_OUTPUT");
            return blindVoteStakeOutput;
        } catch (Throwable t) {
            throw new VoteResultException(t);
        }
    }

    public static Tx getBlindVoteTx(TxOutput blindVoteStakeOutput, BsqStateService bsqStateService,
                                    PeriodService periodService, int chainHeight)
            throws VoteResultException {
        try {
            String blindVoteTxId = blindVoteStakeOutput.getTxId();
            Optional<Tx> optionalBlindVoteTx = bsqStateService.getTx(blindVoteTxId);
            checkArgument(optionalBlindVoteTx.isPresent(), "blindVoteTx with txId " +
                    blindVoteTxId + "not found.");
            Tx blindVoteTx = optionalBlindVoteTx.get();
            Optional<TxType> optionalTxType = bsqStateService.getOptionalTxType(blindVoteTx.getId());
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
