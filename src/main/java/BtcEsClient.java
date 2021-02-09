import daemon.Params;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import java.io.IOException;


public class BtcEsClient {

    public static String UTXO_INDEX=Params.utxoIndexName;

    private static HttpHost[] esHosts=new HttpHost[Params.elasticSearchServerUri.size()];
    static{
        for(int i=0;i<Params.elasticSearchServerUri.size();i++){
            esHosts[i]=new HttpHost(Params.elasticSearchServerUri.get(i).split("://")[1].split(":")[0],
                    Integer.parseInt(Params.elasticSearchServerUri.get(i).split("://")[1].split(":")[1]),
                    Params.elasticSearchServerUri.get(i).split("://")[0]);
            //esHosts[1]=new HttpHost("localhost",9201,"http");
        }
    }

    public static RestClient restClient = RestClient.builder(esHosts).build();


    private static final RequestOptions COMMON_OPTIONS;
    static {
        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        COMMON_OPTIONS = builder.build();
    }

    public static boolean existsTransaction(String txid){
        try {
            Response response=restClient.performRequest(new Request("GET","/"+UTXO_INDEX+"/_doc/"+txid+"$0"));
            boolean oVal= !response.hasWarnings() && response.getStatusLine().getStatusCode()==200;
            return oVal;
        } catch (IOException e) {
            return false;
        }
    }

}
