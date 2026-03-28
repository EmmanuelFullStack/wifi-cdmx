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

  override def findNearby(
      lat: Double,
      lon: Double,
      p: Pagination
  ): Future[Either[AppError, PagedResult[WifiPoint]]] =
    run(countAndPage(points, p))

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