package exchanges.blockchaindot;

import org.apache.http.impl.client.cache.CacheConfig;

public class TickerClient {

    public static String BTC_USD_SYMBOL="BTC-USD";

    private static CacheConfig cacheConfig = CacheConfig.custom()
            .setMaxCacheEntries(2)
            .setMaxObjectSize(512)
            .build();

    private static Double lastTradeValue;


    public static Double getLastTradeValue() {
        return lastTradeValue;
    }

    public static void setLastTradeValue(Double lastTradeValue) {
        TickerClient.lastTradeValue = lastTradeValue;
    }
}
