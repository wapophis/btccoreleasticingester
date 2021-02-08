
//import coinbase.thetransactioncompany.jsonrpc2.JSONRPC2Request;
//import coinbase.thetransactioncompany.jsonrpc2.JSONRPC2Response;
//import coinbase.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
//import coinbase.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;


import com.google.gson.Gson;
import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import daemon.Params;
import transactions.BtcTransaction;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

public class BtcTransactionClient {

    static HashMap<String,String> basicAuth;
    static{
        basicAuth=new HashMap<>();

        basicAuth.put("Authorization","Basic "+ Base64.getEncoder().
                encodeToString(
                        (Params.btccoreRpcNodeUser+":"+Params.btccoreRpcNodePassword).getBytes(Charset.defaultCharset())
                )
        );

    }
    static URL rpcNodeUrl;

    static {
        try {
            rpcNodeUrl = new URL(Params.btccoreRpcNodeUri);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Specify rpc server uri. Set the -rpcNode value");
        }
    }

    public Object getMemPoolEntry(String txId) throws Throwable {
        JsonRpcHttpClient client = new JsonRpcHttpClient(rpcNodeUrl);
        client.setHeaders(basicAuth);
        Object mempoolEntry = client.invoke("getmempoolentry", new Object[] {txId}, Object.class,basicAuth);
        return  mempoolEntry;
    }

    public Object getRawTransaction(String txId) throws Throwable {
        JsonRpcHttpClient client = new JsonRpcHttpClient(rpcNodeUrl);
        client.setHeaders(basicAuth);

        Object mempoolEntry = client.invoke("getrawtransaction", new Object[] {txId,true}, Object.class,basicAuth);


        return  mempoolEntry;
    }

    /*public Object decodeRawTransaction(String hexString) throws Throwable {
        JsonRpcHttpClient client = new JsonRpcHttpClient(
                new URL("http://127.0.0.1:8332"));
        client.setHeaders(basicAuth);

        Object mempoolEntry = client.invoke("decoderawtransaction", new Object[] {hexString}, Object.class,basicAuth);


        return  mempoolEntry;
    }*/


    public BtcTransaction decodeRawTransaction(String hexString) throws Throwable {
        JsonRpcHttpClient client = new JsonRpcHttpClient(rpcNodeUrl);
        client.setHeaders(basicAuth);
        BtcTransaction transaction=client.invoke("decoderawtransaction", new Object[] {hexString}, BtcTransaction.class,basicAuth);
        return transaction;
    }


    /** CHECK THE UTXO IN THE MEMPOOL **/
    public boolean isUtxo(String txId,int voutidx) throws Throwable {
        JsonRpcHttpClient client = new JsonRpcHttpClient(rpcNodeUrl);
        client.setHeaders(basicAuth);

        Object mempoolEntry = client.invoke("gettxout", new Object[] {txId,voutidx}, Object.class,basicAuth);

        if(mempoolEntry==null)
        {
            return false;
        }else{
            return true;
        }

    }

    public Date getUtxoDate(String txId, int voutidx) throws Throwable{
        JsonRpcHttpClient client = new JsonRpcHttpClient(rpcNodeUrl);
        String blockHash;
        BtcBlockClient blockClient=new BtcBlockClient();
        client.setHeaders(basicAuth);

        LinkedHashMap<String,Object> mempoolEntry = (LinkedHashMap<String, Object>) client.invoke("gettxout", new Object[] {txId,voutidx}, Object.class,basicAuth);

        if(mempoolEntry==null)
        {
            throw new IllegalStateException("Cannot find the utxo requested in the block chain");
        }else{
            blockHash= (String) mempoolEntry.get("bestblock");
            return new Date(blockClient.getResumedBlock(blockHash).time*1000L);
        }

    }

}
