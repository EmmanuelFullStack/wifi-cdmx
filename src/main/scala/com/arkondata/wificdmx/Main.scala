package com.arkondata.wificdmx

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import com.arkondata.wificdmx.api.routes.WifiPointRoutes
import com.arkondata.wificdmx.repository.{DatabaseConfig, SlickWifiPointRepository}
import com.arkondata.wificdmx.service.WifiPointServiceImpl
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object Main extends App with LazyLogging {

  implicit val system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "wifi-cdmx")
  implicit val ec: ExecutionContext = system.executionContext

  val config = ConfigFactory.load()
  val host   = config.getString("wifi-cdmx.http.host")
  val port   = config.getInt("wifi-cdmx.http.port")

  val dbConfig   = new DatabaseConfig(config)
  val repo       = new SlickWifiPointRepository(dbConfig)
  val service    = new WifiPointServiceImpl(repo)
  val restRoutes = new WifiPointRoutes(service)

  val bindingFuture: Future[ServerBinding] =
    Http().newServerAt(host, port).bind(restRoutes.routes)

  bindingFuture.onComplete {
    case Success(binding) =>
      val addr = binding.localAddress
      logger.info(s"WiFi CDMX API running at http://${addr.getHostString}:${addr.getPort}/")
      logger.info(s"  REST: http://${addr.getHostString}:${addr.getPort}/api/v1/wifi")
    case Failure(ex) =>
      logger.error(s"Failed to bind on $host:$port", ex)
      system.terminate()
  }

  sys.addShutdownHook {
    logger.info("Shutting down — draining in-flight requests...")
    val shutdown = bindingFuture
      .flatMap(_.terminate(hardDeadline = 10.seconds))
      .flatMap { _ =>
        logger.info("HTTP server drained. Terminating actor system...")
        dbConfig.close()
        system.terminate()
        system.whenTerminated
      }
    Await.result(shutdown, 20.seconds)
    logger.info("Shutdown complete.")
  }
}