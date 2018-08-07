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

package bisq.core.dao.governance.proposal.confiscatebond;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.governance.ValidationException;
import bisq.core.dao.governance.proposal.BaseProposalService;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.state.BsqStateService;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates ConfiscateBondProposal and transaction.
 */
@Slf4j
public class ConfiscateBondProposalService extends BaseProposalService<ConfiscateBondProposal> {
    private final ConfiscateBondValidator confiscateBondValidator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ConfiscateBondProposalService(BsqWalletService bsqWalletService,
                                         BtcWalletService btcWalletService,
                                         BsqStateService bsqStateService,
                                         ConfiscateBondValidator confiscateBondValidator) {
        super(bsqWalletService,
                btcWalletService,
                bsqStateService);
        this.confiscateBondValidator = confiscateBondValidator;
    }

    public ProposalWithTransaction createProposalWithTransaction(String name,
                                                                 String link,
                                                                 byte[] hash)
            throws ValidationException, InsufficientMoneyException, TransactionVerificationException,
            WalletException {

        // As we don't know the txId we create a temp object with txId set to an empty string.
        ConfiscateBondProposal proposal = new ConfiscateBondProposal(
                name,
                link,
                hash);
        validate(proposal);

        Transaction transaction = createTransaction(proposal);

        final ConfiscateBondProposal proposalWithTxId = cloneProposalAndAddTxId(proposal, transaction.getHashAsString());
        return new ProposalWithTransaction(proposalWithTxId, transaction);
    }

    private void validate(ConfiscateBondProposal proposal) throws ValidationException {
        confiscateBondValidator.validateDataFields(proposal);
    }
}
