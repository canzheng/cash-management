package com.netfinworks.matrix.services

import com.netfinworks.matrix.orders.domain._
import spray.json._

/**
 * Created by canzheng on 8/12/15.
 */
object OrderProtocol extends DefaultJsonProtocol {
  implicit val otFormat = new JsonFormat[OrderType] {
    override def read(json: JsValue): OrderType = json match {
      case JsString("Buy") => Buy
      case JsString("Sell") => Sell
      case JsString("Invest") => Invest
      case JsString("Redeem") => Redeem
      case JsString("QRedeem") => QRedeem
      case _ => throw new DeserializationException(s"$json is not a valid extension of OrderType")
    }
    override def write(ot: OrderType): JsValue = {
      JsString(ot.toString)
    }
  }

  implicit val osFormat = new JsonFormat[OrderStatus] {
    override def read(json: JsValue): OrderStatus = json match {
      case JsString("Open") => Open
      case JsString("PendingRec") => PendingRec
      case JsString("Completed") => Completed
      case _ => throw new DeserializationException(s"$json is not a valid extension of OrderStatus")
    }
    override def write(os: OrderStatus): JsValue = {
      JsString(os.toString)
    }
  }

  implicit val prFormat = jsonFormat2(PartyRole)

  implicit val orderFormat = jsonFormat8(Order)

}
