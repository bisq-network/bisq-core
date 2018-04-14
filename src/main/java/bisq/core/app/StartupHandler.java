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
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;

import org.bitcoinj.core.Coin;

import java.util.Date;
import java.util.List;

public interface StartupHandler {
    void onCryptoSetupError(String errorMessage);

    void onShowTac();

    void onShowTorNetworkSettingsWindow();

    void onHideTorNetworkSettingsWindow();

    void onLockedUpFundsWarning(Coin balance, String addressString, String offerId);

    void onBtcDownloadProgress(double percentage, int peers);

    void onBtcDownloadError(int numBtcPeers);

    void onWalletSetupException(Throwable exception);

    void onShowFirstPopupIfResyncSPVRequested();

    void onShowWalletPasswordWindow();

    void onShowTakeOfferRequestError(String errorMessage);

    void onFeeServiceInitialized();

    void onDaoSetupError(String errorMessage);

    void onSeedNodeBanned();

    void onPriceNodeBanned();

    void onShowSecurityRecommendation(String key);

    void onDisplayPrivateNotification(PrivateNotificationPayload notificationPayload);

    void onDisplayUpdateDownloadWindow(Alert alert, String key);

    void onDisplayAlertMessageWindow(Alert alert);

    void onWarnOldOffers(String offers, List<OpenOffer> outDatedOffers);

    void onHalfTradePeriodReached(String shortId, Date maxTradePeriodDate);

    void onTradePeriodEnded(String shortId, Date maxTradePeriodDate);

    void onOfferWithoutAccountAgeWitness(Offer offer);

    void setTotalAvailableBalance(Coin balance);

    void setReservedBalance(Coin balance);

    void setLockedBalance(Coin balance);

    void onShowAppScreen();

}
