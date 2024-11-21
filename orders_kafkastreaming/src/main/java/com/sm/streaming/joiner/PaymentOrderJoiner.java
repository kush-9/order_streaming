package com.sm.streaming.joiner;

import com.sm.streaming.model.OrderModel;
import com.sm.streaming.model.OrderPaymentModel;
import com.sm.streaming.model.PaymentModel;
import org.apache.kafka.streams.kstream.ValueJoiner;

public class PaymentOrderJoiner implements ValueJoiner<PaymentModel,OrderModel, OrderPaymentModel> {

    @Override
    public OrderPaymentModel apply(PaymentModel payment, OrderModel order) {
        String orderId = order!=null?order.orderId:null;
        int orderQty =order!=null?order.orderQty:null ;
        String paymentId = payment!=null?payment.paymentId:null;
        String itemId = order!=null?order.itemId:null ;
        double txnAmount = payment!=null?payment.txnAmount:0.0;
        String status= payment!=null?payment.status:null;;

        OrderPaymentModel op = new OrderPaymentModel(orderId,orderQty,paymentId,
                itemId,txnAmount,status);
        return op;
    }
}
