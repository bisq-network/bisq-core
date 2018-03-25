/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.proposal.consensus;

import bisq.core.dao.blockchain.ReadableBsqBlockChain;

import org.bitcoinj.core.Coin;

public class ProposalConsensus {

    public static int getMaxLengthDescriptionText() {
        return 100;
    }

    public static boolean isDescriptionSizeValid(String description) {
        return description.length() <= getMaxLengthDescriptionText();
    }

    public static Coin getCreateCompensationRequestFee(ReadableBsqBlockChain readableBsqBlockChain) {
        return Coin.valueOf(readableBsqBlockChain.getCreateCompensationRequestFee(readableBsqBlockChain
                .getChainHeadHeight()));
    }
}
