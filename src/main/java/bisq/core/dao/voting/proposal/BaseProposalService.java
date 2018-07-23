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

package bisq.core.dao.voting.proposal;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.state.BsqStateService;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates BondedRoleProposal and transaction.
 */
@Slf4j
public class BaseProposalService<R extends Proposal> {
    protected final BsqWalletService bsqWalletService;
    protected final BtcWalletService btcWalletService;
    protected final BsqStateService bsqStateService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BaseProposalService(BsqWalletService bsqWalletService,
                               BtcWalletService btcWalletService,
                               BsqStateService bsqStateService) {
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.bsqStateService = bsqStateService;
    }

    // We have txId set to null in proposal as we cannot know it before the tx is created.
    // Once the tx is known we will create a new object including the txId.
    // The hashOfPayload used in the opReturnData is created with the txId set to null.
    protected Transaction getTransaction(R proposal)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException {

        final Coin fee = ProposalConsensus.getFee(bsqStateService, bsqStateService.getChainHeight());
        final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedProposalTx(fee);

        // payload does not have txId at that moment
        byte[] hashOfPayload = ProposalConsensus.getHashOfPayload(proposal);
        byte[] opReturnData = ProposalConsensus.getOpReturnData(hashOfPayload);

        final Transaction txWithBtcFee = btcWalletService.completePreparedProposalTx(preparedBurnFeeTx,
                opReturnData);

        final Transaction transaction = bsqWalletService.signTx(txWithBtcFee);
        log.info("Proposal tx: " + transaction);
        return transaction;
    }

    protected R getProposalWithTxId(R proposal, String txId) {
        return (R) proposal.cloneWithTxId(txId);
    }
}
