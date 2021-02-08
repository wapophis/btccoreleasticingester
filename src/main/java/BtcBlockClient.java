import block.BlockInfo;
import com.googlecode.jsonrpc4j.JsonRpcHttpClient;
import daemon.Params;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class BtcBlockClient {

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

    public String getBlockHash(int blockIndex) throws Throwable {
        JsonRpcHttpClient client = new JsonRpcHttpClient(rpcNodeUrl);

        client.setHeaders(basicAuth);

        String blockHash = (String) client.invoke("getblockhash", new Object[] {blockIndex}, Object.class,basicAuth);

        return  blockHash;
    }

    public BlockInfo getBlock(String blockHash) throws Throwable {
        Date startDate=new Date();

        JsonRpcHttpClient client = new JsonRpcHttpClient(rpcNodeUrl);

        client.setHeaders(basicAuth);

        BlockInfo block = client.invoke("getblock", new Object[] {blockHash,2}, BlockInfo.class,basicAuth);

        return  block;
    }


    public BlockInfo getResumedBlock(String blockHash) throws Throwable {
        Date startDate=new Date();

        JsonRpcHttpClient client = new JsonRpcHttpClient(rpcNodeUrl);

        client.setHeaders(basicAuth);

        LinkedHashMap<String,Object> block = (LinkedHashMap<String, Object>) client.invoke("getblock", new Object[] {blockHash,1}, Object.class,basicAuth);

        BlockInfo oVal=new BlockInfo();

        oVal.time= (int) block.get("time");

        return  oVal;
    }
}
