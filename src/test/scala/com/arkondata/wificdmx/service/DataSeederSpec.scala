package com.arkondata.wificdmx.service

import com.arkondata.wificdmx.domain.{AppError, Coordinates, WifiPoint}
import com.arkondata.wificdmx.repository.InMemoryWifiPointRepository
import com.arkondata.wificdmx.util.DataSeeder
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global

class DataSeederSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit val patience: PatienceConfig =
    PatienceConfig(Span(10, Seconds), Span(50, Millis))

  "DataSeeder" should {

    "skip seeding when DB already has data (idempotent)" in {
      val repo = new InMemoryWifiPointRepository
      repo.seed(Seq(WifiPoint(0, "C", "A", "S", "P", None,
        Coordinates(19.43, -99.13))))
      // URL and path don't matter — seeder exits before touching them
      val seeder = new DataSeeder(repo,
        "http://unreachable.invalid/f.xlsx", "/tmp/no.xlsx")
      seeder.seed().futureValue shouldBe Right(0)
    }

    "return Left(ImportError) when URL unreachable and no local file" in {
      val repo   = new InMemoryWifiPointRepository
      val seeder = new DataSeeder(repo,
        "http://unreachable.invalid/f.xlsx", "/tmp/definitely-not-there.xlsx")
      val result = seeder.seed().futureValue
      result                       shouldBe a[Left[_, _]]
      result.swap.toOption.get     shouldBe a[AppError.ImportError]
    }

    "return Left(ImportError) for a corrupt local file" in {
      val repo = new InMemoryWifiPointRepository
      val f    = java.io.File.createTempFile("bad", ".xlsx")
      f.deleteOnExit()
      java.nio.file.Files.write(f.toPath, "not-xlsx".getBytes)
      val seeder = new DataSeeder(repo,
        "http://unreachable.invalid/f.xlsx", f.getAbsolutePath)
      seeder.seed().futureValue shouldBe a[Left[_, _]]
    }
  }
}