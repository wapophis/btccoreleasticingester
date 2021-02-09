package exchanges;

import com.google.gson.Gson;
import daemon.Params;
import org.apache.http.HttpHost;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.action.bulk.BackoffPolicy;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.DateTime;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ElasticSearchTicker implements TickerClient {

    private static HttpHost[] esHosts=new HttpHost[Params.elasticSearchServerUri.size()];
    static{
        for(int i=0;i<Params.elasticSearchServerUri.size();i++){
            esHosts[i]=new HttpHost(Params.elasticSearchServerUri.get(i).split("://")[1].split(":")[0],
                    Integer.parseInt(Params.elasticSearchServerUri.get(i).split("://")[1].split(":")[1]),
                    Params.elasticSearchServerUri.get(i).split("://")[0]);
            //esHosts[1]=new HttpHost("localhost",9201,"http");
        }
    }
     private static RestClientBuilder clientBuilder=            RestClient.builder(esHosts)
             .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                 @Override
                 public HttpAsyncClientBuilder customizeHttpClient(
                         HttpAsyncClientBuilder httpClientBuilder) {
                     return httpClientBuilder.setDefaultIOReactorConfig(
                             IOReactorConfig.custom()
                                     .setIoThreadCount(1)
                                     .build());
                 }
             });

     private String indexName;
     private SimpleDateFormat minutePrecision =new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
     private SimpleDateFormat hourPrecision =new SimpleDateFormat("yyyy-MM-dd'T'HHZ");
     private SimpleDateFormat dayPrecision =new SimpleDateFormat("yyyy-MM-dd");

    //yyyy-MM-dd'T'HH:mm:ss.SSSZ

     public ElasticSearchTicker(String indexName){
         this.indexName=indexName;
     }



    @Override
    public BigDecimal getCurrentPrice(TimeUnit precision) throws IOException {
        return getPriceAtDate(new DateTime(),precision);
    }

    @Override
    public BigDecimal getPriceAtDate(DateTime date, TimeUnit precision) throws IOException {
        System.out.print("\n ElasticSearch ticker query at "+minutePrecision.format(date.toDate())+"\r");
        String dateAtPrecision=null;

        if(precision.equals(TimeUnit.MINUTES)){
            dateAtPrecision=minutePrecision.format(date.toDate());
        }

        if(precision.equals(TimeUnit.HOURS)){
            dateAtPrecision=hourPrecision.format(date.toDate());
        }

        if(precision.equals(TimeUnit.DAYS)){
            dateAtPrecision=dayPrecision.format(date.toDate());
        }

        if(dateAtPrecision==null){
            throw new IllegalArgumentException("The precision specified is not supported");
        }

        RestHighLevelClient client = new RestHighLevelClient(ElasticSearchTicker.clientBuilder);

        SearchRequest searchRequest = new SearchRequest(indexName);

        MatchQueryBuilder dateQuery=new MatchQueryBuilder("date", dateAtPrecision);

        MatchQueryBuilder precisionQuery=new MatchQueryBuilder("precision",transformPrecisionTag(precision));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        searchSourceBuilder.query(QueryBuilders.boolQuery().must(dateQuery).must(precisionQuery));

        searchRequest.source(searchSourceBuilder);

        SearchResponse res=client.search(searchRequest, RequestOptions.DEFAULT);

        final TikCoin[] a = {null};
        Gson gson=new Gson();
        res.getHits().forEach(new Consumer<SearchHit>() {
            @Override
            public void accept(SearchHit documentFields) {
                if(documentFields.getScore()>=1L){
                    a[0] =gson.fromJson(documentFields.getSourceAsString(),TikCoin.class);
                }
            }
        });
        client.close();

        return a[0]!=null?a[0].value:null;

    }

    @Override
    public BigDecimal getPriceAtDate(DateTime date, TimeUnit precision, String sourceTag) throws IOException {
        RestHighLevelClient client = new RestHighLevelClient(ElasticSearchTicker.clientBuilder);

        SearchRequest searchRequest = new SearchRequest(indexName);

        MatchQueryBuilder dateMatcher=new MatchQueryBuilder("date", minutePrecision.format(date.toDate()));

        MatchQueryBuilder sourceMatcher=new MatchQueryBuilder("origin",sourceTag);

        MatchQueryBuilder precisionQuery=new MatchQueryBuilder("precision",transformPrecisionTag(precision));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        searchSourceBuilder.query(QueryBuilders.boolQuery().must(dateMatcher).must(precisionQuery).must(sourceMatcher));

        searchRequest.source(searchSourceBuilder);

        SearchResponse res=client.search(searchRequest, RequestOptions.DEFAULT);

        final TikCoin[] a = {null};
        Gson gson=new Gson();
        res.getHits().forEach(new Consumer<SearchHit>() {
            @Override
            public void accept(SearchHit documentFields) {
                if(documentFields.getScore()>=1L){
                    a[0] =gson.fromJson(documentFields.getSourceAsString(),TikCoin.class);
                }
            }
        });
        client.close();
        return a[0]!=null?a[0].value:null;
    }

    public TikCoin getMostRecentTicker() throws IOException {

        RestHighLevelClient client = new RestHighLevelClient(ElasticSearchTicker.clientBuilder);



        SearchRequest searchRequest = new SearchRequest(indexName);

        searchRequest.source(new SearchSourceBuilder().sort("date", SortOrder.DESC));

        SearchResponse res=client.search(searchRequest, RequestOptions.DEFAULT);

        final TikCoin[] a = {null};
        Gson gson=new Gson();
        res.getHits().forEach(new Consumer<SearchHit>() {
            @Override
            public void accept(SearchHit documentFields) {
                  a[0] =gson.fromJson(documentFields.getSourceAsString(),TikCoin.class);
            }
        });

        client.close();
        return a[0];
    }


    public void persistTick(TikCoin tikCoin,TimeUnit precision) throws IOException {

        tikCoin.precision=transformPrecisionTag(precision);
        RestHighLevelClient client = new RestHighLevelClient(ElasticSearchTicker.clientBuilder);
        Gson gson=new Gson();
        IndexRequest request=new IndexRequest("a").source(gson.toJson(tikCoin), XContentType.JSON);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.WAIT_UNTIL);
        client.index(request,RequestOptions.DEFAULT);
        client.close();
    }


    /*** Generic listener for bulkprocessor*/
    private static BulkProcessor.Listener listener = new BulkProcessor.Listener() {

        @Override
        public void beforeBulk(long l, BulkRequest bulkRequest) {
            System.out.println("Sending "+bulkRequest.numberOfActions()+"utxos to elastic search ");
        }

        @Override
        public void afterBulk(long l, BulkRequest bulkRequest, BulkResponse bulkResponse) {

        }

        @Override
        public void afterBulk(long l, BulkRequest bulkRequest, Throwable throwable) {
            throwable.printStackTrace();
        }
    };


    public void persistTickBulk(List<TikCoin> tikCoin, TimeUnit precision) throws IOException {
        RestHighLevelClient highLevelClient = new RestHighLevelClient(ElasticSearchTicker.clientBuilder);

        BulkProcessor.Builder builder = BulkProcessor.builder(
                (request, bulkListener) ->
                        highLevelClient.bulkAsync(request, RequestOptions.DEFAULT, bulkListener),
                listener);
        builder.setBulkActions(1000);
        builder.setBulkSize(new ByteSizeValue(1L, ByteSizeUnit.MB));
        builder.setConcurrentRequests(1);
        builder.setFlushInterval(TimeValue.timeValueSeconds(10L));
        builder.setBackoffPolicy(BackoffPolicy
                .constantBackoff(TimeValue.timeValueSeconds(1L), 3));
        BulkProcessor bulkProcessor=builder.build();


        tikCoin.forEach(new Consumer<TikCoin>() {
            Gson gson=new Gson();
             @Override
             public void accept(TikCoin tikCoin) {
                 tikCoin.precision=transformPrecisionTag(precision);
                 bulkProcessor.add(new IndexRequest(indexName).source(gson.toJson(tikCoin),XContentType.JSON));
             }
         });



        try {
            boolean terminated = bulkProcessor.awaitClose(30L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private String transformPrecisionTag(TimeUnit precision){
         String tag=null;
        if(TimeUnit.MINUTES.equals(precision)){
            tag="Minutes";
        }
        if(TimeUnit.HOURS.equals(precision)){
            tag="Hour";
        }
        if(TimeUnit.DAYS.equals(precision)){
            tag="Days";
        }

        if(tag==null){
            throw new IllegalArgumentException("The precision specified is not supported");
            }
        return tag;

       }


    @Override
    public void run() {
    }

}
