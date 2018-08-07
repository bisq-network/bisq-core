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

package bisq.core.dao.governance.proposal.param;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.governance.ValidationException;
import bisq.core.dao.governance.proposal.BaseProposalService;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.ext.Param;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates ChangeParamProposal and transaction.
 */
@Slf4j
public class ChangeParamProposalService extends BaseProposalService<ChangeParamProposal> {
    private final ChangeParamValidator changeParamValidator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ChangeParamProposalService(BsqWalletService bsqWalletService,
                                      BtcWalletService btcWalletService,
                                      BsqStateService bsqStateService,
                                      ChangeParamValidator changeParamValidator) {
        super(bsqWalletService,
                btcWalletService,
                bsqStateService);
        this.changeParamValidator = changeParamValidator;
    }

    public ProposalWithTransaction createProposalWithTransaction(String name,
                                                                 String link,
                                                                 Param param,
                                                                 long paramValue)
            throws ValidationException, InsufficientMoneyException, TransactionVerificationException,
            WalletException {

        // As we don't know the txId we create a temp object with txId set to an empty string.
        ChangeParamProposal proposal = new ChangeParamProposal(
                name,
                link,
                param,
                paramValue);
        validate(proposal);

        Transaction transaction = getTransaction(proposal);

        final ChangeParamProposal proposalWithTxId = getProposalWithTxId(proposal, transaction.getHashAsString());
        return new ProposalWithTransaction(proposalWithTxId, transaction);
    }

    private void validate(ChangeParamProposal proposal) throws ValidationException {
        changeParamValidator.validateDataFields(proposal);
    }
}
