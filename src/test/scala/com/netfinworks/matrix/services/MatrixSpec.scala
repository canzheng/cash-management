package com.netfinworks.matrix.services

import java.util.UUID

import akka.actor.{ActorSystem, Inbox}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import com.netfinworks.matrix.positions.PositionManager._
import com.netfinworks.matrix.positions.domain._
import com.netfinworks.matrix.positions.{Position, PositionManager}
import org.scalatest.Inspectors._
import org.scalatest.{FlatSpecLike, Matchers}

import scala.collection.mutable
import scala.concurrent.duration._

/**
 * Created by canzheng on 7/17/15.
 */
class MatrixSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers {

  def this() = this(ActorSystem("MyActorSystem"))

  val account1 = "acct001"

  val product1 = "prod001"

  val product2 = "prod002"


//  "A non-existent position query " should "result in empty map" in {
//
//    implicit val i = Inbox.create(_system)
//
//    val posMgr = TestActorRef[PositionManager]
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]] should be ('empty)
//
//  }
//
//
//  "A new position creation" should "create new position" in {
//    val quantity1 = 100
//
//    implicit val i = Inbox.create(_system)
//
//    val posMgr = TestActorRef[PositionManager]
//    val update = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1)
//    i.send(posMgr, update)
//    assert(posMgr.underlyingActor.posData.exists(t => matches(updateMatcher(update), t._2)))
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
//    forAll(result) {
//      t =>
//        t._2 should have(
//          'account(account1),
//          'productId(product1),
//          'positionType(Long),
//          'quantity(quantity1)
//        )
//    }
//
//    posMgr.underlyingActor.histPosData should be (Nil)
//
//  }
//
//  "A position increase" should "increase position on same account, productId, positionType" in {
//    val quantity1 = 100
//    val quantity2 = 300
//
//    implicit val i = Inbox.create(_system)
//    implicit val timeout = Timeout(5 minutes)
//
//    val posMgr = TestActorRef[PositionManager]
//    val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1)
//    i.send(posMgr, update1)
//    val update2 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity2)
//    i.send(posMgr, update2)
//
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
//    forAll(result) {
//      t =>
//        t._2 should have(
//          'account(account1),
//          'productId(product1),
//          'positionType(Long),
//          'quantity(quantity1 + quantity2)
//        )
//    }
//
//    posMgr.underlyingActor.histPosData should have size(1)
//    posMgr.underlyingActor.histPosData.head should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(quantity1)
//    )
//  }
//
//  "A first-time position transfer" should "decrease fromPosition and create new toPosition" in {
//    val quantity1 = 100
//
//    val transQty1 = 20
//
//    implicit val i = Inbox.create(_system)
//
//    val posMgr = TestActorRef[PositionManager]
//    val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
//    i.send(posMgr, update1)
//    val transfer1 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty1)
//    i.send(posMgr, transfer1)
//
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
//    result should have size (2)
//    result.find(t => t._2.positionType == Long).map(_._2).get should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(quantity1 - transQty1)
//    )
//
//    result.find(t => t._2.positionType == Collateral).map(_._2).get should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Collateral),
//      'quantity(transQty1)
//    )
//
//    posMgr.underlyingActor.histPosData should have size(1)
//    posMgr.underlyingActor.histPosData.head should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(quantity1)
//    )
//  }
//
//  "A second-time positon transfer" should "decrease fromPosition and increase toPosition" in {
//    val quantity1 = 100
//    val transQty1 = 20
//
//    val transQty2 = 30
//
//    implicit val i = Inbox.create(_system)
//
//    val posMgr = TestActorRef[PositionManager]
//    val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
//    i.send(posMgr, update1)
//    val transfer1 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty1)
//    i.send(posMgr, transfer1)
//    val transfer2 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty2)
//    i.send(posMgr, transfer2)
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
//    result should have size (2)
//    result.find(t => t._2.positionType == Long).map(_._2).get should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(quantity1 - transQty1 - transQty2)
//    )
//
//    result.find(t => t._2.positionType == Collateral).map(_._2).get should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Collateral),
//      'quantity(transQty1 + transQty2)
//    )
//
//    posMgr.underlyingActor.histPosData should have size(3)
//    exactly(1, posMgr.underlyingActor.histPosData) should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(quantity1)
//    )
//
//    exactly(1, posMgr.underlyingActor.histPosData) should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(quantity1 - transQty1)
//    )
//
//    exactly(1, posMgr.underlyingActor.histPosData) should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Collateral),
//      'quantity(transQty1)
//    )
//
//  }
//
//
//  "A first-time complete transfer" should "decrease fromPosition to 0 and create toPosition" in {
//    val quantity1 = 100
//
//    implicit val i = Inbox.create(_system)
//
//    val posMgr = TestActorRef[PositionManager]
//    val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
//    i.send(posMgr, update1)
//    val transfer1 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, isCompleteTransfer = true)
//    i.send(posMgr, transfer1)
//
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
//    result should have size (2)
//    result.find(t => t._2.positionType == Long).map(_._2).get should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(0)
//    )
//
//    result.find(t => t._2.positionType == Collateral).map(_._2).get should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Collateral),
//      'quantity(quantity1)
//    )
//
//    // history should have quantity1
//    posMgr.underlyingActor.histPosData should have size(1)
//    posMgr.underlyingActor.histPosData.head should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(quantity1)
//    )
//  }
//
//  "A second-time complete transfer" should "decrease fromPosition to 0 and increase toPosition" in {
//    val quantity1 = 100
//    val transQty1 = 20
//
//    implicit val i = Inbox.create(_system)
//
//    val posMgr = TestActorRef[PositionManager]
//    val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
//    i.send(posMgr, update1)
//    val transfer1 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty1)
//    i.send(posMgr, transfer1)
//    val transfer2 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, isCompleteTransfer = true)
//    i.send(posMgr, transfer2)
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
//    result should have size (2)
//    result.find(t => t._2.positionType == Long).map(_._2).get should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(0)
//    )
//
//    result.find(t => t._2.positionType == Collateral).map(_._2).get should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Collateral),
//      'quantity(quantity1)
//    )
//
//    // history should have 2 records quantity1 - transQty1
//    posMgr.underlyingActor.histPosData should have size(3)
//    exactly(1, posMgr.underlyingActor.histPosData) should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(quantity1)
//    )
//    exactly(1, posMgr.underlyingActor.histPosData) should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Long),
//      'quantity(quantity1 - transQty1)
//    )
//    exactly(1, posMgr.underlyingActor.histPosData) should have (
//      'account(account1),
//      'productId(product1),
//      'positionType(Collateral),
//      'quantity(transQty1)
//    )
//
//  }
//
//  "A transfer from non-existent position" should "receive error" in {
//
//    val quantity1 = 100
//
//    val transQty1 = 20
//
//    implicit val i = Inbox.create(_system)
//
//    val posMgr = TestActorRef[PositionManager]
//    val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
//    i.send(posMgr, update1)
//    val transfer1 = Transfer(UUID.randomUUID().toString, account1, product2, Long, Collateral, dQuantity = transQty1)
//    i.send(posMgr, transfer1)
//
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    i.receive(5 minutes) should be (FromPositionMissing)
//  }
//
//  "A transfer from insuffient position" should "receive error" in {
//
//    val quantity1 = 100
//
//    val transQty1 = 200
//
//    implicit val i = Inbox.create(_system)
//
//    val posMgr = TestActorRef[PositionManager]
//    val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
//    i.send(posMgr, update1)
//    val transfer1 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty1)
//    i.send(posMgr, transfer1)
//
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    i.receive(5 minutes) should be (NotEnoughToTransfer)
//  }
//
//  "An update with an old requestId" should "receive error" in {
//
//    val quantity1 = 100
//    val quantity2 = 300
//
//    implicit val i = Inbox.create(_system)
//    implicit val timeout = Timeout(5 minutes)
//
//    val posMgr = TestActorRef[PositionManager]
//    val reqId = UUID.randomUUID().toString
//    val update1 = Update(reqId, account1, product1, Long, dQuantity = quantity1)
//    i.send(posMgr, update1)
//    val update2 = Update(reqId, account1, product1, Long, dQuantity = quantity2)
//    i.send(posMgr, update2)
//
//
//    i.receive(5 minutes) should be (DuplicatedRequest)
//
//    val update3 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity2)
//    i.send(posMgr, update3)
//
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
//    forAll(result) {
//      t =>
//        t._2 should have(
//          'account(account1),
//          'productId(product1),
//          'quantity(quantity1 + quantity2)
//        )
//    }
//
//  }
//
//  "A transfer with an old requestId" should "receive error" in {
//
//    val quantity1 = 100
//
//    val transQty1 = 20
//
//    implicit val i = Inbox.create(_system)
//
//    val posMgr = TestActorRef[PositionManager]
//    val reqId = UUID.randomUUID().toString
//    val update1 = Update(reqId, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
//    i.send(posMgr, update1)
//
//    val transfer1 = Transfer(reqId, account1, product1, Long, Collateral, dQuantity = transQty1)
//    i.send(posMgr, transfer1)
//    i.receive(5 minutes) should be (DuplicatedRequest)
//
//    val transfer2 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty1)
//    i.send(posMgr, transfer2)
//
//    val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
//    i.send(posMgr, query)
//    val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
//    result should have size (2)
//    result.find(t => t._2.positionType == Long).map(_._2).get should have (
//      'account(account1),
//      'productId(product1),
//      'quantity(quantity1 - transQty1)
//    )
//
//    result.find(t => t._2.positionType == Collateral).map(_._2).get should have (
//      'account(account1),
//      'productId(product1),
//      'quantity(transQty1)
//    )
//  }

}
