package com.arkondata.wificdmx.repository

import com.arkondata.wificdmx.domain.{Coordinates, WifiPoint}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import java.time.LocalDate

private[repository] final case class WifiPointRow(
    id: Long,
    colonia: String,
    alcaldia: String,
    calle: String,
    programa: String,
    fechaInstalacion: Option[LocalDate],
    lat: Double,
    lon: Double
)

private[repository] object WifiPointRow {
  def toDomain(r: WifiPointRow): WifiPoint =
    WifiPoint(
      r.id,
      r.colonia,
      r.alcaldia,
      r.calle,
      r.programa,
      r.fechaInstalacion,
      Coordinates(r.lat, r.lon)
    )

  def fromDomain(p: WifiPoint): WifiPointRow =
    WifiPointRow(
      p.id,
      p.colonia,
      p.alcaldia,
      p.calle,
      p.programa,
      p.fechaInstalacion,
      p.coordinates.lat,
      p.coordinates.lon
    )
}

class WifiPointTable(tag: Tag) extends Table[WifiPointRow](tag, "wifi_points") {
  def id               = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def colonia          = column[String]("colonia")
  def alcaldia         = column[String]("alcaldia")
  def calle            = column[String]("calle")
  def programa         = column[String]("programa")
  def fechaInstalacion = column[Option[LocalDate]]("fecha_instalacion")
  def lat              = column[Double]("lat")
  def lon              = column[Double]("lon")

  override def * : ProvenShape[WifiPointRow] =
    (id, colonia, alcaldia, calle, programa, fechaInstalacion, lat, lon).mapTo[WifiPointRow]
}
