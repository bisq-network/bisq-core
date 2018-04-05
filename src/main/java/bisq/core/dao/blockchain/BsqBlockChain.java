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

package bisq.core.dao.blockchain;

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.dao.blockchain.vo.util.TxIdIndexTuple;

import bisq.common.UserThread;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.util.FunctionalReadWriteLock;
import bisq.common.util.Tuple2;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;


/**
 * Mutual state of the BSQ blockchain data.
 * <p>
 * We only have one thread which is writing data from the lite node or full node executors.
 * We use ReentrantReadWriteLock in a functional style.
 * <p>
 * We limit the access to BsqBlockChain over interfaces for read (ReadableBsqBlockChain) and
 * write (WritableBsqBlockChain) to have better overview and control about access.
 * <p>
 * TODO consider refactoring to move data access to a ChainStateService class.
 */
@Slf4j
public class BsqBlockChain implements PersistableEnvelope, WritableBsqBlockChain, ReadableBsqBlockChain {

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

    public interface Listener {
        void onBlockAdded(BsqBlock bsqBlock);
    }

    public interface IssuanceListener {
        void onIssuance();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final String genesisTxId;
    private final int genesisBlockHeight;

    private final LinkedList<BsqBlock> bsqBlocks;
    private final Map<String, Tx> txMap;
    private final Map<TxIdIndexTuple, TxOutput> unspentTxOutputsMap;

    private final List<Listener> listeners = new ArrayList<>();
    private final List<IssuanceListener> issuanceListeners = new ArrayList<>();

    private int chainHeadHeight = 0;
    @Nullable
    private Tx genesisTx;

    transient private final FunctionalReadWriteLock lock;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqBlockChain(@Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                         @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this(new LinkedList<>(),
                new HashMap<>(),
                new HashMap<>(),
                genesisTxId,
                genesisBlockHeight,
                0,
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BsqBlockChain(LinkedList<BsqBlock> bsqBlocks,
                          Map<String, Tx> txMap,
                          Map<TxIdIndexTuple, TxOutput> unspentTxOutputsMap,
                          String genesisTxId,
                          int genesisBlockHeight,
                          int chainHeadHeight,
                          @Nullable Tx genesisTx) {
        this.bsqBlocks = bsqBlocks;
        this.txMap = txMap;
        this.unspentTxOutputsMap = unspentTxOutputsMap;
        this.genesisTxId = genesisTxId;
        this.genesisBlockHeight = genesisBlockHeight;
        this.chainHeadHeight = chainHeadHeight;
        this.genesisTx = genesisTx;

        lock = new FunctionalReadWriteLock(true);
    }

    @Override
    public Message toProtoMessage() {
        return PB.PersistableEnvelope.newBuilder().setBsqBlockChain(getBsqBlockChainBuilder()).build();
    }

    private PB.BsqBlockChain.Builder getBsqBlockChainBuilder() {
        final PB.BsqBlockChain.Builder builder = PB.BsqBlockChain.newBuilder()
                .addAllBsqBlocks(bsqBlocks.stream()
                        .map(BsqBlock::toProtoMessage)
                        .collect(Collectors.toList()))
                .putAllTxMap(txMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                v -> v.getValue().toProtoMessage())))
                .putAllUnspentTxOutputsMap(unspentTxOutputsMap.entrySet().stream()
                        .collect(Collectors.toMap(k -> k.getKey().getAsString(),
                                v -> v.getValue().toProtoMessage())))
                .setGenesisTxId(genesisTxId)
                .setGenesisBlockHeight(genesisBlockHeight)
                .setChainHeadHeight(chainHeadHeight);

        Optional.ofNullable(genesisTx).ifPresent(e -> builder.setGenesisTx(genesisTx.toProtoMessage()));

        return builder;
    }

    public static PersistableEnvelope fromProto(PB.BsqBlockChain proto) {
        return new BsqBlockChain(new LinkedList<>(proto.getBsqBlocksList().stream()
                .map(BsqBlock::fromProto)
                .collect(Collectors.toList())),
                new HashMap<>(proto.getTxMapMap().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, v -> Tx.fromProto(v.getValue())))),
                new HashMap<>(proto.getUnspentTxOutputsMapMap().entrySet().stream()
                        .collect(Collectors.toMap(k -> new TxIdIndexTuple(k.getKey()), v -> TxOutput.fromProto(v.getValue())))),
                proto.getGenesisTxId(),
                proto.getGenesisBlockHeight(),
                proto.getChainHeadHeight(),
                proto.hasGenesisTx() ? Tx.fromProto(proto.getGenesisTx()) : null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public synchronized void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    @Override
    public synchronized void addIssuanceListener(IssuanceListener listener) {
        issuanceListeners.add(listener);
    }

    @Override
    public synchronized void removeIssuanceListener(IssuanceListener listener) {
        issuanceListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: BsqBlockChain
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot(BsqBlockChain snapshot) {
        lock.write(() -> {
            bsqBlocks.clear();
            bsqBlocks.addAll(snapshot.bsqBlocks);

            txMap.clear();
            txMap.putAll(snapshot.txMap);

            unspentTxOutputsMap.clear();
            unspentTxOutputsMap.putAll(snapshot.unspentTxOutputsMap);

            chainHeadHeight = snapshot.chainHeadHeight;
            genesisTx = snapshot.genesisTx;
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: BsqBlock
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addBlock(BsqBlock bsqBlock) {
        lock.write(() -> {
            bsqBlocks.add(bsqBlock);
            chainHeadHeight = bsqBlock.getHeight();
            printNewBlock(bsqBlock);
            listeners.forEach(l -> UserThread.execute(() -> l.onBlockAdded(bsqBlock)));
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setGenesisTx(Tx tx) {
        lock.write(() -> genesisTx = tx);
    }

    @Override
    public void addTxToMap(Tx tx) {
        lock.write(() -> txMap.put(tx.getId(), tx));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addUnspentTxOutput(TxOutput txOutput) {
        lock.write(() -> {
            checkArgument(txOutput.isVerified(), "txOutput must be verified at addUnspentTxOutput");
            unspentTxOutputsMap.put(txOutput.getTxIdIndexTuple(), txOutput);
        });
    }

    @Override
    public void removeUnspentTxOutput(TxOutput txOutput) {
        lock.write(() -> unspentTxOutputsMap.remove(txOutput.getTxIdIndexTuple()));
    }

    @Override
    public void issueBsq(TxOutput txOutput) {
        lock.write(() -> {
            // The magic happens, we print money! ;-)
            //TODO handle maturity

            // We should track spent status and output has to be unspent anyway
            txOutput.setUnspent(true);
            txOutput.setVerified(true);
            addUnspentTxOutput(txOutput);

            final Optional<Tx> optionalTx = getTx(txOutput.getTxId());
            checkArgument(optionalTx.isPresent(), "optionalTx must be present");
            final Tx tx = optionalTx.get();
            tx.setIssuanceBlockHeight(chainHeadHeight);
            tx.setIssuanceTx(true);

            issuanceListeners.forEach(l -> UserThread.execute(l::onIssuance));
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: BsqBlockChain
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public BsqBlockChain getClone() {
        return lock.read(() -> getClone(this));
    }

    @Override
    public BsqBlockChain getClone(BsqBlockChain bsqBlockChain) {
        return lock.read(() -> (BsqBlockChain) BsqBlockChain.fromProto(bsqBlockChain.getBsqBlockChainBuilder().build()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: BsqBlock
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public LinkedList<BsqBlock> getBsqBlocks() {
        return lock.read(() -> bsqBlocks);
    }

    @Override
    public boolean containsBsqBlock(BsqBlock bsqBlock) {
        return lock.read(() -> bsqBlocks.contains(bsqBlock));
    }

    @Override
    public int getChainHeadHeight() {
        return chainHeadHeight;
    }

    @Override
    public int getGenesisBlockHeight() {
        return genesisBlockHeight;
    }

    @Override
    public List<BsqBlock> getClonedBlocksFrom(int fromBlockHeight) {
        return lock.read(() -> {
            return getClone().bsqBlocks.stream()
                    .filter(block -> block.getHeight() >= fromBlockHeight)
                    .map(bsqBlock -> BsqBlock.clone(bsqBlock, true))
                    .collect(Collectors.toList());
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Optional<Tx> getTx(String txId) {
        return lock.read(() -> Optional.ofNullable(txMap.get(txId)));
    }

    @Override
    public Map<String, Tx> getTxMap() {
        return lock.read(() -> txMap);
    }

    @Override
    public Set<Tx> getTransactions() {
        return lock.read(() -> getTxMap().entrySet().stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet()));
    }

    @Override
    public Set<Tx> getFeeTransactions() {
        return lock.read(() -> getTxMap().entrySet().stream()
                .filter(e -> e.getValue().getBurntFee() > 0)
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet()));
    }

    @Override
    public boolean hasTxBurntFee(String txId) {
        return lock.read(() -> getTx(txId)
                .map(Tx::getBurntFee)
                .filter(fee -> fee > 0)
                .isPresent());
    }

    @Override
    public boolean containsTx(String txId) {
        return lock.read(() -> getTx(txId).isPresent());
    }

    @Nullable
    public Tx getGenesisTx() {
        return genesisTx;
    }

    @Override
    public String getGenesisTxId() {
        return genesisTxId;
    }

    @Override
    public long getBlockTime(int height) {
        return lock.read(() -> {
            return bsqBlocks.stream()
                    .filter(block -> block.getHeight() == height)
                    .mapToLong(BsqBlock::getTime)
                    .sum();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO handle BOND_LOCK, BOND_UNLOCK
    // TODO handle BLIND_VOTE_STAKE_OUTPUT more specifically
    @Override
    public boolean isTxOutputSpendable(String txId, int index) {
        return lock.read(() -> getUnspentAndMatureTxOutput(txId, index)
                .filter(txOutput -> txOutput.getTxOutputType() != TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT)
                .isPresent());
    }

    @Override
    public Set<TxOutput> getUnspentTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream().
                filter(e -> e.isVerified() && e.isUnspent())
                .collect(Collectors.toSet()));
    }

    public Set<TxOutput> getVerifiedTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream().
                filter(TxOutput::isVerified)
                .collect(Collectors.toSet()));
    }

    public Set<TxOutput> getBlindVoteStakeTxOutputs() {
        return lock.read(() -> getUnspentTxOutputs().stream()
                .filter(e -> e.getTxOutputType() == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT)
                .collect(Collectors.toSet()));
    }

    @Override
    public Set<TxOutput> getLockedInBondsOutputs() {
        return lock.read(() -> getUnspentTxOutputs().stream()
                .filter(e -> e.getTxOutputType() == TxOutputType.BOND_LOCK)
                .collect(Collectors.toSet()));
    }

    @Override
    public Set<TxOutput> getSpentTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream().filter(e -> e.isVerified() && !e.isUnspent()).collect(Collectors.toSet()));
    }


    @Override
    public Optional<TxOutput> getUnspentAndMatureTxOutput(TxIdIndexTuple txIdIndexTuple) {
        return lock.read(() -> getUnspentTxOutput(txIdIndexTuple)
                .filter(this::isTxOutputMature));
    }

    @Override
    public Optional<TxOutput> getUnspentAndMatureTxOutput(String txId, int index) {
        return lock.read(() -> getUnspentAndMatureTxOutput(new TxIdIndexTuple(txId, index)));
    }

    @Override
    public Set<TxOutput> getVoteRevealTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream()
                .filter(e -> e.getTxOutputType() == TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT)
                .collect(Collectors.toSet()));
    }

    // We don't use getVerifiedTxOutputs as out output is not a valid BSQ output before the issuance.
    // We marked it only as candidate for issuance and after voting result is applied it might change it's state.
    //TODO we should add unspent check (need to be set in parser)
    @Override
    public Set<TxOutput> getCompReqIssuanceTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream()
                .filter(e -> e.getTxOutputType() == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT)
                .collect(Collectors.toSet()));
    }

    private Optional<TxOutput> getUnspentTxOutput(TxIdIndexTuple txIdIndexTuple) {
        return lock.read(() -> unspentTxOutputsMap.entrySet().stream()
                .filter(e -> e.getKey().equals(txIdIndexTuple))
                .map(Map.Entry::getValue)
                .filter(TxOutput::isVerified) //TODO is it needed?
                .findAny()
        );
    }

    public Set<TxOutput> getAllTxOutputs() {
        return lock.read(() -> txMap.values().stream()
                .flatMap(tx -> tx.getOutputs().stream())
                .collect(Collectors.toSet()));
    }

    //TODO
    // for genesis we don't need it and for issuance we need more implemented first
    private boolean isTxOutputMature(TxOutput spendingTxOutput) {
        return lock.read(() -> true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: TxType
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Optional<TxType> getTxType(String txId) {
        return lock.read(() -> getTx(txId).map(Tx::getTxType));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Coin getTotalBurntFee() {
        return lock.read(() -> Coin.valueOf(getTxMap().entrySet().stream().mapToLong(e -> e.getValue().getBurntFee()).sum()));
    }

    @Override
    public Coin getIssuedAmountAtGenesis() {
        return lock.read(() -> BsqBlockChain.GENESIS_TOTAL_SUPPLY);
    }


    private long getValueAtHeight(Set<Tuple2<Long, Integer>> set, int blockHeight) {
        return lock.read(() -> {
            long value = -1;
            for (Tuple2<Long, Integer> currentValue : set) {
                if (currentValue.second <= blockHeight)
                    value = currentValue.first;
            }
            checkArgument(value > -1, "value must be set");
            return value;
        });
    }

    private void printNewBlock(BsqBlock bsqBlock) {
        log.debug("\nchainHeadHeight={}\n" +
                        "    blocks.size={}\n" +
                        "    txMap.size={}\n" +
                        "    unspentTxOutputsMap.size={}\n" +
                        getChainHeadHeight(),
                bsqBlocks.size(),
                txMap.size(),
                unspentTxOutputsMap.size());

        if (!bsqBlocks.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n##############################################################################");
            printBlock(bsqBlock, sb);
            sb.append("\n\n##############################################################################\n");
            log.debug(sb.toString());
        }
    }

    private void printBlock(BsqBlock bsqBlock, StringBuilder sb) {
        sb.append("\n\nBsqBlock prev -> current hash: ").append(bsqBlock.getPreviousBlockHash()).append(" -> ").append(bsqBlock.getHash());
        sb.append("\nblockHeight: ").append(bsqBlock.getHeight());
        sb.append("\nNew BSQ txs in block: ");
        sb.append(bsqBlock.getTxs().stream().map(Tx::getId).collect(Collectors.toList()).toString());
        sb.append("\nAll BSQ tx new state: ");
        txMap.values().stream()
                .sorted(Comparator.comparing(Tx::getBlockHeight))
                .forEach(tx -> printTx(tx, sb));
    }

    private void printTx(Tx tx, StringBuilder sb) {
        sb.append("\n\nTx with ID: ").append(tx.getId());
        sb.append("\n    added at blockHeight: ").append(tx.getBlockHeight());
        sb.append("\n    txType: ").append(tx.getTxType());
        sb.append("\n    burntFee: ").append(tx.getBurntFee());
        for (int i = 0; i < tx.getOutputs().size(); i++) {
            final TxOutput txOutput = tx.getOutputs().get(i);
            sb.append("\n        txOutput ").append(i)
                    .append(": txOutputType: ").append(txOutput.getTxOutputType())
                    .append(", isVerified: ").append(txOutput.isVerified())
                    .append(", isUnspent: ").append(txOutput.isUnspent())
                    .append(", getSpentInfo: ").append(txOutput.getSpentInfo());
        }
    }

    // Probably not needed anymore
    public <T> T callFunctionWithWriteLock(Supplier<T> supplier) {
        return lock.write(supplier);
    }

}

