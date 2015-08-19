package com.netfinworks.matrix.services

import java.util.UUID

import akka.actor.{Props, Actor}
import akka.util.Timeout
import com.netfinworks.matrix.orders.OrderManager
import com.netfinworks.matrix.orders.domain._
import com.netfinworks.matrix.positions.PositionManager
import com.netfinworks.matrix.positions.domain.BatchUpdate
import com.netfinworks.matrix.utils.Constants
import spray.http.MediaTypes._
import spray.http.StatusCodes
import spray.routing.HttpService
import akka.pattern.ask
import spray.httpx.SprayJsonSupport._
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
import OrderProtocol._

/**
 * Created by canzheng on 8/12/15.
 */
class MatrixService extends Actor with HttpService {
  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  val orderMgr = actorRefFactory.actorOf(Props[OrderManager], "orderManager")
  val positionMgr = actorRefFactory.actorOf(Props[PositionManager], "positionManager")


  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)

  implicit val timeout = Timeout(5 seconds)

  val myRoute =
    path("orders") {
      get {
        respondWithMediaType(`application/json`) {
          // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            val orders = Await.result(orderMgr.ask(OrdQuery(pred = {
              _ != null
            })), 5 seconds).asInstanceOf[List[Order]]
            orders
          }
        }
      }
    } ~
      path("order") {
        post {
          entity(as[Order]) { o =>
            orderMgr ! o
            complete(StatusCodes.OK)
          }
        }
      } ~
      path("ordersnetting") {
        get {
          complete {
            val res = orderMgr ? DayEndNetting
            val netQty = Await.result(res, 5 seconds).asInstanceOf[BigDecimal]
            println(netQty)
            StatusCodes.OK
          }
        }
      } ~
      path("updatepositions") {
        get {
          parameters( 'ordQty.as[Double], 'totPos.as[Double], 'prodId ? Constants.CurrentMMFundId) {  ( qty, pos, prodId) =>
            complete {
              val res = orderMgr ? BatchUpdate(UUID.randomUUID().toString, prodId, qty, pos)
              val divList = Await.result(res, 5 seconds).asInstanceOf[List[(String, BigDecimal)]]
              println(divList)
              StatusCodes.OK
            }
          }
        }
      }
}
