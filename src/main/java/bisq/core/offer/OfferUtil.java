/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.offer;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;
import bisq.core.util.CoinUtil;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * This class holds utility methods for the creation of an Offer.
 * Most of these are extracted here because they are used both in the GUI and in the API.
 * <p>
 * Long-term there could be a GUI-agnostic OfferService which provides these and other functionalities to both the
 * GUI and the API.
 */
@Slf4j
public class OfferUtil {

    /**
     * Given the direction, is this a BUY?
     *
     * @param direction
     * @return
     */
    public static boolean isBuyOffer(OfferPayload.Direction direction) {
        return direction == OfferPayload.Direction.BUY;
    }

    /**
     * Returns the makerFee as Coin, this can be priced in BTC or BSQ.
     *
     * @param bsqWalletService
     * @param preferences          preferences are used to see if the user indicated a preference for paying fees in BTC
     * @param amount
     * @param marketPriceAvailable
     * @param marketPriceMargin
     * @return
     */
    @Nullable
    public static Coin getMakerFee(BsqWalletService bsqWalletService, Preferences preferences, Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        final boolean isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc(preferences, bsqWalletService, amount, marketPriceAvailable, marketPriceMargin);
        return getMakerFee(isCurrencyForMakerFeeBtc,
                amount,
                marketPriceAvailable,
                marketPriceMargin);
    }

    /**
     * Calculates the maker fee for the given amount, marketPrice and marketPriceMargin.
     *
     * @param isCurrencyForMakerFeeBtc
     * @param amount
     * @param marketPriceAvailable
     * @param marketPriceMargin
     * @return
     */
    @Nullable
    public static Coin getMakerFee(boolean isCurrencyForMakerFeeBtc, @Nullable Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        if (amount != null) {
            final Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getMakerFeePerBtc(isCurrencyForMakerFeeBtc), amount);
            double makerFeeAsDouble = (double) feePerBtc.value;
            if (marketPriceAvailable) {
                if (marketPriceMargin > 0)
                    makerFeeAsDouble = makerFeeAsDouble * Math.sqrt(marketPriceMargin * 100);
                else
                    makerFeeAsDouble = 0;
                // For BTC we round so min value change is 100 satoshi
                if (isCurrencyForMakerFeeBtc)
                    makerFeeAsDouble = MathUtils.roundDouble(makerFeeAsDouble / 100, 0) * 100;
            }

            return CoinUtil.maxCoin(Coin.valueOf(MathUtils.doubleToLong(makerFeeAsDouble)), FeeService.getMinMakerFee(isCurrencyForMakerFeeBtc));
        } else {
            return null;
        }
    }


    /**
     * Checks if the maker fee should be paid in BTC, this can be the case due to user preference or because the user
     * doesn't have enough BSQ.
     *
     * @param preferences
     * @param bsqWalletService
     * @param amount
     * @param marketPriceAvailable
     * @param marketPriceMargin
     * @return
     */
    public static boolean isCurrencyForMakerFeeBtc(Preferences preferences, BsqWalletService bsqWalletService, Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        return preferences.getPayFeeInBtc() ||
                !isBsqForFeeAvailable(bsqWalletService, amount, marketPriceAvailable, marketPriceMargin);
    }

    /**
     * Checks if the available BSQ balance is sufficient to pay for the offer's maker fee.
     *
     * @param bsqWalletService
     * @param amount
     * @param marketPriceAvailable
     * @param marketPriceMargin
     * @return
     */
    public static boolean isBsqForFeeAvailable(BsqWalletService bsqWalletService, @Nullable Coin amount, boolean marketPriceAvailable, double marketPriceMargin) {
        final Coin makerFee = getMakerFee(false, amount, marketPriceAvailable, marketPriceMargin);
        final Coin availableBalance = bsqWalletService.getAvailableBalance();
        return makerFee != null &&
                BisqEnvironment.isBaseCurrencySupportingBsq() &&
                availableBalance != null &&
                !availableBalance.subtract(makerFee).isNegative();
    }

    public static Volume getRoundedFiatVolume(Volume volumeByAmount, String currencyCode) {
        // We want to get rounded to 1 unit of the fiat currency, e.g. 1 EUR.
        return getAdjustedFiatVolume(volumeByAmount, currencyCode, 1);
    }

    public static Volume getAdjustedVolumeForHalCash(Volume volumeByAmount) {
        // EUR has precision 4 and we want multiple of 10 so we divide by 100000 then
        // round and multiply with 10
        return getAdjustedFiatVolume(volumeByAmount, "EUR", 10);
    }

    private static Volume getAdjustedFiatVolume(Volume volumeByAmount, String currencyCode, int factor) {
        // Fiat currencies has precision 4 and we want multiple of 10 so we divide by 10000 * factor then
        // round and multiply with factor
        long rounded = Math.max(1, Math.round((double) volumeByAmount.getValue() / (10000d * factor)));
        // We pass EUR but any fiat currency would be good.
        return Volume.parse(String.valueOf(rounded * factor), currencyCode);
    }

    public static Coin getRoundedFiatAmount(Coin amount, Price price, String currencyCode, long maxTradeLimit) {
        return getAdjustedAmount(amount, price, maxTradeLimit, currencyCode, 1);
    }

    public static Coin getAdjustedAmountForHalCash(Coin amount, Price price, long maxTradeLimit) {
        return getAdjustedAmount(amount, price, maxTradeLimit, "EUR", 10);
    }

    private static Coin getAdjustedAmount(Coin amount, Price price, long maxTradeLimit, String currencyCode, int factor) {
        // Amount must result in a volume of min factor units of the fiat currency, e.g. 1 EUR or 10 EUR in case of Halcash
        Volume volumeRoundedToFactor = Volume.parse(String.valueOf(factor), currencyCode);
        Coin amountByVolumeRoundedToFactor = price.getAmountByVolume(volumeRoundedToFactor);
        // We set min amount so it has a volume of 10 EUR
        if (amount.compareTo(amountByVolumeRoundedToFactor) < 0)
            amount = amountByVolumeRoundedToFactor;

        // We adjust the amount so that the volume is a multiple of 10 EUR
        Volume volume = getAdjustedFiatVolume(price.getVolumeByAmount(amount), currencyCode, factor);
        amount = price.getAmountByVolume(volume);

        // We want only 4 decimal places
        long rounded = Math.round((double) amount.value / 10000d) * 10000;
        if (rounded > maxTradeLimit) {
            // If we are above out trade limit we reduce the amount by the correlating 10 EUR volume
            rounded = Math.min(maxTradeLimit, rounded - amountByVolumeRoundedToFactor.value);
        }

        return Coin.valueOf(rounded);
    }
}
