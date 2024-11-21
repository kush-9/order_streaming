package com.sm.streaming;

import com.sm.streaming.joiner.OrderPaymentJoiner;
import com.sm.streaming.joiner.OrderPymtItemJoiner;
import com.sm.streaming.joiner.PaymentOrderJoiner;
import com.sm.streaming.model.*;
import com.sm.streaming.serde.JsonDeserializer;
import com.sm.streaming.serde.JsonSerializer;
import com.sm.streaming.serde.WrapperSerde;
import com.sm.streaming.stockstats.model.TradeStats;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;

import java.time.Duration;
import java.util.Properties;

public class OrderProcessingApp {


    public static void main(String[] args) {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "order-join-app-2");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"); //127.0.0.1
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(StreamsConfig.STATE_DIR_CONFIG,"/Volumes/D/project/kafka-statestore");
        //config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        //config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();
        //KStream<String, OrderModel> orders = builder.stream(Constants.ORDER_TOPIC, Consumed.with(Serdes.String(),new OrderSerde()));
        //order table key=orderid
        KTable<String, OrderModel> orderTable = builder.table(Constants.ORDER_TOPIC, Consumed.with(Serdes.String(),new OrderSerde()),Materialized.<String, OrderModel>as(
                Stores.persistentKeyValueStore("orders-store"))
                .withKeySerde(Serdes.String())
                .withValueSerde(new OrderSerde()).withRetention(Duration.ofDays(2)));


        KTable<String, ItemsPriceModel> itemPrice = builder.table(Constants.ITEM_PRICE_TOPIC, Consumed.with(Serdes.String(), new ItemPriceSerde()));
        //KStream<String, PaymentModel> payments = builder.stream(Constants.PAYMENT_TOPIC, Consumed.with(Serdes.String(), new PaymentSerde()));
        //payment table  key changed from payment to orderid
        KTable<String, PaymentModel> paymentsTable = builder.stream(Constants.PAYMENT_TOPIC, Consumed.with(Serdes.String(), new PaymentSerde())).selectKey((k, payment)-> {return payment.orderId;})
                .toTable(Materialized.<String, PaymentModel>as(
                                Stores.persistentKeyValueStore("payments-store"))
                        .withKeySerde(Serdes.String())
                        .withValueSerde(new PaymentSerde()).withRetention(Duration.ofDays(2)));

        KStream<String, OrderPaymentModel> order_pymt_aggregation = builder.stream("ord_paymt_agg", Consumed.with(Serdes.String(), new OrderPaymentSerde()));


        //repartitioning
       // KStream<String, PaymentModel> repartitionedByOrderId = payments.selectKey((k, payment)-> {return payment.orderId;});
       // repartitionedByOrderId.peek((k,v)-> System.out.println("Repartitioned with k="+k+" v orderid="+v.orderId+" paymentid="+v.paymentId));
        //repartitionedByOrderId.to("stockstats2");
      //  JoinWindows tenMinWindow =  JoinWindows.of(Duration.ofMinutes(10));



        //value joiner for order and payment join
        ValueJoiner<OrderModel, PaymentModel, OrderPaymentModel> orderPaymentJoiner = new OrderPaymentJoiner();
        ValueJoiner<PaymentModel,OrderModel, OrderPaymentModel> paymentOrderJoiner = new PaymentOrderJoiner();



        // KStream<String, OrderPaymentModel> order_payment_stream = orders.join(repartitionedByOrderId,orderPaymentJoiner,tenMinWindow, StreamJoined.with(Serdes.String(),new OrderSerde(),new PaymentSerde()));

        //order_payment_stream.print(Printed.<String, OrderPaymentModel>toSysOut().withLabel(" joined order and payment"));

        //repartitioning to join with items before ***
       // KStream<String, OrderPaymentModel> orderpayment_byitem_stream = order_payment_stream.selectKey((k, v)-> {return v.itemId;});



        //value joiner for final order payment and item
        ValueJoiner<OrderPaymentModel, ItemsPriceModel, OrderPymtItemModel> orderpymtItemJoiner = new OrderPymtItemJoiner();




        //KStream<String, OrderPymtItemModel> orderpayment_item_stream = orderpayment_byitem_stream.leftJoin(itemPrice,orderpymtItemJoiner,Joined.with(Serdes.String(),new OrderPaymentSerde(),new ItemPriceSerde()));

        //orderpayment_item_stream.peek((k,v)-> System.out.println("orderpayment_item_stream with k="+k+" v as ="+v.toString()));

        //orderpayment_item_stream.print(Printed.<String, OrderPymtItemModel>toSysOut().withLabel(" joined order_payment with items"));
        //orderpayment_item_stream.selectKey((k,v) -> {return  v.orderId;}).to("stockstats2", Produced.with(Serdes.String(),new OrderPymtItemPojoSerde()));


        //joining order to payments :: two tables ::: and dumping to stockstats2
        orderTable.leftJoin(paymentsTable,orderPaymentJoiner).toStream().to("ord_paymt_agg",Produced.with(Serdes.String(),new OrderPaymentSerde()));
        //joining payments to order
        paymentsTable.leftJoin(orderTable,paymentOrderJoiner).toStream().to("ord_paymt_agg",Produced.with(Serdes.String(),new OrderPaymentSerde()));

        long windowSize = 10000; //10 seconds
        //window of 1 min.. with size of 20 seconds
        KStream<Windowed<String>, OrderPaymentModel > order_pyment_aggregate = order_pymt_aggregation.groupByKey().windowedBy(TimeWindows.of(Duration.ofMinutes(1))).aggregate(()-> new OrderPaymentModel(),(k,v,opmodel)-> opmodel.merge(v),
                Materialized.<String,OrderPaymentModel, WindowStore<Bytes,byte[]>>as("order-payment-aggregates")
                .withValueSerde(new OrderPaymentSerde())).toStream();

        order_pyment_aggregate.to("ord_paymt_output",Produced.keySerde(WindowedSerdes.timeWindowedSerdeFrom(String.class, windowSize)));



        KafkaStreams stream = new KafkaStreams(builder.build(),config);
        stream.cleanUp();
        stream.start();

        Runtime.getRuntime().addShutdownHook(new Thread(stream::close));

    }




    static public final class ItemPriceSerde extends WrapperSerde<ItemsPriceModel> {
        public ItemPriceSerde() {
            super(new JsonSerializer<ItemsPriceModel>(), new JsonDeserializer<ItemsPriceModel>(ItemsPriceModel.class));
        }
    }

    static public final class OrderSerde extends WrapperSerde<OrderModel> {
        public OrderSerde() {
            super(new JsonSerializer<OrderModel>(), new JsonDeserializer<OrderModel>(OrderModel.class));
        }
    }

    static public  final class PaymentSerde extends  WrapperSerde<PaymentModel>{
        public PaymentSerde() {
            super(new JsonSerializer<PaymentModel>(), new JsonDeserializer<PaymentModel>(PaymentModel.class));
        }
    }

    static public final class OrderPaymentSerde extends WrapperSerde<OrderPaymentModel> {
        public OrderPaymentSerde() {
            super(new JsonSerializer<OrderPaymentModel>(), new JsonDeserializer<OrderPaymentModel>(OrderPaymentModel.class));
        }
    }

    static public final class OrderPymtItemPojoSerde extends WrapperSerde<OrderPymtItemModel> {
        public OrderPymtItemPojoSerde() {
            super(new JsonSerializer<OrderPymtItemModel>(), new JsonDeserializer<OrderPymtItemModel>(OrderPymtItemModel.class));
        }
    }

}


