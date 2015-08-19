package com.netfinworks.matrix.batches

import akka.actor.ActorSystem
import com.netfinworks.matrix.positions.Position
import spray.http._
import spray.json.DefaultJsonProtocol
import spray.httpx.encoding.{Gzip, Deflate}
import spray.httpx.SprayJsonSupport._
import spray.client.pipelining._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
 * Created by canzheng on 8/11/15.
 */
object DayEndNettingJob extends App {

  implicit val system = ActorSystem()
  import system.dispatcher // execution context for futures

  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  val response: Future[HttpResponse] =
    pipeline(Get("http://127.0.0.1:8080/orders/netting"))

  println(Await.result(response, 5 seconds).message)
}
