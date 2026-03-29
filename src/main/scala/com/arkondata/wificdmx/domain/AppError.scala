package com.arkondata.wificdmx.domain

/** Centralized Error Definitions for the application.
  *
  * Uses a sealed trait 'AppError' to provide a exhaustive set of error types,
  * which allows for powerful pattern matching in services and API layers.
  */
sealed trait AppError extends Product with Serializable

object AppError {
  final case class NotFound(message: String)                                extends AppError
  final case class ValidationError(message: String)                         extends AppError
  final case class DatabaseError(message: String, cause: Option[Throwable]) extends AppError
  final case class ImportError(message: String)                             extends AppError
}
