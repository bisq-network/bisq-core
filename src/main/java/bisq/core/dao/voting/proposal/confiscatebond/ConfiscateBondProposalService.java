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

package bisq.core.dao.voting.proposal.confiscatebond;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.voting.ValidationException;
import bisq.core.dao.voting.proposal.ProposalConsensus;
import bisq.core.dao.voting.proposal.ProposalWithTransaction;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates ConfiscateBondProposal and transaction.
 */
@Slf4j
public class ConfiscateBondProposalService {
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final BsqStateService bsqStateService;
    private final ConfiscateBondValidator confiscateBondValidator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ConfiscateBondProposalService(BsqWalletService bsqWalletService,
                                         BtcWalletService btcWalletService,
                                         BsqStateService bsqStateService,
                                         ConfiscateBondValidator confiscateBondValidator) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.bsqStateService = bsqStateService;
        this.confiscateBondValidator = confiscateBondValidator;
    }

    public ProposalWithTransaction createProposalWithTransaction(String name,
                                                                 String title,
                                                                 String description,
                                                                 String link,
                                                                 byte[] bondId)
            throws ValidationException, InsufficientMoneyException, IOException, TransactionVerificationException,
            WalletException {

        // As we don't know the txId we create a temp object with txId set to an empty string.
        ConfiscateBondProposal proposal = new ConfiscateBondProposal(
                name,
                title,
                description,
                link,
                bondId);
        validate(proposal);

        Transaction transaction = getTransaction(proposal);

        final ConfiscateBondProposal proposalWithTxId = getProposalWithTxId(proposal, transaction.getHashAsString());
        return new ProposalWithTransaction(proposalWithTxId, transaction);
    }

    // We have txId set to null in proposal as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    // The hashOfPayload used in the opReturnData is created with the txId set to null.
    private Transaction getTransaction(ConfiscateBondProposal proposal)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException, IOException {

        final Coin fee = ProposalConsensus.getFee(bsqStateService, bsqStateService.getChainHeight());
        final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTx(fee);

        // payload does not have txId at that moment
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(proposal);
        byte[] opReturnData = ConfiscateBondConsensus.getOpReturnData(hashOfPayload);

        final Transaction txWithBtcFee = btcWalletService.completePreparedGenericProposalTx(preparedBurnFeeTx,
                opReturnData);

        final Transaction transaction = bsqWalletService.signTx(txWithBtcFee);
        log.info("ConfiscateBondProposal tx: " + transaction);
        return transaction;
    }

    private void validate(ConfiscateBondProposal proposal) throws ValidationException {
        confiscateBondValidator.validateDataFields(proposal);
    }

    private ConfiscateBondProposal getProposalWithTxId(ConfiscateBondProposal proposal, String txId) {
        return (ConfiscateBondProposal) proposal.cloneWithTxId(txId);
    }
}
