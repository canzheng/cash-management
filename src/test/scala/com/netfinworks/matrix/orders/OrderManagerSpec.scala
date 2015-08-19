package com.netfinworks.matrix.orders

import java.util.UUID

import akka.actor.{PoisonPill, ActorSystem, Inbox}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import com.netfinworks.matrix.orders.domain._
import com.netfinworks.matrix.positions.PositionManager._
import com.netfinworks.matrix.positions.domain._
import com.netfinworks.matrix.positions.{Position, PositionManager}
import com.netfinworks.matrix.utils.Constants._
import org.scalatest.Inspectors._
import org.scalatest.LoneElement._
import org.scalatest._
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

/**
 * Created by canzheng on 7/17/15.
 */
class OrderManagerSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers {

  def this() = this(ActorSystem("MyActorSystem"))

  def PosDividend = com.netfinworks.matrix.positions.domain.Dividend


  val account1 = "acct001"

  val account2 = "acct002"

  val account3 = "acct003"

  val ordProto = Order(
    orderId = "",
    productId = CurrentMMFundId,
    orderType = Invest,
    quantity = 1000,
    amount = 0,
    account = account1,
    parties = Nil,
    status = Open)


  val ordMgr = TestActorRef[OrderManager](name = "orderManager")
  val posMgr = TestActorRef[PositionManager](name = "positionManager")
  implicit val i = Inbox.create(_system)
  implicit val timeout = Timeout(5 seconds)

  def assertHasOneOrder(acct: String, orderType: OrderType, qty: BigDecimal,
                 status: OrderStatus, prodId: String = CurrentMMFundId,
                 coll: List[Order] = ordMgr.underlyingActor.orders) = {
    exactly(1, coll) should have (
      'account(acct),
      'orderType(orderType),
      'productId(prodId),
      'quantity(qty),
      'status(status)
    )
  }
  def assertHasOnePosition(acct: String, posType: PositionType, qty: BigDecimal,
                           prodId: String = CurrentMMFundId, coll: List[Position] = posMgr.underlyingActor.posData) = {

    exactly(1, coll) should have (
      'account(acct),
      'productId(prodId),
      'positionType(posType),
      'quantity(qty)
    )
  }
  override def withFixture(test: NoArgTest) = {
    //----------------------------------------------
    //- T0：设置3个账户，各投资1000份，持有1000已确认份额

    posMgr.underlyingActor.posData = Nil
    posMgr.underlyingActor.requestLog.clear()
    posMgr.underlyingActor.histPosData = Nil
    ordMgr.underlyingActor.orders = Nil
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account3)

    // all order in open status
    assertResult(3)(ordMgr.underlyingActor.orders.count(_.status == Open))
    i.send(ordMgr, DayEndNetting)
    assertResult(3000)(i.receive(5 seconds).asInstanceOf[BigDecimal])

    // all order in pendingRec status
    assertResult(3)(ordMgr.underlyingActor.orders.count(_.status == PendingRec))

    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, 3000, 3000)
    Thread sleep 1000

    ordMgr.underlyingActor.orders should have size(3)
    posMgr.underlyingActor.posData should have size(3)

    assertResult(3)(ordMgr.underlyingActor.orders.count(_.status == Completed))

    assertHasOneOrder(account1, Invest, 1000, Completed)
    assertHasOneOrder(account2, Invest, 1000, Completed)
    assertHasOneOrder(account3, Invest, 1000, Completed)

    assertHasOnePosition(account1, Long, 1000)
    assertHasOnePosition(account2, Long, 1000)
    assertHasOnePosition(account3, Long, 1000)


    try super.withFixture(test) // Invoke the test function
    finally {

    }
  }


  "A Invest order " should "be recorded" in {
    //----------------------------------------------
    //- 投资1000，订单被记录，昨日份额无收益
    //----------------------------------------------

    //- T1: account1投资1000份，订单被记录
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString)

    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (1000)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (1)

    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, 1000, 4000)
    Thread sleep 1000
    //- T2: 总确认份额为4000时，T1收益为0，account1持仓2000
    assertHasOnePosition(account1, Long, 2000)
  }

  "An Invest order " should "be recorded" in {
    //----------------------------------------------
    //- 投资1000，订单被记录，昨日份额有收益
    //----------------------------------------------
    //- T1: account1投资1000份，订单被记录
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString)

    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (1000)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (1)

    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, 1000, 4003)
    Thread sleep 1000

    //- T2: 总确认份额为4003时，T1收益为3，account1持仓2000，account1收益1
    assertHasOnePosition(account1, Long, 2000)
    assertHasOnePosition(account1, PosDividend, 1)

  }

  "A QRedeem order with sufficient qr quota" should "be broken into a buy a sell and a normal redeem" in {
    //----------------------------------------------
    //- 快赎500
    //----------------------------------------------
    //- T1：account1快赎500，快赎限额足够，生成平台买单500，account1卖单500，平台普赎500
    //- 平台头寸500，account1头寸500 （1000-500）
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, orderType = QRedeem, quantity = 500)

    assertHasOneOrder(account1, Sell, 500, Completed)
    assertHasOneOrder(PlatformAccount, Buy, 500, Completed)
    assertHasOneOrder(PlatformAccount, Redeem, 500, Open)

    assertHasOnePosition(account1, Long, 500)
    assertHasOnePosition(PlatformAccount, Long, 500)


    //- 轧差额-500
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (-500)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (1)

    //- T2：确认订单-500，份额2503
    //- 平台份额0，account1份额500，平台收益0.5，account1收益0.5
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, -500, 2503)
    Thread sleep 1000

    assertHasOnePosition(account1, Long, 500)
    assertHasOnePosition(account2, Long, 1000)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 0)

    assertHasOnePosition(account1, PosDividend, 0.5)
    assertHasOnePosition(account2, PosDividend, 1)
    assertHasOnePosition(account3, PosDividend, 1)
    assertHasOnePosition(PlatformAccount, PosDividend, 0.5)
  }

  "A net redeem with 1 Invest and 1 Qredeem" should "be recorded" in {
    //----------------------------------------------
    //- 快赎800，申购300，结果净赎500
    //----------------------------------------------
    //- T1：account1快赎800，快赎限额足够，生成平台买单800，account1卖单800，平台普赎800
    //- T1: account2申购300
    //- 平台头寸800，account1头寸200
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, orderType = QRedeem, quantity = 800)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2, orderType = Invest, quantity = 300)

    assertHasOneOrder(account1, Sell, 800, Completed)
    assertHasOneOrder(PlatformAccount, Buy, 800, Completed)
    assertHasOneOrder(PlatformAccount, Redeem, 800, Open)
    assertHasOneOrder(account2, Invest, 300, Open)

    assertHasOnePosition(account1, Long, 200)
    assertHasOnePosition(PlatformAccount, Long, 800)
    assertHasOnePosition(account2, Long, 1000)
    assertHasOnePosition(account3, Long, 1000)

    //- 轧差额-500
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (-500)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (2)

    //- T2：确认订单-300，份额2503
    //- 平台份额0，account1份额200，account2份额1300，平台收益0.8，account1收益0.2
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, -500, 2503)
    Thread sleep 1000

    assertHasOnePosition(account1, Long, 200)
    assertHasOnePosition(account2, Long, 1300)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 0)

    assertHasOnePosition(account1, PosDividend, 0.2)
    assertHasOnePosition(account2, PosDividend, 1)
    assertHasOnePosition(account3, PosDividend, 1)
    assertHasOnePosition(PlatformAccount, PosDividend, 0.8)
  }

  "A net Invest with 1 Invest and 1 Qredeem" should "be recorded" in {
    //----------------------------------------------
    //- 快赎300，申购800，结果净申500
    //----------------------------------------------
    //- T1：account1快赎300，快赎限额足够，生成平台买单300，account1卖单300，平台普赎300
    //- T1: account2申购800
    //- 平台头寸300，account1头寸700
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, orderType = QRedeem, quantity = 300)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2, orderType = Invest, quantity = 800)


    assertHasOneOrder(account1, Sell, 300, Completed)
    assertHasOneOrder(PlatformAccount, Buy, 300, Completed)
    assertHasOneOrder(PlatformAccount, Redeem, 300, Open)
    assertHasOneOrder(account2, Invest, 800, Open)


    assertHasOnePosition(account1, Long, 700)
    assertHasOnePosition(PlatformAccount, Long, 300)
    assertHasOnePosition(account2, Long, 1000)
    assertHasOnePosition(account3, Long, 1000)

    //- 轧差额500
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (500)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (2)

    //- T2：确认订单500，份额3503
    //- 平台份额0，account1份额700，account2份额1800，平台收益0.3，account1收益0.7
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, 500, 3503)
    Thread sleep 1000

    assertHasOnePosition(account1, Long, 700)
    assertHasOnePosition(account2, Long, 1800)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 0)

    assertHasOnePosition(account1, PosDividend, 0.7)
    assertHasOnePosition(account2, PosDividend, 1)
    assertHasOnePosition(account3, PosDividend, 1)
    assertHasOnePosition(PlatformAccount, PosDividend, 0.3)
  }

  "A net wash (netting 0) with 2 Invest and 1 Qredeem" should "be recorded" in {
    //----------------------------------------------
    //- 快赎800，总申购800，结果对冲
    //----------------------------------------------
    //- T1：account1快赎800，快赎限额足够，生成平台买单800，account1卖单800，平台普赎800
    //- T1: account2申购300
    //- T1: account3申购500
    //- 平台头寸800，account1头寸200
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, orderType = QRedeem, quantity = 800)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2, orderType = Invest, quantity = 300)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account3, orderType = Invest, quantity = 500)

    assertHasOneOrder(account1, Sell, 800, Completed)
    assertHasOneOrder(PlatformAccount, Buy, 800, Completed)
    assertHasOneOrder(PlatformAccount, Redeem, 800, Open)
    assertHasOneOrder(account2, Invest, 300, Open)
    assertHasOneOrder(account3, Invest, 500, Open)

    assertHasOnePosition(account1, Long, 200)
    assertHasOnePosition(PlatformAccount, Long, 800)
    assertHasOnePosition(account2, Long, 1000)
    assertHasOnePosition(account3, Long, 1000)

    //- 轧差额0
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (0)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (3)

    //- T2：确认订单0，份额3003
    //- 平台份额0，account1份额200，account2份额1300，account3份额1500，平台收益0.8，account1收益0.2
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, 0, 3003)
    Thread sleep 1000

    assertHasOnePosition(account1, Long, 200)
    assertHasOnePosition(account2, Long, 1300)
    assertHasOnePosition(account3, Long, 1500)
    assertHasOnePosition(PlatformAccount, Long, 0)

    assertHasOnePosition(account1, PosDividend, 0.2)
    assertHasOnePosition(account2, PosDividend, 1)
    assertHasOnePosition(account3, PosDividend, 1)
    assertHasOnePosition(PlatformAccount, PosDividend, 0.8)
  }

  "A Redeem with not enough position" should "be recorded" in {
    //----------------------------------------------
    //- 快赎1800，申购300，快赎等待，结果净申300
    //----------------------------------------------
    // - Question: qredeem 1800, position 1000, do we redeem the 1000 existing position
    //- T1：account1快赎1800，快赎限额足够，持有头寸不够，快赎单计入下次轧差
    //- T1: account2申购300
    //- 平台头寸0，account1头寸1000，account2头寸1000
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, orderType = QRedeem, quantity = 1800)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2, orderType = Invest, quantity = 300)

    assertHasOneOrder(account1, QRedeem, 1800, Open)
    assertHasOneOrder(account2, Invest, 300, Open)

    assertHasOnePosition(account1, Long, 1000)
    assertHasOnePosition(account2, Long, 1000)
    assertHasOnePosition(account3, Long, 1000)

    //- 轧差额300
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (300)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (1)

    //- T2：确认订单300，份额3303
    //- 平台份额0，account1份额1000，account2份额1300，accoun1收益1，account2收益1
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, 300, 3303)
    Thread sleep 1000

    //- account1快赎1800仍在
    assertHasOneOrder(account1, QRedeem, 1800, Open)

    assertHasOnePosition(account1, Long, 1000)
    assertHasOnePosition(account2, Long, 1300)
    assertHasOnePosition(account3, Long, 1000)

    assertHasOnePosition(account1, PosDividend, 1)
    assertHasOnePosition(account2, PosDividend, 1)
    assertHasOnePosition(account3, PosDividend, 1)
  }

  "Recognized quantity less than recorded quantity (net invest)" should "be recorded" in {
    //----------------------------------------------
    //- 机构确认小于平台记录(机构确认净申）
    //- Question：do we hold negative pos, or do we limit Invest orders
    //----------------------------------------------
    //- T1：account1申购500
    //- T1: account2申购500
    //- 平台头寸0，account1头寸1000，account2头寸1000
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, quantity = 500)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2, quantity = 500)

    assertHasOneOrder(account1, Invest, 500, Open)
    assertHasOneOrder(account2, Invest, 500, Open)

    assertHasOnePosition(account1, Long, 1000)
    assertHasOnePosition(account2, Long, 1000)
    assertHasOnePosition(account3, Long, 1000)

    //- 轧差额1000
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (1000)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (2)

    //- T2：确认订单600，份额3603
    //- 平台份额-400，account1份额1500，account2份额1500，accoun1收益1，account2收益1
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, 600, 3603)
    Thread sleep 1000

    assertHasOneOrder(PlatformAccount, Invest, 400, Open)

    assertHasOnePosition(account1, Long, 1500)
    assertHasOnePosition(account2, Long, 1500)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, -400)


    assertHasOnePosition(account1, PosDividend, 1)
    assertHasOnePosition(account2, PosDividend, 1)
    assertHasOnePosition(account3, PosDividend, 1)
  }

  "Recognized quantity less than recorded quantity (net redeem)" should "be recorded" in {
    //----------------------------------------------
    //- 机构确认小于平台记录(机构确认赎回）
    //----------------------------------------------
    //- T1：account1赎回500
    //- T1: account2赎回300
    //- 平台头寸0，account1头寸500，account2头寸700
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, orderType=QRedeem, quantity = 500)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2, orderType=QRedeem, quantity = 300)

    assertHasOneOrder(account1, Sell, 500, Completed)
    assertHasOneOrder(account2, Sell, 300, Completed)
    assertHasOneOrder(PlatformAccount, Buy, 500, Completed)
    assertHasOneOrder(PlatformAccount, Buy, 300, Completed)
    assertHasOneOrder(PlatformAccount, Redeem, 500, Open)
    assertHasOneOrder(PlatformAccount, Redeem, 300, Open)

    assertHasOnePosition(account1, Long, 500)
    assertHasOnePosition(account2, Long, 700)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 800)

    //- 轧差额-800
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (-800)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (2)

    //- T2：确认订单-1000，份额2003
    //- 平台份额-200，account1份额500，account2份额700，accoun1收益0.5，account2收益0.7，平台收益0.8
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, -1000, 2003)
    Thread sleep 1000

    assertHasOneOrder(PlatformAccount, Invest, 200, Open)

    assertHasOnePosition(account1, Long, 500)
    assertHasOnePosition(account2, Long, 700)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, -200)


    assertHasOnePosition(account1, PosDividend, 0.5)
    assertHasOnePosition(account2, PosDividend, 0.7)
    assertHasOnePosition(account3, PosDividend, 1)
    assertHasOnePosition(PlatformAccount, PosDividend, 0.8)

  }

  "Recognized quantity more than recorded quantity (net invest)" should "be recorded" in {
    //----------------------------------------------
    //- 机构确认大于平台记录(机构确认净申）
    //- Question: do we need to redeem right away
    //----------------------------------------------
    //- T1：account1申购500
    //- T1: account2申购500
    //- 平台头寸0，account1头寸1000，account2头寸1000
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, quantity = 500)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2, quantity = 500)

    assertHasOneOrder(account1, Invest, 500, Open)
    assertHasOneOrder(account2, Invest, 500, Open)

    assertHasOnePosition(account1, Long, 1000)
    assertHasOnePosition(account2, Long, 1000)
    assertHasOnePosition(account3, Long, 1000)

    //- 轧差额1000
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (1000)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (2)

    //- T2：确认订单1200，份额4203
    //- 平台份额200，account1份额1500，account2份额1500，accoun1收益1，account2收益1
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, 1200, 4203)
    Thread sleep 1000

    assertHasOneOrder(PlatformAccount, Invest, 200, Completed)

    assertHasOnePosition(account1, Long, 1500)
    assertHasOnePosition(account2, Long, 1500)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 200)


    assertHasOnePosition(account1, PosDividend, 1)
    assertHasOnePosition(account2, PosDividend, 1)
    assertHasOnePosition(account3, PosDividend, 1)
  }

  "Recognized quantity more than recorded quantity (net redeem)" should "be recorded" in {
    //----------------------------------------------
    //- 机构确认大于平台记录(机构确认赎回）
    //----------------------------------------------
    //- T1：account1赎回500
    //- T1: account2赎回300
    //- 平台头寸0，account1头寸500，account2头寸700
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, orderType=QRedeem, quantity = 500)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2, orderType=QRedeem, quantity = 300)

    assertHasOneOrder(account1, Sell, 500, Completed)
    assertHasOneOrder(account2, Sell, 300, Completed)
    assertHasOneOrder(PlatformAccount, Buy, 500, Completed)
    assertHasOneOrder(PlatformAccount, Buy, 300, Completed)
    assertHasOneOrder(PlatformAccount, Redeem, 500, Open)
    assertHasOneOrder(PlatformAccount, Redeem, 300, Open)

    assertHasOnePosition(account1, Long, 500)
    assertHasOnePosition(account2, Long, 700)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 800)

    //- 轧差额-800
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (-800)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (2)

    //- T2：确认订单-400，份额2603
    //- 平台份额400，account1份额500，account2份额700，accoun1收益0.5，account2收益0.7，平台收益0.8
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, -400, 2603)
    Thread sleep 1000

    assertHasOneOrder(PlatformAccount, Invest, 400, Completed)

    assertHasOnePosition(account1, Long, 500)
    assertHasOnePosition(account2, Long, 700)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 400)


    assertHasOnePosition(account1, PosDividend, 0.5)
    assertHasOnePosition(account2, PosDividend, 0.7)
    assertHasOnePosition(account3, PosDividend, 1)
    assertHasOnePosition(PlatformAccount, PosDividend, 0.8)

  }



  "Net invest lower than institute minimum" should "be recorded" in {
    //----------------------------------------------
    //- 机构确认净申，小于机构最小净申
    //- Question: do we need to redeem right away
    //----------------------------------------------
    //- T1：account1申购150
    //- T1: account2赎回100
    //- 平台头寸100，account1头寸1000，account2头寸900
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, quantity = 150)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2, orderType=QRedeem, quantity = 100)

    assertHasOneOrder(account1, Invest, 150, Open)
    assertHasOneOrder(account2, Sell, 100, Completed)
    assertHasOneOrder(PlatformAccount, Buy, 100, Completed)
    assertHasOneOrder(PlatformAccount, Redeem, 100, Open)

    assertHasOnePosition(account1, Long, 1000)
    assertHasOnePosition(account2, Long, 900)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 100)


    //- 轧差额100
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (100)

    assertHasOneOrder(PlatformAccount, Invest, 50, PendingRec)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (3)

    //- T2：确认订单100，份额3103
    //- 平台份额50，account1份额1150，account2份额900，accoun1收益1，account2收益0.9, 平台收益0.1
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, 100, 3103)
    Thread sleep 1000

    assertHasOnePosition(account1, Long, 1150)
    assertHasOnePosition(account2, Long, 900)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 50)


    assertHasOnePosition(account1, PosDividend, 1)
    assertHasOnePosition(account2, PosDividend, 0.9)
    assertHasOnePosition(account3, PosDividend, 1)
    assertHasOnePosition(PlatformAccount, PosDividend, 0.1)

  }

  "Net redeem lower than institute minimum" should "be recorded" in {
    //----------------------------------------------
    //- 机构确认净赎，小于机构最小净赎
    //----------------------------------------------
    //- T1：account1申购100
    //- T1: account2赎回140
    //- 平台头寸150，account1头寸1000，account2头寸860
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, quantity = 100)
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = account2, orderType=QRedeem, quantity = 140)

    assertHasOneOrder(account1, Invest, 100, Open)
    assertHasOneOrder(account2, Sell, 140, Completed)
    assertHasOneOrder(PlatformAccount, Buy, 140, Completed)
    assertHasOneOrder(PlatformAccount, Redeem, 140, Open)

    assertHasOnePosition(account1, Long, 1000)
    assertHasOnePosition(account2, Long, 860)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 140)


    //- 轧差额100
    i.send(ordMgr, DayEndNetting)
    i.receive(5 seconds).asInstanceOf[BigDecimal] should be (0)

    assertHasOneOrder(PlatformAccount, Invest, 40, PendingRec)
    ordMgr.underlyingActor.orders.count(_.status == PendingRec) should be (3)

    //- T2：确认订单0，份额3003
    //- 平台份额40，account1份额1100，account2份额860，accoun1收益1，account2收益0.86, 平台收益0.14
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, 0, 3003)
    Thread sleep 1000

    assertHasOnePosition(account1, Long, 1100)
    assertHasOnePosition(account2, Long, 860)
    assertHasOnePosition(account3, Long, 1000)
    assertHasOnePosition(PlatformAccount, Long, 40)


    assertHasOnePosition(account1, PosDividend, 1)
    assertHasOnePosition(account2, PosDividend, 0.86)
    assertHasOnePosition(account3, PosDividend, 1)
    assertHasOnePosition(PlatformAccount, PosDividend, 0.14)

  }


  // TODO: when qredeem quota is zero
  // TODO: multiple nettings
  // TODO: rerun batch update (possibly add new orders to last batch)

  // TODO: keep record of "should have" positions
  // TODO: add dividend to position

  // TODO(low): extract dayendnettingjob

//  "A QRedeem order without sufficient position" should "be broken into a platform redeem, a platform buy and a client sell order" in {
//
//
//
//    ordMgr ! ordProto.copy(orderType = QRedeem)
//    ordMgr.underlyingActor.orders should have size(4)
//    exactly(1, ordMgr.underlyingActor.orders) should have (
//      'account(account1),
//      'orderType(QRedeem),
//      'productId(CurrentMMFundId),
//      'quantity(1000),
//      'status(Completed)
//    )
//    exactly(1, ordMgr.underlyingActor.orders) should have (
//      'account(account1),
//      'orderType(Sell),
//      'productId(CurrentMMFundId),
//      'quantity(1000),
//      'status(Completed)
//    )
//    exactly(1, ordMgr.underlyingActor.orders) should have (
//      'account(PlatformAccount),
//      'orderType(Buy),
//      'productId(CurrentMMFundId),
//      'quantity(1000),
//      'status(Completed)
//    )
//    exactly(1, ordMgr.underlyingActor.orders) should have (
//      'account(PlatformAccount),
//      'orderType(Redeem),
//      'productId(CurrentMMFundId),
//      'quantity(1000),
//      'status(Open)
//    )
//
//  }
//
//  "A QRedeem order when fully dealed" should "be broken into a platform redeem, a platform buy and a client sell order" in {
//
//
//
//    ordMgr ! ordProto.copy(orderType = QRedeem)
//    ordMgr.underlyingActor.orders should have size(4)
//    exactly(1, ordMgr.underlyingActor.orders) should have (
//      'account(account1),
//      'orderType(QRedeem),
//      'productId(CurrentMMFundId),
//      'quantity(1000),
//      'status(Completed)
//    )
//    exactly(1, ordMgr.underlyingActor.orders) should have (
//      'account(account1),
//      'orderType(Sell),
//      'productId(CurrentMMFundId),
//      'quantity(1000),
//      'status(Completed)
//    )
//    exactly(1, ordMgr.underlyingActor.orders) should have (
//      'account(PlatformAccount),
//      'orderType(Buy),
//      'productId(CurrentMMFundId),
//      'quantity(1000),
//      'status(Completed)
//    )
//    exactly(1, ordMgr.underlyingActor.orders) should have (
//      'account(PlatformAccount),
//      'orderType(Redeem),
//      'productId(CurrentMMFundId),
//      'quantity(1000),
//      'status(Open)
//    )
//
//
//  }


  //   "A new position creation" should "create new position" in {
  //     val quantity1 = 100
  //
  //     implicit val i = Inbox.create(_system)
  //
  //     val posMgr = TestActorRef[PositionManager]
  //     val update = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1)
  //     i.send(posMgr, update)
  //     assert(posMgr.underlyingActor.posData.exists(t => matches(updateMatcher(update), t._2)))
  //
  //     val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
  //     i.send(posMgr, query)
  //     val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
  //     forAll(result) {
  //       t =>
  //         t._2 should have(
  //           'account(account1),
  //           'productId(product1),
  //           'positionType(Long),
  //           'quantity(quantity1)
  //         )
  //     }
  //
  //     posMgr.underlyingActor.histPosData should be (Nil)
  //
  //   }
  //
  //   "A position increase" should "increase position on same account, productId, positionType" in {
  //     val quantity1 = 100
  //     val quantity2 = 300
  //
  //     implicit val i = Inbox.create(_system)
  //     implicit val timeout = Timeout(5 minutes)
  //
  //     val posMgr = TestActorRef[PositionManager]
  //     val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1)
  //     i.send(posMgr, update1)
  //     val update2 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity2)
  //     i.send(posMgr, update2)
  //
  //
  //     val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
  //     i.send(posMgr, query)
  //     val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
  //     forAll(result) {
  //       t =>
  //         t._2 should have(
  //           'account(account1),
  //           'productId(product1),
  //           'positionType(Long),
  //           'quantity(quantity1 + quantity2)
  //         )
  //     }
  //
  //     posMgr.underlyingActor.histPosData should have size(1)
  //     posMgr.underlyingActor.histPosData.head should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(quantity1)
  //     )
  //   }
  //
  //   "A first-time position transfer" should "decrease fromPosition and create new toPosition" in {
  //     val quantity1 = 100
  //
  //     val transQty1 = 20
  //
  //     implicit val i = Inbox.create(_system)
  //
  //     val posMgr = TestActorRef[PositionManager]
  //     val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
  //     i.send(posMgr, update1)
  //     val transfer1 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty1)
  //     i.send(posMgr, transfer1)
  //
  //
  //     val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
  //     i.send(posMgr, query)
  //     val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
  //     result should have size (2)
  //     result.find(t => t._2.positionType == Long).map(_._2).get should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(quantity1 - transQty1)
  //     )
  //
  //     result.find(t => t._2.positionType == Collateral).map(_._2).get should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Collateral),
  //       'quantity(transQty1)
  //     )
  //
  //     posMgr.underlyingActor.histPosData should have size(1)
  //     posMgr.underlyingActor.histPosData.head should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(quantity1)
  //     )
  //   }
  //
  //   "A second-time positon transfer" should "decrease fromPosition and increase toPosition" in {
  //     val quantity1 = 100
  //     val transQty1 = 20
  //
  //     val transQty2 = 30
  //
  //     implicit val i = Inbox.create(_system)
  //
  //     val posMgr = TestActorRef[PositionManager]
  //     val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
  //     i.send(posMgr, update1)
  //     val transfer1 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty1)
  //     i.send(posMgr, transfer1)
  //     val transfer2 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty2)
  //     i.send(posMgr, transfer2)
  //
  //     val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
  //     i.send(posMgr, query)
  //     val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
  //     result should have size (2)
  //     result.find(t => t._2.positionType == Long).map(_._2).get should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(quantity1 - transQty1 - transQty2)
  //     )
  //
  //     result.find(t => t._2.positionType == Collateral).map(_._2).get should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Collateral),
  //       'quantity(transQty1 + transQty2)
  //     )
  //
  //     posMgr.underlyingActor.histPosData should have size(3)
  //     exactly(1, posMgr.underlyingActor.histPosData) should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(quantity1)
  //     )
  //
  //     exactly(1, posMgr.underlyingActor.histPosData) should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(quantity1 - transQty1)
  //     )
  //
  //     exactly(1, posMgr.underlyingActor.histPosData) should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Collateral),
  //       'quantity(transQty1)
  //     )
  //
  //   }
  //
  //
  //   "A first-time complete transfer" should "decrease fromPosition to 0 and create toPosition" in {
  //     val quantity1 = 100
  //
  //     implicit val i = Inbox.create(_system)
  //
  //     val posMgr = TestActorRef[PositionManager]
  //     val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
  //     i.send(posMgr, update1)
  //     val transfer1 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, isCompleteTransfer = true)
  //     i.send(posMgr, transfer1)
  //
  //
  //     val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
  //     i.send(posMgr, query)
  //     val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
  //     result should have size (2)
  //     result.find(t => t._2.positionType == Long).map(_._2).get should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(0)
  //     )
  //
  //     result.find(t => t._2.positionType == Collateral).map(_._2).get should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Collateral),
  //       'quantity(quantity1)
  //     )
  //
  //     // history should have quantity1
  //     posMgr.underlyingActor.histPosData should have size(1)
  //     posMgr.underlyingActor.histPosData.head should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(quantity1)
  //     )
  //   }
  //
  //   "A second-time complete transfer" should "decrease fromPosition to 0 and increase toPosition" in {
  //     val quantity1 = 100
  //     val transQty1 = 20
  //
  //     implicit val i = Inbox.create(_system)
  //
  //     val posMgr = TestActorRef[PositionManager]
  //     val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
  //     i.send(posMgr, update1)
  //     val transfer1 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty1)
  //     i.send(posMgr, transfer1)
  //     val transfer2 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, isCompleteTransfer = true)
  //     i.send(posMgr, transfer2)
  //
  //     val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
  //     i.send(posMgr, query)
  //     val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
  //     result should have size (2)
  //     result.find(t => t._2.positionType == Long).map(_._2).get should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(0)
  //     )
  //
  //     result.find(t => t._2.positionType == Collateral).map(_._2).get should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Collateral),
  //       'quantity(quantity1)
  //     )
  //
  //     // history should have 2 records quantity1 - transQty1
  //     posMgr.underlyingActor.histPosData should have size(3)
  //     exactly(1, posMgr.underlyingActor.histPosData) should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(quantity1)
  //     )
  //     exactly(1, posMgr.underlyingActor.histPosData) should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Long),
  //       'quantity(quantity1 - transQty1)
  //     )
  //     exactly(1, posMgr.underlyingActor.histPosData) should have (
  //       'account(account1),
  //       'productId(product1),
  //       'positionType(Collateral),
  //       'quantity(transQty1)
  //     )
  //
  //   }
  //
  //   "A transfer from non-existent position" should "receive error" in {
  //
  //     val quantity1 = 100
  //
  //     val transQty1 = 20
  //
  //     implicit val i = Inbox.create(_system)
  //
  //     val posMgr = TestActorRef[PositionManager]
  //     val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
  //     i.send(posMgr, update1)
  //     val transfer1 = Transfer(UUID.randomUUID().toString, account1, product2, Long, Collateral, dQuantity = transQty1)
  //     i.send(posMgr, transfer1)
  //
  //
  //     val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
  //     i.send(posMgr, query)
  //     i.receive(5 minutes) should be (FromPositionMissing)
  //   }
  //
  //   "A transfer from insuffient position" should "receive error" in {
  //
  //     val quantity1 = 100
  //
  //     val transQty1 = 200
  //
  //     implicit val i = Inbox.create(_system)
  //
  //     val posMgr = TestActorRef[PositionManager]
  //     val update1 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
  //     i.send(posMgr, update1)
  //     val transfer1 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty1)
  //     i.send(posMgr, transfer1)
  //
  //
  //     val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
  //     i.send(posMgr, query)
  //     i.receive(5 minutes) should be (NotEnoughToTransfer)
  //   }
  //
  //   "An update with an old requestId" should "receive error" in {
  //
  //     val quantity1 = 100
  //     val quantity2 = 300
  //
  //     implicit val i = Inbox.create(_system)
  //     implicit val timeout = Timeout(5 minutes)
  //
  //     val posMgr = TestActorRef[PositionManager]
  //     val reqId = UUID.randomUUID().toString
  //     val update1 = Update(reqId, account1, product1, Long, dQuantity = quantity1)
  //     i.send(posMgr, update1)
  //     val update2 = Update(reqId, account1, product1, Long, dQuantity = quantity2)
  //     i.send(posMgr, update2)
  //
  //
  //     i.receive(5 minutes) should be (DuplicatedRequest)
  //
  //     val update3 = Update(UUID.randomUUID().toString, account1, product1, Long, dQuantity = quantity2)
  //     i.send(posMgr, update3)
  //
  //
  //     val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
  //     i.send(posMgr, query)
  //     val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
  //     forAll(result) {
  //       t =>
  //         t._2 should have(
  //           'account(account1),
  //           'productId(product1),
  //           'quantity(quantity1 + quantity2)
  //         )
  //     }
  //
  //   }
  //
  //   "A transfer with an old requestId" should "receive error" in {
  //
  //     val quantity1 = 100
  //
  //     val transQty1 = 20
  //
  //     implicit val i = Inbox.create(_system)
  //
  //     val posMgr = TestActorRef[PositionManager]
  //     val reqId = UUID.randomUUID().toString
  //     val update1 = Update(reqId, account1, product1, Long, dQuantity = quantity1, dAvailableQty = quantity1)
  //     i.send(posMgr, update1)
  //
  //     val transfer1 = Transfer(reqId, account1, product1, Long, Collateral, dQuantity = transQty1)
  //     i.send(posMgr, transfer1)
  //     i.receive(5 minutes) should be (DuplicatedRequest)
  //
  //     val transfer2 = Transfer(UUID.randomUUID().toString, account1, product1, Long, Collateral, dQuantity = transQty1)
  //     i.send(posMgr, transfer2)
  //
  //     val query = PosQuery(None, Some(account1), Some(product1), Some(Long))
  //     i.send(posMgr, query)
  //     val result = i.receive(5 minutes).asInstanceOf[mutable.Map[String, Position]]
  //     result should have size (2)
  //     result.find(t => t._2.positionType == Long).map(_._2).get should have (
  //       'account(account1),
  //       'productId(product1),
  //       'quantity(quantity1 - transQty1)
  //     )
  //
  //     result.find(t => t._2.positionType == Collateral).map(_._2).get should have (
  //       'account(account1),
  //       'productId(product1),
  //       'quantity(transQty1)
  //     )
  //   }

}
