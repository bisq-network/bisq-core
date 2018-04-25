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

package bisq.core.dao.voting.ballot.compensation;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.state.StateService;
import bisq.core.dao.voting.ValidationException;
import bisq.core.dao.voting.ballot.BallotWithTransaction;
import bisq.core.dao.voting.ballot.proposal.ProposalConsensus;
import bisq.core.dao.voting.ballot.proposal.compensation.CompensationConsensus;
import bisq.core.dao.voting.ballot.proposal.compensation.CompensationProposal;
import bisq.core.dao.voting.ballot.proposal.compensation.CompensationValidator;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompensationBallotService {
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final StateService stateService;
    private final CompensationValidator compensationValidator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CompensationBallotService(BsqWalletService bsqWalletService,
                                     BtcWalletService btcWalletService,
                                     StateService stateService,
                                     CompensationValidator compensationValidator) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.stateService = stateService;
        this.compensationValidator = compensationValidator;
    }

    public BallotWithTransaction createBallotWithTransaction(String name,
                                                             String title,
                                                             String description,
                                                             String link,
                                                             Coin requestedBsq,
                                                             String bsqAddress)
            throws ValidationException, InsufficientMoneyException, IOException, TransactionVerificationException,
            WalletException {

        // As we don't know the txId we create a temp object with TxId set to an empty string.
        CompensationProposal proposal = new CompensationProposal(
                name,
                title,
                description,
                link,
                requestedBsq,
                bsqAddress
        );
        validate(proposal);

        Transaction transaction = getTransaction(proposal);
        CompensationBallot compensationBallot = getCompensationBallot(proposal, transaction);
        return new BallotWithTransaction(compensationBallot, transaction);
    }

    private void validate(CompensationProposal proposal) throws ValidationException {
        compensationValidator.validateDataFields(proposal);
    }

    // We have txId set to null in tempPayload as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    // The hashOfPayload used in the opReturnData is created with the txId set to null.
    private Transaction getTransaction(CompensationProposal tempPayload)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException, IOException {

        final Coin fee = ProposalConsensus.getFee(stateService, stateService.getChainHeight());
        final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTx(fee);

        // payload does not have txId at that moment
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(tempPayload);
        byte[] opReturnData = CompensationConsensus.getOpReturnData(hashOfPayload);

        final Transaction txWithBtcFee = btcWalletService.completePreparedCompensationRequestTx(
                tempPayload.getRequestedBsq(),
                tempPayload.getAddress(),
                preparedBurnFeeTx,
                opReturnData);

        final Transaction completedTx = bsqWalletService.signTx(txWithBtcFee);
        log.info("CompensationBallot tx: " + completedTx);
        return completedTx;
    }

    // We have txId set to null in proposal as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    private CompensationBallot getCompensationBallot(CompensationProposal proposal, Transaction transaction) {
        final String txId = transaction.getHashAsString();
        CompensationProposal compensationProposal = (CompensationProposal) proposal.cloneWithTxId(txId);
        return new CompensationBallot(compensationProposal);
    }
}
