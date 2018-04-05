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

package bisq.core.dao.vote.proposal.generic;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.param.DaoParamService;
import bisq.core.dao.vote.proposal.ProposalConsensus;

import bisq.common.crypto.KeyRing;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import java.security.PublicKey;

import java.util.Date;
import java.util.UUID;

public class GenericProposalService {
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final DaoParamService daoParamService;
    private final PublicKey signaturePubKey;
    private final ReadableBsqBlockChain readableBsqBlockChain;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public GenericProposalService(BsqWalletService bsqWalletService,
                                  BtcWalletService btcWalletService,
                                  ReadableBsqBlockChain readableBsqBlockChain,
                                  DaoParamService daoParamService,
                                  KeyRing keyRing) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.daoParamService = daoParamService;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }

    public GenericProposalPayload createGenericProposalPayload(String name,
                                                               String title,
                                                               String description,
                                                               String link) {
        return new GenericProposalPayload(
                UUID.randomUUID().toString(),
                name,
                title,
                description,
                link,
                signaturePubKey,
                new Date()
        );
    }

    public GenericProposal createGenericProposal(GenericProposalPayload payload)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException {
        GenericProposal proposal = new GenericProposal(payload);

        final Coin fee = ProposalConsensus.getFee(daoParamService, readableBsqBlockChain);
        final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTx(fee);

        // payload does not have tx ID at that moment
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(payload);
        byte[] opReturnData = ProposalConsensus.getOpReturnData(hashOfPayload);

        final Transaction txWithBtcFee = btcWalletService.completePreparedGenericProposalTx(
                preparedBurnFeeTx,
                opReturnData);

        final Transaction completedTx = bsqWalletService.signTx(txWithBtcFee);

        // We need the tx for showing the user tx details before publishing (fee, size).
        proposal.setTx(completedTx);

        return proposal;
    }
}
