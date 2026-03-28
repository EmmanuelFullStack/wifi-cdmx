package com.arkondata.wificdmx

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import com.arkondata.wificdmx.api.routes.{GraphQLRoute, WifiPointRoutes}
import com.arkondata.wificdmx.repository.{DatabaseConfig, SlickWifiPointRepository}
import com.arkondata.wificdmx.service.WifiPointServiceImpl
import com.arkondata.wificdmx.util.DataSeeder
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

// ============================================================
// Application entrypoint
//
// Wiring order:
//   1. Actor system + execution context
//   2. Config
//   3. DB + repo + service
//   4. REST routes + GraphQL route
//   5. Async seeder (API available immediately while seeding)
//   6. HTTP server bind
//   7. Graceful shutdown hook
// ============================================================

object Main extends App with LazyLogging {

  implicit val system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "wifi-cdmx")
  implicit val ec: ExecutionContext = system.executionContext

  val config    = ConfigFactory.load()
  val host      = config.getString("wifi-cdmx.http.host")
  val port      = config.getInt("wifi-cdmx.http.port")
  val xlsxUrl   = config.getString("wifi-cdmx.data.xlsx-url")
  val localPath = config.getString("wifi-cdmx.data.local-path")

  // ── Infrastructure ────────────────────────────────────────
  val dbConfig     = new DatabaseConfig(config)
  val repo         = new SlickWifiPointRepository(dbConfig)
  val service      = new WifiPointServiceImpl(repo)
  val restRoutes   = new WifiPointRoutes(service)
  val graphqlRoute = new GraphQLRoute(service)
  val seeder       = new DataSeeder(repo, xlsxUrl, localPath)

  // ── Seeder (background — server available immediately) ────
  seeder.seed().onComplete {
    case Success(Right(n)) if n > 0 => logger.info(s"Seeder: $n records inserted")
    case Success(Right(_))          => logger.info("Seeder: data already present, skipped")
    case Success(Left(err))         => logger.warn(s"Seeder warning (non-fatal): $err")
    case Failure(ex)                => logger.error("Seeder failed unexpectedly", ex)
  }

  // ── HTTP server (REST + GraphQL) ──────────────────────────
  val allRoutes = restRoutes.routes ~ graphqlRoute.route

  val bindingFuture: Future[ServerBinding] =
    Http().newServerAt(host, port).bind(allRoutes)

  bindingFuture.onComplete {
    case Success(binding) =>
      val addr = binding.localAddress
      logger.info(s"WiFi CDMX API running at http://${addr.getHostString}:${addr.getPort}/")
      logger.info(s"  REST:    http://${addr.getHostString}:${addr.getPort}/api/v1/wifi")
      logger.info(s"  GraphQL: http://${addr.getHostString}:${addr.getPort}/api/v1/graphql")
      logger.info(s"  Swagger: http://${addr.getHostString}:${addr.getPort}/swagger")
    case Failure(ex) =>
      logger.error(s"Failed to bind on $host:$port", ex)
      system.terminate()
  }

  // ── Graceful shutdown ─────────────────────────────────────
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
