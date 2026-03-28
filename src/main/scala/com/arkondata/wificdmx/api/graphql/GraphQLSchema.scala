package com.arkondata.wificdmx.api.graphql

import com.arkondata.wificdmx.domain.{Coordinates, WifiPoint}
import sangria.schema._

object GraphQLSchema {

  val CoordinatesType: ObjectType[Unit, Coordinates] =
    ObjectType("Coordinates", "Geographic coordinate pair",
      fields[Unit, Coordinates](
        Field("lat", FloatType, Some("Latitude"),  resolve = _.value.lat),
        Field("lon", FloatType, Some("Longitude"), resolve = _.value.lon)
      ))

  val WifiPointType: ObjectType[Unit, WifiPoint] =
    ObjectType("WifiPoint", "A public WiFi access point in Mexico City",
      fields[Unit, WifiPoint](
        Field("id",       LongType,   Some("Primary key"), resolve = _.value.id),
        Field("colonia",  StringType, Some("Neighborhood"), resolve = _.value.colonia),
        Field("alcaldia", StringType, Some("Borough"),      resolve = _.value.alcaldia),
        Field("calle",    StringType, Some("Street"),       resolve = _.value.calle),
        Field("programa", StringType, Some("Provider"),     resolve = _.value.programa),
        Field("fechaInstalacion", OptionType(StringType), Some("Installation date"),
          resolve = _.value.fechaInstalacion.map(_.toString)),
        Field("coordinates", CoordinatesType, Some("Geo coordinates"),
          resolve = _.value.coordinates)
      ))
}