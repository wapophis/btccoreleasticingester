package transactions;

import java.util.UUID;

public class UtxoOutput{
    public Double value;
    public int n;
    public ScriptPubKey scriptPubKey;
    public String uid=UUID.randomUUID().toString();
}
