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

package bisq.core.dao.consensus.node.full;

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.consensus.node.blockchain.btcd.PubKeyScript;
import bisq.core.dao.consensus.node.blockchain.exceptions.BsqBlockchainException;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.consensus.state.blockchain.TxInput;
import bisq.core.dao.consensus.state.blockchain.TxOutput;

import bisq.common.UserThread;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Utils;

import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.client.BtcdClient;
import com.neemre.btcdcli4j.core.client.BtcdClientImpl;
import com.neemre.btcdcli4j.core.domain.Block;
import com.neemre.btcdcli4j.core.domain.RawTransaction;
import com.neemre.btcdcli4j.core.domain.Transaction;
import com.neemre.btcdcli4j.core.domain.enums.ScriptTypes;
import com.neemre.btcdcli4j.daemon.BtcdDaemon;
import com.neemre.btcdcli4j.daemon.BtcdDaemonImpl;
import com.neemre.btcdcli4j.daemon.event.BlockListener;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import com.google.inject.Inject;

import javax.inject.Named;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

/**
 * Request blockchain data via RPC from Bitcoin Core.
 * Runs in a custom thread.
 * See the rpc.md file in the doc directory for more info about the setup.
 */
@Slf4j
public class RpcService {
    private final String rpcUser;
    private final String rpcPassword;
    private final String rpcPort;
    private final String rpcBlockPort;
    private final boolean dumpBlockchainData;

    private BtcdClient client;
    private BtcdDaemon daemon;
    private final ListeningExecutorService executor = Utilities.getListeningExecutorService("RpcService", 15, 30, 60);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public RpcService(@Named(DaoOptionKeys.RPC_USER) String rpcUser,
                      @Named(DaoOptionKeys.RPC_PASSWORD) String rpcPassword,
                      @Named(DaoOptionKeys.RPC_PORT) String rpcPort,
                      @Named(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT) String rpcBlockPort,
                      @Named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA) boolean dumpBlockchainData) {
        this.rpcUser = rpcUser;
        this.rpcPassword = rpcPassword;
        this.rpcPort = rpcPort;
        this.rpcBlockPort = rpcBlockPort;
        this.dumpBlockchainData = dumpBlockchainData;

    }

    public void setup(ResultHandler resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Void> future = executor.submit(() -> {
            try {
                long startTs = System.currentTimeMillis();
                PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
                CloseableHttpClient httpProvider = HttpClients.custom().setConnectionManager(cm).build();
                Properties nodeConfig = new Properties();
                nodeConfig.setProperty("node.bitcoind.rpc.protocol", "http");
                nodeConfig.setProperty("node.bitcoind.rpc.host", "127.0.0.1");
                nodeConfig.setProperty("node.bitcoind.rpc.auth_scheme", "Basic");
                nodeConfig.setProperty("node.bitcoind.rpc.user", rpcUser);
                nodeConfig.setProperty("node.bitcoind.rpc.password", rpcPassword);
                nodeConfig.setProperty("node.bitcoind.rpc.port", rpcPort);
                nodeConfig.setProperty("node.bitcoind.notification.block.port", rpcBlockPort);
                nodeConfig.setProperty("node.bitcoind.notification.alert.port", "64647");
                nodeConfig.setProperty("node.bitcoind.notification.wallet.port", "64648");
                nodeConfig.setProperty("node.bitcoind.http.auth_scheme", "Basic");
                BtcdClientImpl client = new BtcdClientImpl(httpProvider, nodeConfig);
                daemon = new BtcdDaemonImpl(client);
                log.info("Setup took {} ms", System.currentTimeMillis() - startTs);
                this.client = client;
            } catch (BitcoindException | CommunicationException e) {
                if (e instanceof CommunicationException)
                    log.error("Probably Bitcoin core is not running or the rpc port is not set correctly. rpcPort=" + rpcPort);
                log.error(e.toString());
                e.printStackTrace();
                log.error(e.getCause() != null ? e.getCause().toString() : "e.getCause()=null");
                throw new BsqBlockchainException(e.getMessage(), e);
            } catch (Throwable e) {
                log.error(e.toString());
                e.printStackTrace();
                throw new BsqBlockchainException(e.toString(), e);
            }
            return null;
        });

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void ignore) {
                UserThread.execute(resultHandler::handleResult);
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    public void registerBlockHandler(Consumer<Block> blockHandler) {
        daemon.addBlockListener(new BlockListener() {
            @Override
            public void blockDetected(Block block) {
                if (block != null) {
                    log.info("New block received: height={}, id={}", block.getHeight(), block.getHash());
                    if (block.getHeight() != null && block.getHash() != null) {
                        UserThread.execute(() -> blockHandler.accept(block));
                    } else {
                        log.warn("We received a block with block.getHeight()=null or block.getHash()=null. That should not happen.");
                    }
                } else {
                    log.warn("We received a block with value null. That should not happen.");
                }
            }
        });
    }

    public void requestChainHeadHeight(Consumer<Integer> resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Integer> future = executor.submit(client::getBlockCount);
        Futures.addCallback(future, new FutureCallback<Integer>() {
            public void onSuccess(Integer chainHeadHeight) {
                UserThread.execute(() -> resultHandler.accept(chainHeadHeight));
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });

        // return client.getBlockCount();
    }

    public void requestBlockWithAllTransactions(int blockHeight, Consumer<Tuple2<Block, List<Tx>>> resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Tuple2<Block, List<Tx>>> future = executor.submit(() -> {
            String blockHash = client.getBlockHash(blockHeight);
            Block block = client.getBlock(blockHash);
            List<Tx> txList = getTxList(block);
            return new Tuple2<>(block, txList);
        });

        Futures.addCallback(future, new FutureCallback<Tuple2<Block, List<Tx>>>() {
            @Override
            public void onSuccess(Tuple2<Block, List<Tx>> result) {
                UserThread.execute(() -> resultHandler.accept(result));
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    public void requestAllTransactionsOfBlock(Block block, Consumer<List<Tx>> resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<List<Tx>> future = executor.submit(() -> getTxList(block));

        Futures.addCallback(future, new FutureCallback<List<Tx>>() {
            @Override
            public void onSuccess(List<Tx> result) {
                UserThread.execute(() -> resultHandler.accept(result));
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    @NotNull
    private List<Tx> getTxList(Block block) throws BsqBlockchainException {
        long startTs = System.currentTimeMillis();
        List<Tx> txList = new ArrayList<>();
        int height = block.getHeight();
        // Ordering of the tx is essential! So we do not use multiple threads for requesting the txs.
        // Might be optimized in future but then we need to make sure order is correct.
        for (String txId : block.getTx()) {
            Tx tx = requestTx(txId, height);
            txList.add(tx);
        }
        log.debug("getTxList via RPC took {} ms.", (System.currentTimeMillis() - startTs));
        return txList;
    }


    // TODO only used in tests
    public void requestBlock(int blockHeight, Consumer<Block> resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Block> future = executor.submit(() -> {
            final String blockHash = client.getBlockHash(blockHeight);
            return client.getBlock(blockHash);
        });

        Futures.addCallback(future, new FutureCallback<Block>() {
            @Override
            public void onSuccess(Block block) {
                UserThread.execute(() -> resultHandler.accept(block));
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    public void requestFees(String txId, int blockHeight, Map<Integer, Long> feesByBlock) throws BsqBlockchainException {
        try {
            Transaction transaction = requestTx(txId);
            final BigDecimal fee = transaction.getFee();
            if (fee != null)
                feesByBlock.put(blockHeight, Math.abs(fee.multiply(BigDecimal.valueOf(Coin.COIN.value)).longValue()));
        } catch (BitcoindException | CommunicationException e) {
            log.error("error at requestFees with txId={}, blockHeight={}", txId, blockHeight);
            throw new BsqBlockchainException(e.getMessage(), e);
        }
    }

    public Tx requestTx(String txId, int blockHeight) throws BsqBlockchainException {
        try {
            RawTransaction rawTransaction = requestRawTransaction(txId);
            // rawTransaction.getTime() is in seconds but we keep it in ms internally
            final long time = rawTransaction.getTime() * 1000;
            final List<TxInput> txInputs = rawTransaction.getVIn()
                    .stream()
                    .filter(rawInput -> rawInput != null && rawInput.getVOut() != null && rawInput.getTxId() != null)
                    .map(rawInput -> new TxInput(rawInput.getTxId(), rawInput.getVOut()))
                    .collect(Collectors.toList());

            final List<TxOutput> txOutputs = rawTransaction.getVOut()
                    .stream()
                    .filter(e -> e != null && e.getN() != null && e.getValue() != null && e.getScriptPubKey() != null)
                    .map(rawOutput -> {
                                byte[] opReturnData = null;
                                final com.neemre.btcdcli4j.core.domain.PubKeyScript scriptPubKey = rawOutput.getScriptPubKey();
                                if (scriptPubKey.getType().equals(ScriptTypes.NULL_DATA)) {
                                    String[] chunks = scriptPubKey.getAsm().split(" ");
                                    // TODO only store BSQ OP_RETURN date filtered by type byte

                                    // We get on testnet a lot of "OP_RETURN 0" data, so we filter those away
                                    if (chunks.length == 2 && chunks[0].equals("OP_RETURN") && !"0".equals(chunks[1])) {
                                        try {
                                            opReturnData = Utils.HEX.decode(chunks[1]);
                                        } catch (Throwable t) {
                                            // We get sometimes exceptions, seems BitcoinJ
                                            // cannot handle all existing OP_RETURN data, but we ignore them
                                            // anyway as our OP_RETURN data is valid in BitcoinJ
                                            log.warn("Error at Utils.HEX.decode(chunks[1]): " + t.toString() + " / chunks[1]=" + chunks[1]);
                                        }
                                    }
                                }
                                // We don't support raw MS which are the only case where scriptPubKey.getAddresses()>1
                                String address = scriptPubKey.getAddresses() != null &&
                                        scriptPubKey.getAddresses().size() == 1 ? scriptPubKey.getAddresses().get(0) : null;
                                final PubKeyScript pubKeyScript = dumpBlockchainData ? new PubKeyScript(scriptPubKey) : null;
                                return new TxOutput(rawOutput.getN(),
                                        rawOutput.getValue().movePointRight(8).longValue(),
                                        rawTransaction.getTxId(),
                                        pubKeyScript,
                                        address,
                                        opReturnData,
                                        blockHeight);
                            }
                    )
                    .collect(Collectors.toList());

            return new Tx(txId,
                    blockHeight,
                    rawTransaction.getBlockHash(),
                    time,
                    ImmutableList.copyOf(txInputs),
                    ImmutableList.copyOf(txOutputs));
        } catch (BitcoindException | CommunicationException e) {
            log.error("error at requestTx with txId={}, blockHeight={}", txId, blockHeight);
            throw new BsqBlockchainException(e.getMessage(), e);
        }
    }

    private RawTransaction requestRawTransaction(String txId) throws BitcoindException, CommunicationException {
        return (RawTransaction) client.getRawTransaction(txId, 1);
    }

    private Transaction requestTx(String txId) throws BitcoindException, CommunicationException {
        return client.getTransaction(txId);
    }
}
