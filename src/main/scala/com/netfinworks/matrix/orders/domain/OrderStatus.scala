package com.netfinworks.matrix.orders.domain

/**
 * Created by canzheng on 8/13/15.
 */
object OrderStatus {
  implicit def fromString(osString: String): OrderStatus = {
    osString.toLowerCase() match {
      case "open" => Open
      case "pendingrec" => PendingRec
      case "completed" => Completed
      case "pendingredeem" => PendingRedeem
      case "replaced" => Replaced
    }
  }
}

sealed trait OrderStatus

case object Open extends OrderStatus
case object PendingRec extends OrderStatus
case object Completed extends OrderStatus
case object PendingRedeem extends OrderStatus
case object Replaced extends OrderStatus