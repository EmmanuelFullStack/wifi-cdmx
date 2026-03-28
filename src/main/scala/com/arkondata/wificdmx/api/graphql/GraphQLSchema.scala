package com.arkondata.wificdmx.api.graphql

import com.arkondata.wificdmx.domain.{Coordinates, PagedResult, WifiPoint}
import com.arkondata.wificdmx.service.WifiPointService
import sangria.schema._

import scala.concurrent.ExecutionContext

object GraphQLSchema {

  val CoordinatesType: ObjectType[Unit, Coordinates] =
    ObjectType(
      "Coordinates",
      "Geographic coordinate pair",
      fields[Unit, Coordinates](
        Field("lat", FloatType, Some("Latitude"), resolve = _.value.lat),
        Field("lon", FloatType, Some("Longitude"), resolve = _.value.lon)
      )
    )

  val WifiPointType: ObjectType[Unit, WifiPoint] =
    ObjectType(
      "WifiPoint",
      "A public WiFi access point in Mexico City",
      fields[Unit, WifiPoint](
        Field("id", LongType, Some("Primary key"), resolve = _.value.id),
        Field("colonia", StringType, Some("Neighborhood"), resolve = _.value.colonia),
        Field("alcaldia", StringType, Some("Borough"), resolve = _.value.alcaldia),
        Field("calle", StringType, Some("Street"), resolve = _.value.calle),
        Field("programa", StringType, Some("Provider"), resolve = _.value.programa),
        Field(
          "fechaInstalacion",
          OptionType(StringType),
          Some("Installation date"),
          resolve = _.value.fechaInstalacion.map(_.toString)
        ),
        Field(
          "coordinates",
          CoordinatesType,
          Some("Geo coordinates"),
          resolve = _.value.coordinates
        )
      )
    )

  val WifiPageType: ObjectType[Unit, PagedResult[WifiPoint]] =
    ObjectType(
      "WifiPointsPage",
      "Paginated list of WiFi points",
      fields[Unit, PagedResult[WifiPoint]](
        Field("data", ListType(WifiPointType), Some("Items in this page"), resolve = _.value.data),
        Field("total", LongType, Some("Total matching records"), resolve = _.value.total),
        Field("page", IntType, Some("Current page number"), resolve = _.value.page),
        Field("pageSize", IntType, Some("Items per page"), resolve = _.value.pageSize),
        Field("totalPages", LongType, Some("Total number of pages"), resolve = _.value.totalPages)
      )
    )

  val PageArg     = Argument("page", IntType, defaultValue = 1)
  val PageSizeArg = Argument("pageSize", IntType, defaultValue = 20)
  val IdArg       = Argument("id", LongType)
  val AlcaldiaArg = Argument("alcaldia", OptionInputType(StringType))
  val LatArg      = Argument("lat", FloatType)
  val LonArg      = Argument("lon", FloatType)

  def build(service: WifiPointService)(implicit ec: ExecutionContext): Schema[Unit, Unit] = {

    val QueryType = ObjectType(
      "Query",
      fields[Unit, Unit](
        Field(
          "wifiPoints",
          WifiPageType,
          arguments = List(PageArg, PageSizeArg, AlcaldiaArg),
          description = Some("Paginated list of WiFi points, optionally filtered by alcaldía"),
          resolve = ctx =>
            ctx.arg(AlcaldiaArg) match {
              case Some(a) =>
                service
                  .getByAlcaldia(a, ctx.arg(PageArg), ctx.arg(PageSizeArg))
                  .map(_.fold(e => throw new Exception(e.toString), identity))
              case None =>
                service
                  .getAll(ctx.arg(PageArg), ctx.arg(PageSizeArg))
                  .map(_.fold(e => throw new Exception(e.toString), identity))
            }
        ),
        Field(
          "wifiPoint",
          OptionType(WifiPointType),
          arguments = List(IdArg),
          description = Some("Find a single WiFi point by its ID"),
          resolve = ctx =>
            service
              .getById(ctx.arg(IdArg))
              .map(_.fold(_ => None, Some(_)))
        ),
        Field(
          "nearbyPoints",
          WifiPageType,
          arguments = List(LatArg, LonArg, PageArg, PageSizeArg),
          description = Some("WiFi points sorted by proximity to the given coordinates"),
          resolve = ctx =>
            service
              .getNearby(ctx.arg(LatArg), ctx.arg(LonArg), ctx.arg(PageArg), ctx.arg(PageSizeArg))
              .map(_.fold(e => throw new Exception(e.toString), identity))
        )
      )
    )

    Schema(QueryType)
  }
}
