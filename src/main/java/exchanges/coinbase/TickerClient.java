package exchanges.coinbase;


import com.google.gson.Gson;
import exchanges.ElasticSearchTicker;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.joda.time.Days;
import org.joda.time.Instant;
import org.joda.time.MutableDateTime;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TickerClient {

    public static String BTC_USD_SYMBOL = "BTC-USD";

    private static Map<String, Double> spotValue = new HashMap<>();

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");


    public static Double getSpotValue(Date date) {

        if (spotValue.get(sdf.format(date)) == null) {

            CloseableHttpClient client = HttpClientBuilder.create().build();

            HttpGet request = null;

            try {
                //BasicHttpParams params=new BasicHttpParams();
                //params.setParameter("date",sdf.format(date));
                request = new HttpGet(new URI("https://apssi.exchanges.coinbase/v2/prices/BTC-USD/spot?date=" + sdf.format(date)));
                //request.setParams(params);

            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            HttpEntity entity = null;
            try {
                entity = client.execute(request).getEntity();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (entity != null) {
                // return it as a String
                String result = null;
                try {
                    result = EntityUtils.toString(entity);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Gson gson = new Gson();
                Map o = (Map) gson.fromJson(result, Object.class);
                spotValue.put(sdf.format(date), Double.valueOf((String) ((Map) o.get("data")).get("amount")));
            }
        }

        return spotValue.get(sdf.format(date));
    }


    /**
     * @param nameIndex elastic search index name
     * @param from      date from
     * @param to        datte to
     * @param sleep     int sleep time
     * @param unit      in units
     */
    public static Runnable indexSpotValues(String nameIndex, Date from, Date to, int sleep, TimeUnit unit) {
        RestHighLevelClient highLevelClient = new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9201, "http"))
                        .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                            @Override
                            public HttpAsyncClientBuilder customizeHttpClient(
                                    HttpAsyncClientBuilder httpClientBuilder) {
                                return httpClientBuilder.setDefaultIOReactorConfig(
                                        IOReactorConfig.custom()
                                                .setIoThreadCount(1)
                                                .build());
                            }
                        })

        );
        BulkRequest request=new BulkRequest();

        return new Runnable() {
            @Override
            public void run() {
                ElasticSearchTicker tickerClient=new ElasticSearchTicker("a");
                Calendar rightNow = Calendar.getInstance();
                rightNow.setTime(from);
                MutableDateTime start = new MutableDateTime(from);
                Days days = Days.daysBetween(new Instant(from), new Instant(to));
                Map<String,Object> values=new HashMap<>();
                while (days.getDays() > 0) {
                    try {
                        System.out.println(tickerClient.getPriceAtDate(start.toDateTime(),TimeUnit.MINUTES,"coinbase"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
//                    System.out.println("days:" + days.getDays());
//                    System.out.println("query for:" + start.toString());
//                    values.put("value",getSpotValue(start.toDate()));
//                    values.put("date",start.toString());
//                    values.put("origin","coinbase");
//                    values.put("precision","Days");
//                    request.add(new IndexRequest(nameIndex).source(values));
                    days = days.minus(1);
                    start.addDays(1);

                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
//                try {
//                    highLevelClient.bulk(request, RequestOptions.DEFAULT);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }
        };
    }
}