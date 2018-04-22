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

package bisq.core.dao.consensus.ballot;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.ValidationException;
import bisq.core.dao.consensus.ballot.Ballot;
import bisq.core.dao.consensus.ballot.compensation.CompensationBallot;
import bisq.core.dao.consensus.proposal.ProposalConsensus;
import bisq.core.dao.consensus.proposal.compensation.CompensationConsensus;
import bisq.core.dao.consensus.proposal.compensation.CompensationProposal;
import bisq.core.dao.consensus.proposal.compensation.CompensationValidator;
import bisq.core.dao.consensus.proposal.param.ChangeParamService;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.presentation.PresentationService;

import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompensationBallotFactory implements PresentationService{
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final ChangeParamService changeParamService;
    private final CompensationValidator compensationValidator;
    private final StateService stateService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CompensationBallotFactory(BsqWalletService bsqWalletService,
                                     BtcWalletService btcWalletService,
                                     StateService stateService,
                                     ChangeParamService changeParamService,
                                     CompensationValidator compensationValidator) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.stateService = stateService;
        this.changeParamService = changeParamService;
        this.compensationValidator = compensationValidator;
    }

    public Tuple2<Ballot, Transaction> getTuple(String name,
                                                String title,
                                                String description,
                                                String link,
                                                Coin requestedBsq,
                                                String bsqAddress)
            throws ValidationException, InsufficientMoneyException, IOException, TransactionVerificationException,
            WalletException {

        // As we don't know the txId we create a temp object with TxId set to an empty string.
        final CompensationProposal proposal = new CompensationProposal(
                name,
                title,
                description,
                link,
                requestedBsq,
                bsqAddress
        );
        validate(proposal);

        Transaction transaction = getTransaction(proposal);

        return new Tuple2<>(getCompensationBallot(proposal, transaction), transaction);
    }

    private void validate(CompensationProposal proposal) throws ValidationException {
        compensationValidator.validateDataFields(proposal);
    }

    // We have txId set to null in tempPayload as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    // The hashOfPayload used in the opReturnData is created with the txId set to null.
    private Transaction getTransaction(CompensationProposal tempPayload)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException, IOException {

        final Coin fee = ProposalConsensus.getFee(changeParamService, stateService.getChainHeight());
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
