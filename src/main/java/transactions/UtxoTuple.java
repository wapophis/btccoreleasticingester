package transactions;
import java.io.StringWriter;
import java.math.BigDecimal;

public class UtxoTuple {
    public String txid;
    public String date;
    public BigDecimal value;
    public boolean spent;
    public String spentDate;
    public int n;
    public String address;
    public BigDecimal trade_price;
    public BigDecimal spent_price;
    public int outSize;
    public int inSize;

    public String toString(){
        StringWriter stringWriter=new StringWriter();
        stringWriter.append("{ txId:\""+ txid +"\"");
        stringWriter.append(" n:\""+Integer.toString(n)+"\"");
        stringWriter.append(", date:\""+date+"\"");
        stringWriter.append(", value:"+value.toString());
        stringWriter.append(", spent:\""+Boolean.toString(spent)+"\"");
        stringWriter.append(", spentdate:\""+spentDate+"\"");
        stringWriter.append("}");
        return stringWriter.toString();
    }

}
