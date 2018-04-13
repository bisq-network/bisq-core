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

package bisq.core.dao.vote.proposal.compensation;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.state.StateService;
import bisq.core.dao.vote.proposal.Proposal;
import bisq.core.dao.vote.proposal.ProposalConsensus;
import bisq.core.dao.vote.proposal.ValidationException;
import bisq.core.dao.vote.proposal.param.ParamService;

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
    private final ParamService paramService;
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
                                      ParamService paramService,
                                      CompensationRequestPayloadValidator compensationRequestPayloadValidator,
                                      KeyRing keyRing) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.stateService = stateService;
        this.paramService = paramService;
        this.compensationRequestPayloadValidator = compensationRequestPayloadValidator;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }

    public Tuple2<Proposal, Transaction> makeTxAndGetCompensationRequest(String name,
                                                                         String title,
                                                                         String description,
                                                                         String link,
                                                                         Coin requestedBsq,
                                                                         String bsqAddress)
            throws ValidationException, InsufficientMoneyException, IOException, TransactionVerificationException,
            WalletException {

        // As we don't know the txId we create a temp object with TxId set to null.
        final CompensationRequestPayload tempPayload = new CompensationRequestPayload(
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
    private Transaction createCompensationRequestTx(CompensationRequestPayload tempPayload)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException, IOException {

        final Coin fee = ProposalConsensus.getFee(paramService, stateService.getChainHeadHeight());
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
        log.info("CompensationRequest tx: " + completedTx);
        return completedTx;
    }

    // We have txId set to null in tempPayload as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    private CompensationRequest createCompensationRequest(CompensationRequestPayload tempPayload, Transaction transaction) {
        final String txId = transaction.getHashAsString();
        CompensationRequestPayload compensationRequestPayload = (CompensationRequestPayload) tempPayload.cloneWithTxId(txId);
        return new CompensationRequest(compensationRequestPayload);
    }
}
