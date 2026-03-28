package com.arkondata.wificdmx.service

import com.arkondata.wificdmx.domain.{AppError, Coordinates, WifiPoint}
import com.arkondata.wificdmx.repository.InMemoryWifiPointRepository
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global

class WifiPointServiceSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit val patience: PatienceConfig =
    PatienceConfig(Span(5, Seconds), Span(50, Millis))

  private def pt(alcaldia: String, lat: Double, lon: Double) =
    WifiPoint(0, "Col", alcaldia, "Calle", "Prog", None, Coordinates(lat, lon))

  private def build() = {
    val repo = new InMemoryWifiPointRepository
    (repo, new WifiPointServiceImpl(repo))
  }

  "WifiPointService.getById" should {
    "return the point when it exists" in {
      val (repo, svc) = build()
      repo.seed(Seq(pt("Cuauhtémoc", 19.43, -99.13)))
      svc.getById(1L).futureValue shouldBe a[Right[_, _]]
    }
    "return NotFound for unknown id" in {
      val (_, svc) = build()
      svc.getById(999L).futureValue shouldBe
        Left(AppError.NotFound("WiFi point with id=999 not found"))
    }
    "return ValidationError for id <= 0" in {
      val (_, svc) = build()
      svc.getById(0L).futureValue.swap.toOption.get shouldBe a[AppError.ValidationError]
    }
  }

  "WifiPointService.getAll" should {
    "return correct total and page" in {
      val (repo, svc) = build()
      repo.seed((1 to 25).map(i => pt("A", 19.0 + i * 0.01, -99.0)))
      val r = svc.getAll(1, 10).futureValue.toOption.get
      r.total     shouldBe 25L
      r.data.size shouldBe 10
    }
    "return ValidationError for pageSize > 200" in {
      val (_, svc) = build()
      svc.getAll(1, 500).futureValue.swap.toOption.get shouldBe a[AppError.ValidationError]
    }
    "return ValidationError for page < 1" in {
      val (_, svc) = build()
      svc.getAll(0, 10).futureValue.swap.toOption.get shouldBe a[AppError.ValidationError]
    }
  }

  "WifiPointService.getByAlcaldia" should {
    "return only matching alcaldía" in {
      val (repo, svc) = build()
      repo.seed(Seq(pt("Cuauhtémoc", 19.43, -99.13), pt("Iztapalapa", 19.36, -99.08)))
      val r = svc.getByAlcaldia("Cuauhtémoc", 1, 20).futureValue.toOption.get
      r.total             shouldBe 1L
      r.data.head.alcaldia shouldBe "Cuauhtémoc"
    }
    "return ValidationError for blank alcaldía" in {
      val (_, svc) = build()
      svc.getByAlcaldia("  ", 1, 20).futureValue.swap.toOption.get shouldBe
        a[AppError.ValidationError]
    }
  }

  "WifiPointService.getNearby" should {
    "return closest point first" in {
      val (repo, svc) = build()
      repo.seed(Seq(
        pt("A", 19.43, -99.13),
        pt("B", 19.50, -99.20),
        pt("C", 19.00, -98.00)
      ))
      val r = svc.getNearby(19.43, -99.13, 1, 10).futureValue.toOption.get
      r.data.head.alcaldia shouldBe "A"
    }
    "return ValidationError for invalid latitude" in {
      val (_, svc) = build()
      svc.getNearby(200.0, -99.13, 1, 10).futureValue.swap.toOption.get shouldBe
        a[AppError.ValidationError]
    }
    "return ValidationError for invalid longitude" in {
      val (_, svc) = build()
      svc.getNearby(19.43, 400.0, 1, 10).futureValue.swap.toOption.get shouldBe
        a[AppError.ValidationError]
    }
  }
}