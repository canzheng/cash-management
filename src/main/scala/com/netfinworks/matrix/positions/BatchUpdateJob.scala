package com.netfinworks.matrix.positions

import java.util.UUID

import akka.actor.Actor
import akka.actor.Actor.Receive
import akka.util.Timeout
import com.netfinworks.matrix.orders.domain._
import com.netfinworks.matrix.positions.BatchUpdateJob.StartJob
import com.netfinworks.matrix.positions.domain.{PosQuery, Long, Update}
import com.netfinworks.matrix.utils.Constants

import scala.concurrent.Await
import akka.pattern.ask

import scala.concurrent.duration._

/**
 * Created by canzheng on 8/14/15.
 */

object BatchUpdateJob {

  case class StartJob(
                       requestId: String,
                       productId: String,
                       totalOrderQty: BigDecimal,
                       totalPosition: BigDecimal
                       )

}

class BatchUpdateJob extends Actor {
  implicit val timeout = Timeout(5 seconds)

  override def receive: Receive = {
    case StartJob(reqId, prodId, ordQty, totalPos) =>
      // to simplify calculation, only use quantity in the prototype
      //if (validateRequest(b)) {
      val orderManager = context.actorSelection("../../orderManager")
      val positionManager = context.actorSelection("../../positionManager")
      // reconcile posted orders
      var pendingRecOrders =
        Await.result(orderManager ? OrdQuery(o => o.productId == prodId && o.status == PendingRec), 5 seconds).asInstanceOf[List[Order]]
      var netQty = pendingRecOrders.filter(o => o.orderType == Invest || o.orderType == Redeem).foldRight(BigDecimal(0)) { (o, a) =>
        if (o.orderType == Invest)
          a + o.quantity
        else a - o.quantity
      }
      // ord qty from fund inst > net qty recorded in system
      if (ordQty > netQty) {
        val o = Order(
          orderId = UUID.randomUUID().toString,
          productId = prodId,
          orderType = Invest,
          quantity = ordQty - netQty,
          amount = 0,
          account = Constants.PlatformAccount,
          parties = Nil,
          status = PendingRec
        )
        orderManager ! o
//        positionManager ! Update(
//          requestId = o.orderId,
//          account = o.account,
//          productId = o.productId,
//          positionType = com.netfinworks.matrix.positions.domain.Long,
//          dQuantity = o.quantity,
//          dAvailableQty = o.quantity
//        )

      }
      // ord qty from fund inst < net qty recorded in system

      else if (ordQty < netQty) {
        // net qty from fund < net qty recorded in system
        val oRedeem = Order(
          orderId = UUID.randomUUID().toString,
          productId = prodId,
          orderType = Redeem,
          quantity = netQty - ordQty,
          amount = 0,
          account = Constants.PlatformAccount,
          parties = Nil,
          status = PendingRec
        )
        // an additional order to fix the potential temporary negative position
        val oInvest = Order(
          orderId = UUID.randomUUID().toString,
          productId = prodId,
          orderType = Invest,
          quantity = netQty - ordQty,
          amount = 0,
          account = Constants.PlatformAccount,
          parties = Nil,
          status = Open
        )

        orderManager ! oRedeem
        orderManager ! oInvest

//        positionManager ! Update(
//          requestId = oRedeem.orderId,
//          account = oRedeem.account,
//          productId = oRedeem.productId,
//          positionType = com.netfinworks.matrix.positions.domain.Long,
//          dQuantity = -oRedeem.quantity,
//          dAvailableQty = -oRedeem.quantity
//        )

      }

      // calculate dividend
      // returning dividend in the form of List[(account, dividend)]

      // get pending recs again in case it has been updated
      pendingRecOrders =
        Await.result(orderManager ? OrdQuery(o => o.productId == prodId && o.status == PendingRec), 5 seconds).asInstanceOf[List[Order]]

      val posData = positionManager ? PosQuery(p => p.positionType == Long && p.productId == Constants.CurrentMMFundId)
      val eligiblePos = Await.result(posData, 5 seconds).asInstanceOf[Traversable[Position]]
      val prevTotalPos = eligiblePos.foldLeft(BigDecimal(0))(_ + _.availableQuantity)
      if (prevTotalPos == 0)
        sender ! Nil
      else {
        val totalDiv = totalPos - prevTotalPos - ordQty
        val divMap = for (t <- eligiblePos) yield {
          val divQty = t.availableQuantity * totalDiv / prevTotalPos

          // fire a dividend order, which in turn send a position update
          orderManager ! Order(UUID.randomUUID().toString,
            t.productId,
            com.netfinworks.matrix.orders.domain.Dividend,
            divQty,
            0,
            t.account,
            Nil,
            Open)

          (t.account, divQty)
        }
        sender ! divMap.toList

      }
      // add order quantity to position
      pendingRecOrders.foreach(o => {
        val dQty = if (o.orderType == Invest) o.quantity else -o.quantity
        val u = Update(
          requestId = o.orderId,
          account = o.account,
          productId = o.productId,
          positionType = Long,
          dQuantity = dQty,
          dAvailableQty = dQty)
        positionManager ! u

//        println (u)
      })

      orderManager ! DoneForDay
  }

  //}
}
