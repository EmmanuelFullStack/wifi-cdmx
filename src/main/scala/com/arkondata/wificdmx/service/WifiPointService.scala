package com.arkondata.wificdmx.service

import com.arkondata.wificdmx.domain.{AppError, PagedResult, WifiPoint}

import scala.concurrent.Future

trait WifiPointService {
  def getById(id: Long): Future[Either[AppError, WifiPoint]]
  def getAll(page: Int, pageSize: Int): Future[Either[AppError, PagedResult[WifiPoint]]]
  def getByAlcaldia(alcaldia: String, page: Int, pageSize: Int): Future[Either[AppError, PagedResult[WifiPoint]]]
  def getNearby(lat: Double, lon: Double, page: Int, pageSize: Int): Future[Either[AppError, PagedResult[WifiPoint]]]
}