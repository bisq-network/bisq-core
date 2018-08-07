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

package bisq.core.dao.governance.proposal.role;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.governance.proposal.ProposalWithTransaction;
import bisq.core.dao.governance.role.BondedRole;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.voting.ValidationException;
import bisq.core.dao.voting.proposal.BaseProposalService;
import bisq.core.dao.voting.proposal.ProposalWithTransaction;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates BondedRoleProposal and transaction.
 */
@Slf4j
public class BondedRoleProposalService extends BaseProposalService<BondedRoleProposal> {
    private final BondedRoleValidator bondedRoleValidator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BondedRoleProposalService(BsqWalletService bsqWalletService,
                                     BtcWalletService btcWalletService,
                                     BsqStateService bsqStateService,
                                     BondedRoleValidator bondedRoleValidator) {
        super(bsqWalletService,
                btcWalletService,
                bsqStateService);
        this.bondedRoleValidator = bondedRoleValidator;
    }

    public ProposalWithTransaction createProposalWithTransaction(BondedRole bondedRole)
            throws ValidationException, InsufficientMoneyException, TransactionVerificationException,
            WalletException {

        // As we don't know the txId we create a temp object with txId set to an empty string.
        BondedRoleProposal proposal = new BondedRoleProposal(bondedRole);
        validate(proposal);

        Transaction transaction = getTransaction(proposal);

        final BondedRoleProposal proposalWithTxId = getProposalWithTxId(proposal, transaction.getHashAsString());
        return new ProposalWithTransaction(proposalWithTxId, transaction);
    }

    private void validate(BondedRoleProposal proposal) throws ValidationException {
        bondedRoleValidator.validateDataFields(proposal);
    }
}
