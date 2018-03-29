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

package bisq.core.dao.node.consensus;

import bisq.core.dao.OpReturnTypes;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;

import bisq.common.app.Version;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class OpReturnBlindVoteController {

    private final ReadableBsqBlockChain readableBsqBlockChain;

    @Inject
    public OpReturnBlindVoteController(ReadableBsqBlockChain readableBsqBlockChain) {
        this.readableBsqBlockChain = readableBsqBlockChain;
    }

    public boolean verify(byte[] opReturnData, long bsqFee, int blockHeight, BsqTxController.MutableState mutableState) {
        return mutableState.getBlindVoteStakeOutput() != null &&
                opReturnData.length == 22 &&
                Version.BLIND_VOTE_VERSION == opReturnData[1] &&
                bsqFee == readableBsqBlockChain.getBlindVoteFee(blockHeight) &&
                readableBsqBlockChain.isBlindVotePeriodValid(blockHeight);
    }

    public void applyStateChange(TxOutput opReturnTxOutput, BsqTxController.MutableState mutableState) {
        opReturnTxOutput.setTxOutputType(TxOutputType.VOTE_OP_RETURN_OUTPUT);
        checkArgument(mutableState.getBlindVoteStakeOutput() != null, "mutableState." +
                "getVoteStakeOutput() must not be null");
        mutableState.getBlindVoteStakeOutput().setTxOutputType(TxOutputType.VOTE_STAKE_OUTPUT);
        mutableState.setVerifiedOpReturnType(OpReturnTypes.BLIND_VOTE);
    }
}
