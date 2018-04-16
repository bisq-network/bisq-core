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
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxBlock;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.events.AddBlindVoteEvent;
import bisq.core.dao.state.events.AddChangeParamEvent;
import bisq.core.dao.state.events.AddProposalPayloadEvent;
import bisq.core.dao.state.events.StateChangeEvent;
import bisq.core.dao.vote.blindvote.BlindVote;
import bisq.core.dao.vote.period.Cycle;
import bisq.core.dao.vote.proposal.ProposalPayload;
import bisq.core.dao.vote.proposal.param.ChangeParamPayload;

import bisq.common.ThreadContextAwareListener;
import bisq.common.UserThread;
import bisq.common.util.FunctionalReadWriteLock;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;


/**
 * Encapsulates the access to the State of the DAO.
 * Write access is in the context of the nodeExecutor thread. Read access can be any thread.
 * <p>
 * TODO check if locks are required.
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
    // BlockListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface BlockListener extends ThreadContextAwareListener {
        void onBlockAdded(Block block);

        default void onStartParsingBlock(int blockHeight) {
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final State state;
    private final String genesisTxId;
    private final int genesisBlockHeight;

    // BlockListeners can be added and removed from the user thread but iterated at the parser thread, which would
    // lead to a ConcurrentModificationException
    private final List<BlockListener> blockListeners = new CopyOnWriteArrayList<>();

    // StateChangeEventListProviders get added from the user thread, thought they will be called in the
    // nodeExecutors thread. To avoid ConcurrentModificationException we use a CopyOnWriteArrayList.
    private List<Function<TxBlock, Set<StateChangeEvent>>> stateChangeEventsProviders = new CopyOnWriteArrayList<>();

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

    public void addBlockListener(BlockListener blockListener) {
        blockListeners.add(blockListener);
    }

    public void removeBlockListener(BlockListener blockListener) {
        blockListeners.remove(blockListener);
    }

    public void registerStateChangeEventsProvider(Function<TxBlock, Set<StateChangeEvent>> stateChangeEventsProvider) {
        stateChangeEventsProviders.add(stateChangeEventsProvider);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Snapshot
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot(State snapshot) {
        lock.write(() -> {
            state.getBlocks().clear();
            state.getBlocks().addAll(snapshot.getBlocks());

            state.getUnspentTxOutputMap().clear();
            state.getUnspentTxOutputMap().putAll(snapshot.getUnspentTxOutputMap());

            //TODO
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Block handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Notify listeners when we start parsing a block
    public void onStartParsingBlock(int blockHeight) {
        blockListeners.forEach(listener -> {
            if (listener.executeOnUserThread())
                UserThread.execute(() -> listener.onStartParsingBlock(blockHeight));
            else
                listener.onStartParsingBlock(blockHeight);
        });
    }

    // After parsing of a new txBlock is complete we trigger the processing of any non blockchain data like
    // proposalPayloads or blindVotes and after we have collected all stateChangeEvents we create a Block and
    // notify the listeners.
    public void onNewTxBlock(TxBlock txBlock) {
        lock.write(() -> {
            // Those who registered to process a txBlock might return a list of StateChangeEvents.
            // We collect all from all providers and then go on.
            // The providers are called in the parser thread, so we have a single threaded execution model here.
            Set<StateChangeEvent> stateChangeEvents = new HashSet<>();
            stateChangeEventsProviders.forEach(stateChangeEventsProvider -> {
                stateChangeEvents.addAll(stateChangeEventsProvider.apply(txBlock));
            });
            // Now we have both the immutable txBlock and the collected stateChangeEvents.
            // We now add the immutable Block containing both data.
            final Block block = new Block(txBlock, ImmutableSet.copyOf(stateChangeEvents));
            state.getBlocks().add(block);

            // blockListeners.forEach(listener -> listener.execute(() -> listener.onBlockAdded(block)));

            // If the listener has not implemented the executeOnUserThread method and overwritten with a return
            // value of false we map to user thread. Otherwise we run the code directly from our current thread.
            // Using executor.execute() would not work as the parser thread can be busy for a long time when parsing
            // all the blocks and we want to get called our listener synchronously and not once the parsing task is
            // completed.
            blockListeners.forEach(listener -> {
                if (listener.executeOnUserThread())
                    UserThread.execute(() -> listener.onBlockAdded(block));
                else
                    listener.onBlockAdded(block);
            });
            log.info("New block added at blockHeight " + block.getHeight());
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: Cycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addCycle(Cycle cycle) {
        lock.write(() -> state.getCycles().add(cycle));
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

            state.getIssuanceBlockHeightMap().put(txOutput.getTxId(), getChainHeight());
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
    // Read access: StateChangeEvent
    ///////////////////////////////////////////////////////////////////////////////////////////

    public LinkedList<Block> getBlocks() {
        return lock.read(state::getBlocks);
    }

    public Block getLastBlock() {
        return lock.read(() -> state.getBlocks().getLast());
    }

    public Set<StateChangeEvent> getStateChangeEvents() {
        return lock.read(() -> state.getBlocks().stream()
                .flatMap(block -> block.getStateChangeEvents().stream())
                .collect(Collectors.toSet()));
    }

    public Set<AddChangeParamEvent> getAddChangeParamEvents() {
        return lock.read(() -> getStateChangeEvents().stream()
                .filter(event -> event instanceof AddChangeParamEvent)
                .map(event -> (AddChangeParamEvent) event)
                .collect(Collectors.toSet()));
    }

    public Set<AddProposalPayloadEvent> getAddProposalPayloadEvents() {
        return lock.read(() -> getStateChangeEvents().stream()
                .filter(event -> event instanceof AddProposalPayloadEvent)
                .map(event -> (AddProposalPayloadEvent) event)
                .collect(Collectors.toSet()));
    }

    public Set<AddBlindVoteEvent> getAddBlindVoteEvents() {
        return lock.read(() -> getStateChangeEvents().stream()
                .filter(event -> event instanceof AddBlindVoteEvent)
                .map(event -> (AddBlindVoteEvent) event)
                .collect(Collectors.toSet()));
    }

    public Set<ProposalPayload> getProposalPayloads() {
        return lock.read(() -> getAddProposalPayloadEvents().stream()
                .map(AddProposalPayloadEvent::getProposalPayload)
                .collect(Collectors.toSet()));
    }

    public Set<BlindVote> getBlindVotes() {
        return lock.read(() -> getAddBlindVoteEvents().stream()
                .map(AddBlindVoteEvent::getBlindVote)
                .collect(Collectors.toSet()));
    }

    public Set<ChangeParamPayload> getChangeParamPayloads() {
        return lock.read(() -> getAddChangeParamEvents().stream()
                .map(AddChangeParamEvent::getChangeParamPayload)
                .collect(Collectors.toSet()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: TxBlock
    ///////////////////////////////////////////////////////////////////////////////////////////

    public State getClone() {
        return lock.read((Supplier<State>) state::getClone);
    }

    public LinkedList<TxBlock> getBsqBlocks() {
        return lock.read(state::getTxBlocks);
    }

    public boolean containsBsqBlock(TxBlock txBlock) {
        return lock.read(() -> state.getTxBlocks().contains(txBlock));
    }

    public int getChainHeight() {
        return lock.read(() -> !state.getTxBlocks().isEmpty() ? state.getTxBlocks().getLast().getHeight() : 0);
    }

    public int getGenesisBlockHeight() {
        return genesisBlockHeight;
    }

    public List<TxBlock> getClonedBlocksFrom(int fromBlockHeight) {
        return lock.read(() -> {
            return state.getClone().getTxBlocks().stream()
                    .filter(block -> block.getHeight() >= fromBlockHeight)
                    .map(TxBlock::clone)
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
        return lock.read(() -> state.getTxBlocks().stream()
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
    private boolean isTxOutputMature(TxOutput txOutput) {
        return lock.read(() -> true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getBlockTime(int height) {
        return lock.read(() -> {
            return state.getTxBlocks().stream()
                    .filter(block -> block.getHeight() == height)
                    .mapToLong(TxBlock::getTime)
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

