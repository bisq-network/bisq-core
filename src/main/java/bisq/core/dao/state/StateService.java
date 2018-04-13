/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.state;

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.state.blockchain.vo.BsqBlock;
import bisq.core.dao.state.blockchain.vo.SpentInfo;
import bisq.core.dao.state.blockchain.vo.Tx;
import bisq.core.dao.state.blockchain.vo.TxInput;
import bisq.core.dao.state.blockchain.vo.TxOutput;
import bisq.core.dao.state.blockchain.vo.TxOutputType;
import bisq.core.dao.state.blockchain.vo.TxType;
import bisq.core.dao.state.events.StateChangeEvent;
import bisq.core.dao.vote.proposal.ProposalPayload;

import bisq.common.ThreadContextAwareListener;
import bisq.common.util.FunctionalReadWriteLock;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;


/**
 * Mutual state of the BSQ blockchain data.
 * <p>
 * We only have one thread which is writing data from the lite node or full node executors.
 * We use ReentrantReadWriteLock in a functional style.
 * <p>
 * We limit the access to StateService over interfaces for read (StateService) and
 * write (StateService) to have better overview and control about access.
 * <p>
 * TODO consider refactoring to move data access to a StateService class.
 */
@Slf4j
public class StateService {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final int ISSUANCE_MATURITY = 144 * 30; // 30 days
    private static final Coin GENESIS_TOTAL_SUPPLY = Coin.parseCoin("2.5");

    //mainnet
    // this tx has a lot of outputs
    // https://blockchain.info/de/tx/ee921650ab3f978881b8fe291e0c025e0da2b7dc684003d7a03d9649dfee2e15
    // BLOCK_HEIGHT 411779
    // 411812 has 693 recursions
    // block 376078 has 2843 recursions and caused once a StackOverflowError, a second run worked. Took 1,2 sec.

    // BTC MAIN NET
    public static final String BTC_GENESIS_TX_ID = "e5c8313c4144d219b5f6b2dacf1d36f2d43a9039bb2fcd1bd57f8352a9c9809a";
    public static final int BTC_GENESIS_BLOCK_HEIGHT = 477865; // 2017-07-28


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////


    public interface Listener extends ThreadContextAwareListener {
        void onBlockAdded(BsqBlock bsqBlock);

        default void onStateChange(StateChangeEvent stateChangeEvent) {
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final State state;

    private final String genesisTxId;
    private final int genesisBlockHeight;

    private final List<Listener> listeners = new ArrayList<>();

    transient private final FunctionalReadWriteLock lock;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public StateService(State state,
                        @Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                        @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this.state = state;
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;

        lock = new FunctionalReadWriteLock(true);

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addListener(Listener listener) {
        lock.write(() -> listeners.add(listener));
    }

    public void removeListener(Listener listener) {
        lock.write(() -> listeners.remove(listener));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: StateService
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot(State snapshot) {
        lock.write(() -> {
            state.getBsqBlocks().clear();
            state.getBsqBlocks().addAll(snapshot.getBsqBlocks());

            state.getUnspentTxOutputMap().clear();
            state.getUnspentTxOutputMap().putAll(snapshot.getUnspentTxOutputMap());

            //TODO
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: StateChangeEvent
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addStateChangeEvent(StateChangeEvent stateChangeEvent) {
        lock.write(() -> {
            if (!stateChangeEventWithPayloadExists(stateChangeEvent)) {
                state.getStateChangeEvents().add(stateChangeEvent);
            }
        });
    }

    public boolean stateChangeEventWithPayloadExists(StateChangeEvent stateChangeEvent) {
        return lock.read(() -> state.getStateChangeEvents().stream()
                .anyMatch(event -> event.getPayload().equals(stateChangeEvent.getPayload())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: BsqBlock
    ///////////////////////////////////////////////////////////////////////////////////////////

    // After parsing of a new block is complete we trigger the processing of any non blockchain data like
    // Proposals or Blind votes.
    public void blockParsingComplete(BsqBlock bsqBlock) {
        lock.write(() -> {
            state.getBsqBlocks().add(bsqBlock);
            log.info("New block added at blockHeight " + bsqBlock.getHeight());

            // If the client has set a specific executor we call on that thread.
            // By default the userThread's executor is used.
            listeners.forEach(listener -> listener.execute(() -> listener.onBlockAdded(bsqBlock)));

            // We check if we have a StateChangeEvent at that blockHeight and if so we notify the listeners.
            // The onBlockAdded handler usually triggers that new StateChangeEvent gets added so we are processing
            // those newly added events in the listener handler.
            // Though we support also to process a collection of old events which the client has not added himself.
            // The immutable data structures like the bsqBlocks and the stateChangeEvents can be used to recreate the
            // mutable state.
            state.getStateChangeEvents().stream()
                    .filter(event -> event.getChainHeight() == bsqBlock.getHeight())
                    .forEach(event -> listeners.forEach(listener -> listener.execute(() -> listener.onStateChange(event))));
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: Tx
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void setTxType(String txId, TxType txType) {
        lock.write(() -> state.getTxTypeMap().put(txId, txType));
    }

    public void setBurntFee(String txId, long burnedFee) {
        lock.write(() -> state.getBurntFeeMap().put(txId, burnedFee));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addUnspentTxOutput(TxOutput txOutput) {
        lock.write(() -> {
            state.getUnspentTxOutputMap().put(txOutput.getKey(), txOutput);
        });
    }

    public void removeUnspentTxOutput(TxOutput txOutput) {
        lock.write(() -> state.getUnspentTxOutputMap().remove(txOutput.getKey()));
    }

    public void addIssuanceTxOutput(TxOutput txOutput) {
        lock.write(() -> {
            //TODO handle maturity

            addUnspentTxOutput(txOutput);

            state.getIssuanceBlockHeightMap().put(txOutput.getTxId(), getChainHeadHeight());
        });
    }

    public void setSpentInfo(TxOutput txOutput, int blockHeight, String txId, int inputIndex) {
        lock.write(() -> state.getTxOutputSpentInfoMap().put(txOutput.getKey(),
                new SpentInfo(blockHeight, txId, inputIndex)));

    }

    public void setTxOutputType(TxOutput txOutput, TxOutputType txOutputType) {
        lock.write(() -> state.getTxOutputTypeMap().put(txOutput.getKey(), txOutputType));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: Misc
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void putProposalPayload(String txId, ProposalPayload proposalPayload) {
        lock.write(() -> state.getProposalPayloadByTxIdMap().put(txId, proposalPayload));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: BsqBlock
    ///////////////////////////////////////////////////////////////////////////////////////////

    public State getClone() {
        return lock.read((Supplier<State>) state::getClone);
    }

    public LinkedList<BsqBlock> getBsqBlocks() {
        return lock.read(state::getBsqBlocks);
    }

    public boolean containsBsqBlock(BsqBlock bsqBlock) {
        return lock.read(() -> state.getBsqBlocks().contains(bsqBlock));
    }

    public int getChainHeadHeight() {
        return lock.read(() -> !state.getBsqBlocks().isEmpty() ? state.getBsqBlocks().getLast().getHeight() : 0);
    }

    public int getGenesisBlockHeight() {
        return genesisBlockHeight;
    }

    public List<BsqBlock> getClonedBlocksFrom(int fromBlockHeight) {
        return lock.read(() -> {
            return state.getClone().getBsqBlocks().stream()
                    .filter(block -> block.getHeight() >= fromBlockHeight)
                    .map(BsqBlock::clone)
                    .collect(Collectors.toList());
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Tx> getGenesisTx() {
        return getTx(BTC_GENESIS_TX_ID);
    }

    public String getGenesisTxId() {
        return genesisTxId;
    }

    public long getBurntFee(String txId) {
        return lock.read(() -> state.getBurntFeeMap().containsKey(txId) ?
                (long) state.getBurntFeeMap().get(txId) : 0);
    }

    public boolean isIssuanceTx(String txId) {
        return lock.read(() -> state.getIssuanceBlockHeightMap().containsKey(txId));
    }

    public int getIssuanceBlockHeight(String txId) {
        return lock.read(() -> isIssuanceTx(txId) ? state.getIssuanceBlockHeightMap().get(txId) : -1);
    }


    public Optional<Tx> getTx(String txId) {
        return lock.read(() -> Optional.ofNullable(getTxMap().get(txId)));
    }

    public Map<String, Tx> getTxMap() {
        return lock.read(() -> state.getBsqBlocks().stream()
                .flatMap(bsqBlock -> bsqBlock.getTxs().stream())
                .collect(Collectors.toMap(Tx::getId, tx -> tx)));
    }

    public Set<Tx> getTransactions() {
        return lock.read(() -> new HashSet<>(getTxMap().values()));
    }

    public Set<Tx> getFeeTransactions() {
        return lock.read(() -> getTxMap().values().stream()
                .filter(e -> getBurntFee(e.getId()) > 0)
                .collect(Collectors.toSet()));
    }

    public boolean hasTxBurntFee(String txId) {
        return lock.read(() -> getBurntFee(txId) > 0);
    }

    public boolean containsTx(String txId) {
        return lock.read(() -> getTx(txId).isPresent());
    }

    public Optional<TxType> getTxType(String txId) {
        return lock.read(() -> {
            if (state.getTxTypeMap().containsKey(txId))
                return Optional.of(state.getTxTypeMap().get(txId));
            else
                return Optional.empty();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: TxInput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxOutput> getConnectedTxOutput(TxInput txInput) {
        return lock.read(() -> getTx(txInput.getConnectedTxOutputTxId())
                .map(tx -> tx.getOutputs().get(txInput.getConnectedTxOutputIndex())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SpentInfo getSpentInfo(TxOutput txOutput) {
        return lock.read(() -> state.getTxOutputSpentInfoMap().get(txOutput.getKey()));
    }

    public boolean isUnspent(TxOutput txOutput) {
        return lock.read(() -> state.getUnspentTxOutputMap().containsKey(txOutput.getKey()));
    }

    public TxOutputType getTxOutputType(TxOutput txOutput) {
        return lock.read(() -> state.getTxOutputTypeMap().get(txOutput.getKey()));
    }

    public boolean isBsqTxOutputType(TxOutput txOutput) {
        return lock.read(() -> getTxOutputType(txOutput) != TxOutputType.UNDEFINED &&
                getTxOutputType(txOutput) != TxOutputType.BTC_OUTPUT &&
                getTxOutputType(txOutput) != TxOutputType.INVALID_OUTPUT);
    }

    // TODO handle BOND_LOCK, BOND_UNLOCK
    // TODO handle BLIND_VOTE_STAKE_OUTPUT more specifically
    public boolean isTxOutputSpendable(String txId, int index) {
        return lock.read(() -> getUnspentAndMatureTxOutput(txId, index)
                .filter(txOutput -> getTxOutputType(txOutput) != TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT)
                .isPresent());
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return lock.read(() -> new HashSet<>(state.getUnspentTxOutputMap().values()));
    }

    public Set<TxOutput> getUnspentBlindVoteStakeTxOutputs() {
        return lock.read(() -> getUnspentTxOutputs().stream()
                .filter(txOutput -> getTxOutputType(txOutput) == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT)
                .collect(Collectors.toSet()));
    }


    public Set<TxOutput> getLockedInBondsOutputs() {
        return lock.read(() -> getUnspentTxOutputs().stream()
                .filter(txOutput -> getTxOutputType(txOutput) == TxOutputType.BOND_LOCK)
                .collect(Collectors.toSet()));
    }

    public Optional<TxOutput> getUnspentAndMatureTxOutput(TxOutput.Key key) {
        return lock.read(() -> getUnspentTxOutput(key)
                .filter(this::isTxOutputMature));
    }

    public Optional<TxOutput> getUnspentAndMatureTxOutput(String txId, int index) {
        return lock.read(() -> getUnspentAndMatureTxOutput(new TxOutput.Key(txId, index)));
    }

    public Set<TxOutput> getVoteRevealOpReturnTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream()
                .filter(txOutput -> getTxOutputType(txOutput) == TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT)
                .collect(Collectors.toSet()));
    }

    // We don't use getVerifiedTxOutputs as out output is not a valid BSQ output before the issuance.
    // We marked it only as candidate for issuance and after voting result is applied it might change it's state.
    //TODO we should add unspent check (need to be set in parser)
    public Set<TxOutput> getCompReqIssuanceTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream()
                .filter(e -> getTxOutputType(e) == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT)
                .collect(Collectors.toSet()));
    }

    private Optional<TxOutput> getUnspentTxOutput(TxOutput.Key key) {
        return lock.read(() -> state.getUnspentTxOutputMap().containsKey(key) ?
                Optional.of(state.getUnspentTxOutputMap().get(key)) :
                Optional.empty()
        );
    }

    public Set<TxOutput> getAllTxOutputs() {
        return lock.read(() -> getTxMap().values().stream()
                .flatMap(tx -> tx.getOutputs().stream())
                .collect(Collectors.toSet()));
    }

    //TODO
    // for genesis we don't need it and for issuance we need more implemented first
    private boolean isTxOutputMature(TxOutput spendingTxOutput) {
        return lock.read(() -> true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getBlockTime(int height) {
        return lock.read(() -> {
            return state.getBsqBlocks().stream()
                    .filter(block -> block.getHeight() == height)
                    .mapToLong(BsqBlock::getTime)
                    .sum();
        });
    }

    public Coin getTotalBurntFee() {
        return lock.read(() -> Coin.valueOf(state.getBurntFeeMap().values().stream()
                .mapToLong(fee -> fee)
                .sum()));
    }

    public Coin getIssuedAmountAtGenesis() {
        return StateService.GENESIS_TOTAL_SUPPLY;
    }

    public Coin getIssuedAmountFromCompRequests() {
        return lock.read(() -> Coin.valueOf(getCompReqIssuanceTxOutputs().stream()
                .filter(txOutput -> getTx(txOutput.getTxId()).isPresent())
                .filter(txOutput -> isIssuanceTx(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum()));
    }
}

