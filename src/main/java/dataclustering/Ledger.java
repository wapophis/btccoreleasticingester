package dataclustering;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class represents a unique ledger
 */
public class Ledger {
    /**
     * ACCOUNTS RELATED TO THIS LEDGER
     */
    private HashSet<String> accounts;
    /**
     * UTXO-ID FROM ES INDEX
     */
    private HashSet<String> utxos;
    /**
     * BALANCE (SYMBOL,VALUE)
     */
    private ConcurrentHashMap<String,Double> balance;


}
