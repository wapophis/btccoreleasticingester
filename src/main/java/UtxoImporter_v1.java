import daemon.Params;
import daemon.TickerPricesLoader;
import exchanges.MultiSourceTicker;
import exchanges.blockchaindot.TickerClient;
import com.google.gson.Gson;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZThread;
import transactions.BtcTransaction;
import zmq.Msg;

import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class UtxoImporter_v1 {

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
    private static ExecutorService executorService=Executors.newFixedThreadPool(4);

    /**
     * Importer to load the mempools tx and decode utxos to be ingested in the elastic server.
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        setParams(args);

        if(Params.SINCRO_AT_STARTUP){
            TimeUnit timeUnit=null;
            if(Params.SINCRO_TIME_UNIT!=null && "minutes".equals(Params.SINCRO_TIME_UNIT)){
                timeUnit=TimeUnit.MINUTES;
            }
            if(Params.SINCRO_TIME_UNIT!=null && "hours".equals(Params.SINCRO_TIME_UNIT)){
                timeUnit=TimeUnit.HOURS;
            }
            if(Params.SINCRO_TIME_UNIT!=null && "days".equals(Params.SINCRO_TIME_UNIT)){
                timeUnit=TimeUnit.DAYS;
            }

            TickerPricesLoader.startSinchroWithBinance(Params.SINCRO_START_AT_DATE!=null
                    && !Params.SINCRO_START_AT_DATE.isEmpty()?new DateTime(Params.SINCRO_START_AT_DATE):null
            ,timeUnit);

        }else {
            try (ZContext ctx = new ZContext()) {
                if (Params.MEMPOOL_ANALISIS_ACTIVE) {
                    ZMQ.Socket subpipe = ZThread.fork(ctx, new Subscriber());
                    subpipe.recvStr();
                }
            }
            try (ZContext ctx1 = new ZContext()) {
                if (Params.BLOCK_ANALISIS_ACTIVE) {
                    ZMQ.Socket rawblocksPipe = ZThread.fork(ctx1, new BlockSubscriber());
                    rawblocksPipe.recvStr();
                }

            }
        }

    }

    /**
     * Setted for command line params
     * @param args
     */
    public static void setParams(String... args){
        String lastCommand=null;
        String lastValue=null;

        for(int i=0;i<args.length;i++){
                if("-sincroTickers".equals(args[i]) && Params.SINCRO_AT_STARTUP==false){
                    Params.SINCRO_AT_STARTUP=true;
                }
                try {
                    Params.SINCRO_START_AT_DATE = args[i].split("-sincroStartAt=")[1];
                }catch (IndexOutOfBoundsException iE){
                    // Ignore
                }
                try{
                Params.SINCRO_TIME_UNIT=args[i].split("-sincroTimeunit=")[1];
                }catch (IndexOutOfBoundsException iE){
                    // Ignore
                }
                try{
                Params.zeroMqRawTxPublishedAddress=args[i].split("-rawtxPublisher=")[1];
                }catch (IndexOutOfBoundsException iE){
                // Ignore
                }
                try{
                Params.zeroMqRawTxPublishedTopic=args[i].split("-rawtxTopic=")[1];
                }catch (IndexOutOfBoundsException iE){
                // Ignore
                }
                try{
                Params.elasticSearchServerUri.add(args[i].split("-esServer=")[1]);
                }catch (IndexOutOfBoundsException iE){
                // Ignore
                }
                try{
                Params.utxoIndexName=args[i].split("-esUtxoName=")[1];
                }catch (IndexOutOfBoundsException iE){
                // Ignore
                }
                try{
                Params.btccoreRpcNodeUri=args[i].split("-rpcNode=")[1];
                }catch (IndexOutOfBoundsException iE){
                // Ignore
                }
                try{
                Params.btccoreRpcNodeUser=args[i].split("-rpcUser=")[1];
                }catch (IndexOutOfBoundsException iE){
                // Ignore
                }
                try{
                Params.btccoreRpcNodePassword=args[i].split("-rpcPassword=")[1];
                }catch (IndexOutOfBoundsException iE){
                // Ignore
                }
                try{
                Params.binanceApiKey=args[i].split("-binanceApikey=")[1];
                }catch (IndexOutOfBoundsException iE){
                // Ignore
                }
                try{
                Params.binanceSecret=args[i].split("-binanceSecret=")[1];
                }catch (IndexOutOfBoundsException iE){
                // Ignore
                }





        }
    }

    //  This is our subscriber. It connects to the publisher and subscribes to
    //  everything. It sleeps for a short time between messages to simulate
    //  doing too much work. If a message is more than one second late, it
    //  croaks.
    private static class Subscriber implements ZThread.IAttachedRunnable {


        @Override
        public void run(Object[] args, ZContext ctx, ZMQ.Socket pipe) {
            Gson gson=new Gson();
            MultiSourceTicker ticker=new MultiSourceTicker();

            //  Subscribe to rawtx
            ZMQ.Socket subscriber = ctx.createSocket(SocketType.SUB);
            subscriber.subscribe(Params.zeroMqRawTxPublishedTopic);

            if(Params.zeroMqRawTxPublishedAddress==null){
                throw new IllegalArgumentException("ZeroMq Published is not defined. Please set -rawtxPublisher to your server publisher address");
            }
            subscriber.connect(Params.zeroMqRawTxPublishedAddress);

            //  Get and process messages
            while (true) {
                Date startTime = new Date();
                BtcTransactionClient client = new BtcTransactionClient();
                // DECODE ON BTC-CORE
                BtcTransaction txDecoded = null;
                try {
                    subscriber.recv(); // TOPIC TAG
                    Msg msg = new Msg(subscriber.recv(0)); //
                    subscriber.recv(); // SEQ TAG
                    byte[] hexTx = new byte[msg.size()];
                    msg.getBytes(0, hexTx, 0, msg.size());
                    // RPC CALL
                    txDecoded = client.decodeRawTransaction(Hex.encodeHexString(hexTx));

                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
                Date txDate = null;
                // System.out.println("\t PROCESSING TX " + txDecoded.txid +" VIN:"+txDecoded.vin.size()+" VOUT:"+txDecoded.vout.size());

                if (txDecoded != null && BtcEsClient.existsTransaction(txDecoded.txid)) {

                    System.out.println("\t TX IS INDEXED YET :"+txDecoded.txid+" AT "+txDecoded.locktime);


                    txDecoded = null;
                }

                if (txDecoded != null) {


                    try {
                        // RPC CALL
                        Map mempoolEntry = (LinkedHashMap) client.getMemPoolEntry(txDecoded.txid);
                        txDate = new Date((Integer) mempoolEntry.get("time") * 1000L);
                        System.out.println("\tTX TIME FROM MEMPOOL :) DELAY " + Long.valueOf(new Date().getTime() - txDate.getTime())+" VIN: "+txDecoded.vin.size()+" VOUT:"+txDecoded.vout.size()+" TXID:"+txDecoded.txid);
                    } catch (Throwable t) {

                        System.out.println("\t TX NOT IN MEMPOOL SEARCHING CORRECT DATE AT BLOCK-CHAIN");

                        try {

                            txDate = client.getUtxoDate(txDecoded.txid, 0);
                            System.out.println("UTXO SETTLED AT"+sdf.format(txDate)+" AT "+txDecoded.txid);

                        } catch (Throwable throwable) {

                            System.out.println("CANNOT FIND A SUITABLE DATE, SO SETTING DATE TO NOW " + throwable.getMessage());

                            txDate = new Date();
                        }


                    }


                    try {
                        TimeUnit pricePrecition=null;
                        if(txDate.before(new SimpleDateFormat("yyyy-MM-dd").parse("2021-02-05"))){
                            pricePrecition=TimeUnit.DAYS;
                        }else{
                            pricePrecition=TimeUnit.MINUTES;
                        }

                        BigDecimal spotPrice=ticker.getPriceAtDate(new DateTime(txDate),pricePrecition);
                        BlockSincroPool blockSincroPool=new BlockSincroPool(txDecoded,txDate,spotPrice);
                        executorService.submit(blockSincroPool);


                    } catch (Throwable r) {
                        r.printStackTrace();
                    }

                }

            }
        }
    }


    private static class BlockSubscriber implements  ZThread.IAttachedRunnable{

        @Override
        public void run(Object[] objects, ZContext zContext, ZMQ.Socket socket) {

            //  Subscribe to rawtx
            ZMQ.Socket subscriber = zContext.createSocket(SocketType.SUB);
            subscriber.subscribe("hashblock");
            subscriber.connect("tcp://127.0.0.1:28334");

            //  Get and process messages
            while (true) {
                Date startTime = new Date();
                BtcTransactionClient client = new BtcTransactionClient();
                // DECODE ON BTC-CORE
                BtcTransaction txDecoded = null;
                try {
                    subscriber.recv(); // TOPIC TAG
                    Msg msg = new Msg(subscriber.recv(0)); // Receive raw block
                    subscriber.recv(); // SEQ TAG
                    byte[] hexTx = new byte[msg.size()];
                    msg.getBytes(0, hexTx, 0, msg.size());

                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
                Date txDate = null;
                // System.out.println("\t PROCESSING TX " + txDecoded.txid +" VIN:"+txDecoded.vin.size()+" VOUT:"+txDecoded.vout.size());

            }

        }
    }
}

