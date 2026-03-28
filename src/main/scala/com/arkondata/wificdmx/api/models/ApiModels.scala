package com.arkondata.wificdmx.api.models

final case class CoordinatesDto(lat: Double, lon: Double)

final case class WifiPointDto(
    id:               Long,
    colonia:          String,
    alcaldia:         String,
    calle:            String,
    programa:         String,
    fechaInstalacion: Option[String],
    coordinates:      CoordinatesDto
)

final case class ErrorResponse(error: String, message: String)