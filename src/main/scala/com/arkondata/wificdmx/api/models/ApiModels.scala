package com.arkondata.wificdmx.api.models

import com.arkondata.wificdmx.domain.{PagedResult, WifiPoint}
import spray.json._

import java.time.format.DateTimeFormatter

/** API Data Transfer Objects (DTOs) and JSON Protocols.
  *
  * This file defines the external-facing models for the API.
  * - Included: DTOs for WiFi points, coordinates, paginated responses, and errors.
  * - JsonProtocol: Handles the serialization/deserialization logic using Spray JSON.
  */

final case class CoordinatesDto(lat: Double, lon: Double)

final case class WifiPointDto(
    id: Long,
    colonia: String,
    alcaldia: String,
    calle: String,
    programa: String,
    fechaInstalacion: Option[String],
    coordinates: CoordinatesDto
)

final case class PagedResponse[A](
    data: Seq[A],
    total: Long,
    page: Int,
    pageSize: Int,
    totalPages: Long
)

final case class ErrorResponse(error: String, message: String)

object WifiPointDto {
  private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

  def fromDomain(wp: WifiPoint): WifiPointDto =
    WifiPointDto(
      id = wp.id,
      colonia = wp.colonia,
      alcaldia = wp.alcaldia,
      calle = wp.calle,
      programa = wp.programa,
      fechaInstalacion = wp.fechaInstalacion.map(_.format(fmt)),
      coordinates = CoordinatesDto(wp.coordinates.lat, wp.coordinates.lon)
    )
}

object PagedResponse {
  def fromDomain[A, B](p: PagedResult[A], f: A => B): PagedResponse[B] =
    PagedResponse(p.data.map(f), p.total, p.page, p.pageSize, p.totalPages)
}

object JsonProtocol extends DefaultJsonProtocol {
  implicit val coordinatesFormat: RootJsonFormat[CoordinatesDto] = jsonFormat2(CoordinatesDto)
  implicit val wifiPointFormat: RootJsonFormat[WifiPointDto]     = jsonFormat7(WifiPointDto.apply)
  implicit val errorFormat: RootJsonFormat[ErrorResponse]        = jsonFormat2(ErrorResponse)
  implicit def pagedFormat[A: JsonFormat]: RootJsonFormat[PagedResponse[A]] =
    jsonFormat5(PagedResponse.apply[A])
}
