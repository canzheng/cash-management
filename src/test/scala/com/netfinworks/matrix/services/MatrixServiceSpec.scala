package com.netfinworks.matrix.services

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.netfinworks.matrix.orders.domain.{Buy, Open, Order}
import org.scalatest.{FlatSpecLike, Matchers}
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import OrderProtocol._
/**
 * Created by canzheng on 8/12/15.
 */
class MatrixServiceSpec (_system: ActorSystem) extends TestKit(_system) with FlatSpecLike with Matchers {

  def this() = this(ActorSystem("MyActorSystem"))
  import system.dispatcher

  "An order service " should "accept a new order" in {
    val o = Order(orderId = "",
      productId = "",
      quantity = 0,
      amount = 0,
      account = "",
      parties = Nil,
    orderType = Buy,
    status = Open)

    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
    val response: Future[HttpResponse] =
      pipeline(Post("http://127.0.0.1:8080/order", o))

    Await.result(response, 5 seconds).status should be (StatusCodes.OK)

  }

}
