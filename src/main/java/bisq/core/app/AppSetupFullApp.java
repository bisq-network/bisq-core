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

package bisq.core.app;

import bisq.core.alert.Alert;
import bisq.core.alert.AlertManager;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.arbitration.Dispute;
import bisq.core.arbitration.DisputeManager;
import bisq.core.btc.AddressEntry;
import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.dao.DaoSetup;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PerfectMoneyAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;
import bisq.core.trade.failed.FailedTradesManager;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.crypto.DecryptedDataTuple;
import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;
import bisq.network.p2p.peers.keepalive.messages.Ping;

import bisq.common.Clock;
import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.app.Version;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.SealedAndSigned;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.store.BlockStoreException;

import javax.inject.Inject;

import com.google.common.net.InetAddresses;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;

import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;

import java.security.Security;

import java.net.InetSocketAddress;
import java.net.Socket;

import java.io.IOException;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class AppSetupFullApp {
    private static final long STARTUP_TIMEOUT_MINUTES = 4;

    private final WalletsManager walletsManager;
    private final WalletsSetup walletsSetup;
    private final BtcWalletService btcWalletService;
    private final ArbitratorManager arbitratorManager;
    private final P2PService p2PService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final DisputeManager disputeManager;
    private final Preferences preferences;
    private final AlertManager alertManager;
    private final PrivateNotificationManager privateNotificationManager;
    private final FilterManager filterManager;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final Clock clock;
    private final FeeService feeService;
    private final DaoSetup daoSetup;
    private final EncryptionService encryptionService;
    private final KeyRing keyRing;
    private final BisqEnvironment bisqEnvironment;
    private final FailedTradesManager failedTradesManager;
    private final ClosedTradableManager closedTradableManager;
    private final AccountAgeWitnessService accountAgeWitnessService;

    // BTC network
    private final StringProperty btcInfo = new SimpleStringProperty(Res.get("mainView.footer.btcInfo.initializing"));
    private final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    private final BooleanProperty newVersionAvailableProperty = new SimpleBooleanProperty(false);


    // P2P network
    private final StringProperty p2PNetworkInfo = new SimpleStringProperty();
    private final BooleanProperty splashP2PNetworkAnimationVisible = new SimpleBooleanProperty(true);
    private final StringProperty p2pNetworkWarnMsg = new SimpleStringProperty();
    private final StringProperty p2PNetworkIconId = new SimpleStringProperty();
    private final BooleanProperty bootstrapComplete = new SimpleBooleanProperty();
    private final StringProperty numPendingTradesAsString = new SimpleStringProperty();
    private final BooleanProperty showPendingTradesNotification = new SimpleBooleanProperty();
    private final StringProperty numOpenDisputesAsString = new SimpleStringProperty();
    private final BooleanProperty showOpenDisputesNotification = new SimpleBooleanProperty();
    private final BooleanProperty isSplashScreenRemoved = new SimpleBooleanProperty();
    private final StringProperty p2pNetworkLabelId = new SimpleStringProperty("footer-pane");

    private final PriceFeedService priceFeedService;
    private final User user;
    private int numBtcPeers = 0;
    private Timer checkNumberOfBtcPeersTimer;
    private Timer checkNumberOfP2pNetworkPeersTimer;
    private final Map<String, Subscription> disputeIsClosedSubscriptionsMap = new HashMap<>();
    private BooleanProperty p2pNetWorkReady;
    private final BooleanProperty walletInitialized = new SimpleBooleanProperty();
    private boolean allBasicServicesInitialized;

    private StartupHandler startupHandler;
    private MonadicBinding<Boolean> allServicesDone;

    @Inject
    public AppSetupFullApp(WalletsManager walletsManager,
                           WalletsSetup walletsSetup,
                           BtcWalletService btcWalletService,
                           PriceFeedService priceFeedService,
                           ArbitratorManager arbitratorManager,
                           P2PService p2PService,
                           TradeManager tradeManager,
                           OpenOfferManager openOfferManager,
                           DisputeManager disputeManager,
                           Preferences preferences,
                           User user,
                           AlertManager alertManager,
                           PrivateNotificationManager privateNotificationManager,
                           FilterManager filterManager,
                           TradeStatisticsManager tradeStatisticsManager,
                           Clock clock,
                           FeeService feeService,
                           DaoSetup daoSetup,
                           EncryptionService encryptionService,
                           KeyRing keyRing,
                           BisqEnvironment bisqEnvironment,
                           FailedTradesManager failedTradesManager,
                           ClosedTradableManager closedTradableManager,
                           AccountAgeWitnessService accountAgeWitnessService) {

        this.walletsManager = walletsManager;
        this.walletsSetup = walletsSetup;
        this.btcWalletService = btcWalletService;
        this.priceFeedService = priceFeedService;
        this.user = user;
        this.arbitratorManager = arbitratorManager;
        this.p2PService = p2PService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.disputeManager = disputeManager;
        this.preferences = preferences;
        this.alertManager = alertManager;
        this.privateNotificationManager = privateNotificationManager;
        this.filterManager = filterManager; // Reference so it's initialized and eventListener gets registered
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.clock = clock;
        this.feeService = feeService;
        this.daoSetup = daoSetup;
        this.encryptionService = encryptionService;
        this.keyRing = keyRing;
        this.bisqEnvironment = bisqEnvironment;
        this.failedTradesManager = failedTradesManager;
        this.closedTradableManager = closedTradableManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
    }

    public void start(StartupHandler startupHandler) {
        // We do the delete of the spv file at startup before BitcoinJ is initialized to avoid issues with locked files under Windows.
        if (preferences.isResyncSpvRequested()) {
            try {
                walletsSetup.reSyncSPVChain();
            } catch (IOException e) {
                log.error(e.toString());
                e.printStackTrace();
            }
        }

        this.startupHandler = startupHandler;
        if (!preferences.isTacAccepted() && !DevEnv.isDevMode()) {
            startupHandler.onShowTac();
        } else {
            checkIfLocalHostNodeIsRunning();
        }
    }

    public void checkIfLocalHostNodeIsRunning() {
        Thread checkIfLocalHostNodeIsRunningThread = new Thread(() -> {
            Thread.currentThread().setName("checkIfLocalHostNodeIsRunningThread");
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddresses.forString("127.0.0.1"),
                        BisqEnvironment.getBaseCurrencyNetwork().getParameters().getPort()), 5000);
                log.info("Localhost peer detected.");
                UserThread.execute(() -> {
                    bisqEnvironment.setBitcoinLocalhostNodeRunning(true);
                    readMapsFromResources();
                });
            } catch (Throwable e) {
                log.info("Localhost peer not detected.");
                UserThread.execute(this::readMapsFromResources);
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignore) {
                    }
                }
            }
        });
        checkIfLocalHostNodeIsRunningThread.start();
    }

    private void readMapsFromResources() {
        SetupUtils.readFromResources(p2PService.getP2PDataStorage()).addListener((observable, oldValue, newValue) -> {
            if (newValue)
                startBasicServices();
        });

        // TODO can be removed in jdk 9
        checkCryptoSetup();
    }

    private void checkCryptoSetup() {
        BooleanProperty result = new SimpleBooleanProperty();
        // We want to test if the client is compiled with the correct crypto provider (BountyCastle)
        // and if the unlimited Strength for cryptographic keys is set.
        // If users compile themselves they might miss that step and then would get an exception in the trade.
        // To avoid that we add here at startup a sample encryption and signing to see if it don't causes an exception.
        // See: https://github.com/bisq-network/exchange/blob/master/doc/build.md#7-enable-unlimited-strength-for-cryptographic-keys
        Thread checkCryptoThread = new Thread(() -> {
            try {
                Thread.currentThread().setName("checkCryptoThread");
                log.trace("Run crypto test");
                // just use any simple dummy msg
                Ping payload = new Ping(1, 1);
                SealedAndSigned sealedAndSigned = EncryptionService.encryptHybridWithSignature(payload,
                        keyRing.getSignatureKeyPair(), keyRing.getPubKeyRing().getEncryptionPubKey());
                DecryptedDataTuple tuple = encryptionService.decryptHybridWithSignature(sealedAndSigned, keyRing.getEncryptionKeyPair().getPrivate());
                if (tuple.getNetworkEnvelope() instanceof Ping &&
                        ((Ping) tuple.getNetworkEnvelope()).getNonce() == payload.getNonce() &&
                        ((Ping) tuple.getNetworkEnvelope()).getLastRoundTripTime() == payload.getLastRoundTripTime()) {
                    log.debug("Crypto test succeeded");

                    if (Security.getProvider("BC") != null) {
                        UserThread.execute(() -> result.set(true));
                    } else {
                        throw new CryptoException("Security provider BountyCastle is not available.");
                    }
                } else {
                    throw new CryptoException("Payload not correct after decryption");
                }
            } catch (CryptoException e) {
                e.printStackTrace();
                String msg = Res.get("popup.warning.cryptoTestFailed", e.getMessage());
                log.error(msg);
                startupHandler.onCryptoSetupError(msg);
            }
        });
        checkCryptoThread.start();
    }

    private void startBasicServices() {
        log.info("startBasicServices");

        ChangeListener<Boolean> walletInitializedListener = (observable, oldValue, newValue) -> {
            // TODO that seems to be called too often if Tor takes longer to start up...
            if (newValue && !p2pNetWorkReady.get())
                startupHandler.onShowTorNetworkSettingsWindow();
        };

        Timer startupTimeout = UserThread.runAfter(() -> {
            log.warn("startupTimeout called");
            if (walletsManager.areWalletsEncrypted())
                walletInitialized.addListener(walletInitializedListener);
            else
                startupHandler.onShowTorNetworkSettingsWindow();
        }, STARTUP_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        p2pNetWorkReady = initP2PNetwork();

        // We only init wallet service here if not using Tor for bitcoinj.
        // When using Tor, wallet init must be deferred until Tor is ready.
        if (!preferences.getUseTorForBitcoinJ() || bisqEnvironment.isBitcoinLocalhostNodeRunning())
            initWalletService();

        // need to store it to not get garbage collected
        allServicesDone = EasyBind.combine(walletInitialized, p2pNetWorkReady,
                (a, b) -> {
                    log.debug("\nwalletInitialized={}\n" +
                                    "p2pNetWorkReady={}",
                            a, b);
                    return a && b;
                });
        allServicesDone.subscribe((observable, oldValue, newValue) -> {
            if (newValue) {
                startupTimeout.stop();
                walletInitialized.removeListener(walletInitializedListener);
                onBasicServicesInitialized();
                startupHandler.onHideTorNetworkSettingsWindow();

            }
        });
    }

    private BooleanProperty initP2PNetwork() {
        log.info("initP2PNetwork");

        StringProperty bootstrapState = new SimpleStringProperty();
        StringProperty bootstrapWarning = new SimpleStringProperty();
        BooleanProperty hiddenServicePublished = new SimpleBooleanProperty();
        BooleanProperty initialP2PNetworkDataReceived = new SimpleBooleanProperty();

        MonadicBinding<String> p2PNetworkInfoBinding = EasyBind.combine(bootstrapState, bootstrapWarning, p2PService.getNumConnectedPeers(), hiddenServicePublished, initialP2PNetworkDataReceived,
                (state, warning, numPeers, hiddenService, dataReceived) -> {
                    String result;
                    int peers = (int) numPeers;
                    if (warning != null && peers == 0) {
                        result = warning;
                    } else {
                        String p2pInfo = Res.get("mainView.footer.p2pInfo", numPeers);
                        if (dataReceived && hiddenService) {
                            result = p2pInfo;
                        } else if (peers == 0)
                            result = state;
                        else
                            result = state + " / " + p2pInfo;
                    }
                    return result;
                });
        p2PNetworkInfoBinding.subscribe((observable, oldValue, newValue) -> {
            p2PNetworkInfo.set(newValue);
        });

        bootstrapState.set(Res.get("mainView.bootstrapState.connectionToTorNetwork"));

        p2PService.getNetworkNode().addConnectionListener(new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
            }

            @Override
            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
                // We only check at seed nodes as they are running the latest version
                // Other disconnects might be caused by peers running an older version
                if (connection.getPeerType() == Connection.PeerType.SEED_NODE &&
                        closeConnectionReason == CloseConnectionReason.RULE_VIOLATION) {
                    log.warn("RULE_VIOLATION onDisconnect closeConnectionReason=" + closeConnectionReason);
                    log.warn("RULE_VIOLATION onDisconnect connection=" + connection);
                }
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        final BooleanProperty p2pNetworkInitialized = new SimpleBooleanProperty();
        p2PService.start(new P2PServiceListener() {
            @Override
            public void onTorNodeReady() {
                log.debug("onTorNodeReady");
                bootstrapState.set(Res.get("mainView.bootstrapState.torNodeCreated"));
                p2PNetworkIconId.set("image-connection-tor");

                if (preferences.getUseTorForBitcoinJ())
                    initWalletService();

                // We want to get early connected to the price relay so we call it already now
                priceFeedService.setCurrencyCodeOnInit();
                priceFeedService.initialRequestPriceFeed();
            }

            @Override
            public void onHiddenServicePublished() {
                log.debug("onHiddenServicePublished");
                hiddenServicePublished.set(true);
                bootstrapState.set(Res.get("mainView.bootstrapState.hiddenServicePublished"));
            }

            @Override
            public void onDataReceived() {
                log.debug("onRequestingDataCompleted");
                initialP2PNetworkDataReceived.set(true);
                bootstrapState.set(Res.get("mainView.bootstrapState.initialDataReceived"));
                splashP2PNetworkAnimationVisible.set(false);
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoSeedNodeAvailable() {
                log.warn("onNoSeedNodeAvailable");
                if (p2PService.getNumConnectedPeers().get() == 0)
                    bootstrapWarning.set(Res.get("mainView.bootstrapWarning.noSeedNodesAvailable"));
                else
                    bootstrapWarning.set(null);

                splashP2PNetworkAnimationVisible.set(false);
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoPeersAvailable() {
                log.warn("onNoPeersAvailable");
                if (p2PService.getNumConnectedPeers().get() == 0) {
                    p2pNetworkWarnMsg.set(Res.get("mainView.p2pNetworkWarnMsg.noNodesAvailable"));
                    bootstrapWarning.set(Res.get("mainView.bootstrapWarning.noNodesAvailable"));
                    p2pNetworkLabelId.set("splash-error-state-msg");
                } else {
                    bootstrapWarning.set(null);
                    p2pNetworkLabelId.set("footer-pane");
                }
                splashP2PNetworkAnimationVisible.set(false);
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onUpdatedDataReceived() {
                log.debug("onBootstrapComplete");
                splashP2PNetworkAnimationVisible.set(false);
                bootstrapComplete.set(true);
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                log.warn("onSetupFailed");
                p2pNetworkWarnMsg.set(Res.get("mainView.p2pNetworkWarnMsg.connectionToP2PFailed", throwable.getMessage()));
                splashP2PNetworkAnimationVisible.set(false);
                bootstrapWarning.set(Res.get("mainView.bootstrapWarning.bootstrappingToP2PFailed"));
                p2pNetworkLabelId.set("splash-error-state-msg");
            }

            @Override
            public void onRequestCustomBridges() {
                startupHandler.onShowTorNetworkSettingsWindow();
            }
        });

        return p2pNetworkInitialized;
    }

    private void checkForLockedUpFunds() {
        Set<String> tradesIdSet = tradeManager.getLockedTradesStream()
                .filter(Trade::hasFailed)
                .map(Trade::getId)
                .collect(Collectors.toSet());
        tradesIdSet.addAll(failedTradesManager.getLockedTradesStream()
                .map(Trade::getId)
                .collect(Collectors.toSet()));
        tradesIdSet.addAll(closedTradableManager.getLockedTradesStream()
                .map(e -> {
                    log.warn("We found a closed trade with locked up funds. " +
                            "That should never happen. trade ID=" + e.getId());
                    return e.getId();
                })
                .collect(Collectors.toSet()));

        btcWalletService.getAddressEntriesForTrade().stream()
                .filter(e -> tradesIdSet.contains(e.getOfferId()) && e.getContext() == AddressEntry.Context.MULTI_SIG)
                .forEach(e -> {
                    final Coin balance = e.getCoinLockedInMultiSig();
                    startupHandler.onLockedUpFundsWarning(balance, e.getAddressString(), e.getOfferId());
                });
    }

    private void initWalletService() {
        log.info("initWalletService");

        ObjectProperty<Throwable> walletServiceException = new SimpleObjectProperty<>();
        MonadicBinding<String> btcInfoBinding = EasyBind.combine(walletsSetup.downloadPercentageProperty(), walletsSetup.numPeersProperty(), walletServiceException,
                (downloadPercentage, numPeers, exception) -> {
                    if (exception == null) {
                        double percentage = (double) downloadPercentage;
                        int peers = (int) numPeers;
                        startupHandler.onBtcDownloadProgress(percentage, peers);
                        if (percentage == 1) {
                            if (allBasicServicesInitialized)
                                checkForLockedUpFunds();
                        }
                    } else {
                        startupHandler.onBtcDownloadError(numBtcPeers);
                        log.error(exception.getMessage());
                        if (exception instanceof TimeoutException) {
                            walletServiceErrorMsg.set(Res.get("mainView.walletServiceErrorMsg.timeout"));
                        } else if (exception.getCause() instanceof BlockStoreException) {
                            startupHandler.onWalletSetupException(exception);
                        } else {
                            walletServiceErrorMsg.set(Res.get("mainView.walletServiceErrorMsg.connectionError", exception.toString()));
                        }
                    }
                    //TODO
                    return "";

                });

        //TODO remove?
        btcInfoBinding.subscribe((observable, oldValue, newValue) -> {
            btcInfo.set(newValue);
        });

        walletsSetup.initialize(null,
                () -> {
                    log.debug("walletsSetup.onInitialized");
                    numBtcPeers = walletsSetup.numPeersProperty().get();

                    // We only check one as we apply encryption to all or none
                    if (walletsManager.areWalletsEncrypted()) {
                        if (p2pNetWorkReady.get())
                            splashP2PNetworkAnimationVisible.set(false);

                        startupHandler.onShowWalletPasswordWindow();
                    } else {
                        if (preferences.isResyncSpvRequested()) {
                            startupHandler.onShowFirstPopupIfResyncSPVRequested();
                        } else {
                            walletInitialized.set(true);
                        }
                    }
                },
                walletServiceException::set);
    }


    private void onBasicServicesInitialized() {
        log.info("onBasicServicesInitialized");

        clock.start();

        PaymentMethod.onAllServicesInitialized();

        // disputeManager
        disputeManager.onAllServicesInitialized();
        disputeManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) change -> {
            change.next();
            onDisputesChangeListener(change.getAddedSubList(), change.getRemoved());
        });
        onDisputesChangeListener(disputeManager.getDisputesAsObservableList(), null);

        // tradeManager
        tradeManager.onAllServicesInitialized();
        tradeManager.getTradableList().addListener((ListChangeListener<Trade>) c -> updateBalance());
        tradeManager.getTradableList().addListener((ListChangeListener<Trade>) change -> onTradesChanged());
        onTradesChanged();
        // We handle the trade period here as we display a global popup if we reached dispute time
        MonadicBinding<Boolean> tradesAndUIReady = EasyBind.combine(isSplashScreenRemoved, tradeManager.pendingTradesInitializedProperty(), (a, b) -> a && b);
        tradesAndUIReady.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                applyTradePeriodState();
        });
        tradeManager.setTakeOfferRequestErrorMessageHandler(errorMessage -> startupHandler.onShowTakeOfferRequestError(errorMessage));


        // walletService
        btcWalletService.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();
            }
        });

        openOfferManager.getObservableList().addListener((ListChangeListener<OpenOffer>) c -> updateBalance());
        tradeManager.getTradableList().addListener((ListChangeListener<Trade>) c -> updateBalance());
        openOfferManager.onAllServicesInitialized();
        removeOffersWithoutAccountAgeWitness();

        arbitratorManager.onAllServicesInitialized();
        alertManager.alertMessageProperty().addListener((observable, oldValue, newValue) -> displayAlertIfPresent(newValue, false));
        privateNotificationManager.privateNotificationProperty().addListener((observable, oldValue, newValue) -> startupHandler.onDisplayPrivateNotification(newValue));
        displayAlertIfPresent(alertManager.alertMessageProperty().get(), false);

        p2PService.onAllServicesInitialized();

        feeService.onAllServicesInitialized();
        startupHandler.onFeeServiceInitialized();

        daoSetup.onAllServicesInitialized(errorMessage -> startupHandler.onDaoSetupError(errorMessage));

        tradeStatisticsManager.onAllServicesInitialized();

        accountAgeWitnessService.onAllServicesInitialized();

        priceFeedService.setCurrencyCodeOnInit();

        filterManager.onAllServicesInitialized();
        filterManager.addListener(filter -> {
            if (filter != null) {
                if (filter.getSeedNodes() != null && !filter.getSeedNodes().isEmpty())
                    startupHandler.onSeedNodeBanned();

                if (filter.getPriceRelayNodes() != null && !filter.getPriceRelayNodes().isEmpty())
                    startupHandler.onPriceNodeBanned();
            }
        });

        setupBtcNumPeersWatcher();
        setupP2PNumPeersWatcher();
        updateBalance();
        if (DevEnv.isDevMode()) {
            preferences.setShowOwnOffersInOfferBook(true);
            setupDevDummyPaymentAccounts();
        }


        swapPendingOfferFundingEntries();

        startupHandler.onShowAppScreen();

        String key = "remindPasswordAndBackup";
        user.getPaymentAccountsAsObservable().addListener((SetChangeListener<PaymentAccount>) change -> {
            if (!walletsManager.areWalletsEncrypted() && preferences.showAgain(key) && change.wasAdded())
                startupHandler.onShowSecurityRecommendation(key);
        });

        checkIfOpenOffersMatchTradeProtocolVersion();

        if (walletsSetup.downloadPercentageProperty().get() == 1)
            checkForLockedUpFunds();

        allBasicServicesInitialized = true;
    }

    private void swapPendingOfferFundingEntries() {
        tradeManager.getAddressEntriesForAvailableBalanceStream()
                .filter(addressEntry -> addressEntry.getOfferId() != null)
                .forEach(addressEntry -> {
                    log.debug("swapPendingOfferFundingEntries, offerId={}, OFFER_FUNDING", addressEntry.getOfferId());
                    btcWalletService.swapTradeEntryToAvailableEntry(addressEntry.getOfferId(), AddressEntry.Context.OFFER_FUNDING);
                });
    }

    private void setupP2PNumPeersWatcher() {
        p2PService.getNumConnectedPeers().addListener((observable, oldValue, newValue) -> {
            int numPeers = (int) newValue;
            if ((int) oldValue > 0 && numPeers == 0) {
                // give a bit of tolerance
                if (checkNumberOfP2pNetworkPeersTimer != null)
                    checkNumberOfP2pNetworkPeersTimer.stop();

                checkNumberOfP2pNetworkPeersTimer = UserThread.runAfter(() -> {
                    // check again numPeers
                    if (p2PService.getNumConnectedPeers().get() == 0) {
                        p2pNetworkWarnMsg.set(Res.get("mainView.networkWarning.allConnectionsLost", Res.get("shared.P2P")));
                        p2pNetworkLabelId.set("splash-error-state-msg");
                    } else {
                        p2pNetworkWarnMsg.set(null);
                        p2pNetworkLabelId.set("footer-pane");
                    }
                }, 5);
            } else if ((int) oldValue == 0 && numPeers > 0) {
                if (checkNumberOfP2pNetworkPeersTimer != null)
                    checkNumberOfP2pNetworkPeersTimer.stop();

                p2pNetworkWarnMsg.set(null);
                p2pNetworkLabelId.set("footer-pane");
            }
        });
    }

    private void setupBtcNumPeersWatcher() {
        walletsSetup.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            int numPeers = (int) newValue;
            if ((int) oldValue > 0 && numPeers == 0) {
                if (checkNumberOfBtcPeersTimer != null)
                    checkNumberOfBtcPeersTimer.stop();

                checkNumberOfBtcPeersTimer = UserThread.runAfter(() -> {
                    // check again numPeers
                    if (walletsSetup.numPeersProperty().get() == 0) {
                        if (bisqEnvironment.isBitcoinLocalhostNodeRunning())
                            walletServiceErrorMsg.set(Res.get("mainView.networkWarning.localhostBitcoinLost", Res.getBaseCurrencyName().toLowerCase()));
                        else
                            walletServiceErrorMsg.set(Res.get("mainView.networkWarning.allConnectionsLost", Res.getBaseCurrencyName().toLowerCase()));
                    } else {
                        walletServiceErrorMsg.set(null);
                    }
                }, 5);
            } else if ((int) oldValue == 0 && numPeers > 0) {
                if (checkNumberOfBtcPeersTimer != null)
                    checkNumberOfBtcPeersTimer.stop();
                walletServiceErrorMsg.set(null);
            }
        });
    }

    private void displayAlertIfPresent(Alert alert, boolean openNewVersionPopup) {
        if (alert != null) {
            if (alert.isUpdateInfo()) {
                user.setDisplayedAlert(alert);
                final boolean isNewVersion = alert.isNewVersion();
                newVersionAvailableProperty.set(isNewVersion);
                String key = "Update_" + alert.getVersion();
                if (isNewVersion && (preferences.showAgain(key) || openNewVersionPopup))
                    startupHandler.onDisplayUpdateDownloadWindow(alert, key);
            } else {
                final Alert displayedAlert = user.getDisplayedAlert();
                if (displayedAlert == null || !displayedAlert.equals(alert))
                    startupHandler.onDisplayAlertMessageWindow(alert);
            }
        }
    }

    private void updateBalance() {
        // Without delaying to the next cycle it does not update.
        // Seems order of events we are listening on causes that...
        UserThread.execute(() -> {
            updateAvailableBalance();
            updateReservedBalance();
            updateLockedBalance();
        });
    }

    private void updateAvailableBalance() {
        Coin balance = Coin.valueOf(tradeManager.getAddressEntriesForAvailableBalanceStream()
                .mapToLong(addressEntry -> btcWalletService.getBalanceForAddress(addressEntry.getAddress()).getValue())
                .sum());
        startupHandler.setTotalAvailableBalance(balance);
    }

    private void updateReservedBalance() {
        Coin balance = Coin.valueOf(openOfferManager.getObservableList().stream()
                .map(openOffer -> {
                    final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(openOffer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);
                    if (addressEntryOptional.isPresent()) {
                        Address address = addressEntryOptional.get().getAddress();
                        return btcWalletService.getBalanceForAddress(address);
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .mapToLong(Coin::getValue)
                .sum());

        startupHandler.setReservedBalance(balance);
    }

    private void updateLockedBalance() {
        Stream<Trade> lockedTrades = Stream.concat(closedTradableManager.getLockedTradesStream(), failedTradesManager.getLockedTradesStream());
        lockedTrades = Stream.concat(lockedTrades, tradeManager.getLockedTradesStream());
        Coin balance = Coin.valueOf(lockedTrades
                .mapToLong(trade -> {
                    final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(trade.getId(), AddressEntry.Context.MULTI_SIG);
                    return addressEntryOptional.map(addressEntry -> addressEntry.getCoinLockedInMultiSig().getValue()).orElse(0L);
                })
                .sum());

        startupHandler.setLockedBalance(balance);
    }

    private void checkIfOpenOffersMatchTradeProtocolVersion() {
        List<OpenOffer> outDatedOffers = openOfferManager.getObservableList()
                .stream()
                .filter(e -> e.getOffer().getProtocolVersion() != Version.TRADE_PROTOCOL_VERSION)
                .collect(Collectors.toList());
        if (!outDatedOffers.isEmpty()) {
            String offers = outDatedOffers.stream()
                    .map(e -> e.getId() + "\n")
                    .collect(Collectors.toList()).toString()
                    .replace("[", "").replace("]", "");

            startupHandler.onWarnOldOffers(offers, outDatedOffers);
        }
    }

    private void applyTradePeriodState() {
        updateTradePeriodState();
        clock.addListener(new Clock.Listener() {
            @Override
            public void onSecondTick() {
            }

            @Override
            public void onMinuteTick() {
                updateTradePeriodState();
            }

            @Override
            public void onMissedSecondTick(long missed) {
            }
        });
    }

    private void updateTradePeriodState() {
        tradeManager.getTradableList().forEach(trade -> {
            if (!trade.isPayoutPublished()) {
                Date maxTradePeriodDate = trade.getMaxTradePeriodDate();
                Date halfTradePeriodDate = trade.getHalfTradePeriodDate();
                if (maxTradePeriodDate != null && halfTradePeriodDate != null) {
                    Date now = new Date();
                    if (now.after(maxTradePeriodDate))
                        trade.setTradePeriodState(Trade.TradePeriodState.TRADE_PERIOD_OVER);
                    else if (now.after(halfTradePeriodDate))
                        trade.setTradePeriodState(Trade.TradePeriodState.SECOND_HALF);

                    String key;
                    switch (trade.getTradePeriodState()) {
                        case FIRST_HALF:
                            break;
                        case SECOND_HALF:
                            key = "displayHalfTradePeriodOver" + trade.getId();
                            if (DontShowAgainLookup.showAgain(key)) {
                                DontShowAgainLookup.dontShowAgain(key, true);
                                startupHandler.onHalfTradePeriodReached(trade.getShortId(), maxTradePeriodDate);
                            }
                            break;
                        case TRADE_PERIOD_OVER:
                            key = "displayTradePeriodOver" + trade.getId();
                            if (DontShowAgainLookup.showAgain(key)) {
                                DontShowAgainLookup.dontShowAgain(key, true);
                                startupHandler.onTradePeriodEnded(trade.getShortId(), maxTradePeriodDate);
                            }
                            break;
                    }
                }
            }
        });
    }


    private void onDisputesChangeListener(List<? extends Dispute> addedList, @Nullable List<? extends
            Dispute> removedList) {
        if (removedList != null) {
            removedList.forEach(dispute -> {
                String id = dispute.getId();
                if (disputeIsClosedSubscriptionsMap.containsKey(id)) {
                    disputeIsClosedSubscriptionsMap.get(id).unsubscribe();
                    disputeIsClosedSubscriptionsMap.remove(id);
                }
            });
        }
        addedList.forEach(dispute -> {
            String id = dispute.getId();
            Subscription disputeStateSubscription = EasyBind.subscribe(dispute.isClosedProperty(),
                    isClosed -> {
                        // We get event before list gets updated, so we execute on next frame
                        UserThread.execute(() -> {
                            int openDisputes = disputeManager.getDisputesAsObservableList().stream()
                                    .filter(e -> !e.isClosed())
                                    .collect(Collectors.toList()).size();
                            if (openDisputes > 0)
                                numOpenDisputesAsString.set(String.valueOf(openDisputes));
                            if (openDisputes > 9)
                                numOpenDisputesAsString.set("★");

                            showOpenDisputesNotification.set(openDisputes > 0);
                        });
                    });
            disputeIsClosedSubscriptionsMap.put(id, disputeStateSubscription);
        });
    }

    private void onTradesChanged() {
        long numPendingTrades = tradeManager.getTradableList().size();
        if (numPendingTrades > 0)
            numPendingTradesAsString.set(String.valueOf(numPendingTrades));
        if (numPendingTrades > 9)
            numPendingTradesAsString.set("★");

        showPendingTradesNotification.set(numPendingTrades > 0);
    }

    private void removeOffersWithoutAccountAgeWitness() {
        if (new Date().after(AccountAgeWitnessService.FULL_ACTIVATION)) {
            openOfferManager.getObservableList().stream()
                    .filter(e -> CurrencyUtil.isFiatCurrency(e.getOffer().getCurrencyCode()))
                    .filter(e -> !e.getOffer().getAccountAgeWitnessHashAsHex().isPresent())
                    .forEach(e -> {
                        startupHandler.onOfferWithoutAccountAgeWitness(e.getOffer());

                    });
        }
    }

    private void setupDevDummyPaymentAccounts() {
        if (user.getPaymentAccounts() != null && user.getPaymentAccounts().isEmpty()) {
            PerfectMoneyAccount perfectMoneyAccount = new PerfectMoneyAccount();
            perfectMoneyAccount.init();
            perfectMoneyAccount.setAccountNr("dummy_" + new Random().nextInt(100));
            perfectMoneyAccount.setAccountName("PerfectMoney dummy");// Don't translate only for dev
            perfectMoneyAccount.setSelectedTradeCurrency(GlobalSettings.getDefaultTradeCurrency());
            user.addPaymentAccount(perfectMoneyAccount);

            if (p2PService.isBootstrapped()) {
                accountAgeWitnessService.publishMyAccountAgeWitness(perfectMoneyAccount.getPaymentAccountPayload());
            } else {
                p2PService.addP2PServiceListener(new BootstrapListener() {
                    @Override
                    public void onUpdatedDataReceived() {
                        accountAgeWitnessService.publishMyAccountAgeWitness(perfectMoneyAccount.getPaymentAccountPayload());
                    }
                });
            }

            CryptoCurrencyAccount cryptoCurrencyAccount = new CryptoCurrencyAccount();
            cryptoCurrencyAccount.init();
            cryptoCurrencyAccount.setAccountName("ETH dummy");// Don't translate only for dev
            cryptoCurrencyAccount.setAddress("0x" + new Random().nextInt(1000000));
            cryptoCurrencyAccount.setSingleTradeCurrency(CurrencyUtil.getCryptoCurrency("ETH").get());
            user.addPaymentAccount(cryptoCurrencyAccount);
        }
    }
}
