package com.netfinworks.matrix.orders

import java.util.UUID

import akka.actor.Actor
import com.netfinworks.matrix.orders.domain._
import com.netfinworks.matrix.positions.Position
import com.netfinworks.matrix.positions.domain._
import com.netfinworks.matrix.utils.Constants


import akka.pattern.ask

import scala.collection
import scala.collection.parallel.mutable
import scala.concurrent.Await
import akka.util.Timeout
import scala.concurrent.duration._


/**
 * Created by canzheng on 8/11/15.
 */

// important assumption:
// client only send in qredeem orders.
// redeem / sell is only allowed where client has sufficient position
// TODO add date to all process
object OrderManager {
}

class OrderManager extends Actor {
  implicit val timeout = Timeout(5 seconds)
  var orders: List[Order] = Nil

  // TODO: at times we need refill the qredeem quota
  var qredeemQuota: BigDecimal = Constants.QRedeemQuota

  override def receive: Receive = {
    // check qredeem quota
    // if there is quota remaining, process qredeem
    // otherwise simply change it to normal redeem order
    case o: Order if {
      o.account != Constants.PlatformAccount &&
      o.orderType == QRedeem || o.orderType == Redeem &&
      o.status == Open
    } =>

      // check if redeem is allowed
      // qty < current position + total pending rec net invest + total open net invest - total pending qredeem
      val posManager = context.actorSelection("../positionManager")
      val res = posManager ? PosQuery(p =>
        p.account == o.account &&
          p.productId == o.productId &&
          p.positionType == com.netfinworks.matrix.positions.domain.Long)

      val posData = Await.result(res, 5 seconds).asInstanceOf[Traversable[Position]]

      val currPos = if (posData.isEmpty) BigDecimal(0) else posData.head.availableQuantity

      val openQty = orders.filter(
        ord => ord.account == o.account
          && (ord.orderType == Invest || ord.orderType == Redeem)
          && ord.productId == o.productId
          && ord.status == Open)
        .foldRight(BigDecimal(0)) { (o, sum) =>
        if (o.orderType == Invest)
          sum + o.quantity
        else sum - o.quantity
      }

      val pendingRecQty = orders.filter(
        ord => ord.account == o.account
          && (ord.orderType == Invest || ord.orderType == Redeem)
          && ord.productId == o.productId
          && ord.status == PendingRec)
        .foldRight(BigDecimal(0)) { (o, sum) =>
        if (o.orderType == Invest) sum + o.quantity else sum - o.quantity
      }

      val pendingRedeemQty = orders.filter(
        ord => ord.account == o.account
          && ord.orderType == QRedeem || ord.orderType == Redeem
          && ord.productId == o.productId
          && ord.status == PendingRedeem)
        .foldRight(BigDecimal(0)) { (o, sum) =>
        if (o.orderType == Invest) sum + o.quantity else sum - o.quantity
      }

      if (o.quantity > currPos + openQty + pendingRecQty - pendingRedeemQty) {
        sender ! OrderRejected
      }
      else {
        var isQRedeem = false
        if (qredeemQuota > o.quantity) {
          isQRedeem = true
          qredeemQuota -= o.quantity
        }

        var remainingQty = o.quantity
        // net out existing invest orders
        if (openQty > 0) {
          val nettedQty = if (openQty > o.quantity) o.quantity else openQty
          remainingQty -= nettedQty
          orders = o.copy(UUID.randomUUID().toString, orderType = Redeem, quantity = nettedQty) :: orders
        }

        // redeem up to current position
        if (remainingQty > 0) {
          // if current position does not cover remaining order amount
          // split redeem order and put the remaining to pending
          val processQty: BigDecimal = if (currPos > remainingQty) remainingQty else currPos
          remainingQty -= processQty

          if (isQRedeem) {
            // the platform redeem order
            orders = o.copy(
              orderId = UUID.randomUUID().toString,
              account = Constants.PlatformAccount,
              orderType = Redeem,
              quantity = processQty,
              status = Open
            ) :: orders

            // genereate a pair of buy and sell order
            // the platform buy order
            val buyOrder = o.copy(
              orderId = UUID.randomUUID().toString,
              account = Constants.PlatformAccount,
              orderType = Buy,
              quantity = processQty,
              status = Completed
            )

            // the client sell order
            var sellOrder = o.copy(
              orderId = UUID.randomUUID().toString,
              orderType = Sell,
              quantity = processQty,
              status = Completed
            )

            orders = buyOrder :: orders
            orders = sellOrder :: orders
            posManager ! Update(
              requestId = buyOrder.orderId,
              account = buyOrder.account,
              productId = buyOrder.productId,
              positionType = Long,
              dQuantity = buyOrder.quantity,
              dAvailableQty = buyOrder.quantity
            )

            posManager ! Update(
              requestId = sellOrder.orderId,
              account = sellOrder.account,
              productId = sellOrder.productId,
              positionType = Long,
              dQuantity = -sellOrder.quantity,
              dAvailableQty = -sellOrder.quantity
            )
          }
          else {
            orders = o.copy(
              orderId = UUID.randomUUID().toString,
              orderType = Redeem,
              quantity = processQty,
              status = Open
            ) :: orders
          }

          if (remainingQty > 0) {
            // TODO logic to scan position to reprocess the redeem order
            orders = o.copy(
              orderId = UUID.randomUUID().toString,
              orderType = if (isQRedeem) QRedeem else Redeem,
              quantity = remainingQty,
              status = PendingRedeem
            ) :: orders
          }
        }
        orders = o.copy(status = Replaced) :: orders
        sender ! OrderConfirm(o.orderId, if (isQRedeem) QRedeem else Redeem)
      }
    case o: Order if o.orderType == com.netfinworks.matrix.orders.domain.Dividend =>
      val posManager = context.actorSelection("../positionManager")
      posManager ! Update(
        requestId = o.orderId,
        account = o.account,
        productId = o.productId,
        positionType = com.netfinworks.matrix.positions.domain.Dividend,
        dQuantity = o.quantity,
        dAvailableQty = o.quantity
      )
      // complete the order immediately
      orders = o.copy(status = Completed) :: orders
    // no confirmation needed

    case o: Order =>
      orders = o :: orders
      // omitted the fake execution in sequence diagram
      sender ! OrderConfirm(o.orderId, o.orderType)
    case OrdQuery(pred) =>
      sender ! orders.filter(pred)
    case QuotaReset =>
      // refill the qredeem quota
      qredeemQuota = Constants.QRedeemQuota

    case DayEndNetting =>
      var netQty = orders.filter(o => (o.orderType == Invest || o.orderType == Redeem) && o.status == Open)
        .foldRight(BigDecimal(0)) { (o, a) =>
        if (o.orderType == Invest)
          a + o.quantity
        else a - o.quantity
      }

      // Note: directly adding order to the list, may miss side-effects
      if (netQty > 0 && netQty < Constants.MinInvestment) {
        // less than minimum investment, create platform investment order
        orders = Order(orderId = UUID.randomUUID().toString,
          productId = Constants.CurrentMMFundId,
          orderType = Invest,
          quantity = Constants.MinInvestment - netQty,
          amount = 0,
          account = Constants.PlatformAccount,
          parties = Nil,
          status = Open
        ) :: orders
        netQty = Constants.MinInvestment
      }
      if (netQty < 0 && netQty > -Constants.MinRedemption) {
        // less than minimum redemption, create platform investment order too
        orders = Order(orderId = UUID.randomUUID().toString,
          productId = Constants.CurrentMMFundId,
          orderType = Invest,
          quantity = -netQty,
          amount = 0,
          account = Constants.PlatformAccount,
          parties = Nil,
          status = Open
        ) :: orders
        netQty = 0
      }
      sender ! netQty
      // put open orders into pending recognition
      // NOTE: qredeem orders are not put into pendingRec, they will be reprocessed immediately after daily batch position update
      orders = orders.map(o => if (o.status == Open && (o.orderType == Invest || o.orderType == Redeem)) o.copy(status = PendingRec) else o)

    case DoneForDay =>
      orders = orders.map(o => if (o.status == PendingRec) o.copy(status = Completed) else o)

  }
}
