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

import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.TxInput;
import bisq.core.dao.blockchain.vo.TxOutput;

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Provide spendable TxOutput and apply state change.
 */

@Slf4j
public class TxInputController {

    private final ReadableBsqBlockChain readableBsqBlockChain;

    @Inject
    public TxInputController(ReadableBsqBlockChain readableBsqBlockChain) {
        this.readableBsqBlockChain = readableBsqBlockChain;
    }

    Optional<TxOutput> getOptionalSpendableTxOutput(TxInput input) {
        // TODO check if Tuple indexes of inputs outputs are not messed up...
        // Get spendable BSQ output for txIdIndexTuple... (get output used as input in tx if it's spendable BSQ)
        return readableBsqBlockChain.getUnspentAndMatureTxOutput(input.getTxIdIndexTuple());
    }
}
