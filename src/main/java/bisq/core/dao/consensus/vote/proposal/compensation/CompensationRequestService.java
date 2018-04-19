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

package bisq.core.dao.consensus.vote.proposal.compensation;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.vote.proposal.Ballot;
import bisq.core.dao.consensus.vote.proposal.ProposalConsensus;
import bisq.core.dao.consensus.vote.proposal.ValidationException;
import bisq.core.dao.consensus.vote.proposal.param.ChangeParamService;

import bisq.common.crypto.KeyRing;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.security.PublicKey;

import java.io.IOException;

import java.util.Date;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompensationRequestService {
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final ChangeParamService changeParamService;
    private final CompensationRequestPayloadValidator compensationRequestPayloadValidator;
    private final PublicKey signaturePubKey;
    private final StateService stateService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CompensationRequestService(BsqWalletService bsqWalletService,
                                      BtcWalletService btcWalletService,
                                      StateService stateService,
                                      ChangeParamService changeParamService,
                                      CompensationRequestPayloadValidator compensationRequestPayloadValidator,
                                      KeyRing keyRing) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.stateService = stateService;
        this.changeParamService = changeParamService;
        this.compensationRequestPayloadValidator = compensationRequestPayloadValidator;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }

    public Tuple2<Ballot, Transaction> makeTxAndGetCompensationRequest(String name,
                                                                       String title,
                                                                       String description,
                                                                       String link,
                                                                       Coin requestedBsq,
                                                                       String bsqAddress)
            throws ValidationException, InsufficientMoneyException, IOException, TransactionVerificationException,
            WalletException {

        // As we don't know the txId we create a temp object with TxId set to null.
        final CompensationRequestProposal tempPayload = new CompensationRequestProposal(
                UUID.randomUUID().toString(),
                name,
                title,
                description,
                link,
                requestedBsq,
                bsqAddress,
                signaturePubKey,
                new Date()
        );
        compensationRequestPayloadValidator.validateDataFields(tempPayload);

        Transaction transaction = createCompensationRequestTx(tempPayload);

        return new Tuple2<>(createCompensationRequest(tempPayload, transaction), transaction);
    }

    // We have txId set to null in tempPayload as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    // The hashOfPayload used in the opReturnData is created with the txId set to null.
    private Transaction createCompensationRequestTx(CompensationRequestProposal tempPayload)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException, IOException {

        final Coin fee = ProposalConsensus.getFee(changeParamService, stateService.getChainHeight());
        final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTx(fee);

        // payload does not have txId at that moment
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(tempPayload);
        byte[] opReturnData = CompensationRequestConsensus.getOpReturnData(hashOfPayload);

        final Transaction txWithBtcFee = btcWalletService.completePreparedCompensationRequestTx(
                tempPayload.getRequestedBsq(),
                tempPayload.getAddress(),
                preparedBurnFeeTx,
                opReturnData);

        final Transaction completedTx = bsqWalletService.signTx(txWithBtcFee);
        log.info("CompensationRequestBallot tx: " + completedTx);
        return completedTx;
    }

    // We have txId set to null in tempPayload as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    private CompensationRequestBallot createCompensationRequest(CompensationRequestProposal tempPayload, Transaction transaction) {
        final String txId = transaction.getHashAsString();
        CompensationRequestProposal compensationRequestProposal = (CompensationRequestProposal) tempPayload.cloneWithTxId(txId);
        return new CompensationRequestBallot(compensationRequestProposal);
    }
}
