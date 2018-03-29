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

package bisq.core.dao.node.consensus;

import bisq.core.dao.blockchain.WritableBsqBlockChain;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Checks if an output is a BSQ output and apply state change.
 */
@Slf4j
public class GenesisTxOutputController extends TxOutputController {
    @Inject
    public GenesisTxOutputController(WritableBsqBlockChain writableBsqBlockChain, OpReturnController opReturnController) {
        super(writableBsqBlockChain, opReturnController);
    }

    // Use bsqInputBalance for counting remaining BSQ not yet allocated to a txOutput
    void verify(TxOutput txOutput, Model remainingAmount) {
        if (txOutput.getValue() <= remainingAmount.getAvailableInputValue()) {
            remainingAmount.subtractFromInputValue(txOutput.getValue());
            applyStateChangeForBsqOutput(txOutput, TxOutputType.BSQ_OUTPUT);
        } else {
            // No more outputs are considered BSQ after the first non BSQ output
            // Theoretically some BSQ might be left, it would be considered as burned fee,
            // but the genesis is constructed so that inputs matches exactly all outputs.
            applyStateChangeForBtcOutput(txOutput);
        }
    }
}
