package com.sm.streaming.model;

import com.sm.streaming.stockstats.model.Trade;
import com.sm.streaming.stockstats.model.TradeStats;

public class OrderPaymentModel {

    public String orderId;
    public int orderQty;
    public String paymentId;
    public String itemId;
    public double txnAmount;
    public String status;

    public OrderPaymentModel(String orderId, int orderQty, String paymentId, String itemId, double txnAmount, String status) {
        this.orderId = orderId;
        this.orderQty = orderQty;
        this.paymentId = paymentId;
        this.itemId = itemId;
        this.txnAmount = txnAmount;
        this.status = status;
    }

    public OrderPaymentModel(){

    }



    public OrderPaymentModel merge(OrderPaymentModel orderPaymentModel){
        if(this.paymentId==null){
            this.paymentId=orderPaymentModel.paymentId;
        }
        if(this.orderId==null){
            this.orderId=orderPaymentModel.orderId;
        }
        if(this.itemId==null){
            this.itemId=orderPaymentModel.itemId;
        }
        if(this.orderQty==0){
            this.orderQty=orderPaymentModel.orderQty;
        }
        if(this.txnAmount==0){
            this.txnAmount=orderPaymentModel.txnAmount;
        }
        if(this.status==null){
            this.status=orderPaymentModel.status;
        }

        return this;
    }

    @Override
    public String toString() {
        return "OderWithPayment{" +
                "orderId='" + orderId + '\'' +
                ", orderQty=" + orderQty +
                ", paymentId='" + paymentId + '\'' +
                ", itemId='" + itemId + '\'' +
                ", txnAmount=" + txnAmount +
                ", status='" + status + '\'' +
                '}';
    }
}
