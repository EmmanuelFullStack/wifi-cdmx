package com.arkondata.wificdmx.domain

import java.time.LocalDate

final case class Coordinates(lat: Double, lon: Double)

final case class WifiPoint(
    id: Long,
    colonia: String,
    alcaldia: String,
    calle: String,
    programa: String,
    fechaInstalacion: Option[LocalDate],
    coordinates: Coordinates
)

final case class Pagination(page: Int, pageSize: Int) {
  require(page >= 1, "page must be >= 1")
  require(pageSize >= 1 && pageSize <= 200, "pageSize must be 1-200")
  def offset: Int = (page - 1) * pageSize
  def limit: Int  = pageSize
}

object Pagination {
  val Default: Pagination = Pagination(1, 20)
}

final case class PagedResult[A](
    data: Seq[A],
    total: Long,
    page: Int,
    pageSize: Int
) {
  def totalPages: Long = math.ceil(total.toDouble / pageSize).toLong
}
