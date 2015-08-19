package com.netfinworks.matrix.exchange

import akka.actor.ActorSystem
import akka.actor.Inbox
import akka.testkit.{TestKit, TestActorRef, TestFSMRef}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

import org.scalatest.{Matchers, FlatSpecLike, WordSpecLike, FlatSpec}

/**
 * Created by canzheng on 7/17/15.
 */
class OrderFSMSpec(_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers {

  def this() = this(ActorSystem("MyActorSystem"))

  "An account" should "accept positive deposits" in {
    implicit val i = Inbox.create(_system)
    implicit val timeout = Timeout(5 minutes)

    var orderFSM = TestFSMRef(new OrderFSM)
    val ref: TestActorRef[OrderFSM] = orderFSM
    // assert(orderFSM.stateName == OrderFSM.New)
    i.send(orderFSM,new OrderFSM.OrderMsg("order"))
    assert(orderFSM.stateName == OrderFSM.PendingExec)

    val execRpt = new OrderFSM.ExecutionReport()

    i.send(orderFSM,execRpt)
    assert(orderFSM.stateName == OrderFSM.PendingPayment)
    //expectMsg(execRpt)
    i.receive(5 minutes) should be (execRpt)


    val paymentRpt = new OrderFSM.PaymentReport()
    i.send(orderFSM, paymentRpt)
    assert(orderFSM.stateName == OrderFSM.Completed)

    //expectMsg(execRpt)
    i.receive(5 minutes) should be (paymentRpt)

  }
}
