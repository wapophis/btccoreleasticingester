package exchanges.binance;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;
import daemon.ConsoleColors;
import daemon.Params;
import exchanges.TickerClient;
import exchanges.TikCoin;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.MutableDateTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class BinanceTicker implements TickerClient {
    BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(Params.binanceApiKey,Params.binanceSecret);
    BinanceApiRestClient client = factory.newRestClient();

    @Override
    public BigDecimal getCurrentPrice(TimeUnit precision) {

        if(TimeUnit.MINUTES.equals(precision)){
            TickerPrice tickerPrice=client.getPrice("BTCUSDC");
            return new BigDecimal(tickerPrice.getPrice());
        }

        if(TimeUnit.HOURS.equals(precision)){
            MutableDateTime startCandleAt=new MutableDateTime();
            startCandleAt.setMinuteOfHour(0);
            MutableDateTime endCandleAt=new MutableDateTime();
            Interval interval=new Interval(startCandleAt,endCandleAt);
            interval.toDuration().getStandardMinutes();
           // startCandleAt.setMinuteOfHour(59);
            List<Candlestick> prices=client.getCandlestickBars("BTCUSDC",fromInterval(interval),1,startCandleAt.getMillis(),endCandleAt.getMillis());
            if(prices.isEmpty()){
                return null;
            }
            return medianCandleStick(prices.get(0));
        }

        if(TimeUnit.DAYS.equals(precision)){
            MutableDateTime startCandleAt=new MutableDateTime();
            startCandleAt.setMinuteOfHour(0);
            startCandleAt.setHourOfDay(0);
            MutableDateTime endCandleAt=new MutableDateTime();
            Interval interval=new Interval(startCandleAt,endCandleAt);
            interval.toDuration().getStandardMinutes();
            // startCandleAt.setMinuteOfHour(59);
            List<Candlestick> prices=client.getCandlestickBars("BTCUSDC",fromInterval(interval),1,startCandleAt.getMillis(),endCandleAt.getMillis());
            if(prices.isEmpty()){
                return null;
            }
            return medianCandleStick(prices.get(0));
        }


        return null;

    }

    public List<TikCoin> getPricesAtRange(DateTime start,DateTime end,TimeUnit precision,int limit){
        ExecutorService throttledPool= Executors.newFixedThreadPool(1);
        int maxPageSize=limit;
        int currentPage=0;
        int pagesNeeded=0;
        long offset=0;
        CandlestickInterval candlestickInterval=null;
        List<TikCoin> oVal=new ArrayList<>();

        Interval interval=new Interval(start,end);

        if(TimeUnit.MINUTES.equals(precision)){
            pagesNeeded= Math.toIntExact(interval.toDuration().getStandardMinutes() / maxPageSize);
            offset=maxPageSize*60000;
            candlestickInterval=CandlestickInterval.ONE_MINUTE;
        }

        if(TimeUnit.HOURS.equals(precision)){
            pagesNeeded= Math.toIntExact(interval.toDuration().getStandardHours() / maxPageSize);
            offset=maxPageSize*3600000;
            candlestickInterval=CandlestickInterval.HOURLY;
        }

        if(TimeUnit.DAYS.equals(precision)){
            pagesNeeded= Math.toIntExact(interval.toDuration().getStandardDays() / maxPageSize);
            offset=maxPageSize*24*60*60*1000;
            candlestickInterval=CandlestickInterval.DAILY;
        }

        for(int i=0;i<=pagesNeeded;i++){
            final long startOffset=Long.valueOf(offset*i);
            Runnable queryService=new Runnable() {

                @Override
                public void run() {
                    System.out.println(ConsoleColors.GREEN_BRIGHT+" Requesting candles at binance api from "+start+" offset "+startOffset+ConsoleColors.RESET);
                    List<Candlestick> prices=client.getCandlestickBars("BTCUSDC",CandlestickInterval.ONE_MINUTE,maxPageSize,start.getMillis()+startOffset,null);
                    TransformCandleStick transformCandleStick=new TransformCandleStick(oVal);
                    prices.forEach(transformCandleStick);
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
            try {
                throttledPool.submit(queryService).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

       return oVal;

    }


    @Override
    public BigDecimal getPriceAtDate(DateTime date, TimeUnit precision) {

        if(TimeUnit.MINUTES.equals(precision)){
            MutableDateTime startCandleAt=new MutableDateTime(date);
            startCandleAt.setSecondOfMinute(0);
            MutableDateTime endCandleAt=new MutableDateTime();
            Interval interval=new Interval(startCandleAt,endCandleAt);
            if(interval.toDuration().getStandardSeconds()>=59){
                endCandleAt=new MutableDateTime(date);
                endCandleAt.setSecondOfMinute(59);
            }else{
                return getCurrentPrice(precision);
            }

            // startCandleAt.setMinuteOfHour(59);

            List<Candlestick> prices=client.getCandlestickBars("BTCUSDC",fromInterval(interval),1,startCandleAt.getMillis(),endCandleAt.getMillis());
            if(prices.isEmpty()){
                System.out.println("BE CAREFULL, PREVENT FLOODING BINANCE. NO PRICE RETURNED AT "+date.toString()+" using precition "+precision.toString());
                return null;
            }
            return medianCandleStick(prices.get(0));
        }

        if(TimeUnit.HOURS.equals(precision)){
            MutableDateTime startCandleAt=new MutableDateTime(date);
            startCandleAt.setMinuteOfHour(0);
            MutableDateTime endCandleAt=new MutableDateTime();
            Interval interval=new Interval(startCandleAt,endCandleAt);
            if(interval.toDuration().getStandardMinutes()>59){
                endCandleAt=new MutableDateTime(date);
                endCandleAt.setMinuteOfHour(59);
            }

            // startCandleAt.setMinuteOfHour(59);
            List<Candlestick> prices=client.getCandlestickBars("BTCUSDC",fromInterval(interval),1,startCandleAt.getMillis(),endCandleAt.getMillis());
            if(prices.isEmpty()){
                return null;
            }
            return medianCandleStick(prices.get(0));
        }

        if(TimeUnit.DAYS.equals(precision)){
            MutableDateTime startCandleAt=new MutableDateTime(date);
            startCandleAt.setMinuteOfHour(0);
            startCandleAt.setHourOfDay(0);
            MutableDateTime endCandleAt=new MutableDateTime();
            Interval interval=new Interval(startCandleAt,endCandleAt);
            if(interval.toDuration().getStandardMinutes()>(59*24)){
                endCandleAt=new MutableDateTime(date);
                endCandleAt.setMinuteOfHour(59);
                endCandleAt.setHourOfDay(23);
            }
            // startCandleAt.setMinuteOfHour(59);
            List<Candlestick> prices=client.getCandlestickBars("BTCUSDC",fromInterval(interval),1,startCandleAt.getMillis(),endCandleAt.getMillis());
            if(prices.isEmpty()){
                return null;
            }
            return medianCandleStick(prices.get(0));
        }
        return null;

    }

    @Override
    public BigDecimal getPriceAtDate(DateTime date, TimeUnit precision, String sourceTag) {
        throw new IllegalArgumentException("Not supported yet");

    }

    @Override
    public void run() {
        throw new IllegalArgumentException("Not supported yet");
    }

    private BigDecimal medianCandleStick(Candlestick candlestick){
        double open=Double.valueOf(candlestick.getOpen());
        double close=Double.valueOf(candlestick.getClose());
        double high=Double.valueOf(candlestick.getHigh());
        double low=Double.valueOf(candlestick.getLow());

        return new BigDecimal(Double.toString((open+close+high+low)/4));
    }
    private CandlestickInterval fromInterval(Interval interval){



        if(interval.toDuration().getStandardMinutes()>=1 && interval.toDuration().getStandardMinutes()<3){
            return CandlestickInterval.ONE_MINUTE;
        }

        if(interval.toDuration().getStandardMinutes()>=3 && interval.toDuration().getStandardMinutes()<5){
            return CandlestickInterval.THREE_MINUTES;
        }

        if(interval.toDuration().getStandardMinutes()>=5 && interval.toDuration().getStandardMinutes()<15){
            return CandlestickInterval.FIVE_MINUTES;
        }

        if(interval.toDuration().getStandardMinutes()>=15 && interval.toDuration().getStandardMinutes()<30){
            return CandlestickInterval.FIFTEEN_MINUTES;
        }

        if(interval.toDuration().getStandardMinutes()>=30 && interval.toDuration().getStandardMinutes()<60){
            return CandlestickInterval.HALF_HOURLY;
        }

       if(interval.toDuration().getStandardMinutes()>=60 && interval.toDuration().getStandardMinutes()<120){
            return CandlestickInterval.HOURLY;
        }

        if(interval.toDuration().getStandardMinutes()>=120 && interval.toDuration().getStandardMinutes()<240){
            return CandlestickInterval.TWO_HOURLY;
        }

        if(interval.toDuration().getStandardMinutes()>=240 && interval.toDuration().getStandardMinutes()<360){
            return CandlestickInterval.FOUR_HOURLY;
        }

        if(interval.toDuration().getStandardMinutes()>=360 && interval.toDuration().getStandardMinutes()<480){
            return CandlestickInterval.SIX_HOURLY;
        }

        if(interval.toDuration().getStandardMinutes()>=480 && interval.toDuration().getStandardMinutes()<720){
            return CandlestickInterval.EIGHT_HOURLY;
        }

        if(interval.toDuration().getStandardMinutes()>=720 && interval.toDuration().getStandardMinutes()<(24*60)){
            return CandlestickInterval.TWELVE_HOURLY;
        }

        if(interval.toDuration().getStandardMinutes()>=(24*60) && interval.toDuration().getStandardMinutes()<(72*60)){
            return CandlestickInterval.DAILY;
        }

        if(interval.toDuration().getStandardMinutes()>=(72*60) && interval.toDuration().getStandardMinutes()<(7*24*60)){
            return CandlestickInterval.THREE_DAILY;
        }

        if(interval.toDuration().getStandardMinutes()>=(7*(24*60)) && interval.toDuration().getStandardDays()<30){
            return CandlestickInterval.WEEKLY;
        }

        if(interval.toDuration().getStandardDays()>=30){
            return CandlestickInterval.MONTHLY;
        }

        throw new IllegalArgumentException("There is no representation for this candlestick."+interval.toDuration().getStandardMinutes());

    }


    public class TransformCandleStick implements Consumer<Candlestick> {
        List<TikCoin> oVal=null;
        TimeUnit precision=null;

        public TransformCandleStick(List<TikCoin> oVal){
            this.oVal=oVal;
        }
        @Override
        public void accept(Candlestick candlestick) {
                //System.out.println(candlestick.toString());
                TikCoin coin=new TikCoin();
                coin.value=medianCandleStick(candlestick);
                coin.date=new DateTime(candlestick.getOpenTime().longValue()).toString();
                coin.origin="binance";
                this.oVal.add(coin);

        }
    }
}
