Bitcoin Elastic Search Utxo Ingester
======

This is a java utility to decode and persists the utxos into an elastic search server.
Price info is retrieved using binance api, so be carefull with your keys and please do not flood the api services. 

## Prerequisites
* Full Bitcoin core node working witH:
  * Indexing enabled.
  * Rpc enabled.
  * Zeromq enabled publishing rawtx. 
   
* Elastic Search Server to send the info:
   * No security enabled.
* Binance Api key and secret to retrieve prices info at real time. 
 

## Use
Launch the jar using the tools you decide.

###### Mempool ingestion  

` java -jar btccorelasticingester.jar `

###### Blocks processing

` java -cp  btccoreelasticingester.jar BlockImporter <startBlockIndex> <endBlockIndex> `

###### Send prices from binance api to Elastic Search index.

`java -jar btccorelasticingester.jar -sincroTickers -sincroStartAt=<yyyy-mm-dd> -sincroTimeunit=minutes`

## Configuration params
Btc Core and Binance require credentials to connect with, and url too. So this info must be configured
at command line using this params:
* -sincroTickers Enable the sincro tickers function to send data at elastic search server
* -sincroStartAt= <yyyy-mm-dd> to start sincro from
* -sincroTimeunit= <minutes | hours | days>
* -rawtxPublisher= <URL> in wich the btc core zeroMq is publishing the raw tx
* -rawtxTopic=<Topic-Name>
* -esServer= <URL TO ELASTIC SEARCH> this params repeated allows to connect multiples hosts
* -esUtxoName=<IndexName> Utxo index name
* -rpcNode=<URL> RPC url node configured at btc core
* -rpcUser=<User Name> RPC user name
* -rpcPassword=<Password> RPC password
* -binanceApikey=<API-KEY> Binance Api key needeed to query prices. 
* -binanceSecret=<API-SECRET> Binance Api secret needed to query prices. 

