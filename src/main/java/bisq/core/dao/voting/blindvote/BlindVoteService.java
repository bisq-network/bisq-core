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
import bisq.core.dao.state.ChainHeightListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.BallotList;
import bisq.core.dao.voting.ballot.BallotListService;
import bisq.core.dao.voting.ballot.proposal.ProposalValidator;
import bisq.core.dao.voting.blindvote.storage.appendonly.BlindVoteAppendOnlyPayload;
import bisq.core.dao.voting.blindvote.storage.appendonly.BlindVoteAppendOnlyStorageService;
import bisq.core.dao.voting.blindvote.storage.protectedstorage.BlindVotePayload;
import bisq.core.dao.voting.blindvote.storage.protectedstorage.BlindVoteStorageService;
import bisq.core.dao.voting.myvote.MyVoteListService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and published blindVote and the blindVote tx.
 */
@Slf4j
public class BlindVoteService implements ChainHeightListener, HashMapChangedListener, AppendOnlyDataStoreListener {
    private final StateService stateService;
    private final P2PService p2PService;
    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final BallotListService ballotListService;
    private final BlindVoteListService blindVoteListService;
    private final MyVoteListService myVoteListService;
    private PeriodService periodService;
    private final ProposalValidator proposalValidator;
    private BlindVoteValidator blindVoteValidator;
    private final PublicKey signaturePubKey;

    // BlindVotes we receive in the blind vote phase. They could be theoretically removed (we might add a feature to
    // remove a published blind vote in future)in that phase and that list must not be used for consensus critical code.
    @Getter
    private final List<BlindVote> preliminaryBlindVotes = new ArrayList<>();

    // BlindVotes which got added to the append-only data store at the beginning of the vote reveal phase.
    // They cannot be removed anymore. This list is used for consensus critical code.
    @Getter
    private final List<BlindVote> confirmedBlindVotes = new ArrayList<>();


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
                            PeriodService periodService,
                            BlindVoteAppendOnlyStorageService blindVoteAppendOnlyStorageService,
                            BlindVoteStorageService blindVoteStorageService,
                            AppendOnlyDataStoreService appendOnlyDataStoreService,
                            ProtectedDataStoreService protectedDataStoreService,
                            ProposalValidator proposalValidator,
                            BlindVoteValidator blindVoteValidator,
                            KeyRing keyRing) {
        this.stateService = stateService;
        this.p2PService = p2PService;
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.ballotListService = ballotListService;
        this.blindVoteListService = blindVoteListService;
        this.myVoteListService = myVoteListService;
        this.periodService = periodService;
        this.proposalValidator = proposalValidator;
        this.blindVoteValidator = blindVoteValidator;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();

        appendOnlyDataStoreService.addService(blindVoteAppendOnlyStorageService);
        protectedDataStoreService.addService(blindVoteStorageService);

        stateService.addChainHeightListener(this);
        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);
    }


  /*  @Override
    public void onChainHeightChanged(int blockHeight) {
        // When we enter the  blind vote phase we re-publish all proposals we have received from the p2p network to the
        // append only data store.
        // From now on we will access proposals only from that data store.
        if (periodService.getFirstBlockOfPhase(blockHeight, DaoPhase.Phase.BLIND_VOTE) == blockHeight) {
            ballotListService.getBallotList().stream()
                    .filter(ballot -> proposalValidator.isValidOrUnconfirmed(ballot.getProposal()))
                    .map(Ballot::getProposal)
                    .map(ProposalAppendOnlyPayload::new)
                    .forEach(proposalAppendOnlyPayload -> {
                        boolean success = p2PService.addPersistableNetworkPayload(proposalAppendOnlyPayload, true);
                        if (!success)
                            log.warn("addProposalsToAppendOnlyStore failed for proposal " + proposalAppendOnlyPayload.getProposal());
                    });
        }
    }*/

    ///////////////////////////////////////////////////////////////////////////////////////////
    // ChainHeightListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onChainHeightChanged(int blockHeight) {
        // When we enter the vote reveal phase we re-publish all blindVotes we have received from the p2p network to the
        // append only data store.
        // From now on we will access blindVotes only from that data store.
        if (periodService.getFirstBlockOfPhase(blockHeight, DaoPhase.Phase.VOTE_REVEAL) == blockHeight) {
            rePublishBlindVotesToAppendOnlyDataStore();

            // At republish we get set out local map synchronously so we can use that to fill our confirmed list.
            fillConfirmedBlindVotes();
        } else if (periodService.getFirstBlockOfPhase(blockHeight, DaoPhase.Phase.PROPOSAL) == blockHeight) {
            // Cycle has changed, we reset the lists.
            fillPreliminaryBlindVotes();
            fillConfirmedBlindVotes();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry entry) {
        onAddedProtectedData(entry);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry entry) {
        onRemovedProtectedData(entry);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        onAddedAppendOnlyData(payload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        fillPreliminaryBlindVotes();
        fillConfirmedBlindVotes();
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
            final VoteWithProposalTxIdList voteWithProposalTxIdList = getSortedVoteWithProposalTxIdList();
            log.info("voteWithProposalTxIdList used in blind vote. voteWithProposalTxIdList={}", voteWithProposalTxIdList);

            SecretKey secretKey = BlindVoteConsensus.getSecretKey();
            byte[] encryptedVotes = BlindVoteConsensus.getEncryptedVotes(voteWithProposalTxIdList, secretKey);

            final byte[] hash = BlindVoteConsensus.getHashOfEncryptedProposalList(encryptedVotes);
            log.info("Sha256Ripemd160 hash of encryptedVotes: " + Utilities.bytesAsHexString(hash));
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

            BlindVote blindVote = createAndStoreBlindVote(encryptedVotes, blindVoteTx, stake, secretKey);

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

    public VoteWithProposalTxIdList getSortedVoteWithProposalTxIdList() {
        final List<VoteWithProposalTxId> list = getSortedBallotList().stream()
                .map(ballot -> new VoteWithProposalTxId(ballot.getProposalTxId(), ballot.getVote()))
                .collect(Collectors.toList());
        return new VoteWithProposalTxIdList(list);
    }

    public BallotList getSortedBallotList() {
        final List<Ballot> list = ballotListService.getBallotList().stream()
                .filter(ballot -> proposalValidator.isValidOrUnconfirmed(ballot.getProposal()))
                .collect(Collectors.toList());
        BlindVoteConsensus.sortBallotList(list);
        return new BallotList(list);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static boolean containsBlindVote(BlindVote blindVote, List<BlindVote> blindVoteList) {
        return findBlindVoteInList(blindVote, blindVoteList).isPresent();
    }

    public static Optional<BlindVote> findBlindVoteInList(BlindVote blindVote, List<BlindVote> blindVoteList) {
        return blindVoteList.stream()
                .filter(vote -> vote.equals(blindVote))
                .findAny();
    }

    public static Optional<BlindVote> findBlindVote(String blindVoteTxId, BlindVoteList blindVoteList) {
        return blindVoteList.stream()
                .filter(blindVote -> blindVote.getTxId().equals(blindVoteTxId))
                .findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void fillPreliminaryBlindVotes() {
        preliminaryBlindVotes.clear();
        p2PService.getDataMap().values().forEach(this::onAddedProtectedData);
    }

    private void fillConfirmedBlindVotes() {
        confirmedBlindVotes.clear();
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(this::onAddedAppendOnlyData);
    }

    private void rePublishBlindVotesToAppendOnlyDataStore() {
        preliminaryBlindVotes.stream()
                .filter(blindVoteValidator::isValidAndConfirmed)
                .map(BlindVoteAppendOnlyPayload::new)
                .forEach(appendOnlyPayload -> {
                    boolean success = p2PService.addPersistableNetworkPayload(appendOnlyPayload, true);
                    if (!success)
                        log.warn("rePublishBlindVotesToAppendOnlyDataStore failed for blindVote " +
                                appendOnlyPayload.getBlindVote());
                });
    }

    private void onAddedProtectedData(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof BlindVotePayload) {
            final BlindVote blindVote = ((BlindVotePayload) protectedStoragePayload).getBlindVote();
            if (!containsBlindVote(blindVote, preliminaryBlindVotes)) {
                if (blindVoteValidator.isValid(blindVote, true)) {
                    log.info("We received a new blindVote from the P2P network. BlindVote.txId={}", blindVote.getTxId());
                    preliminaryBlindVotes.add(blindVote);
                } else {
                    log.warn("We received a invalid blindVote from the P2P network. BlindVote={}", blindVote);

                }
            } else {
                log.debug("We received a new blindVote from the P2P network but we have it already in out list. " +
                        "We ignore that blindVote. BlindVote.txId={}", blindVote.getTxId());
            }
        }
    }

    private void onRemovedProtectedData(ProtectedStorageEntry entry) {
        if (entry.getProtectedStoragePayload() instanceof BlindVotePayload) {
            String msg = "We do not support removal of blind votes. ProtectedStoragePayload=" +
                    entry.getProtectedStoragePayload();
            log.error(msg);
            throw new UnsupportedOperationException(msg);
        }
    }

    private void onAddedAppendOnlyData(PersistableNetworkPayload payload) {
        if (payload instanceof BlindVoteAppendOnlyPayload) {
            final BlindVote blindVote = ((BlindVoteAppendOnlyPayload) payload).getBlindVote();
            if (!containsBlindVote(blindVote, confirmedBlindVotes)) {
                if (blindVoteValidator.isValidAndConfirmed(blindVote)) {
                    log.info("We received a new append-only blindVote from the P2P network. BlindVote.txId={}",
                            blindVote.getTxId());
                    confirmedBlindVotes.add(blindVote);
                } else {
                    log.warn("We received a invalid append-only blindVote from the P2P network. BlindVote={}", blindVote);
                }
            } else {
                log.debug("We received a new append-only blindVote from the P2P network but we have it already in out list. " +
                        "We ignore that blindVote. BlindVote.txId={}", blindVote.getTxId());
            }
        }
    }


    private BlindVote createAndStoreBlindVote(byte[] encryptedVotes, Transaction blindVoteTx, Coin stake,
                                              SecretKey secretKey) {
        BlindVote blindVote = new BlindVote(encryptedVotes, blindVoteTx.getHashAsString(), stake.value);
        blindVoteListService.addMyBlindVote(blindVote);

        //TODO is it needed to maintain myVoteList extra?
        myVoteListService.createAndAddNewMyVote(getSortedBallotList(), secretKey, blindVote);
        return blindVote;
    }

    private boolean addToP2pNetwork(BlindVote blindVote) {
        BlindVotePayload blindVotePayload = new BlindVotePayload(blindVote, signaturePubKey);
        return p2PService.addProtectedStorageEntry(blindVotePayload, true);
    }
}
