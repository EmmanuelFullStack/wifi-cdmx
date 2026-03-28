package com.arkondata.wificdmx.repository

import com.arkondata.wificdmx.domain.{AppError, PagedResult, Pagination, WifiPoint}

import scala.collection.mutable
import scala.concurrent.Future

// ============================================================
// In-memory repository — used exclusively in unit tests.
// No database, no I/O — pure Scala collections.
// Runs in milliseconds and requires no infrastructure.
// ============================================================

final class InMemoryWifiPointRepository extends WifiPointRepository {

  private val store   = mutable.Map.empty[Long, WifiPoint]
  private var counter = 1L

  /** Pre-populate the store with test data. */
  def seed(points: Seq[WifiPoint]): Unit =
    points.foreach { p =>
      val id = counter; counter += 1
      store(id) = p.copy(id = id)
    }

  override def findById(id: Long): Future[Either[AppError, WifiPoint]] =
    Future.successful(
      store.get(id).toRight(AppError.NotFound(s"WiFi point with id=$id not found"))
    )

  override def findAll(p: Pagination): Future[Either[AppError, PagedResult[WifiPoint]]] =
    Future.successful(Right(page(store.values.toSeq.sortBy(_.id), p)))

  override def findByAlcaldia(
      alcaldia: String,
      p: Pagination
  ): Future[Either[AppError, PagedResult[WifiPoint]]] =
    Future.successful(Right(page(
      store.values.filter(_.alcaldia.equalsIgnoreCase(alcaldia)).toSeq.sortBy(_.id), p
    )))

  override def findNearby(
      lat: Double,
      lon: Double,
      p: Pagination
  ): Future[Either[AppError, PagedResult[WifiPoint]]] = {
    def dist(pt: WifiPoint): Double = {
      val dLat = math.toRadians(pt.coordinates.lat - lat)
      val dLon = math.toRadians(pt.coordinates.lon - lon)
      val a = math.sin(dLat / 2) * math.sin(dLat / 2) +
              math.cos(math.toRadians(lat)) * math.cos(math.toRadians(pt.coordinates.lat)) *
              math.sin(dLon / 2) * math.sin(dLon / 2)
      2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    }
    Future.successful(Right(page(store.values.toSeq.sortBy(dist), p)))
  }

  override def insertAll(points: Seq[WifiPoint]): Future[Either[AppError, Int]] = {
    seed(points)
    Future.successful(Right(points.size))
  }

  private def page(seq: Seq[WifiPoint], p: Pagination): PagedResult[WifiPoint] =
    PagedResult(seq.slice(p.offset, p.offset + p.limit), seq.size.toLong, p.page, p.pageSize)
}