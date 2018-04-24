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

import bisq.core.dao.voting.ballot.BallotList;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds all data from a decrypted vote item.
 */

@Slf4j
@Value
class DecryptedVote {
    private final byte[] hashOfBlindVoteList;
    private final String voteRevealTxId; // not used yet but keep it for now
    private final String blindVoteTxId; // not used yet but keep it for now
    private final long stake;
    private final BallotList ballotList;

    DecryptedVote(byte[] hashOfBlindVoteList, String voteRevealTxId, String blindVoteTxId, long stake, BallotList ballotList) {
        this.hashOfBlindVoteList = hashOfBlindVoteList;
        this.voteRevealTxId = voteRevealTxId;
        this.blindVoteTxId = blindVoteTxId;
        this.stake = stake;
        this.ballotList = ballotList;
    }
}
