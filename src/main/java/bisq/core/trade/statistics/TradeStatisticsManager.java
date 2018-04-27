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

package bisq.core.trade.statistics;

import bisq.core.app.AppOptionKeys;
import bisq.core.locale.CurrencyTuple;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Trade;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;
import bisq.common.storage.JsonFileManager;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.io.File;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeStatisticsManager {

    static TradeStatistics2 ConvertToTradeStatistics2(TradeStatistics tradeStatistics) {
        return new TradeStatistics2(tradeStatistics.getDirection(),
                tradeStatistics.getBaseCurrency(),
                tradeStatistics.getCounterCurrency(),
                tradeStatistics.getOfferPaymentMethod(),
                tradeStatistics.getOfferDate(),
                tradeStatistics.isOfferUseMarketBasedPrice(),
                tradeStatistics.getOfferMarketPriceMargin(),
                tradeStatistics.getOfferAmount(),
                tradeStatistics.getOfferMinAmount(),
                tradeStatistics.getOfferId(),
                tradeStatistics.getTradePrice().getValue(),
                tradeStatistics.getTradeAmount().getValue(),
                tradeStatistics.getTradeDate().getTime(),
                tradeStatistics.getDepositTxId(),
                null,
                tradeStatistics.getExtraDataMap());
    }

    private final JsonFileManager jsonFileManager;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final boolean dumpStatistics;
    private final ObservableSet<TradeStatistics2> observableTradeStatisticsSet = FXCollections.observableSet();
    private final HashSet<TradeStatistics2> tradeStatisticsSet = new HashSet<>();

    @Inject
    public TradeStatisticsManager(P2PService p2PService,
                                  PriceFeedService priceFeedService,
                                  TradeStatistics2StorageService tradeStatistics2StorageService,
                                  AppendOnlyDataStoreService appendOnlyDataStoreService,
                                  @Named(Storage.STORAGE_DIR) File storageDir,
                                  @Named(AppOptionKeys.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.dumpStatistics = dumpStatistics;
        jsonFileManager = new JsonFileManager(storageDir);

        appendOnlyDataStoreService.addService(tradeStatistics2StorageService);
    }

    public void onAllServicesInitialized() {
        if (dumpStatistics) {
            ArrayList<CurrencyTuple> fiatCurrencyList = new ArrayList<>(CurrencyUtil.getAllSortedFiatCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toList()));
            jsonFileManager.writeToDisc(Utilities.objectToJson(fiatCurrencyList), "fiat_currency_list");

            ArrayList<CurrencyTuple> cryptoCurrencyList = new ArrayList<>(CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toList()));
            cryptoCurrencyList.add(0, new CurrencyTuple(Res.getBaseCurrencyCode(), Res.getBaseCurrencyName(), 8));
            jsonFileManager.writeToDisc(Utilities.objectToJson(cryptoCurrencyList), "crypto_currency_list");
        }

        p2PService.getP2PDataStorage().addPersistableNetworkPayloadMapListener(payload -> {
            if (payload instanceof TradeStatistics2)
                addToMap((TradeStatistics2) payload, true);
        });

        p2PService.getP2PDataStorage().getPersistableNetworkPayloadList().getMap().values().forEach(e -> {
            if (e instanceof TradeStatistics2)
                addToMap((TradeStatistics2) e, false);
        });

        //TODO can be removed after version older than v0.6.0 are not used anymore
        // We listen to TradeStatistics objects from old clients as well and convert them into TradeStatistics2 objects
        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
                if (protectedStoragePayload instanceof TradeStatistics)
                    p2PService.getP2PDataStorage().addPersistableNetworkPayload(ConvertToTradeStatistics2((TradeStatistics) protectedStoragePayload),
                            p2PService.getNetworkNode().getNodeAddress(), true, false, false, false);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                // We don't remove items
            }
        });

        priceFeedService.applyLatestBisqMarketPrice(tradeStatisticsSet);
        dump();

        // print all currencies sorted by nr. of trades
        // printAllCurrencyStats();
    }

    public void publishTradeStatistics(List<Trade> trades) {
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            TradeStatistics2 tradeStatistics = new TradeStatistics2(trade.getOffer().getOfferPayload(),
                    trade.getTradePrice(),
                    trade.getTradeAmount(),
                    trade.getDate(),
                    (trade.getDepositTx() != null ? trade.getDepositTx().getHashAsString() : ""));
            addToMap(tradeStatistics, true);

            // We only republish trades from last 10 days
            if ((new Date().getTime() - trade.getDate().getTime()) < TimeUnit.DAYS.toMillis(10)) {
                long delay = 5000;
                long minDelay = (i + 1) * delay;
                long maxDelay = (i + 2) * delay;
                UserThread.runAfterRandomDelay(() -> {
                    p2PService.addPersistableNetworkPayload(tradeStatistics, true);
                }, minDelay, maxDelay, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void addToMap(TradeStatistics2 tradeStatistics, boolean storeLocally) {
        if (!tradeStatisticsSet.contains(tradeStatistics)) {
            boolean itemAlreadyAdded = tradeStatisticsSet.stream().filter(e -> (e.getOfferId().equals(tradeStatistics.getOfferId()))).findAny().isPresent();
            if (!itemAlreadyAdded) {
                tradeStatisticsSet.add(tradeStatistics);
                observableTradeStatisticsSet.add(tradeStatistics);

                tradeStatistics.getTradePrice().getValue();

                if (storeLocally) {
                    priceFeedService.applyLatestBisqMarketPrice(tradeStatisticsSet);
                    dump();
                }
            } else {
                log.debug("We have already an item with the same offer ID. That might happen if both the maker and the taker published the tradeStatistics");
            }
        }
    }

    public ObservableSet<TradeStatistics2> getObservableTradeStatisticsSet() {
        return observableTradeStatisticsSet;
    }

    private void dump() {
        if (dumpStatistics) {
            // We store the statistics as json so it is easy for further processing (e.g. for web based services)
            // TODO This is just a quick solution for storing to one file.
            // 1 statistic entry has 500 bytes as json.
            // Need a more scalable solution later when we get more volume.
            // The flag will only be activated by dedicated nodes, so it should not be too critical for the moment, but needs to
            // get improved. Maybe a LevelDB like DB...? Could be impl. in a headless version only.
            List<TradeStatisticsForJson> list = tradeStatisticsSet.stream().map(TradeStatisticsForJson::new).collect(Collectors.toList());
            list.sort((o1, o2) -> (o1.tradeDate < o2.tradeDate ? 1 : (o1.tradeDate == o2.tradeDate ? 0 : -1)));
            TradeStatisticsForJson[] array = new TradeStatisticsForJson[list.size()];
            list.toArray(array);
            jsonFileManager.writeToDisc(Utilities.objectToJson(array), "trade_statistics");
        }
    }
}
