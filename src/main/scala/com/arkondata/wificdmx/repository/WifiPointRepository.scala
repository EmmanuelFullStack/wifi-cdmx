package com.arkondata.wificdmx.repository

import com.arkondata.wificdmx.domain.{AppError, PagedResult, Pagination, WifiPoint}

import scala.concurrent.Future

trait WifiPointRepository {
  def findById(id: Long): Future[Either[AppError, WifiPoint]]
  def findAll(pagination: Pagination): Future[Either[AppError, PagedResult[WifiPoint]]]
  def findByAlcaldia(alcaldia: String, pagination: Pagination): Future[Either[AppError, PagedResult[WifiPoint]]]
  def findNearby(lat: Double, lon: Double, pagination: Pagination): Future[Either[AppError, PagedResult[WifiPoint]]]
  def insertAll(points: Seq[WifiPoint]): Future[Either[AppError, Int]]
}