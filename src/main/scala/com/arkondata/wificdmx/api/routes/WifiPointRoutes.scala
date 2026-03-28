package com.arkondata.wificdmx.api.routes

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.arkondata.wificdmx.service.WifiPointService
import com.typesafe.scalalogging.LazyLogging

final class WifiPointRoutes(service: WifiPointService) extends LazyLogging {

  def routes: Route =
    healthRoute

  private def healthRoute: Route =
    path("health") {
      get {
        complete(HttpEntity(
          ContentTypes.`application/json`,
          """{"status":"UP"}"""
        ))
      }
    }
}