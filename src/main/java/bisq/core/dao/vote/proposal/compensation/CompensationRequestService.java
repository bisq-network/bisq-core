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
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.vote.proposal.ProposalConsensus;
import bisq.core.dao.vote.proposal.ValidationException;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.security.PublicKey;

import java.io.IOException;

import java.util.Date;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

public class CompensationRequestService {
    private final P2PService p2PService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final PublicKey signaturePubKey;
    private final ReadableBsqBlockChain readableBsqBlockChain;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CompensationRequestService(P2PService p2PService,
                                      BsqWalletService bsqWalletService,
                                      BtcWalletService btcWalletService,
                                      ReadableBsqBlockChain readableBsqBlockChain,
                                      KeyRing keyRing) {
        this.p2PService = p2PService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.readableBsqBlockChain = readableBsqBlockChain;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }

    public CompensationRequestPayload getCompensationRequestPayload(String name,
                                                                    String title,
                                                                    String description,
                                                                    String link,
                                                                    Coin requestedBsq,
                                                                    String bsqAddress) throws ValidationException {
        final CompensationRequestPayload payload = new CompensationRequestPayload(
                UUID.randomUUID().toString(),
                name,
                title,
                description,
                link,
                requestedBsq,
                bsqAddress,
                checkNotNull(p2PService.getAddress()),
                signaturePubKey,
                new Date()
        );

        payload.validate();

        return payload;
    }


    public CompensationRequest getCompensationRequest(CompensationRequestPayload payload)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException, IOException {
        CompensationRequest compensationRequest = new CompensationRequest(payload);

        final Coin fee = ProposalConsensus.getFee(readableBsqBlockChain);
        final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTx(fee);

        // payload does not have tx ID at that moment
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(payload);
        byte[] opReturnData = CompensationRequestConsensus.getOpReturnData(hashOfPayload);

        final Transaction txWithBtcFee = btcWalletService.completePreparedCompensationRequestTx(
                compensationRequest.getRequestedBsq(),
                compensationRequest.getAddress(),
                preparedBurnFeeTx,
                opReturnData);

        final Transaction completedTx = bsqWalletService.signTx(txWithBtcFee);

        // We need the tx for showing the user tx details before publishing (fee, size).
        compensationRequest.setTx(completedTx);

        return compensationRequest;
    }
}
