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

package bisq.core.dao.vote.consensus;

import bisq.core.dao.OpReturnTypes;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.proposal.Proposal;
import bisq.core.dao.proposal.ProposalList;
import bisq.core.dao.vote.BlindVote;

import bisq.common.app.Version;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.Hash;

import org.bitcoinj.core.Coin;

import javax.crypto.SecretKey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * All consensus critical aspects are handled here.
 */
@Slf4j
public class VoteConsensus {
    public static Comparator<BlindVote> getBlindVoteListComparator() {
        return Comparator.comparing(BlindVote::getTxId);
    }

    public static List<Proposal> getSortedProposalList(List<Proposal> proposals) {
        proposals.sort(Comparator.comparing(proposal -> proposal.getProposalPayload().getTxId()));
        return proposals;
    }

    public static byte[] getProposalListAsByteArray(ProposalList proposalList) {
        return proposalList.toProtoMessage().toByteArray();
    }

    public static Coin getVoteFee(ReadableBsqBlockChain readableBsqBlockChain) {
        return Coin.valueOf(readableBsqBlockChain.getVotingFee(readableBsqBlockChain
                .getChainHeadHeight()));
    }

    public static byte[] getOpReturnData(byte[] encryptedProposalList) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(OpReturnTypes.VOTE);
            outputStream.write(Version.VOTING_VERSION);
            outputStream.write(Hash.getSha256Ripemd160hash(encryptedProposalList));
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Not expected to happen ever
            e.printStackTrace();
            log.error(e.toString());
            return new byte[0];
        }
    }

    public static SecretKey getSecretKey() {
        return Encryption.generateSecretKey();
    }
}
