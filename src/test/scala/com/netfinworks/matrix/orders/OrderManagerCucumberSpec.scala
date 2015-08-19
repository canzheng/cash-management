package com.netfinworks.matrix.orders

import java.util.UUID

import akka.actor.{ActorSystem, Inbox}
import akka.testkit.{TestActorRef, TestKit}
import akka.util.Timeout
import com.netfinworks.matrix.orders.domain
import com.netfinworks.matrix.orders.domain._
import com.netfinworks.matrix.positions.domain._
import com.netfinworks.matrix.positions.{Position, PositionManager}
import com.netfinworks.matrix.utils.Constants._
import cucumber.api.scala.{EN, ScalaDsl}
import org.scalatest._

import scala.concurrent.duration._

/**
 * Created by canzheng on 7/17/15.
 */
class OrderManagerCucumberSpec(_system: ActorSystem) extends TestKit(_system)
with ScalaDsl with EN with Matchers {

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
    exactly(1, coll) should have(
      'account(acct),
      'orderType(orderType),
      'productId(prodId),
      'quantity(qty),
      'status(status)
    )
  }

  def assertHasOnePosition(acct: String, posType: PositionType, qty: BigDecimal,
                           prodId: String = CurrentMMFundId, coll: List[Position] = posMgr.underlyingActor.posData) = {

    exactly(1, coll) should have(
      'account(acct),
      'productId(prodId),
      'positionType(posType),
      'quantity(qty)
    )
  }

  Given( """系统初始化""") { () =>
    posMgr.underlyingActor.posData = Nil
    posMgr.underlyingActor.requestLog.clear()
    posMgr.underlyingActor.histPosData = Nil
    ordMgr.underlyingActor.orders = Nil
  }

  Given( """用户 ([a-zA-Z0-9]+) ([a-zA-Z]+) (-?\d+\.?\d*) 份额""") { (acct: String, ordType: String, qty: String) =>
    ordMgr ! ordProto.copy(orderId = UUID.randomUUID().toString, account = acct, orderType = ordType, quantity = BigDecimal(qty))
  }

  Given("""完成轧差 (\-?\d+\.?\d*)""") { (ordQty: String) =>
  i.send(ordMgr, DayEndNetting)
  assertResult(BigDecimal(ordQty))(i.receive(5 seconds).asInstanceOf[BigDecimal])
  }

  Given("""基金确认订单总额 (-?\d+\.?\d*), 平台总份额 (\d+\.?\d*)""") { (totalOrderQty: String, totalPosQty: String) =>
    posMgr ! BatchUpdate(UUID.randomUUID().toString, CurrentMMFundId, BigDecimal(totalOrderQty), BigDecimal(totalPosQty))
    Thread sleep 1000
  }

  Then ("""用户 ([a-z0-9]+) 有 ([a-zA-Z]+) ([a-zA-Z]+) 订单 (-?\d+\.?\d*) 份""") { (acct: String, ordStatus: String, ordType: String, qty: String) =>
    assertHasOneOrder(acct, ordType, BigDecimal(qty), ordStatus)
  }

  Then ("""用户 ([a-z0-9]+) 有 ([a-zA-Z]+) 头寸 (-?\d+\.?\d*) 份""") { (acct: String, posType: String, qty: String) =>
    assertHasOnePosition(acct, posType, BigDecimal(qty))
  }

  Then ("""用户 ([a-z0-9]+) 无法赎回 (-?\d+\.?\d*) 份""") { (acct: String, qty: String) =>
    i.send(ordMgr, ordProto.copy(orderId = UUID.randomUUID().toString, orderType = QRedeem, quantity = BigDecimal(qty)))
    i.receive(5 seconds).isInstanceOf[OrderRejected]
  }










  // TODO: when qredeem quota is zero
  // TODO: multiple nettings
  // TODO: rerun batch update (possibly add new orders to last batch)

  // TODO: keep record of "should have" positions
  // TODO: add dividend to position

  // TODO(low): extract dayendnettingjob


}
