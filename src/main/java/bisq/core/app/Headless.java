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
import bisq.core.alert.PrivateNotificationPayload;
import bisq.core.arbitration.ArbitratorManager;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.dao.DaoSetup;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.setup.CorePersistedDataHost;
import bisq.core.setup.CoreSetup;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.setup.CommonSetup;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import com.google.inject.Guice;
import com.google.inject.Injector;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Headless {
    private static BisqEnvironment bisqEnvironment;
    private final AppSetupFullApp appSetupFullApp;

    protected static void setEnvironment(BisqEnvironment bisqEnvironment) {
        Headless.bisqEnvironment = bisqEnvironment;
    }

    private HeadlessModule module;
    private Injector injector;
    private boolean shutDownRequested;

    public Headless() {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("Headless")
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));


        CommonSetup.setup((throwable, doShutDown) -> {
            log.error(throwable.toString());
        });
        CoreSetup.setup(bisqEnvironment);


        module = new HeadlessModule(bisqEnvironment);
        injector = Guice.createInjector(module);

        PersistedDataHost.apply(CorePersistedDataHost.getPersistedDataHosts(injector));

        DevEnv.setup(injector);

        checkForCorrectOSArchitecture();

        appSetupFullApp = injector.getInstance(AppSetupFullApp.class);
        appSetupFullApp.start(new StartupHandler() {
            @Override
            public void onCryptoSetupError(String errorMessage) {
                log.error("onCryptoSetupError {}", errorMessage);
            }

            @Override
            public void onShowTac() {
                log.info("onShowTac");

                // First time startup we need to get TAC acceptance
                Preferences preferences = injector.getInstance(Preferences.class);
                preferences.setTacAccepted(true);
                appSetupFullApp.checkIfLocalHostNodeIsRunning();
            }

            @Override
            public void onShowAppScreen() {
                log.info("onShowAppScreen");
            }

            @Override
            public void onShowTorNetworkSettingsWindow() {
                log.info("onShowTorNetworkSettingsWindow");
            }

            @Override
            public void onHideTorNetworkSettingsWindow() {
                log.info("onHideTorNetworkSettingsWindow");
            }

            @Override
            public void onLockedUpFundsWarning(Coin balance, String addressString, String offerId) {
                log.warn("onLockedUpFundsWarning");
            }

            @Override
            public void onBtcDownloadProgress(double percentage, int peers) {
                log.info("onBtcDownloadProgress");
            }

            @Override
            public void onBtcDownloadError(int numBtcPeers) {
                log.error("onBtcDownloadError");
            }

            @Override
            public void onWalletSetupException(Throwable exception) {
                log.info("onWalletSetupException {}", exception);
            }

            @Override
            public void onShowFirstPopupIfResyncSPVRequested() {
                log.info("onShowFirstPopupIfResyncSPVRequested");
            }

            @Override
            public void onShowWalletPasswordWindow() {
                log.info("onShowWalletPasswordWindow");
            }

            @Override
            public void onShowTakeOfferRequestError(String errorMessage) {
                log.error(errorMessage);
            }

            @Override
            public void onFeeServiceInitialized() {
                log.info("onFeeServiceInitialized");
            }

            @Override
            public void onDaoSetupError(String errorMessage) {
                log.error(errorMessage);
            }

            @Override
            public void onSeedNodeBanned() {
                log.warn("onSeedNodeBanned");
            }

            @Override
            public void onPriceNodeBanned() {
                log.warn("onPriceNodeBanned");
            }

            @Override
            public void onShowSecurityRecommendation(String key) {
                log.info("onShowSecurityRecommendation");
            }

            @Override
            public void onDisplayPrivateNotification(PrivateNotificationPayload notificationPayload) {
                log.info("onDisplayPrivateNotification");
            }

            @Override
            public void onDisplayUpdateDownloadWindow(Alert alert, String key) {
                log.info("onDisplayUpdateDownloadWindow");
            }

            @Override
            public void onDisplayAlertMessageWindow(Alert alert) {
                log.info("onDisplayAlertMessageWindow");
            }

            @Override
            public void onWarnOldOffers(String offers, List<OpenOffer> outDatedOffers) {
                log.warn("onWarnOldOffers");
            }

            @Override
            public void onHalfTradePeriodReached(String shortId, Date maxTradePeriodDate) {
                log.warn("onHalfTradePeriodReached");
            }

            @Override
            public void onTradePeriodEnded(String shortId, Date maxTradePeriodDate) {
                log.warn("onTradePeriodEnded");
            }

            @Override
            public void onOfferWithoutAccountAgeWitness(Offer offer) {
                log.warn("onOfferWithoutAccountAgeWitness offerId={}", offer.getId());
            }

            @Override
            public void setTotalAvailableBalance(Coin balance) {
                log.info("TotalAvailableBalance {}", balance);
            }

            @Override
            public void setReservedBalance(Coin balance) {
                log.info("ReservedBalance {}", balance);
            }

            @Override
            public void setLockedBalance(Coin balance) {
                log.info("LockedBalance {}", balance);
            }
        });
    }


    private void checkForCorrectOSArchitecture() {
        if (!Utilities.isCorrectOSArchitecture())
            log.error("You probably have the wrong Bisq version for this computer. osArchitecture={}", Utilities.getOSArchitecture());
    }

    @SuppressWarnings("CodeBlock2Expr")
    public void stop() {
        if (!shutDownRequested) {
            //noinspection CodeBlock2Expr
            UserThread.runAfter(() -> {
                gracefulShutDown(() -> {
                    log.debug("App shutdown complete");
                    System.exit(0);
                });
            }, 200, TimeUnit.MILLISECONDS);
            shutDownRequested = true;
        }
    }

    private void gracefulShutDown(ResultHandler resultHandler) {
        try {
            if (injector != null) {
                injector.getInstance(ArbitratorManager.class).shutDown();
                injector.getInstance(TradeManager.class).shutDown();
                injector.getInstance(DaoSetup.class).shutDown();
                //noinspection CodeBlock2Expr
                injector.getInstance(OpenOfferManager.class).shutDown(() -> {
                    injector.getInstance(P2PService.class).shutDown(() -> {
                        injector.getInstance(WalletsSetup.class).shutDownComplete.addListener((ov, o, n) -> {
                            module.close(injector);
                            log.debug("Graceful shutdown completed");
                            resultHandler.handleResult();
                        });
                        injector.getInstance(WalletsSetup.class).shutDown();
                        injector.getInstance(BtcWalletService.class).shutDown();
                        injector.getInstance(BsqWalletService.class).shutDown();
                    });
                });
                // we wait max 20 sec.
                UserThread.runAfter(() -> {
                    log.warn("Timeout triggered resultHandler");
                    resultHandler.handleResult();
                }, 20);
            } else {
                log.warn("injector == null triggered resultHandler");
                UserThread.runAfter(resultHandler::handleResult, 1);
            }
        } catch (Throwable t) {
            log.error("App shutdown failed with exception");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
