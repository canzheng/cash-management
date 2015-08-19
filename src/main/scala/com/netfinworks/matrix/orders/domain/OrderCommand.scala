package com.netfinworks.matrix.orders.domain

/**
 * Created by canzheng on 8/12/15.
 */
sealed trait OrderCommand

case class Order (
                   orderId: String,
                   productId: String,
                   orderType: OrderType,
                   quantity: BigDecimal,
                   amount: BigDecimal,
                   account: String,
                   parties: List[PartyRole],
                   status: OrderStatus
                   ) extends OrderCommand

case object QuotaReset extends OrderCommand

case object DayEndNetting extends OrderCommand

case object DoneForDay extends OrderCommand


case class OrderConfirm (
                          orderId: String,
                          orderType: OrderType
                          )

case class OrderRejected (
                           msg: String = "Not enough position to redeem"
                             )

case class OrdQuery (pred: Order => Boolean) extends OrderCommand