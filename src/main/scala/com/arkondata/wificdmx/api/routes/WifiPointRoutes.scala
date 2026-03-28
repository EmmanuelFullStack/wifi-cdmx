package com.arkondata.wificdmx.api.routes

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MissingQueryParamRejection, RejectionHandler, Route}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.arkondata.wificdmx.api.models.JsonProtocol._
import com.arkondata.wificdmx.api.models.{ErrorResponse, PagedResponse, WifiPointDto}
import com.arkondata.wificdmx.domain.AppError
import com.arkondata.wificdmx.service.WifiPointService
import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success}

final class WifiPointRoutes(service: WifiPointService) extends LazyLogging {

  private def errorToResponse(err: AppError): Route = err match {
    case AppError.NotFound(msg)        => complete(StatusCodes.NotFound            -> ErrorResponse("NOT_FOUND",        msg))
    case AppError.ValidationError(msg) => complete(StatusCodes.BadRequest          -> ErrorResponse("VALIDATION_ERROR", msg))
    case AppError.DatabaseError(msg,_) => complete(StatusCodes.InternalServerError -> ErrorResponse("DATABASE_ERROR",   msg))
    case AppError.ImportError(msg)     => complete(StatusCodes.InternalServerError -> ErrorResponse("IMPORT_ERROR",     msg))
  }

  private def serverError(msg: String): Route =
    complete(StatusCodes.InternalServerError -> ErrorResponse("INTERNAL_ERROR", msg))

  private val missingParamHandler: RejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case MissingQueryParamRejection(p) =>
        complete(StatusCodes.BadRequest ->
          ErrorResponse("MISSING_PARAM", s"Required parameter '$p' is missing"))
      }
      .result()

  private val paginationParams =
    parameters("page".as[Int].withDefault(1), "pageSize".as[Int].withDefault(20))

  def routes: Route =
    healthRoute ~
    pathPrefix("api" / "v1") { wifiRoutes }

  private def healthRoute: Route =
    path("health") {
      get {
        onComplete(service.getAll(1, 1)) {
          case Success(Right(_)) =>
            complete(HttpEntity(ContentTypes.`application/json`,
              """{"status":"UP","db":"UP"}"""))
          case _ =>
            complete(StatusCodes.ServiceUnavailable ->
              HttpEntity(ContentTypes.`application/json`,
                """{"status":"UP","db":"DOWN"}"""))
        }
      }
    }

  private def wifiRoutes: Route =
    handleRejections(missingParamHandler) {
      pathPrefix("wifi") {
        concat(

          path("nearby") {
            get {
              parameters("lat".as[Double], "lon".as[Double]) { (lat, lon) =>
                paginationParams { (page, pageSize) =>
                  onComplete(service.getNearby(lat, lon, page, pageSize)) {
                    case Success(Right(p))  => complete(PagedResponse.fromDomain(p, WifiPointDto.fromDomain))
                    case Success(Left(err)) => errorToResponse(err)
                    case Failure(ex)        =>
                      logger.error("getNearby failed", ex)
                      serverError("Unexpected error")
                  }
                }
              }
            }
          },

          path(LongNumber) { id =>
            get {
              onComplete(service.getById(id)) {
                case Success(Right(pt)) => complete(WifiPointDto.fromDomain(pt))
                case Success(Left(err)) => errorToResponse(err)
                case Failure(ex)        =>
                  logger.error(s"getById id=$id failed", ex)
                  serverError("Unexpected error")
              }
            }
          },
          pathEnd {
            get {
              parameter("alcaldia".?) { alcaldiaOpt =>
                paginationParams { (page, pageSize) =>
                  val result = alcaldiaOpt match {
                    case Some(a) => service.getByAlcaldia(a, page, pageSize)
                    case None    => service.getAll(page, pageSize)
                  }
                  onComplete(result) {
                    case Success(Right(p))  => complete(PagedResponse.fromDomain(p, WifiPointDto.fromDomain))
                    case Success(Left(err)) => errorToResponse(err)
                    case Failure(ex)        =>
                      logger.error("getAll failed", ex)
                      serverError("Unexpected error")
                  }
                }
              }
            }
          }
        )
      }
    }
}