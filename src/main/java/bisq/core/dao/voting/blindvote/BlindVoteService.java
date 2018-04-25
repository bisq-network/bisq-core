/*
 * This file is part of bisq.
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

package bisq.core.dao.voting.blindvote;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcastTimeoutException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.TxMalleabilityException;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.StateService;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.BallotList;
import bisq.core.dao.voting.ballot.BallotListService;
import bisq.core.dao.voting.myvote.MyVoteListService;
import bisq.core.dao.voting.ballot.proposal.ProposalValidator;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ExceptionHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import javax.crypto.SecretKey;

import java.security.PublicKey;

import java.io.IOException;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates and published blindVote and the blindVote tx.
 */
@Slf4j
public class BlindVoteService {
    private final StateService stateService;
    private final P2PService p2PService;
    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final BallotListService ballotListService;
    private final BlindVoteListService blindVoteListService;
    private final MyVoteListService myVoteListService;
    private final ProposalValidator proposalValidator;
    private final PublicKey signaturePubKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteService(StateService stateService,
                            P2PService p2PService,
                            WalletsManager walletsManager,
                            BsqWalletService bsqWalletService,
                            BtcWalletService btcWalletService,
                            BallotListService ballotListService,
                            BlindVoteListService blindVoteListService,
                            MyVoteListService myVoteListService,
                            ProposalValidator proposalValidator,
                            KeyRing keyRing) {
        this.stateService = stateService;
        this.p2PService = p2PService;
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.ballotListService = ballotListService;
        this.blindVoteListService = blindVoteListService;
        this.myVoteListService = myVoteListService;
        this.proposalValidator = proposalValidator;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
    }

    // For showing fee estimation in confirmation popup
    public Transaction getDummyBlindVoteTx(Coin stake, Coin fee)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        // We set dummy opReturn data
        return getBlindVoteTx(stake, fee, new byte[22]);
    }

    private Transaction getBlindVoteTx(Coin stake, Coin fee, byte[] opReturnData)
            throws InsufficientMoneyException, WalletException, TransactionVerificationException {
        Transaction preparedTx = bsqWalletService.getPreparedBlindVoteTx(fee, stake);
        Transaction txWithBtcFee = btcWalletService.completePreparedBlindVoteTx(preparedTx, opReturnData);
        return bsqWalletService.signTx(txWithBtcFee);
    }

    public void publishBlindVote(Coin stake, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        try {
            final BallotList sortedBallotList = getSortedBallotList();
            log.info("BallotList used in blind vote. sortedBallotList={}", sortedBallotList);

            SecretKey secretKey = BlindVoteConsensus.getSecretKey();
            byte[] encryptedBallotList = BlindVoteConsensus.getEncryptedBallotList(sortedBallotList, secretKey);

            final byte[] hash = BlindVoteConsensus.getHashOfEncryptedProposalList(encryptedBallotList);
            log.info("Sha256Ripemd160 hash of encryptedBallotList: " + Utilities.bytesAsHexString(hash));
            byte[] opReturnData = BlindVoteConsensus.getOpReturnData(hash);

            final Transaction blindVoteTx = getBlindVoteTx(stake, getBlindVoteFee(), opReturnData);
            log.info("blindVoteTx={}", blindVoteTx);
            walletsManager.publishAndCommitBsqTx(blindVoteTx, new TxBroadcaster.Callback() {
                @Override
                public void onSuccess(Transaction transaction) {
                    resultHandler.handleResult();
                }

                @Override
                public void onTimeout(TxBroadcastTimeoutException exception) {
                    // TODO handle
                    // We need to handle cases where a timeout happens and
                    // the tx might get broadcasted at a later restart!
                    // We need to be sure that in case of a failed tx the locked stake gets unlocked!
                    exceptionHandler.handleException(exception);
                }

                @Override
                public void onTxMalleability(TxMalleabilityException exception) {
                    // TODO handle
                    // We need to be sure that in case of a failed tx the locked stake gets unlocked!
                    exceptionHandler.handleException(exception);
                }

                @Override
                public void onFailure(TxBroadcastException exception) {
                    // TODO handle
                    // We need to be sure that in case of a failed tx the locked stake gets unlocked!
                    exceptionHandler.handleException(exception);
                }
            });

            // We prefer to not wait for the tx broadcast as if the tx broadcast would fail we still prefer to have our
            // blind vote stored and broadcasted to the p2p network. The tx might get re-broadcasted at a restart and
            // in worst case if it does not succeed the blind vote will be ignored anyway.
            // Inconsistently propagated blind votes in the p2p network could have potentially worse effects.

            BlindVote blindVote = createAndStoreBlindVote(encryptedBallotList, blindVoteTx, stake, secretKey);

            boolean success = addToP2pNetwork(blindVote);
            if (success) {
                log.info("Added blindVotePayload to P2P network.\nblindVote={}", blindVote);
            } else {
                final String msg = "Adding of blindVote to P2P network failed.\nblindVote=" + blindVote;
                log.error(msg);
                //TODO define specific exception
                exceptionHandler.handleException(new Exception(msg));
            }
        } catch (CryptoException | TransactionVerificationException | InsufficientMoneyException |
                WalletException | IOException exception) {
            exceptionHandler.handleException(exception);
        }
    }

    public Coin getBlindVoteFee() {
        return BlindVoteConsensus.getFee(stateService, stateService.getChainHeight());
    }

    public BallotList getSortedBallotList() {
        final List<Ballot> ballots = ballotListService.getBallotList().stream()
                .filter(ballot -> proposalValidator.isValid(ballot.getProposal()))
                .collect(Collectors.toList());
        BlindVoteConsensus.sortProposalList(ballots);
        return new BallotList(ballots);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BlindVote createAndStoreBlindVote(byte[] encryptedBallotList, Transaction blindVoteTx, Coin stake,
                                              SecretKey secretKey) {
        BlindVote blindVote = new BlindVote(encryptedBallotList, blindVoteTx.getHashAsString(), stake.value);
        blindVoteListService.addMyBlindVote(blindVote);
        myVoteListService.createAndAddNewMyVote(getSortedBallotList(), secretKey, blindVote);
        return blindVote;
    }

    private boolean addToP2pNetwork(BlindVote blindVote) {
        BlindVotePayload blindVotePayload = new BlindVotePayload(blindVote, signaturePubKey);
        return p2PService.addProtectedStorageEntry(blindVotePayload, true);
    }
}
