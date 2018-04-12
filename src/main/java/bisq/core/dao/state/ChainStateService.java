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
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.SpentInfo;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxInput;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.dao.vote.proposal.ProposalPayload;

import bisq.common.ThreadContextAwareListener;
import bisq.common.UserThread;
import bisq.common.util.FunctionalReadWriteLock;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.ArrayList;
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
 * We limit the access to ChainStateService over interfaces for read (ChainStateService) and
 * write (ChainStateService) to have better overview and control about access.
 * <p>
 * TODO consider refactoring to move data access to a ChainStateService class.
 */
@Slf4j
public class ChainStateService {

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

    public void setTxType(String txId, TxType txType) {
        chainState.getTxTypeByTxIdMap().put(txId, txType);
    }

    public void setBurntFee(String txId, long burnedFee) {
        chainState.getBurntFeeByTxIdMap().put(txId, burnedFee);
    }

    public long getBurntFee(String txId) {
        return chainState.getBurntFeeByTxIdMap().containsKey(txId) ? chainState.getBurntFeeByTxIdMap().get(txId) : 0;
    }

    public void setProposalPayload(String txId, ProposalPayload proposalPayload) {
        chainState.getProposalPayloadByTxIdMap().put(txId, proposalPayload);
    }

    public boolean isIssuanceTx(String txId) {
        return chainState.getIssuanceBlockHeightByTxIdMap().containsKey(txId);
    }

    public int getIssuanceBlockHeight(String txId) {
        return isIssuanceTx(txId) ? chainState.getIssuanceBlockHeightByTxIdMap().get(txId) : -1;
    }

    public Optional<TxOutput> getConnectedTxOutput(TxInput txInput) {
        return getTx(txInput.getConnectedTxOutputTxId())
                .map(tx -> tx.getOutputs().get(txInput.getConnectedTxOutputIndex()));
    }

    public boolean isInUTXOMap(TxOutput txOutput) {
        return chainState.getUnspentTxOutputMap().containsKey(txOutput.getKey());
    }

    public void setSpentInfo(TxOutput txOutput, int blockHeight, String txId, int inputIndex) {
        chainState.getTxOutputSpentInfoMap().put(txOutput.getKey(),
                new SpentInfo(blockHeight, txId, inputIndex));

    }

    public SpentInfo getSpentInfo(TxOutput txOutput) {
        return chainState.getTxOutputSpentInfoMap().get(txOutput.getKey());
    }

    public boolean isUnspent(TxOutput txOutput) {
        return chainState.getUnspentTxOutputMap().containsKey(txOutput.getKey());
    }

    public void setTxOutputType(TxOutput txOutput, TxOutputType txOutputType) {
        chainState.getTxOutputTypeMap().put(txOutput.getKey(), txOutputType);
    }

    public TxOutputType getTxOutputType(TxOutput txOutput) {
        return chainState.getTxOutputTypeMap().get(txOutput.getKey());
    }

    public boolean isBsqTxOutputType(TxOutput txOutput) {
        return getTxOutputType(txOutput) != TxOutputType.UNDEFINED &&
                getTxOutputType(txOutput) != TxOutputType.BTC_OUTPUT &&
                getTxOutputType(txOutput) != TxOutputType.INVALID_OUTPUT;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface IssuanceListener {
        void onIssuance();
    }

    public interface Listener extends ThreadContextAwareListener {
        void onBlockAdded(BsqBlock bsqBlock);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final ChainState chainState;

    private final String genesisTxId;
    private final int genesisBlockHeight;

    private final List<Listener> listeners = new ArrayList<>();
    private final List<IssuanceListener> issuanceListeners = new ArrayList<>();

    transient private final FunctionalReadWriteLock lock;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public ChainStateService(ChainState chainState,
                             @Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                             @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this.chainState = chainState;
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

    public void addIssuanceListener(IssuanceListener listener) {
        lock.write(() -> issuanceListeners.add(listener));
    }

    public void removeIssuanceListener(IssuanceListener listener) {
        lock.write(() -> issuanceListeners.remove(listener));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: ChainStateService
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applySnapshot(ChainState snapshot) {
        lock.write(() -> {
            chainState.getBsqBlocks().clear();
            chainState.getBsqBlocks().addAll(snapshot.getBsqBlocks());

            chainState.getUnspentTxOutputMap().clear();
            chainState.getUnspentTxOutputMap().putAll(snapshot.getUnspentTxOutputMap());

            //TODO
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: BsqBlock
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addBlock(BsqBlock bsqBlock) {
        lock.write(() -> {
            chainState.getBsqBlocks().add(bsqBlock);
            printNewBlock(bsqBlock);
            log.info("New block added at blockHeight " + bsqBlock.getHeight());

            // If the client has set a specific executor we call on that thread.
            // By default the Userthreads executor is used.
            listeners.forEach(listener -> listener.execute(() -> listener.onBlockAdded(bsqBlock)));
        });
    }

    public BsqBlock getLastBsqBlock() {
        return lock.read(() -> {
            return chainState.getBsqBlocks().peekLast();
        });
    }

    public Optional<BsqBlock> getBsqBlock(int blockHeight) {
        return lock.read(() -> {
            return chainState.getBsqBlocks().stream()
                    .filter(bsqBlock -> bsqBlock.getHeight() == blockHeight)
                    .findFirst();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setGenesisTx(Tx tx) {
        lock.write(() -> chainState.setGenesisTx(tx));
    }

    public void addTxToMap(Tx tx) {
        lock.write(() -> getTxMap().put(tx.getId(), tx));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Write access: TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addUTXO(TxOutput txOutput) {
        lock.write(() -> {
            chainState.getUnspentTxOutputMap().put(txOutput.getKey(), txOutput);
        });
    }

    public void removeFromUTXOMap(TxOutput txOutput) {
        lock.write(() -> chainState.getUnspentTxOutputMap().remove(txOutput.getKey()));
    }

    public void issueBsq(TxOutput txOutput) {
        lock.write(() -> {
            //TODO handle maturity

            addUTXO(txOutput);

            chainState.getIssuanceBlockHeightByTxIdMap().put(txOutput.getTxId(), getChainHeadHeight());

            issuanceListeners.forEach(l -> UserThread.execute(l::onIssuance));
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: BsqBlock
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ChainState getClone() {
        return chainState.getClone();
    }

    public LinkedList<BsqBlock> getBsqBlocks() {
        return lock.read(() -> chainState.getBsqBlocks());
    }

    public boolean containsBsqBlock(BsqBlock bsqBlock) {
        return lock.read(() -> chainState.getBsqBlocks().contains(bsqBlock));
    }

    public int getChainHeadHeight() {
        return !chainState.getBsqBlocks().isEmpty() ? chainState.getBsqBlocks().getLast().getHeight() : 0;
    }

    public int getGenesisBlockHeight() {
        return genesisBlockHeight;
    }

    public List<BsqBlock> getClonedBlocksFrom(int fromBlockHeight) {
        return lock.read(() -> {
            return chainState.getClone().getBsqBlocks().stream()
                    .filter(block -> block.getHeight() >= fromBlockHeight)
                    .map(bsqBlock -> BsqBlock.clone(bsqBlock))
                    .collect(Collectors.toList());
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Tx> getTx(String txId) {
        return lock.read(() -> Optional.ofNullable(getTxMap().get(txId)));
    }

    public Map<String, Tx> getTxMap() {
        return lock.read(() -> chainState.getBsqBlocks().stream()
                .flatMap(bsqBlock -> bsqBlock.getTxs().stream())
                .collect(Collectors.toMap(Tx::getId, tx -> tx)));
    }

    public Set<Tx> getTransactions() {
        return lock.read(() -> getTxMap().entrySet().stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet()));
    }

    public Set<Tx> getFeeTransactions() {
        return lock.read(() -> getTxMap().values().stream()
                .filter(e -> getBurntFee(e.getId()) > 0)
                .collect(Collectors.toSet()));
    }

    public boolean hasTxBurntFee(String txId) {
        return lock.read(() -> getTx(txId)
                .filter(tx -> chainState.getBurntFeeByTxIdMap().containsKey(txId))
                .isPresent());
    }

    public boolean containsTx(String txId) {
        return lock.read(() -> getTx(txId).isPresent());
    }

    @Nullable
    public Tx getGenesisTx() {
        return chainState.getGenesisTx();
    }

    public String getGenesisTxId() {
        return genesisTxId;
    }

    public long getBlockTime(int height) {
        return lock.read(() -> {
            return chainState.getBsqBlocks().stream()
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
    public boolean isTxOutputSpendable(String txId, int index) {
        return lock.read(() -> getUnspentAndMatureTxOutput(txId, index)
                .filter(txOutput -> getTxOutputType(txOutput) != TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT)
                .isPresent());
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream().
                filter(txOutput -> getUnspentTxOutput(txOutput).isPresent())
                .collect(Collectors.toSet()));
    }

    public Set<TxOutput> getUnspentBlindVoteStakeTxOutputs() {
        return lock.read(() -> getUnspentTxOutputs().stream()
                .filter(e -> getTxOutputType(e) == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT)
                .collect(Collectors.toSet()));
    }


    public Set<TxOutput> getLockedInBondsOutputs() {
        return lock.read(() -> getUnspentTxOutputs().stream()
                .filter(e -> getTxOutputType(e) == TxOutputType.BOND_LOCK)
                .collect(Collectors.toSet()));
    }

    public Optional<TxOutput> getUnspentAndMatureTxOutput(TxOutput.Key key) {
        return lock.read(() -> getUnspentTxOutput(key)
                .filter(this::isTxOutputMature));
    }

    public Optional<TxOutput> getUnspentAndMatureTxOutput(String txId, int index) {
        return lock.read(() -> getUnspentAndMatureTxOutput(new TxOutput.Key(txId, index)));
    }

    public Set<TxOutput> getVoteRevealTxOutputs() {
        return lock.read(() -> getAllTxOutputs().stream()
                .filter(e -> getTxOutputType(e) == TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT)
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
        return lock.read(() -> chainState.getUnspentTxOutputMap().entrySet().stream()
                .filter(e -> e.getKey().equals(key))
                .map(Map.Entry::getValue)
                .findAny()
        );
    }

    private Optional<TxOutput> getUnspentTxOutput(TxOutput txOutput) {
        return lock.read(() -> chainState.getUnspentTxOutputMap().entrySet().stream()
                .filter(e -> e.getKey().equals(txOutput.getKey()))
                .map(Map.Entry::getValue)
                .findAny()
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
    // Read access: TxType
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxType> getTxType(String txId) {
        return lock.read(() -> {
            if (chainState.getTxTypeByTxIdMap().containsKey(txId))
                return Optional.of(chainState.getTxTypeByTxIdMap().get(txId));
            else
                return Optional.empty();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access: Misc
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getTotalBurntFee() {
        return lock.read(() -> Coin.valueOf(chainState.getBurntFeeByTxIdMap().values().stream().mapToLong(fee -> fee).sum()));
    }

    public Coin getIssuedAmountAtGenesis() {
        return lock.read(() -> ChainStateService.GENESIS_TOTAL_SUPPLY);
    }

    public Coin getIssuedAmountFromCompRequests() {
        return lock.read(() -> Coin.valueOf(getCompReqIssuanceTxOutputs().stream()
                .filter(txOutput -> getTx(txOutput.getTxId()).isPresent())
                .filter(txOutput -> isIssuanceTx(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum()));
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
     /*   log.debug("\nchainHeadHeight={}\n" +
                        "    blocks.size={}\n" +
                        "    getTxMap().size={}\n" +
                        "    chainState.getUnspentTxOutputs().size={}\n" +
                        getChainHeadHeight(),
                chainState.getBsqBlocks().size(),
                getTxMap().size(),
                chainState.getUnspentTxOutputMap().size());

        if (!chainState.getBsqBlocks().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n##############################################################################");
            printBlock(bsqBlock, sb);
            sb.append("\n\n##############################################################################\n");
            log.debug(sb.toString());
        }*/
    }

    private void printBlock(BsqBlock bsqBlock, StringBuilder sb) {
/*        sb.append("\n\nBsqBlock prev -> current hash: ").append(bsqBlock.getPreviousBlockHash()).append(" -> ").append(bsqBlock.getHash());
        sb.append("\nblockHeight: ").append(bsqBlock.getHeight());
        sb.append("\nNew BSQ txs in block: ");
        sb.append(bsqBlock.getTxs().stream().map(Tx::getId).collect(Collectors.toList()).toString());
        sb.append("\nAll BSQ tx new state: ");
        getTxMap().values().stream()
                .sorted(Comparator.comparing(Tx::getBlockHeight))
                .forEach(tx -> printTx(tx, sb));*/
    }

    private void printTx(Tx tx, StringBuilder sb) {
      /*  sb.append("\n\nTx with ID: ").append(tx.getId());
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
        }*/
    }

    // Probably not needed anymore
    public <T> T callFunctionWithWriteLock(Supplier<T> supplier) {
        return lock.write(supplier);
    }

}

