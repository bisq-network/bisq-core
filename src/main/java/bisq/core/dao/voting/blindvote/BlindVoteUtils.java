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

package bisq.core.dao.voting.blindvote;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BlindVoteUtils {
    public static boolean containsBlindVote(BlindVote blindVote, List<BlindVote> blindVoteList) {
        return findBlindVoteInList(blindVote, blindVoteList).isPresent();
    }

    public static Optional<BlindVote> findBlindVoteInList(BlindVote blindVote, List<BlindVote> blindVoteList) {
        return blindVoteList.stream()
                .filter(vote -> vote.equals(blindVote))
                .findAny();
    }

    public static Optional<BlindVote> findBlindVote(String blindVoteTxId, MyBlindVoteList myBlindVoteList) {
        return myBlindVoteList.stream()
                .filter(blindVote -> blindVote.getTxId().equals(blindVoteTxId))
                .findAny();
    }

}
