package com.arkondata.wificdmx.repository

import com.arkondata.wificdmx.domain.{AppError, PagedResult, Pagination, WifiPoint}
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait WifiPointRepository {
  def findById(id: Long): Future[Either[AppError, WifiPoint]]
  def findAll(pagination: Pagination): Future[Either[AppError, PagedResult[WifiPoint]]]
  def findByAlcaldia(alcaldia: String, pagination: Pagination): Future[Either[AppError, PagedResult[WifiPoint]]]
  def findNearby(lat: Double, lon: Double, pagination: Pagination): Future[Either[AppError, PagedResult[WifiPoint]]]
  def insertAll(points: Seq[WifiPoint]): Future[Either[AppError, Int]]
}

final class SlickWifiPointRepository(dbConfig: DatabaseConfig)(implicit ec: ExecutionContext)
    extends WifiPointRepository with LazyLogging {

  private val db     = dbConfig.db
  private val points = TableQuery[WifiPointTable]

  private def run[A](action: DBIO[A]): Future[Either[AppError, A]] =
    db.run(action)
      .map(Right(_))
      .recover { case NonFatal(e) =>
        logger.error("Database error", e)
        Left(AppError.DatabaseError(e.getMessage, Some(e)))
      }

  override def findById(id: Long): Future[Either[AppError, WifiPoint]] =
    run(points.filter(_.id === id).result.headOption).map {
      case Right(Some(row)) => Right(WifiPointRow.toDomain(row))
      case Right(None)      => Left(AppError.NotFound(s"WiFi point with id=$id not found"))
      case Left(err)        => Left(err)
    }

  override def findAll(p: Pagination): Future[Either[AppError, PagedResult[WifiPoint]]] =
    run(countAndPage(points, p))

  override def findByAlcaldia(
      alcaldia: String,
      p: Pagination
  ): Future[Either[AppError, PagedResult[WifiPoint]]] =
    run(countAndPage(points.filter(_.alcaldia.toLowerCase === alcaldia.toLowerCase), p))

  /**
   * Proximity search using the Haversine formula.
   *
   * Formula: distance = 2R * arcsin(sqrt(
   *   sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
   * ))
   */
  override def findNearby(
      lat: Double,
      lon: Double,
      p: Pagination
  ): Future[Either[AppError, PagedResult[WifiPoint]]] = {

    val R = 6371.0

    val action = SimpleDBIO { session =>
      val conn = session.connection

      val countStmt = conn.prepareStatement("SELECT COUNT(*) FROM wifi_points")
      val countRs   = countStmt.executeQuery()
      countRs.next()
      val total = countRs.getLong(1)
      countRs.close()
      countStmt.close()

      val rowsSql =
        s"""SELECT id, colonia, alcaldia, calle, programa, fecha_instalacion, lat, lon
           |FROM wifi_points
           |ORDER BY (
           |  2 * $R * asin(sqrt(
           |    power(sin(radians(lat - ?) / 2), 2) +
           |    cos(radians(?)) * cos(radians(lat)) *
           |    power(sin(radians(lon - ?) / 2), 2)
           |  ))
           |) ASC
           |LIMIT ? OFFSET ?""".stripMargin

      val stmt = conn.prepareStatement(rowsSql)
      stmt.setDouble(1, lat)
      stmt.setDouble(2, lat)
      stmt.setDouble(3, lon)
      stmt.setInt(4, p.limit)
      stmt.setInt(5, p.offset)

      val rs  = stmt.executeQuery()
      val buf = scala.collection.mutable.ArrayBuffer.empty[WifiPointRow]

      while (rs.next()) {
        buf += WifiPointRow(
          id               = rs.getLong("id"),
          colonia          = rs.getString("colonia"),
          alcaldia         = rs.getString("alcaldia"),
          calle            = rs.getString("calle"),
          programa         = rs.getString("programa"),
          fechaInstalacion = Option(rs.getDate("fecha_instalacion")).map(_.toLocalDate),
          lat              = rs.getDouble("lat"),
          lon              = rs.getDouble("lon")
        )
      }
      rs.close()
      stmt.close()

      PagedResult(buf.map(WifiPointRow.toDomain).toSeq, total, p.page, p.pageSize)
    }

    run(action)
  }

  override def insertAll(pts: Seq[WifiPoint]): Future[Either[AppError, Int]] =
    run((TableQuery[WifiPointTable] ++= pts.map(WifiPointRow.fromDomain)).map(_.getOrElse(0)))

  private def countAndPage(
      query: Query[WifiPointTable, WifiPointRow, Seq],
      p: Pagination
  ): DBIO[PagedResult[WifiPoint]] =
    for {
      total <- query.length.result
      rows  <- query.drop(p.offset).take(p.limit).result
    } yield PagedResult(rows.map(WifiPointRow.toDomain), total.toLong, p.page, p.pageSize)
}