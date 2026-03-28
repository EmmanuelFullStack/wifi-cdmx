package com.arkondata.wificdmx.service

import com.arkondata.wificdmx.domain.{AppError, PagedResult, Pagination, WifiPoint}
import com.arkondata.wificdmx.repository.WifiPointRepository
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

trait WifiPointService {
  def getById(id: Long): Future[Either[AppError, WifiPoint]]
  def getAll(page: Int, pageSize: Int): Future[Either[AppError, PagedResult[WifiPoint]]]
  def getByAlcaldia(alcaldia: String, page: Int, pageSize: Int): Future[Either[AppError, PagedResult[WifiPoint]]]
  def getNearby(lat: Double, lon: Double, page: Int, pageSize: Int): Future[Either[AppError, PagedResult[WifiPoint]]]
}

final class WifiPointServiceImpl(repo: WifiPointRepository)(implicit ec: ExecutionContext)
    extends WifiPointService with LazyLogging {

  private def validatePagination(page: Int, pageSize: Int): Either[AppError, Pagination] =
    if (page < 1)
      Left(AppError.ValidationError("'page' must be >= 1"))
    else if (pageSize < 1 || pageSize > 200)
      Left(AppError.ValidationError("'pageSize' must be 1-200"))
    else
      Right(Pagination(page, pageSize))

  private def validateId(id: Long): Either[AppError, Long] =
    if (id <= 0) Left(AppError.ValidationError(s"'id' must be positive, got: $id"))
    else         Right(id)

  override def getById(id: Long): Future[Either[AppError, WifiPoint]] = {
    logger.debug(s"getById id=$id")
    validateId(id) match {
      case Left(err)    => Future.successful(Left(err))
      case Right(validId) => repo.findById(validId)
    }
  }

  override def getAll(page: Int, pageSize: Int): Future[Either[AppError, PagedResult[WifiPoint]]] = {
    logger.debug(s"getAll page=$page pageSize=$pageSize")
    validatePagination(page, pageSize) match {
      case Left(err)  => Future.successful(Left(err))
      case Right(pgn) => repo.findAll(pgn)
    }
  }

  override def getByAlcaldia(
      alcaldia: String, page: Int, pageSize: Int
  ): Future[Either[AppError, PagedResult[WifiPoint]]] = {
    logger.debug(s"getByAlcaldia alcaldia=$alcaldia page=$page pageSize=$pageSize")
    val trimmed = alcaldia.trim
    if (trimmed.isEmpty)
      Future.successful(Left(AppError.ValidationError("'alcaldia' must not be blank")))
    else
      validatePagination(page, pageSize) match {
        case Left(err)  => Future.successful(Left(err))
        case Right(pgn) => repo.findByAlcaldia(trimmed, pgn)
      }
  }

  override def getNearby(
      lat: Double, lon: Double, page: Int, pageSize: Int
  ): Future[Either[AppError, PagedResult[WifiPoint]]] = {
    logger.debug(s"getNearby lat=$lat lon=$lon page=$page pageSize=$pageSize")
    validatePagination(page, pageSize) match {
      case Left(err)  => Future.successful(Left(err))
      case Right(pgn) => repo.findNearby(lat, lon, pgn)
    }
  }
}