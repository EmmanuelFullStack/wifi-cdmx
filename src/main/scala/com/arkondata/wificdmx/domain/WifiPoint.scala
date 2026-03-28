package com.arkondata.wificdmx.domain

final case class WifiPoint(
    id:       Long,
    colonia:  String,
    alcaldia: String,
    calle:    String,
    programa: String
)