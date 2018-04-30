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

package bisq.core.dao.voting.proposal.compensation;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.state.StateService;
import bisq.core.dao.voting.ValidationException;
import bisq.core.dao.voting.proposal.ProposalConsensus;
import bisq.core.dao.voting.proposal.ProposalWithTransaction;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompensationProposalService {
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final StateService stateService;
    private final CompensationValidator compensationValidator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CompensationProposalService(BsqWalletService bsqWalletService,
                                       BtcWalletService btcWalletService,
                                       StateService stateService,
                                       CompensationValidator compensationValidator) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.stateService = stateService;
        this.compensationValidator = compensationValidator;
    }

    public ProposalWithTransaction createProposalWithTransaction(String name,
                                                                 String title,
                                                                 String description,
                                                                 String link,
                                                                 Coin requestedBsq,
                                                                 String bsqAddress)
            throws ValidationException, InsufficientMoneyException, IOException, TransactionVerificationException,
            WalletException {

        // As we don't know the txId we create a temp object with txId set to an empty string.
        CompensationProposal proposal = new CompensationProposal(
                name,
                title,
                description,
                link,
                requestedBsq,
                bsqAddress);
        validate(proposal);

        Transaction transaction = getTransaction(proposal);

        final CompensationProposal proposalWithTxId = getProposalWithTxId(proposal, transaction);
        return new ProposalWithTransaction(proposalWithTxId, transaction);
    }

    // We have txId set to null in proposal as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    // The hashOfPayload used in the opReturnData is created with the txId set to null.
    private Transaction getTransaction(CompensationProposal proposal)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException, IOException {

        final Coin fee = ProposalConsensus.getFee(stateService, stateService.getChainHeight());
        final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTx(fee);

        // payload does not have txId at that moment
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(proposal);
        byte[] opReturnData = CompensationConsensus.getOpReturnData(hashOfPayload);

        final Transaction txWithBtcFee = btcWalletService.completePreparedCompensationRequestTx(
                proposal.getRequestedBsq(),
                proposal.getAddress(),
                preparedBurnFeeTx,
                opReturnData);

        final Transaction transaction = bsqWalletService.signTx(txWithBtcFee);
        log.info("CompensationProposal tx: " + transaction);
        return transaction;
    }

    private void validate(CompensationProposal proposal) throws ValidationException {
        compensationValidator.validateDataFields(proposal);
    }

    private CompensationProposal getProposalWithTxId(CompensationProposal proposal, Transaction transaction) {
        final String txId = transaction.getHashAsString();
        return (CompensationProposal) proposal.cloneWithTxId(txId);
    }
}
