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

package bisq.core.dao.voting.proposal.param;

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

/**
 * Creates ParamProposal and transaction.
 */
@Slf4j
public class ParamProposalService {
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final StateService stateService;
    private final ParamValidator paramValidator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ParamProposalService(BsqWalletService bsqWalletService,
                                BtcWalletService btcWalletService,
                                StateService stateService,
                                ParamValidator paramValidator) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.stateService = stateService;
        this.paramValidator = paramValidator;
    }

    public ProposalWithTransaction createProposalWithTransaction(String name,
                                                                 String title,
                                                                 String description,
                                                                 String link,
                                                                 Param param,
                                                                 long paramValue)
            throws ValidationException, InsufficientMoneyException, IOException, TransactionVerificationException,
            WalletException {

        // As we don't know the txId we create a temp object with txId set to an empty string.
        ParamProposal proposal = new ParamProposal(
                name,
                title,
                description,
                link,
                param,
                paramValue);
        validate(proposal);

        Transaction transaction = getTransaction(proposal);

        final ParamProposal proposalWithTxId = getProposalWithTxId(proposal, transaction.getHashAsString());
        return new ProposalWithTransaction(proposalWithTxId, transaction);
    }

    // We have txId set to null in proposal as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    // The hashOfPayload used in the opReturnData is created with the txId set to null.
    private Transaction getTransaction(ParamProposal proposal)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException, IOException {

        final Coin fee = ProposalConsensus.getFee(stateService, stateService.getChainHeight());
        final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTx(fee);

        // payload does not have txId at that moment
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(proposal);
        byte[] opReturnData = ParamConsensus.getOpReturnData(hashOfPayload);

        final Transaction txWithBtcFee = btcWalletService.completePreparedGenericProposalTx(preparedBurnFeeTx,
                opReturnData);

        final Transaction transaction = bsqWalletService.signTx(txWithBtcFee);
        log.info("ParamProposal tx: " + transaction);
        return transaction;
    }

    private void validate(ParamProposal proposal) throws ValidationException {
        paramValidator.validateDataFields(proposal);
    }

    private ParamProposal getProposalWithTxId(ParamProposal proposal, String txId) {
        return (ParamProposal) proposal.cloneWithTxId(txId);
    }
}
