/*
 * This file is part of Bisq.
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

package bisq.core.dao.blockchain;

import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;

public interface WritableBsqBlockChain {

    // state change
    void applySnapshot(BsqBlockChain snapshot);

    void addBlock(BsqBlock bsqBlock);

    void setGenesisTx(Tx tx);

    void addTxToMap(Tx tx);

    void addUnspentTxOutput(TxOutput txOutput);

    void removeUnspentTxOutput(TxOutput spendableTxOutput);

    void issueBsq(TxOutput txOutput);


    // not used yet outside of BsqBlockChain but leave it for later use
    void setCreateCompensationRequestFee(long value, int genesisBlockHeight);

    void setBlindVoteFee(long value, int genesisBlockHeight);

    void setQuorum(long quorum, int blockHeight);

    void setVoteThreshold(long voteThreshold, int blockHeight);
}
