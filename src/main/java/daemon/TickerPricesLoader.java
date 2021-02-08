package daemon;

import exchanges.ElasticSearchTicker;
import exchanges.TikCoin;
import exchanges.binance.BinanceTicker;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Daemon process to load the ticker prices into the es system.
 */
public class TickerPricesLoader {
    private static ElasticSearchTicker elasticSearchTicker;
    private static BinanceTicker binanceTicker;
    private static DateTime sincroStartAt;
    static{
        elasticSearchTicker=new ElasticSearchTicker(Params.tickerIndex);
        binanceTicker=new BinanceTicker();
        try {
            sincroStartAt=new DateTime(elasticSearchTicker.getMostRecentTicker().date);

        } catch (IOException e) {
            Params.binanceEnabled=false;
            Params.coinbaseEnabled=false;
            Params.BLOCK_ANALISIS_ACTIVE=false;
            Params.MEMPOOL_ANALISIS_ACTIVE=false;
            throw new IllegalStateException("Exchanges sincro disabled to prevent flood the apis. ");
        }
    }
    /**
     * Init the process of sincro with binance.
     */
    public static void startSinchroWithBinance(DateTime startDate,TimeUnit timeUnit){
        if(Params.binanceEnabled && Params.SINCRO_AT_STARTUP){
            if(timeUnit==null){
                System.out.println(ConsoleColors.RED+"Please specify a timeunit to parse"+ConsoleColors.RESET);
            }
            // Async call may tame some time
            List<TikCoin> results=binanceTicker.getPricesAtRange(startDate!=null?startDate:sincroStartAt,new DateTime(), timeUnit,1000);
            try {
                elasticSearchTicker.persistTickBulk(results,TimeUnit.MINUTES);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
