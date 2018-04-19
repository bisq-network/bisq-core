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

package bisq.core.dao.consensus.node.blockchain.json;

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.consensus.node.blockchain.btcd.PubKeyScript;
import bisq.core.dao.consensus.state.State;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.SpentInfo;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.consensus.state.blockchain.TxOutput;
import bisq.core.dao.consensus.state.blockchain.TxType;

import bisq.common.storage.FileUtil;
import bisq.common.storage.JsonFileManager;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import com.google.inject.Inject;

import javax.inject.Named;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
public class JsonBlockChainExporter {
    private final StateService stateService;
    private final boolean dumpBlockchainData;

    private final ListeningExecutorService executor = Utilities.getListeningExecutorService("JsonExporter", 1, 1, 1200);
    private File txDir, txOutputDir, bsqBlockChainDir;
    private JsonFileManager txFileManager, txOutputFileManager, bsqBlockChainFileManager;

    @Inject
    public JsonBlockChainExporter(StateService stateService,
                                  @Named(Storage.STORAGE_DIR) File storageDir,
                                  @Named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.stateService = stateService;
        this.dumpBlockchainData = dumpBlockchainData;

        init(storageDir, dumpBlockchainData);
    }

    private void init(@Named(Storage.STORAGE_DIR) File storageDir, @Named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        if (dumpBlockchainData) {
            txDir = new File(Paths.get(storageDir.getAbsolutePath(), "tx").toString());
            txOutputDir = new File(Paths.get(storageDir.getAbsolutePath(), "txo").toString());
            bsqBlockChainDir = new File(Paths.get(storageDir.getAbsolutePath(), "all").toString());
            try {
                if (txDir.exists())
                    FileUtil.deleteDirectory(txDir);
                if (txOutputDir.exists())
                    FileUtil.deleteDirectory(txOutputDir);
                if (bsqBlockChainDir.exists())
                    FileUtil.deleteDirectory(bsqBlockChainDir);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (!txDir.mkdir())
                log.warn("make txDir failed.\ntxDir=" + txDir.getAbsolutePath());

            if (!txOutputDir.mkdir())
                log.warn("make txOutputDir failed.\ntxOutputDir=" + txOutputDir.getAbsolutePath());

            if (!bsqBlockChainDir.mkdir())
                log.warn("make bsqBsqBlockChainDir failed.\nbsqBsqBlockChainDir=" + bsqBlockChainDir.getAbsolutePath());

            txFileManager = new JsonFileManager(txDir);
            txOutputFileManager = new JsonFileManager(txOutputDir);
            bsqBlockChainFileManager = new JsonFileManager(bsqBlockChainDir);
        }
    }

    public void shutDown() {
        if (dumpBlockchainData) {
            txFileManager.shutDown();
            txOutputFileManager.shutDown();
            bsqBlockChainFileManager.shutDown();
        }
    }

    public void maybeExport() {
        if (dumpBlockchainData) {
            ListenableFuture<Void> future = executor.submit(() -> {
                final State stateClone = stateService.getClone();
                Map<String, Tx> txMap = stateService.getTxBlockFromState(stateClone).stream()
                        .filter(Objects::nonNull)
                        .flatMap(bsqBlock -> bsqBlock.getTxs().stream())
                        .collect(Collectors.toMap(Tx::getId, tx -> tx));
                for (Tx tx : txMap.values()) {
                    String txId = tx.getId();
                    final Optional<TxType> optionalTxType = stateService.getTxType(txId);
                    if (optionalTxType.isPresent()) {
                        JsonTxType txType = optionalTxType.get() != TxType.UNDEFINED_TX_TYPE ?
                                JsonTxType.valueOf(optionalTxType.get().name()) : null;
                        List<JsonTxOutput> outputs = new ArrayList<>();
                        tx.getOutputs().forEach(txOutput -> {
                            final Optional<SpentInfo> optionalSpentInfo = stateService.getSpentInfo(txOutput);
                            final boolean isBsqOutput = stateService.isBsqTxOutputType(txOutput);
                            final PubKeyScript pubKeyScript = txOutput.getPubKeyScript();
                            final JsonTxOutput outputForJson = new JsonTxOutput(txId,
                                    txOutput.getIndex(),
                                    isBsqOutput ? txOutput.getValue() : 0,
                                    !isBsqOutput ? txOutput.getValue() : 0,
                                    txOutput.getBlockHeight(),
                                    isBsqOutput,
                                    stateService.getBurntFee(tx.getId()),
                                    txOutput.getAddress(),
                                    pubKeyScript != null ? new JsonScriptPubKey(pubKeyScript) : null,
                                    optionalSpentInfo.map(JsonSpentInfo::new).orElse(null),
                                    tx.getTime(),
                                    txType,
                                    txType != null ? txType.getDisplayString() : "",
                                    txOutput.getOpReturnData() != null ? Utils.HEX.encode(txOutput.getOpReturnData()) : null
                            );
                            outputs.add(outputForJson);
                            txOutputFileManager.writeToDisc(Utilities.objectToJson(outputForJson), outputForJson.getId());
                        });


                        List<JsonTxInput> inputs = tx.getInputs().stream()
                                .map(txInput -> {
                                    Optional<TxOutput> optionalTxOutput = stateService.getConnectedTxOutput(txInput);
                                    if (optionalTxOutput.isPresent()) {
                                        final TxOutput connectedTxOutput = optionalTxOutput.get();
                                        final boolean isBsqOutput = stateService.isBsqTxOutputType(connectedTxOutput);
                                        return new JsonTxInput(txInput.getConnectedTxOutputIndex(),
                                                txInput.getConnectedTxOutputTxId(),
                                                connectedTxOutput != null ? connectedTxOutput.getValue() : 0,
                                                connectedTxOutput != null && isBsqOutput,
                                                connectedTxOutput != null ? connectedTxOutput.getAddress() : null,
                                                tx.getTime());
                                    } else {
                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        final JsonTx jsonTx = new JsonTx(txId,
                                tx.getBlockHeight(),
                                tx.getBlockHash(),
                                tx.getTime(),
                                inputs,
                                outputs,
                                txType,
                                txType != null ? txType.getDisplayString() : "",
                                stateService.getBurntFee(tx.getId()));

                        txFileManager.writeToDisc(Utilities.objectToJson(jsonTx), txId);
                    }
                }

                bsqBlockChainFileManager.writeToDisc(Utilities.objectToJson(stateClone), "StateService");
                return null;
            });

            Futures.addCallback(future, new FutureCallback<Void>() {
                public void onSuccess(Void ignore) {
                    log.trace("onSuccess");
                }

                public void onFailure(@NotNull Throwable throwable) {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                }
            });
        }
    }
}
