package exchanges;

import org.joda.time.DateTime;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public interface TickerClient extends Runnable{

    public BigDecimal getCurrentPrice(TimeUnit precision) throws IOException;
    public BigDecimal getPriceAtDate(DateTime date, TimeUnit precision) throws IOException;
    public BigDecimal getPriceAtDate(DateTime date, TimeUnit precision,String sourceTag) throws IOException;


}
