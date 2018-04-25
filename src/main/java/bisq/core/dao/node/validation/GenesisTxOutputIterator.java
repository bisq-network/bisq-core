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

import javax.inject.Inject;

/**
 * Verifies if a given transaction is a BSQ genesis transaction.
 */
public class GenesisTxOutputIterator {

    private final StateService stateService;
    private final GenesisTxOutputValidator genesisTxOutputValidator;

    @Inject
    public GenesisTxOutputIterator(StateService stateService,
                                   GenesisTxOutputValidator genesisTxOutputValidator) {
        this.stateService = stateService;
        this.genesisTxOutputValidator = genesisTxOutputValidator;
    }

    public void iterate(Tx tx) {
        TxState txState = new TxState(stateService.getGenesisTotalSupply().getValue());
        for (int i = 0; i < tx.getOutputs().size(); ++i) {
            genesisTxOutputValidator.validate(tx.getOutputs().get(i), txState);
        }
    }
}
