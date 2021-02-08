package daemon;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Params {

    /**
     *  ENABLED CAPABILITIES
     */
    public static Boolean binanceEnabled=true;
    public static Boolean coinbaseEnabled=false;

    public static Boolean MEMPOOL_ANALISIS_ACTIVE=true;
    public static Boolean BLOCK_ANALISIS_ACTIVE=false;

    /**
     * PRICE TICKER SINCRO COMMANDS
     */
    public static String tickerIndex="a";
    public static Boolean SINCRO_AT_STARTUP=false;
    public static String  SINCRO_START_AT_DATE=null;
    public static String  SINCRO_TIME_UNIT = null;

    /**
     * ZEROMQ CONNECTION INFO
     */

    public static String  zeroMqRawTxPublishedAddress=null;
    public static String  zeroMqRawTxPublishedTopic="rawtx";

    /**
     * ELASTIC SEARCH CONNECTION INFO
     */
    public static ArrayList<String> elasticSearchServerUri=new ArrayList<>();
    public static String  utxoIndexName="utxo";

    /**
     *  BTC CORE RPC CONNECTION INFO
     */
    public static String  btccoreRpcNodeUser=null;
    public static String  btccoreRpcNodePassword=null;
    public static String  btccoreRpcNodeUri=null;


    /**
     * BINANCE API PARAMS, NEEDED TO QUERY PRICES AT BINANCE API
     *
     */
    public static String  binanceApiKey=null;
    public static String  binanceSecret=null;
}
