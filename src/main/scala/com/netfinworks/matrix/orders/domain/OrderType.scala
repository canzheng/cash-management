package com.netfinworks.matrix.orders.domain

/**
 * Created by canzheng on 8/12/15.
 */
object OrderType {
  implicit def fromString(otString: String): OrderType = {
    otString.toLowerCase() match {
      case "buy" => Buy
      case "sell" => Sell
      case "invest" => Invest
      case "redeem" => Redeem
      case "qredeem" => QRedeem
      case "dividend" => Dividend
    }
  }
}
sealed trait OrderType

case object Buy extends OrderType
case object Sell extends OrderType
case object Invest extends OrderType
case object Redeem extends OrderType
case object QRedeem extends OrderType
case object Dividend extends OrderType


