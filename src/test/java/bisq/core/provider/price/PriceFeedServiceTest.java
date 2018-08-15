package bisq.core.provider.price;

import bisq.core.monetary.Price;
import bisq.core.provider.ProvidersRepository;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.user.Preferences;

import bisq.network.http.HttpClient;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Preferences.class, TradeStatistics2.class, Price.class})
public class PriceFeedServiceTest {
    @Test
    public void testApplyLatestBisqMarketPrice() {
        long initialTime = new Date().getTime();

        List<TradeStatistics2> obsoletes = Lists.newArrayList(
                mockTradeStatistics("a", new Date(initialTime), 5),
                mockTradeStatistics("b", new Date(initialTime), 6),
                mockTradeStatistics("b", new Date(initialTime), 7),
                mockTradeStatistics("a", new Date(initialTime), 8));

        List<TradeStatistics2> stats = new ArrayList<>(obsoletes);
        stats.add(mockTradeStatistics("a", new Date(initialTime + 100), 8));
        stats.add(mockTradeStatistics("b", new Date(initialTime + 200), 9));

        Collections.shuffle(stats);

        PriceFeedService service = new PriceFeedService(mock(HttpClient.class),
                mock(ProvidersRepository.class), mock(Preferences.class));
        service.applyLatestBisqMarketPrice(stats);

        assertThat(service.getMarketPrice("a"), notNullValue());
        assertThat(service.getMarketPrice("b"), notNullValue());

        // verify that trade price is queried only once - during mock setup
        obsoletes.forEach(st -> verify(st).getTradePrice());
    }

    private static TradeStatistics2 mockTradeStatistics(String code, Date tradeDate, long value) {
        TradeStatistics2 result = mock(TradeStatistics2.class, RETURNS_DEEP_STUBS);
        when(result.getTradeDate()).thenReturn(tradeDate);
        when(result.getCurrencyCode()).thenReturn(code);
        when(result.getTradePrice().getValue()).thenReturn(value);
        return result;
    }
}
