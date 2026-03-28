package com.arkondata.wificdmx.domain

import java.time.LocalDate

final case class Coordinates(lat: Double, lon: Double)

final case class WifiPoint(
    id:               Long,
    colonia:          String,
    alcaldia:         String,
    calle:            String,
    programa:         String,
    fechaInstalacion: Option[LocalDate],
    coordinates:      Coordinates
)