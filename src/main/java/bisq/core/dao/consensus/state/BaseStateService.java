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

package bisq.core.dao.consensus.state;

import bisq.core.dao.consensus.period.Cycle;
import bisq.core.dao.consensus.state.blockchain.SpentInfo;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.consensus.state.blockchain.TxBlock;
import bisq.core.dao.consensus.state.blockchain.TxInput;
import bisq.core.dao.consensus.state.blockchain.TxOutput;
import bisq.core.dao.consensus.state.blockchain.TxOutputType;
import bisq.core.dao.consensus.state.blockchain.TxType;
import bisq.core.dao.consensus.state.events.AddBlindVoteEvent;
import bisq.core.dao.consensus.state.events.AddChangeParamEvent;
import bisq.core.dao.consensus.state.events.AddProposalPayloadEvent;
import bisq.core.dao.consensus.state.events.StateChangeEvent;
import bisq.core.dao.consensus.state.events.payloads.BlindVote;
import bisq.core.dao.consensus.state.events.payloads.ProposalPayload;
import bisq.core.dao.consensus.state.events.payloads.ChangeParamPayload;

import org.bitcoinj.core.Coin;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;


/**
 * Provides read access utility methods for state data.
 */
@Slf4j
public abstract class BaseStateService {

    // BlockListeners can be added and removed from the user thread but iterated at the parser thread, which would
    // lead to a ConcurrentModificationException
    protected final List<BlockListener> blockListeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BaseStateService() {
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Block
    abstract public LinkedList<Block> getBlocks();

    // Tx
    abstract public Map<String, TxType> getTxTypeMap();

    abstract public Map<String, Long> getBurntFeeMap();

    abstract public Map<String, Integer> getIssuanceBlockHeightMap();

    // TxOutput
    abstract public Map<TxOutput.Key, TxOutput> getUnspentTxOutputMap();

    abstract public Map<TxOutput.Key, SpentInfo> getTxOutputSpentInfoMap();

    abstract public Map<TxOutput.Key, TxOutputType> getTxOutputTypeMap();

    // Cycle
    abstract public List<Cycle> getCycles();

    // Genesis
    abstract public Coin getGenesisTotalSupply();

    abstract public String getGenesisTxId();

    abstract public int getGenesisBlockHeight();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Block
    ///////////////////////////////////////////////////////////////////////////////////////////


    private Optional<Block> getBlockAtHeight(int height) {
        return getBlocks().stream()
                .filter(block -> block.getHeight() == height)
                .findAny();
    }


    public LinkedList<TxBlock> getTxBlocks(LinkedList<Block> blocks) {
        return new LinkedList<>(blocks).stream()
                .map(Block::getTxBlock)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    public LinkedList<TxBlock> getTxBlocks() {
        return getTxBlocks(this.getBlocks());
    }

    public boolean containsTxBlock(TxBlock txBlock) {
        return getTxBlocks().contains(txBlock);
    }

    public int getChainHeight() {
        return !getBlocks().isEmpty() ? getBlocks().getLast().getHeight() : 0;
    }

    public long getBlockTime(int height) {
        return getBlockAtHeight(height).map(Block::getTime).orElse(0L);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Tx> getGenesisTx() {
        return getTx(getGenesisTxId());
    }

    public boolean isIssuanceTx(String txId) {
        return getIssuanceBlockHeightMap().containsKey(txId);
    }

    public int getIssuanceBlockHeight(String txId) {
        return getIssuanceBlockHeightMap().getOrDefault(txId, 0);
    }

    public Optional<Tx> getTx(String txId) {
        return Optional.ofNullable(getTxMap().get(txId));
    }

    public Map<String, Tx> getTxMap() {
        return getTxBlocks().stream()
                .flatMap(txBlock -> txBlock.getTxs().stream())
                .collect(Collectors.toMap(Tx::getId, tx -> tx));
    }

    public Set<Tx> getTxs() {
        return getTxBlocks().stream()
                .flatMap(txBlock -> txBlock.getTxs().stream())
                .collect(Collectors.toSet());
    }

    public Set<Tx> getFeeTxs() {
        return getTxBlocks().stream()
                .flatMap(txBlock -> txBlock.getTxs().stream())
                .filter(tx -> hasTxBurntFee(tx.getId()))
                .collect(Collectors.toSet());
    }

    public boolean hasTxBurntFee(String txId) {
        return getBurntFee(txId) > 0;
    }

    public long getBurntFee(String txId) {
        return getBurntFeeMap().getOrDefault(txId, 0L);
    }

    public long getTotalBurntFee() {
        return getBurntFeeMap().values().stream()
                .mapToLong(fee -> fee)
                .sum();
    }

    public boolean containsTx(String txId) {
        return getTx(txId).isPresent();
    }

    public Optional<TxType> getTxType(String txId) {
        return Optional.ofNullable(getTxTypeMap().getOrDefault(txId, null));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxInput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxOutput> getConnectedTxOutput(TxInput txInput) {
        return getTx(txInput.getConnectedTxOutputTxId())
                .map(tx -> tx.getOutputs().get(txInput.getConnectedTxOutputIndex()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TxOutput
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<TxOutput> getTxOutputs() {
        return getTxMap().values().stream()
                .flatMap(tx -> tx.getOutputs().stream())
                .collect(Collectors.toSet());
    }

    public boolean isUnspent(TxOutput txOutput) {
        return getUnspentTxOutputMap().containsKey(txOutput.getKey());
    }

    public TxOutputType getTxOutputType(TxOutput txOutput) {
        return getTxOutputTypeMap().get(txOutput.getKey());
    }

    public boolean isBsqTxOutputType(TxOutput txOutput) {
        final TxOutputType txOutputType = getTxOutputType(txOutput);
        return txOutputType != TxOutputType.UNDEFINED &&
                txOutputType != TxOutputType.BTC_OUTPUT &&
                txOutputType != TxOutputType.INVALID_OUTPUT;
    }

    public boolean isTxOutputSpendable(String txId, int index) {
        return getUnspentAndMatureTxOutput(txId, index)
                .filter(txOutput -> getTxOutputType(txOutput) != TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT)
                .isPresent();
    }

    public Set<TxOutput> getUnspentTxOutputs() {
        return new HashSet<>(getUnspentTxOutputMap().values());
    }

    public Set<TxOutput> getUnspentBlindVoteStakeTxOutputs() {
        return getUnspentTxOutputMap().values().stream()
                .filter(txOutput -> getTxOutputType(txOutput) == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT)
                .collect(Collectors.toSet());
    }

    public Set<TxOutput> getLockedInBondOutputs() {
        return getUnspentTxOutputMap().values().stream()
                .filter(txOutput -> getTxOutputType(txOutput) == TxOutputType.BOND_LOCK)
                .collect(Collectors.toSet());
    }

    public Optional<TxOutput> getUnspentAndMatureTxOutput(TxOutput.Key key) {
        return getUnspentTxOutput(key).filter(this::isTxOutputMature);
    }

    public Optional<TxOutput> getUnspentTxOutput(TxOutput.Key key) {
        return Optional.ofNullable(getUnspentTxOutputMap().getOrDefault(key, null));
    }

    public Optional<TxOutput> getUnspentAndMatureTxOutput(String txId, int index) {
        return getUnspentAndMatureTxOutput(new TxOutput.Key(txId, index));
    }

    public Set<TxOutput> getVoteRevealOpReturnTxOutputs() {
        return getTxOutputs().stream()
                .filter(txOutput -> getTxOutputType(txOutput) == TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT)
                .collect(Collectors.toSet());
    }

    // We don't use getVerifiedTxOutputs as out output is not a valid BSQ output before the issuance.
    // We marked it only as candidate for issuance and after voting result is applied it might change it's state.
    public Set<TxOutput> getIssuanceCandidateTxOutputs() {
        return getTxOutputs().stream()
                .filter(e -> getTxOutputType(e) == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT)
                .collect(Collectors.toSet());
    }

    public long getTotalIssuedAmountFromCompRequests() {
        return getIssuanceCandidateTxOutputs().stream()
                /*.filter(txOutput -> getTx(txOutput.getTxId()).isPresent())*/ // probably not needed but cross check in parser
                .filter(txOutput -> isIssuanceTx(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum();
    }

    //TODO
    // for genesis we don't need it and for issuance we need more implemented first
    public boolean isTxOutputMature(TxOutput txOutput) {
        return true;
    }

    public Optional<SpentInfo> getSpentInfo(TxOutput txOutput) {
        return Optional.ofNullable(getTxOutputSpentInfoMap().getOrDefault(txOutput.getKey(), null));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateChangeEvent
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Block getLastBlock() {
        return this.getBlocks().getLast();
    }

    public Set<StateChangeEvent> getStateChangeEvents() {
        return this.getBlocks().stream()
                .flatMap(block -> block.getStateChangeEvents().stream())
                .collect(Collectors.toSet());
    }

    public Set<AddChangeParamEvent> getAddChangeParamEvents() {
        return getStateChangeEvents().stream()
                .filter(event -> event instanceof AddChangeParamEvent)
                .map(event -> (AddChangeParamEvent) event)
                .collect(Collectors.toSet());
    }

    public Set<AddProposalPayloadEvent> getAddProposalPayloadEvents() {
        return getStateChangeEvents().stream()
                .filter(event -> event instanceof AddProposalPayloadEvent)
                .map(event -> (AddProposalPayloadEvent) event)
                .collect(Collectors.toSet());
    }

    public Set<AddBlindVoteEvent> getAddBlindVoteEvents() {
        return getStateChangeEvents().stream()
                .filter(event -> event instanceof AddBlindVoteEvent)
                .map(event -> (AddBlindVoteEvent) event)
                .collect(Collectors.toSet());
    }

    public Set<ProposalPayload> getProposalPayloads() {
        return getAddProposalPayloadEvents().stream()
                .map(AddProposalPayloadEvent::getProposalPayload)
                .collect(Collectors.toSet());
    }

    public Set<BlindVote> getBlindVotes() {
        return getAddBlindVoteEvents().stream()
                .map(AddBlindVoteEvent::getBlindVote)
                .collect(Collectors.toSet());
    }

    public Set<ChangeParamPayload> getChangeParamPayloads() {
        return getAddChangeParamEvents().stream()
                .map(AddChangeParamEvent::getChangeParamPayload)
                .collect(Collectors.toSet());
    }
}

