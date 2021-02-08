package threading;

import transactions.UtxoOutput;
import transactions.UtxoTuple;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class TxOutputConsumer  implements Consumer<UtxoOutput> {

    private final ConcurrentLinkedQueue queue;
    private final String date;
    private final BigDecimal price;
    private final String txId;


    public TxOutputConsumer(ConcurrentLinkedQueue queue, String txId,String date, BigDecimal price){
        this.queue=queue;
        this.date=date;
        this.price=price;
        this.txId=txId;
    }

    @Override
    public void accept(UtxoOutput utxoOutput) {
        // REGISTER UTXO
        UtxoTuple voutSoprUtxoTuple =new UtxoTuple();

        voutSoprUtxoTuple.txid= txId;

        voutSoprUtxoTuple.date= date;

        voutSoprUtxoTuple.spent=false;

        voutSoprUtxoTuple.n=utxoOutput.n;

        voutSoprUtxoTuple.value= new BigDecimal(utxoOutput.value);

        if(utxoOutput.scriptPubKey.addresses!=null){
            voutSoprUtxoTuple.address = utxoOutput.scriptPubKey.addresses.get(0);
        }

        voutSoprUtxoTuple.trade_price=price;

        queue.add(voutSoprUtxoTuple);
    }
}
