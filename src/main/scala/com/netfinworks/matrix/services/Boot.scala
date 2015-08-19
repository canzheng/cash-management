package com.netfinworks.matrix.services

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http

import scala.concurrent.duration._

/**
 * Created by canzheng on 8/11/15.
 */
object Boot extends App {
  implicit val system = ActorSystem("matrix-service")

  // create and start our service actor
  val service = system.actorOf(Props[MatrixService], "orders")

  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "localhost", port = 8080)
}
