package com.arkondata.wificdmx.util

import com.arkondata.wificdmx.domain.{AppError, Pagination}
import com.arkondata.wificdmx.repository.WifiPointRepository
import com.typesafe.scalalogging.LazyLogging

import java.io.{File, FileOutputStream}
import java.net.{HttpURLConnection, URI}
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Try


final class DataSeeder(
    repo:      WifiPointRepository,
    xlsxUrl:   String,
    localPath: String
)(implicit ec: ExecutionContext) extends LazyLogging {


  private def downloadFile(): Either[AppError, File] = {
    logger.info(s"Downloading: $xlsxUrl")
    Try {
      val dest = new File(localPath)
      dest.getParentFile.mkdirs()

      var currentUrl = xlsxUrl
      var redirects  = 0
      var conn: HttpURLConnection = null

      while (redirects < 6) {
        conn = URI.create(currentUrl).toURL.openConnection().asInstanceOf[HttpURLConnection]
        conn.setInstanceFollowRedirects(false)
        conn.setConnectTimeout(30_000)
        conn.setReadTimeout(120_000)
        conn.setRequestProperty("User-Agent", "wifi-cdmx-seeder/1.0")
        conn.getResponseCode match {
          case HttpURLConnection.HTTP_OK =>
            redirects = Int.MaxValue
          case code @ (301 | 302 | 303 | 307 | 308) =>
            val loc = conn.getHeaderField("Location")
            conn.disconnect()
            logger.debug(s"HTTP $code → $loc")
            currentUrl = loc
            redirects += 1
          case code =>
            conn.disconnect()
            throw new RuntimeException(s"HTTP $code from $currentUrl")
        }
      }

      import scala.util.Using
      Using(conn.getInputStream) { in =>
        Using(new FileOutputStream(dest)) { out =>
          val buf = new Array[Byte](8192)
          var n = 0; var total = 0L
          while ({ n = in.read(buf); n != -1 }) {
            out.write(buf, 0, n); total += n
          }
          logger.info(s"Saved ${total / 1024} KB → $localPath")
        }.get
      }.get
      conn.disconnect()
      dest
    }.toEither.left.map(e => AppError.ImportError(s"Download failed: ${e.getMessage}"))
  }


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
          case Left(err)   =>
            logger.error(s"Seed aborted: $err")
            Future.successful(Left(err))
          case Right(_) =>
            logger.info("File ready — parsing coming in next commit")
            Future.successful(Right(0))
        }
    }
  }
}