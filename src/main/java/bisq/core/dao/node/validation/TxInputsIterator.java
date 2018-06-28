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

package bisq.core.dao.node.validation;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Iterates all inputs to calculate the available BSQ balance from all inputs and apply state change.
 */
@Slf4j
public class TxInputsIterator {
    private final StateService stateService;
    private final TxInputProcessor txInputProcessor;

    @Inject
    public TxInputsIterator(StateService stateService, TxInputProcessor txInputProcessor) {
        this.stateService = stateService;
        this.txInputProcessor = txInputProcessor;
    }

    ParsingModel iterate(Tx tx, int blockHeight) {
        ParsingModel parsingModel = new ParsingModel();
        for (int inputIndex = 0; inputIndex < tx.getInputs().size(); inputIndex++) {
            TxInput input = tx.getInputs().get(inputIndex);
            txInputProcessor.process(input, blockHeight, tx.getId(), inputIndex, parsingModel, stateService);
        }
        return parsingModel;
    }
}
