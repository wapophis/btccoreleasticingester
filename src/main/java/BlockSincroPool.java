import com.google.gson.Gson;
import daemon.ConsoleColors;
import daemon.Params;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.*;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import threading.TransacionUtxoDecoder;
import transactions.BtcTransaction;
import transactions.UtxoTuple;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class sincronize with the elastic search index the utxos xtracted from the btc core node
 */
public class BlockSincroPool {
    private final static int MAX_TO_SINCRO=100;
    private static CircularFifoQueue<BtcTransaction> queue = new CircularFifoQueue<>(MAX_TO_SINCRO);
    private final static int BATCH_SIZE=500;
    private static final ConcurrentLinkedQueue<UtxoTuple> utxos=new ConcurrentLinkedQueue<>();
    private static SimpleDateFormat sdf= new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static Gson gson=new Gson();

    private static final ExecutorService executor = Executors.newFixedThreadPool(8);//creating a pool of 5 threads

    private static HttpHost[] esHosts=new HttpHost[Params.elasticSearchServerUri.size()];
    static{
        for(int i=0;i<Params.elasticSearchServerUri.size();i++){
            esHosts[i]=new HttpHost(Params.elasticSearchServerUri.get(i));
        }
    }
    private static final RestHighLevelClient highLevelClient = new RestHighLevelClient(
            RestClient.builder(esHosts));


    /*** Generic listener for bulkprocessor*/
    private static BulkProcessor.Listener listener = new BulkProcessor.Listener() {

        @Override
        public void beforeBulk(long l, BulkRequest bulkRequest) {
            System.out.print(ConsoleColors.CYAN+" Sending "+bulkRequest.numberOfActions()+" utxos to elastic search | "+ConsoleColors.RESET);
        }

        @Override
        public void afterBulk(long l, BulkRequest bulkRequest, BulkResponse bulkResponse) {
            System.out.print("\r");
            processBulkResponse(bulkResponse);

        }

        @Override
        public void afterBulk(long l, BulkRequest bulkRequest, Throwable throwable) {
            throwable.printStackTrace();
        }
    };


        private static BulkRequest request = new BulkRequest();


    public static void  addToSincroPool(BtcTransaction transaction,Date registeringDate,BigDecimal spotprice) throws IOException, ParseException {
        if(queue.isFull()){
            throw new IllegalStateException("POOL IS FULL, TX OVERFLOW:"+transaction.txid);
        }
        queue.add(transaction);
        onAdded(registeringDate,spotprice);
    }


    private static void onAdded(Date registeringDate, BigDecimal spotPrice) throws IOException, ParseException {
        // UTXOS ARE ADDED
        if(utxos.size()>=BATCH_SIZE){



            BulkProcessor.Builder builder = BulkProcessor.builder(
                    (request, bulkListener) ->
                            highLevelClient.bulkAsync(request, RequestOptions.DEFAULT, bulkListener),
                    listener);
            builder.setBulkActions(BATCH_SIZE);
            builder.setBulkSize(new ByteSizeValue(1L, ByteSizeUnit.MB));
            builder.setConcurrentRequests(1);
            builder.setFlushInterval(TimeValue.timeValueSeconds(10L));
            builder.setBackoffPolicy(BackoffPolicy
                    .constantBackoff(TimeValue.timeValueSeconds(1L), 3));
            BulkProcessor bulkProcessor=builder.build();

            utxos.forEach(new Consumer<UtxoTuple>() {
                @Override
                public void accept(UtxoTuple utxoTuple) {
                    if(utxoTuple.spent){
                        /// UPDATES
                        Map<String,Object> parameters=new HashMap<>();

                        parameters.put("spendPrice",utxoTuple.spent_price);
                        parameters.put("spendDate",utxoTuple.spentDate);
                        parameters.put("spentValue",true);

                        Script script= new Script(ScriptType.STORED,null,"spendUtxo",parameters);

                        bulkProcessor.add(new UpdateRequest(Params.utxoIndexName,utxoTuple.txid + "$" + utxoTuple.n).id(utxoTuple.txid + "$" + utxoTuple.n)
                                .script(script));

                    }else{
                        // NEW UTXOS
                        bulkProcessor.add(new IndexRequest(Params.utxoIndexName).id(utxoTuple.txid + "$" + utxoTuple.n)
                                .source(gson.toJson(utxoTuple), XContentType.JSON));
                    }
                    utxos.remove(utxoTuple);
                }
            });

            try {
           //     System.out.print(ConsoleColors.GREEN+" Terminated client "+ConsoleColors.RESET);
                boolean terminated = bulkProcessor.awaitClose(30L, TimeUnit.SECONDS);
           //     System.out.print(" "+terminated+" >>> | ");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }else {

            executor.execute(new TransacionUtxoDecoder(queue.poll(), utxos, sdf.format(registeringDate),spotPrice));
        }

    }


    public static  void processBulkResponse(BulkResponse bulkItemResponses){

        HashMap<String,Integer> failures=new HashMap<>();
        HashMap<String,Integer> operations=new HashMap<>();
        System.out.print("BULK RESPONSE:"+bulkItemResponses.getTook()+" - "+
                    bulkItemResponses.getItems().length+" - ");

                        Arrays.asList(bulkItemResponses.getItems()).forEach(new Consumer<BulkItemResponse>() {
                            @Override
                            public void accept(BulkItemResponse bulkItemResponse) {
                                if(operations.get(bulkItemResponse.getOpType().getLowercase())!=null){
                                    int count=operations.get(bulkItemResponse.getOpType().getLowercase());
                                    count++;
                                    operations.put(bulkItemResponse.getOpType().getLowercase(),count);
                                }else{
                                    operations.put(bulkItemResponse.getOpType().getLowercase(),0);
                                }


                                if(bulkItemResponse.isFailed()){
                                    Integer count=failures.get(bulkItemResponse.getFailure().getType());
                                    if(count==null){
                                        count=0;
                                    }
                                    count++;
                                    failures.put(bulkItemResponse.getOpType().toString(),count);
                                }
                            }
                        });
                        System.out.print(ConsoleColors.GREEN+" BULK PROCESSING STATS "+gson.toJson(operations)+ConsoleColors.RESET);
                        System.out.print(ConsoleColors.GREEN+" BULK PROCESSING FAILURES DETECTED "+gson.toJson(failures)+ConsoleColors.RESET);
                        //System.out.print(" \r");
    }





}
