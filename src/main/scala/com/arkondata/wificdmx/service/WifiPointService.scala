package com.arkondata.wificdmx.service

import cats.data.EitherT
import cats.implicits._
import com.arkondata.wificdmx.domain.{AppError, PagedResult, Pagination, WifiPoint}
import com.arkondata.wificdmx.repository.WifiPointRepository
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

trait WifiPointService {
  def getById(id: Long): Future[Either[AppError, WifiPoint]]
  def getAll(page: Int, pageSize: Int): Future[Either[AppError, PagedResult[WifiPoint]]]
  def getByAlcaldia(
      alcaldia: String,
      page: Int,
      pageSize: Int
  ): Future[Either[AppError, PagedResult[WifiPoint]]]
  def getNearby(
      lat: Double,
      lon: Double,
      page: Int,
      pageSize: Int
  ): Future[Either[AppError, PagedResult[WifiPoint]]]
}

final class WifiPointServiceImpl(repo: WifiPointRepository)(implicit ec: ExecutionContext)
    extends WifiPointService
    with LazyLogging {

  private def validatePagination(page: Int, pageSize: Int): Either[AppError, Pagination] =
    if (page < 1) Left(AppError.ValidationError("'page' must be >= 1"))
    else if (pageSize < 1 || pageSize > 200)
      Left(AppError.ValidationError("'pageSize' must be 1-200"))
    else Right(Pagination(page, pageSize))

  private def validateCoordinates(lat: Double, lon: Double): Either[AppError, (Double, Double)] =
    if (lat < -90 || lat > 90) Left(AppError.ValidationError(s"Invalid latitude: $lat"))
    else if (lon < -180 || lon > 180) Left(AppError.ValidationError(s"Invalid longitude: $lon"))
    else Right((lat, lon))

  private def validateId(id: Long): Either[AppError, Long] =
    if (id <= 0) Left(AppError.ValidationError(s"'id' must be positive, got: $id"))
    else Right(id)

  override def getById(id: Long): Future[Either[AppError, WifiPoint]] = {
    logger.debug(s"getById id=$id")
    val result = for {
      validId <- EitherT.fromEither[Future](validateId(id))
      point   <- EitherT(repo.findById(validId))
    } yield point
    result.value
  }

  override def getAll(
      page: Int,
      pageSize: Int
  ): Future[Either[AppError, PagedResult[WifiPoint]]] = {
    logger.debug(s"getAll page=$page pageSize=$pageSize")
    val result = for {
      pgn  <- EitherT.fromEither[Future](validatePagination(page, pageSize))
      data <- EitherT(repo.findAll(pgn))
    } yield data
    result.value
  }

  override def getByAlcaldia(
      alcaldia: String,
      page: Int,
      pageSize: Int
  ): Future[Either[AppError, PagedResult[WifiPoint]]] = {
    logger.debug(s"getByAlcaldia alcaldia=$alcaldia page=$page pageSize=$pageSize")
    val trimmed = alcaldia.trim
    val result = for {
      _ <- EitherT.fromEither[Future](
        if (trimmed.isEmpty) Left(AppError.ValidationError("'alcaldia' must not be blank"))
        else Right(())
      )
      pgn  <- EitherT.fromEither[Future](validatePagination(page, pageSize))
      data <- EitherT(repo.findByAlcaldia(trimmed, pgn))
    } yield data
    result.value
  }

  override def getNearby(
      lat: Double,
      lon: Double,
      page: Int,
      pageSize: Int
  ): Future[Either[AppError, PagedResult[WifiPoint]]] = {
    logger.debug(s"getNearby lat=$lat lon=$lon page=$page pageSize=$pageSize")
    val result = for {
      coords <- EitherT.fromEither[Future](validateCoordinates(lat, lon))
      pgn    <- EitherT.fromEither[Future](validatePagination(page, pageSize))
      (lt, ln) = coords
      data <- EitherT(repo.findNearby(lt, ln, pgn))
    } yield data
    result.value
  }
}
