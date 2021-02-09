package threading;

import transactions.BtcTransaction;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TransacionUtxoDecoder implements Runnable {

    private final BtcTransaction tx;

    private final ConcurrentLinkedQueue store;

    private final BigDecimal txPrice;

    private final String txDate;

    public  TransacionUtxoDecoder(BtcTransaction transaction, ConcurrentLinkedQueue store,String date,BigDecimal price){
        this.tx=transaction;

        this.store=store;

        this.txDate=date;

        this.txPrice=price;
    }
    @Override
    public void run() {
        tx.vin.forEach(new TxInputConsumer(store,txDate,txPrice));
        tx.vout.forEach(new TxOutputConsumer(store,tx.txid,txDate,txPrice));
    }
}
