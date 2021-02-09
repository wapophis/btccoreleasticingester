import block.BlockInfo;
import daemon.ConsoleColors;
import daemon.Params;
import exchanges.ElasticSearchTicker;
import exchanges.MultiSourceTicker;
import exchanges.coinbase.TickerClient;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkResponse;
import org.joda.time.DateTime;
import threading.TransacionUtxoDecoder;
import transactions.BtcTransaction;
import transactions.UtxoInput;
import transactions.UtxoOutput;
import transactions.UtxoTuple;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class BlockImporter {

    private static int startBlock;

    private static int endBlock;

    private static ArrayList<String> hashList=new ArrayList<>();

    private static SimpleDateFormat sdf= new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");


    private final static ExecutorService executorService= Executors.newFixedThreadPool(4);

    /**
     * The block importer imports blocks from the btc-node core, queriying the tx set and transforms it to be ingested by the elastic search node.
     * When importing this way the date registered is refered to the date in which the block has been confirmed
     * @param args expects two inputs numbers that are the start and end block to proccess.
     * @throws Exception
     */

    public static void main(String[] args) throws Exception
    {
        UtxoImporter_v1.setParams(args);
        ElasticSearchTicker elasticSearchTicker=new ElasticSearchTicker(Params.tickerIndex);


        BtcBlockClient blockClient=new BtcBlockClient();
        BlockImporter.startBlock=Integer.parseInt(args[0]);
        BlockImporter.endBlock=Integer.parseInt(args[1]);

        if(startBlock>endBlock){
            throw new IllegalArgumentException("Start block must be lower indexed than end block");
        }

        Date startDate=new Date();
        System.out.println("Client started at "+sdf.format(startDate));

        try {

            for(int i=0;i<endBlock-startBlock;i++){
                System.out.printf("[#  Consultando bloques ] %d de %d",i,endBlock-startBlock);
                hashList.add(blockClient.getBlockHash(startBlock+i));
                System.out.printf("\r");

            }

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        // INGEST UTXO FROM BLOCK ON ES IN ORDER.
        hashList.forEach(new Consumer<String>() {

            @Override
            public void accept(String blockHash) {
                try {

                    BlockInfo block=blockClient.getBlock(blockHash);

                    System.out.print(ConsoleColors.RED+" Procesing block "+blockHash+" at index "+ block.height+ConsoleColors.RESET+" ");

                    BigDecimal spotPrice=elasticSearchTicker.getPriceAtDate(new DateTime(block.time*1000L),TimeUnit.MINUTES);

                    block.tx.forEach(new Consumer<BtcTransaction>() {
                        int cnt=0;
                        @Override
                        public void accept(BtcTransaction transaction) {
                            cnt++;
                            System.out.print(".");
                            if((cnt % 7)==0){
                                System.out.print("\r");
                            }

                            if(!BtcEsClient.existsTransaction(transaction.txid)) {
                                    BlockSincroPool blockSincroPool=new BlockSincroPool(transaction,new Date(block.time * 1000L), spotPrice);
                                    executorService.submit(blockSincroPool);
                            }
                        }
                    });

                    System.out.print("\r");


                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            }
        });

    }


}
