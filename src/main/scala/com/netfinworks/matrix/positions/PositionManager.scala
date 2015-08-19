package com.netfinworks.matrix.positions

import java.util.UUID

import akka.actor.{Props, ActorSelection, Actor}
import akka.util.Timeout
import com.netfinworks.matrix.orders.OrderManager
import com.netfinworks.matrix.orders.domain._
import com.netfinworks.matrix.positions.BatchUpdateJob.StartJob
import com.netfinworks.matrix.utils.Constants
import scala.collection.immutable.HashMap
import scala.collection.mutable
import com.netfinworks.matrix.positions.domain._

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

/**
 * Created by canzheng on 7/28/15.
 */
// TODO need to put all the batch logic outside the position manager (isolate current position when running batch)
import PositionManager._

object PositionManager {

  case class PositionMatcher(account: String, productId: String, positionType: PositionType)

  def matches(u: PositionMatcher, pos: Position): Boolean = {
    if (u.account == pos.account
      && u.productId == pos.productId
      && u.positionType == pos.positionType)
      true
    else false
  }

  def updateMatcher(u: Update): PositionMatcher = {
    new PositionMatcher(u.account, u.productId, u.positionType)
  }

  def fromMatcher(t: Transfer): PositionMatcher = {
    new PositionMatcher(t.account, t.productId, t.fromType)
  }

  def toMatcher(t: Transfer): PositionMatcher = {
    new PositionMatcher(t.account, t.productId, t.toType)
  }


//  def matches(q: PosQuery, pos: Position): Boolean = {
//    if (q.positionId.forall(_ == pos.positionId)
//      && q.account.forall(_ == pos.account)
//      && q.productId.forall(_ == pos.productId)
//      && q.positionType.forall(_ == pos.positionType)
//      && q.includeDeleted == true || pos.deleted == false)
//      true
//    else false
//  }

}

class PositionManager extends Actor {
  var posData : List[Position] = Nil
  val requestLog = new mutable.HashMap[String, PositionCommand]
  var histPosData : List[Position] = Nil
  implicit val timeout = Timeout(5 seconds)

  def validateRequest(cmd: PositionCommand): Boolean = {
    if(requestLog.contains(cmd.requestId)) {
      sender ! DuplicatedRequest
      return false
    }
    else {
      requestLog.put(cmd.requestId, cmd)
      return true
    }
  }

  def updatePosition(oldPos: Position, newPos: Position) = {
    val idx = posData.indexOf(oldPos)
    if (idx > -1) {
      histPosData = oldPos :: histPosData
      posData = posData.patch(idx, Seq(newPos), 1)
    }
    else {
      posData = newPos :: posData
    }
  }
  override def receive: Receive = {
    case update: Update =>
      if (validateRequest(update)) {
        //println(update)
        val pos = posData.find(t => matches(updateMatcher(update), t)).getOrElse {
          new Position(UUID.randomUUID().toString, update.account, update.productId, update.positionType)
        }

        val newAmt: BigDecimal = if(update.dAmount != 0) pos.amount + update.dAmount else 0
        val newQty: BigDecimal = if (update.dQuantity != 0) pos.quantity + update.dQuantity else 0
        val newAvailableAmt: BigDecimal = if (update.dAvailableAmt != 0) pos.availableAmount + update.dAvailableAmt else 0
        val newAvailableQty: BigDecimal = if (update.dAvailableQty != null) pos.availableQuantity + update.dAvailableQty else 0

        val newPos = pos.copy(amount = newAmt, quantity = newQty, availableAmount = newAvailableAmt, availableQuantity = newAvailableQty)
        // for each entry in update.dMiscPos, update position.miscPositionData
        update.dMiscPos.foreach(t => {
          newPos.miscPositionData.put(t._1, newPos.miscPositionData.getOrElse[BigDecimal](t._1, 0) + t._2)
        })

        // adding previous position to history
        updatePosition(pos, newPos)
      }
    case transfer: Transfer =>
      if (validateRequest(transfer)) {
        val fromPos = posData.find(t => matches(fromMatcher(transfer), t)).orNull
        // fromPos is missing
        if (fromPos == null) {
          sender ! FromPositionMissing
        }
        // available qty / amt is less than qty / amt to transfer
        else if (fromPos.availableAmount < transfer.dAmount || fromPos.availableQuantity < transfer.dQuantity) {
          sender ! NotEnoughToTransfer

        }
        else {

          // update transfer amt / qty for complete transfer
          var actualTransfer = transfer
          if (transfer.isCompleteTransfer) {
            actualTransfer = transfer.copy(dAmount = fromPos.availableAmount, dQuantity = fromPos.availableQuantity)
          }

          var histToPosExists = true
          val toPos = posData.find(t => matches(toMatcher(actualTransfer), t)).getOrElse {
            histToPosExists = false
            new Position(UUID.randomUUID().toString, actualTransfer.account, actualTransfer.productId, actualTransfer.toType)
          }

          // update both position records
          var (newFromAmt, newFromAvailableAmt, newFromQty, newFromAvailableQty) = (BigDecimal(0), BigDecimal(0), BigDecimal(0), BigDecimal(0))
          var (newToAmt, newToAvailableAmt, newToQty, newToAvailableQty) = (BigDecimal(0), BigDecimal(0), BigDecimal(0), BigDecimal(0))

          if (actualTransfer.dAmount != 0) {
            newToAmt = toPos.amount + actualTransfer.dAmount
            newToAvailableAmt = toPos.availableAmount + actualTransfer.dAmount
            newFromAmt = fromPos.amount - actualTransfer.dAmount
            newFromAvailableAmt = fromPos.availableAmount - actualTransfer.dAmount
          }

          if (actualTransfer.dQuantity != 0) {
            newToQty = toPos.quantity + actualTransfer.dQuantity
            newToAvailableQty = toPos.availableQuantity + actualTransfer.dQuantity
            newFromQty = fromPos.quantity - actualTransfer.dQuantity
            newFromAvailableQty = fromPos.availableQuantity - actualTransfer.dQuantity
          }

          val newFromPos = fromPos.copy(amount = newFromAmt, quantity = newFromQty, availableAmount = newFromAvailableAmt, availableQuantity = newFromAvailableQty)
          val newToPos = toPos.copy(amount = newToAmt, quantity = newToQty, availableAmount = newToAvailableAmt, availableQuantity = newToAvailableQty)

          if (actualTransfer.dAmount != 0) {
            fromPos.miscPositionData.keySet.foreach(t => {
              newToPos.miscPositionData.put(t, toPos.miscPositionData.getOrElse[BigDecimal](t, 0) + actualTransfer.dAmount)
              newFromPos.miscPositionData.put(t, fromPos.miscPositionData.getOrElse[BigDecimal](t, 0) - actualTransfer.dAmount)
            })
          }
          if (actualTransfer.dQuantity != 0) {

            fromPos.miscPositionData.keySet.foreach(t => {
              newToPos.miscPositionData.put(t, toPos.miscPositionData.getOrElse[BigDecimal](t, 0) + actualTransfer.dQuantity)
              newFromPos.miscPositionData.put(t, fromPos.miscPositionData.getOrElse[BigDecimal](t, 0) - actualTransfer.dQuantity)
            })
          }
          // adding old fromPos and toPos to history

          updatePosition(fromPos, newFromPos)

          updatePosition(toPos, newToPos)

        }
      }
    case q: PosQuery =>
      val result = posData.filter(t => q.pred(t))
      sender ! result

    case BatchUpdate(reqId, prodId, ordQty, totalPos) =>

      val b = context.actorOf(Props[BatchUpdateJob])
      b ! StartJob(reqId, prodId, ordQty, totalPos)


  }
}
