package com.arkondata.wificdmx.util

import com.arkondata.wificdmx.domain.{AppError, Coordinates, Pagination, WifiPoint}
import com.arkondata.wificdmx.repository.WifiPointRepository
import com.typesafe.scalalogging.LazyLogging
import org.apache.poi.ss.usermodel.{Cell, CellType, WorkbookFactory}

import java.io.{File, FileOutputStream}
import java.net.{HttpURLConnection, URI}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.jdk.CollectionConverters._
import scala.util.{Try, Using}

/** Data Seeder Utility for WiFi CDMX points.
  *
  * This utility automates the initial data ingestion from the official CDMX data portal.
  * The ETL pipeline consists of:
  * 1. Check: Skips seeding if the database already contains records.
  * 2. Download: Fetches the XLSX file from a remote URL if not cached locally.
  * 3. Parse: Reads the Excel file, mapping dynamic column headers to the domain model.
  * 4. Batched Insert: Inserts validated data records into the database in batches.
  */

final class DataSeeder(
    repo: WifiPointRepository,
    xlsxUrl: String,
    localPath: String
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  private val dateFormatters = List(
    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
    DateTimeFormatter.ofPattern("M/d/yyyy"),
    DateTimeFormatter.ofPattern("dd-MM-yyyy")
  )

  private def parseDate(raw: String): Option[LocalDate] =
    Option(raw).map(_.trim).filter(_.nonEmpty).flatMap { s =>
      dateFormatters.foldLeft(Option.empty[LocalDate]) {
        case (found @ Some(_), _) => found
        case (None, fmt)          => Try(LocalDate.parse(s, fmt)).toOption
      }
    }

  private def cellString(cell: Cell): String =
    if (cell == null) ""
    else
      cell.getCellType match {
        case CellType.STRING => cell.getStringCellValue.trim
        case CellType.NUMERIC =>
          if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell))
            cell.getLocalDateTimeCellValue.toLocalDate.toString
          else {
            val d = cell.getNumericCellValue
            if (d == d.toLong) d.toLong.toString else d.toString
          }
        case CellType.BOOLEAN => cell.getBooleanCellValue.toString
        case CellType.FORMULA =>
          Try(cell.getStringCellValue)
            .orElse(Try(cell.getNumericCellValue.toString))
            .getOrElse("")
        case _ => ""
      }

  private def cellDouble(cell: Cell): Option[Double] =
    if (cell == null) None
    else
      cell.getCellType match {
        case CellType.NUMERIC => Some(cell.getNumericCellValue)
        case CellType.STRING  => Try(cell.getStringCellValue.trim.toDouble).toOption
        case _                => None
      }

  private def parseXlsx(file: File): Either[AppError, Seq[WifiPoint]] = {
    logger.info(s"Parsing XLSX: ${file.getName}")
    Try {
      Using(WorkbookFactory.create(file, null, true)) { wb =>
        val sheet   = wb.getSheetAt(0)
        val allRows = sheet.iterator().asScala.toSeq
        if (allRows.isEmpty) throw new RuntimeException("XLSX sheet is empty")

        val headers: Map[String, Int] = allRows.head
          .cellIterator()
          .asScala
          .map(c => cellString(c).toLowerCase.trim -> c.getColumnIndex)
          .toMap

        logger.info(s"Columns detected: ${headers.keys.mkString(", ")}")

        def col(aliases: String*): Option[Int] =
          aliases.flatMap(headers.get).headOption

        val latIdx  = col("latitud", "lat", "latitude")
        val lonIdx  = col("longitud", "lon", "longitude")
        val colIdx  = col("colonia", "col")
        val alcIdx  = col("alcaldia", "alcaldía", "delegacion")
        val calIdx  = col("calle", "direccion", "address")
        val progIdx = col("programa", "program", "proveedor")
        val fechIdx = col("fecha_instalacion", "fecha instalacion", "fecha")

        if (latIdx.isEmpty || lonIdx.isEmpty)
          throw new RuntimeException(
            s"Lat/lon columns not found. Available: ${headers.keys.mkString(", ")}"
          )

        val (skipped, points) = allRows.tail.zipWithIndex.partitionMap { case (row, idx) =>
          val str: Int => String = i => cellString(row.getCell(i))
          (cellDouble(row.getCell(latIdx.get)), cellDouble(row.getCell(lonIdx.get))) match {
            case (Some(lat), Some(lon)) if lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 =>
              Right(
                WifiPoint(
                  0L,
                  colIdx.map(str).getOrElse(""),
                  alcIdx.map(str).getOrElse(""),
                  calIdx.map(str).getOrElse(""),
                  progIdx.map(str).getOrElse(""),
                  fechIdx.map(str).flatMap(parseDate),
                  Coordinates(lat, lon)
                )
              )
            case _ =>
              Left(s"row ${idx + 2}: invalid coordinates")
          }
        }

        if (skipped.nonEmpty)
          logger.warn(s"Skipped ${skipped.size} rows with bad coordinates")
        logger.info(s"Valid rows to insert: ${points.size}")
        points
      }.get
    }.toEither.left.map(e => AppError.ImportError(s"XLSX parse error: ${e.getMessage}"))
  }

  private def downloadFile(): Either[AppError, File] = {
    logger.info(s"Downloading: $xlsxUrl")
    Try {
      val dest = new File(localPath)
      dest.getParentFile.mkdirs()

      var currentUrl              = xlsxUrl
      var redirects               = 0
      var conn: HttpURLConnection = null

      while (redirects < 6) {
        conn = URI.create(currentUrl).toURL.openConnection().asInstanceOf[HttpURLConnection]
        conn.setInstanceFollowRedirects(false)
        conn.setConnectTimeout(30_000)
        conn.setReadTimeout(120_000)
        conn.setRequestProperty("User-Agent", "wifi-cdmx-seeder/1.0")
        conn.getResponseCode match {
          case HttpURLConnection.HTTP_OK => redirects = Int.MaxValue
          case code @ (301 | 302 | 303 | 307 | 308) =>
            val loc = conn.getHeaderField("Location")
            conn.disconnect()
            logger.debug(s"HTTP $code → $loc")
            currentUrl = loc; redirects += 1
          case code =>
            conn.disconnect()
            throw new RuntimeException(s"HTTP $code from $currentUrl")
        }
      }

      Using(conn.getInputStream) { in =>
        Using(new FileOutputStream(dest)) { out =>
          val buf = new Array[Byte](8192)
          var n   = 0; var total = 0L
          while ({ n = in.read(buf); n != -1 }) {
            out.write(buf, 0, n); total += n
          }
          logger.info(s"Saved ${total / 1024} KB → $localPath")
        }.get
      }.get
      conn.disconnect(); dest
    }.toEither.left.map(e => AppError.ImportError(s"Download failed: ${e.getMessage}"))
  }

  private def bulkInsert(points: Seq[WifiPoint]): Future[Either[AppError, Int]] = {
    val batches = points.grouped(500).toSeq
    logger.info(s"Inserting ${points.size} rows in ${batches.size} batches")
    batches.foldLeft(Future.successful(Right(0): Either[AppError, Int])) { case (accF, batch) =>
      accF.flatMap {
        case Left(err) => Future.successful(Left(err))
        case Right(n)  => repo.insertAll(batch).map(_.map(_ + n))
      }
    }
  }

  /** Checks if seeding is required and starts the ETL process if so.
    *
    * @return Future indicating success or a specific AppError.
    */
  def seed(): Future[Either[AppError, Int]] = {
    logger.info("DataSeeder: checking if seed is needed...")
    repo.findAll(Pagination(1, 1)).flatMap {
      case Right(p) if p.total > 0 =>
        logger.info(s"DB already has ${p.total} records. Seeder skipped.")
        Future.successful(Right(0))
      case _ =>
        Future(blocking {
          val f = new File(localPath)
          if (f.exists() && f.length() > 0) {
            logger.info(s"Cached file found at $localPath — skipping download")
            Right(f)
          } else downloadFile()
        }).flatMap {
          case Left(err) =>
            logger.error(s"Seed aborted: $err")
            Future.successful(Left(err))
          case Right(file) =>
            parseXlsx(file) match {
              case Left(err) =>
                logger.error(s"Seed aborted: $err")
                Future.successful(Left(err))
              case Right(points) =>
                bulkInsert(points).map { r =>
                  r.foreach(n => logger.info(s"Seed finished: $n records in DB"))
                  r
                }
            }
        }
    }
  }
}
