package com.netfinworks.matrix.exchange

import akka.actor.{ ActorRef, FSM }
import scala.concurrent.duration._
/**
 * Created by canzheng on 7/16/15.
 */

object OrderFSM {

  sealed trait OrderState

  case object New extends OrderState

  case object PendingExec extends OrderState

  case object PendingPayment extends OrderState

  case object Completed extends OrderState

  sealed trait OrderData

  case object Uninitialized extends OrderData

  case class Order(orderMsg: OrderMsg) extends OrderData

  final case class OrderMsg(orderId : String)
  final case class ExecutionReport()
  final case class PaymentReport()

  // reply messages

}

import com.netfinworks.matrix.exchange.OrderFSM._

class OrderFSM extends FSM[OrderState, OrderData] {
  startWith(New, Uninitialized)

  when (New) {
    case Event(orderMsg: OrderMsg, Uninitialized) =>
      goto(PendingExec) using Order(orderMsg)
  }
  when (PendingExec) {
    case Event(executionRpt: ExecutionReport, Order(orderMsg)) =>
      println(sender())
      goto(PendingPayment) replying(executionRpt)
  }
  when (PendingPayment) {
    case Event(paymentRpt: PaymentReport, Order(orderMsg)) =>
      println(sender())

      goto(Completed) replying(paymentRpt)
  }
  when(Completed)(FSM.NullFunction)


  onTransition {
    case PendingExec -> PendingPayment =>
      println("Recording execution")
  }

  initialize()
}
