package exchanges.coinbase;


import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TickerClient implements exchanges.TickerClient {

    public static String BTC_USD_SYMBOL = "BTC-USD";

    private static Map<String, Double> spotValue = new HashMap<>();

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Return the spot values
     * @param date to query the value
     * @return
     */
    private Double getSpotValue(Date date) {

        if (spotValue.get(sdf.format(date)) == null) {

            CloseableHttpClient client = HttpClientBuilder.create().build();

            HttpGet request = null;

            try {
                 request = new HttpGet(new URI("https://api.exchanges.coinbase/v2/prices/BTC-USD/spot?date=" + sdf.format(date)));
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
     * Query price with time precision
     * @param precision minutes | hours | days
     * @return Price
     * @throws IOException
     */
    @Override
    public BigDecimal getCurrentPrice(TimeUnit precision) throws IOException {
        return new BigDecimal(this.getSpotValue(new Date()));
    }

    /**
     * Query price at date with time precision
     * @param date query date
     * @param precision minutes | hours | days
     * @return Price
     * @throws IOException
     */
    @Override
    public BigDecimal getPriceAtDate(DateTime date, TimeUnit precision) throws IOException {
        return null;
    }

    /**
     *
     * @param date
     * @param precision
     * @param sourceTag
     * @return
     * @throws IOException
     */
    @Override
    public BigDecimal getPriceAtDate(DateTime date, TimeUnit precision, String sourceTag) throws IOException {
       return getPriceAtDate(date,precision);
    }

    @Override
    public void run() {

    }
}