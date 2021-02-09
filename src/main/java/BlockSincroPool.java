import com.google.gson.Gson;
import daemon.ConsoleColors;
import daemon.Params;
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

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class sincronize with the elastic search index the utxos xtracted from the btc core node
 */
public class BlockSincroPool implements Runnable {
    private final static int MAX_TO_SINCRO=100;

    private final static int BATCH_SIZE=500;

    private static SimpleDateFormat sdf= new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private static Gson gson=new Gson();

    private static HttpHost[] esHosts=new HttpHost[Params.elasticSearchServerUri.size()];

    static{
        for(int i=0;i<Params.elasticSearchServerUri.size();i++){
            esHosts[i]=new HttpHost(Params.elasticSearchServerUri.get(i));
        }
    }

    private static final RestHighLevelClient highLevelClient = new RestHighLevelClient(RestClient.builder(esHosts));

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

    private ConcurrentLinkedQueue<UtxoTuple> btcUtxos;
    private BtcTransaction transaction;
    private Date registringDate;
    private BigDecimal spotPrice;

    public BlockSincroPool(BtcTransaction transaction,Date registringDate,BigDecimal spotPrice){
        this.transaction=transaction;
        this.registringDate=registringDate;
        this.spotPrice=spotPrice;
        this.btcUtxos=new ConcurrentLinkedQueue<>();
    }

    private static void processBulkResponse(BulkResponse bulkItemResponses){

        HashMap<String,Integer> failures=new HashMap<>();

        HashMap<String,Integer> operations=new HashMap<>();

        System.out.print("BULK RESPONSE:"+bulkItemResponses.getTook()+" - "+bulkItemResponses.getItems().length+" - ");

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

    @Override
    public void run() {
        new TransacionUtxoDecoder(transaction, this.btcUtxos, sdf.format(this.registringDate),this.spotPrice).run();
        sendToEsServer();

    }

    private void sendToEsServer()
    {
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

        btcUtxos.forEach(new Consumer<UtxoTuple>() {
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
            }
        });

        try {
            //     System.out.print(ConsoleColors.GREEN+" Terminated client "+ConsoleColors.RESET);
            boolean terminated = bulkProcessor.awaitClose(30L, TimeUnit.SECONDS);
            //     System.out.print(" "+terminated+" >>> | ");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
