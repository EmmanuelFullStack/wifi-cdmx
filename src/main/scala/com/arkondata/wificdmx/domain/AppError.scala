package com.arkondata.wificdmx.domain

sealed trait AppError extends Product with Serializable

object AppError {
  final case class NotFound(message: String)                                extends AppError
  final case class ValidationError(message: String)                         extends AppError
  final case class DatabaseError(message: String, cause: Option[Throwable]) extends AppError
  final case class ImportError(message: String)                             extends AppError
}
