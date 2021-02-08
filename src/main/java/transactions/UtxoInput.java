package transactions;

import java.math.BigInteger;
import java.util.ArrayList;

public class UtxoInput{
    public String txid;
    public String coinbase;
    public int vout;
    public ScriptSig scriptSig;
    public ArrayList<String> txinwitness;
    public BigInteger sequence;
}