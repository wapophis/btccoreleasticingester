package exchanges;

import daemon.ConsoleColors;
import daemon.Params;
import exchanges.binance.BinanceTicker;
import org.joda.time.DateTime;
import org.joda.time.MutableDateTime;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MultiSourceTicker implements TickerClient {


    ElasticSearchTicker mainTicker=new ElasticSearchTicker("a");
    BinanceTicker binanceTicker=new BinanceTicker();

    SimpleDateFormat minutePrecision=new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");

    @Override
    public BigDecimal getCurrentPrice(TimeUnit precision) throws IOException {
      return getPriceAtDate(new DateTime(),precision);
    }

    @Override
    public BigDecimal getPriceAtDate(DateTime date, TimeUnit precision) throws IOException {
        BigDecimal price=mainTicker.getCurrentPrice(precision);
       // test.setDayOfMonth(test.getDayOfMonth()-100);

        if(price!=null){
            return price;
        }
        if(price==null && Params.binanceEnabled){
            // Query Binance
            System.out.println(ConsoleColors.YELLOW_BOLD_BRIGHT+"\t\t\t\t\t\t\\-------------------- ----------------->Binance Query at"+minutePrecision.format(date.toDate())+ConsoleColors.RESET);

            TikCoin tikCoin=new TikCoin();

            tikCoin.value=binanceTicker.getPriceAtDate(date,precision);

            tikCoin.origin="binance";

            tikCoin.date=minutePrecision.format(new Date());

            mainTicker.persistTick(tikCoin,precision);

            return tikCoin.value;

        }
        if(price==null && Params.coinbaseEnabled){
            // Query coinbase
            // If correct price, persist on elastic
            throw new IllegalStateException("Cannot find a suitable price on this date");
        }
        if(price==null)
            throw new IllegalStateException("Cannot find a suitable price on this date");

        return null;
    }

    @Override
    public BigDecimal getPriceAtDate(DateTime date, TimeUnit precision, String sourceTag) throws IOException {
        return null;
    }

    @Override
    public void run() {

    }
}
