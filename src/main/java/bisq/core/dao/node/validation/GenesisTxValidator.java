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
import bisq.core.dao.state.blockchain.RawTx;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxType;

import javax.inject.Inject;

import java.util.Optional;

/**
 * Verifies if a given transaction is a BSQ genesis transaction.
 */
public class GenesisTxValidator {

    private final StateService stateService;
    private final GenesisTxOutputValidator genesisTxOutputValidator;

    @Inject
    public GenesisTxValidator(StateService stateService,
                              GenesisTxOutputValidator genesisTxOutputValidator) {
        this.stateService = stateService;
        this.genesisTxOutputValidator = genesisTxOutputValidator;
    }

    public Optional<Tx> getGenesisTx(RawTx rawTx, int blockHeight) {
        boolean isGenesis = blockHeight == stateService.getGenesisBlockHeight() &&
                rawTx.getId().equals(stateService.getGenesisTxId());
        if (isGenesis) {
            Tx tx = new Tx(rawTx);
            tx.setTxType(TxType.GENESIS);
            ParsingModel parsingModel = new ParsingModel(stateService.getGenesisTotalSupply().getValue());
            for (int i = 0; i < tx.getTxOutputs().size(); ++i) {
                genesisTxOutputValidator.validate(tx.getTxOutputs().get(i), parsingModel);
            }
            return Optional.of(tx);
        } else {
            return Optional.empty();
        }
    }
}
