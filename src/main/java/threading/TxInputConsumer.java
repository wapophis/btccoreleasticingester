package threading;

import transactions.UtxoInput;
import transactions.UtxoTuple;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class TxInputConsumer implements Consumer<UtxoInput> {

    private final ConcurrentLinkedQueue queue;
    private final String txDate;
    private final BigDecimal price;

    /**
     *
     * @param queue Collection where to store
     * @param txDate spend date
     * @param price spend price
     */
    public TxInputConsumer(ConcurrentLinkedQueue queue, String txDate, BigDecimal price){
        this.queue=queue;
        this.txDate=txDate;
        this.price=price;
    }

    @Override
    public void accept(UtxoInput inputUtxo) {
            UtxoTuple expendedOuput=new UtxoTuple();
            expendedOuput.txid=inputUtxo.txid;
            expendedOuput.n=inputUtxo.vout;

            expendedOuput.spentDate=txDate;
            expendedOuput.spent_price=price;
            expendedOuput.spent=true;
            queue.add(expendedOuput);
        }
    }

