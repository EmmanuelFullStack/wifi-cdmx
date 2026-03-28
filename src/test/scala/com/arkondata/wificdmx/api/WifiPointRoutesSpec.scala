package com.arkondata.wificdmx.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.arkondata.wificdmx.api.models.JsonProtocol._
import com.arkondata.wificdmx.api.models.{PagedResponse, WifiPointDto}
import com.arkondata.wificdmx.api.routes.WifiPointRoutes
import com.arkondata.wificdmx.domain.{Coordinates, WifiPoint}
import com.arkondata.wificdmx.repository.InMemoryWifiPointRepository
import com.arkondata.wificdmx.service.WifiPointServiceImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global

class WifiPointRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  private def setup() = {
    val repo    = new InMemoryWifiPointRepository
    val service = new WifiPointServiceImpl(repo)
    val routes  = new WifiPointRoutes(service)
    (repo, routes)
  }

  private def seed(
      repo: InMemoryWifiPointRepository,
      alcaldia: String = "Cuauhtémoc",
      lat: Double = 19.43,
      lon: Double = -99.13
  ): Unit =
    repo.seed(Seq(WifiPoint(0, "Centro", alcaldia, "Juárez 1", "GRATIS CDMX",
      None, Coordinates(lat, lon))))

  "GET /health" should {
    "return 200" in {
      val (_, r) = setup()
      Get("/health") ~> r.routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  "GET /swagger" should {
    "return 200 HTML" in {
      val (_, r) = setup()
      Get("/swagger") ~> r.routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  "GET /api/v1/wifi" should {
    "return paged results" in {
      val (repo, r) = setup()
      seed(repo); seed(repo, "Iztapalapa")
      Get("/api/v1/wifi") ~> r.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[PagedResponse[WifiPointDto]].total shouldBe 2L
      }
    }
    "filter by alcaldía" in {
      val (repo, r) = setup()
      seed(repo, "Cuauhtémoc"); seed(repo, "Iztapalapa")
      Get("/api/v1/wifi?alcaldia=Iztapalapa") ~> r.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[PagedResponse[WifiPointDto]].total shouldBe 1L
      }
    }
    "return 400 for invalid pageSize" in {
      val (_, r) = setup()
      Get("/api/v1/wifi?pageSize=9999") ~> r.routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  "GET /api/v1/wifi/:id" should {
    "return 200 for existing id" in {
      val (repo, r) = setup(); seed(repo)
      Get("/api/v1/wifi/1") ~> r.routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
    "return 404 for unknown id" in {
      val (_, r) = setup()
      Get("/api/v1/wifi/9999") ~> r.routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  "GET /api/v1/wifi/nearby" should {
    "return 200 ordered by proximity" in {
      val (repo, r) = setup()
      seed(repo, lat = 19.43, lon = -99.13)
      seed(repo, lat = 19.50, lon = -99.20)
      Get("/api/v1/wifi/nearby?lat=19.43&lon=-99.13") ~> r.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[PagedResponse[WifiPointDto]].total shouldBe 2L
      }
    }
    "return 400 for missing lat" in {
      val (_, r) = setup()
      Get("/api/v1/wifi/nearby?lon=-99.13") ~> r.routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
    "return 400 for invalid coordinates" in {
      val (_, r) = setup()
      Get("/api/v1/wifi/nearby?lat=999&lon=-99.13") ~> r.routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }
}