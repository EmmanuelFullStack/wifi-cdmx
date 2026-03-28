package com.arkondata.wificdmx.api.routes

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import com.arkondata.wificdmx.api.graphql.GraphQLSchema
import com.arkondata.wificdmx.service.WifiPointService
import com.typesafe.scalalogging.LazyLogging
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError, QueryReducer}
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

final class GraphQLRoute(service: WifiPointService)(implicit ec: ExecutionContext)
    extends LazyLogging {

  private val schema = GraphQLSchema.build(service)

  private val MaxQueryDepth = 15
  private val depthReducer: QueryReducer[Unit, Unit] =
    QueryReducer.rejectMaxDepth[Unit](MaxQueryDepth)

  def route: Route =
    path("api" / "v1" / "graphql") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, graphiqlHtml))
      } ~
      post {
        entity(as[JsObject]) { body =>
          val queryStr  = body.fields.get("query").map(_.convertTo[String]).getOrElse("")
          val variables = body.fields.get("variables")
            .collect { case o: JsObject => o }
            .getOrElse(JsObject.empty)

          if (queryStr.trim.isEmpty)
            complete(StatusCodes.BadRequest ->
              JsObject("error" -> JsString("Query must not be empty")))
          else
            QueryParser.parse(queryStr) match {
              case Failure(ex) =>
                complete(StatusCodes.BadRequest ->
                  JsObject("error" -> JsString(s"Invalid GraphQL syntax: ${ex.getMessage}")))

              case Success(queryDoc) =>
                onComplete(
                  Executor.execute(
                    schema        = schema,
                    queryAst      = queryDoc,
                    variables     = variables,
                    queryReducers = List(depthReducer)
                  )
                ) {
                  case Success(result: JsValue) => complete(result)
                  case Success(other)           => complete(other.toString)
                  case Failure(ex: QueryAnalysisError) =>
                    complete(StatusCodes.BadRequest -> ex.resolveError)
                  case Failure(ex: ErrorWithResolver) =>
                    complete(StatusCodes.InternalServerError -> ex.resolveError)
                  case Failure(ex) =>
                    logger.error("Unexpected GraphQL execution error", ex)
                    complete(StatusCodes.InternalServerError ->
                      JsObject("error" -> JsString("Internal server error")))
                }
            }
        }
      }
    }

  private val graphiqlHtml: String =
    """<!DOCTYPE html>
      |<html>
      |<head>
      |  <title>WiFi CDMX – GraphiQL</title>
      |  <meta charset="utf-8"/>
      |  <style>
      |    * { box-sizing: border-box; }
      |    body { height: 100vh; margin: 0; display: flex; flex-direction: column; }
      |    #graphiql { flex: 1; overflow: hidden; }
      |    .tab-bar {
      |      display: flex; gap: 6px; padding: 6px 12px; flex-shrink: 0;
      |      background: #1e1e2e; border-bottom: 1px solid #444;
      |      overflow-x: auto; align-items: center; min-height: 40px;
      |    }
      |    .tab-bar span { color: #aaa; font-size: 11px; margin-right: 4px; white-space: nowrap; font-family: sans-serif; }
      |    .tab-bar button {
      |      background: #2e2e3e; color: #ccc; border: 1px solid #555;
      |      border-radius: 4px; padding: 4px 12px; cursor: pointer;
      |      font-size: 12px; white-space: nowrap; font-family: sans-serif;
      |    }
      |    .tab-bar button:hover { background: #3e3e5e; color: #fff; }
      |    .tab-bar button.active { background: #5050a0; color: #fff; border-color: #88aaff; }
      |  </style>
      |  <script crossorigin src="https://unpkg.com/react@18/umd/react.development.js"></script>
      |  <script crossorigin src="https://unpkg.com/react-dom@18/umd/react-dom.development.js"></script>
      |  <link rel="stylesheet" href="https://unpkg.com/graphiql@3/graphiql.min.css"/>
      |  <script src="https://unpkg.com/graphiql@3/graphiql.min.js"></script>
      |</head>
      |<body>
      |<div class="tab-bar">
      |  <span>Ejemplos:</span>
      |  <button id="btn-0" class="active" onclick="loadQuery(0)">📋 Todos los puntos</button>
      |  <button id="btn-1" onclick="loadQuery(1)">Por alcaldía</button>
      |  <button id="btn-2" onclick="loadQuery(2)">Por ID</button>
      |  <button id="btn-3" onclick="loadQuery(3)">Por proximidad</button>
      |  <button id="btn-4" onclick="loadQuery(4)">Campos completos</button>
      |</div>
      |<div id="graphiql"></div>
      |<script>
      |var queries = [
      |  { query: "{\n  wifiPoints(page: 1, pageSize: 5) {\n    total\n    totalPages\n    page\n    pageSize\n    data {\n      id\n      alcaldia\n      colonia\n      programa\n      coordinates { lat lon }\n    }\n  }\n}" },
      |  { query: "{\n  wifiPoints(page: 1, pageSize: 5, alcaldia: \"Iztapalapa\") {\n    total\n    totalPages\n    data {\n      id\n      alcaldia\n      colonia\n      calle\n      coordinates { lat lon }\n    }\n  }\n}" },
      |  { query: "{\n  wifiPoint(id: 1) {\n    id\n    alcaldia\n    colonia\n    calle\n    programa\n    fechaInstalacion\n    coordinates { lat lon }\n  }\n}" },
      |  { query: "{\n  nearbyPoints(lat: 19.4326, lon: -99.1332, page: 1, pageSize: 5) {\n    total\n    totalPages\n    data {\n      id\n      alcaldia\n      colonia\n      programa\n      coordinates { lat lon }\n    }\n  }\n}" },
      |  { query: "{\n  wifiPoints(page: 1, pageSize: 3) {\n    total\n    totalPages\n    page\n    pageSize\n    data {\n      id\n      colonia\n      alcaldia\n      calle\n      programa\n      fechaInstalacion\n      coordinates { lat lon }\n    }\n  }\n}" }
      |];
      |var currentIndex = 0;
      |var fetcher = GraphiQL.createFetcher({ url: '/api/v1/graphql' });
      |var root = ReactDOM.createRoot(document.getElementById('graphiql'));
      |function render(q) {
      |  root.render(React.createElement(GraphiQL, { fetcher: fetcher, defaultQuery: q, key: Date.now() }));
      |}
      |function loadQuery(index) {
      |  currentIndex = index;
      |  for (var i = 0; i < queries.length; i++) {
      |    var btn = document.getElementById('btn-' + i);
      |    if (btn) btn.className = (i === index) ? 'active' : '';
      |  }
      |  render(queries[index].query);
      |  setTimeout(function() {
      |    var cms = document.querySelectorAll('.CodeMirror');
      |    if (cms.length > 0 && cms[0].CodeMirror) cms[0].CodeMirror.setValue(queries[index].query);
      |  }, 200);
      |}
      |render(queries[0].query);
      |</script>
      |</body>
      |</html>""".stripMargin
}