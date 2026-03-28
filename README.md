# WiFi CDMX API

API REST + GraphQL para consultar los [puntos de acceso WiFi gratuito en la Ciudad de México](https://datos.cdmx.gob.mx/dataset/puntosdeaccesowifienlaciudaddemexico), usando el portal de datos abiertos de la CDMX.

---

## Índice

- [Diseño de la solución](#diseño-de-la-solución)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Modelo de datos](#modelo-de-datos)
- [Endpoints REST](#endpoints-rest)
- [API GraphQL](#api-graphql)
- [Documentación Swagger](#documentación-swagger)
- [Levantar el proyecto](#levantar-el-proyecto)
- [Desarrollo local](#desarrollo-local)
- [Variables de entorno](#variables-de-entorno)
- [Pruebas](#pruebas)
- [Decisiones técnicas](#decisiones-técnicas)

---

## Diseño de la solución

### Arquitectura en capas

```
┌─────────────────────────────────────────────────┐
│              Clientes HTTP                      │
│       REST · GraphQL · Swagger UI               │
└────────────────────┬────────────────────────────┘
                     │ HTTP/JSON
┌────────────────────▼────────────────────────────┐
│           API Layer (Akka HTTP)                 │
│   WifiPointRoutes · GraphQLRoute · SwaggerRoute │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│        Service Layer (Cats EitherT)             │
│              WifiPointServiceImpl               │
│    Validación · Orquestación · FP composition   │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│       Repository Layer (Slick + SQL)            │
│          SlickWifiPointRepository               │
│       Haversine proximity · bulk insert         │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│              PostgreSQL 15                      │
│     idx_wifi_alcaldia · idx_wifi_lat_lon         │
└─────────────────────────────────────────────────┘

── Transversal ────────────────────────────────────
DataSeeder  → descarga XLSX · parsea · inserta bulk
Flyway      → migraciones versionadas del esquema
Docker Compose → postgres + flyway + app
```

### Flujo de inicio

```
docker compose up
│
├─► postgres (healthcheck)
│
├─► flyway migrate (crea tabla + índices)
│
└─► app
    ├─► HTTP server bind :8080  ← disponible inmediatamente
    └─► DataSeeder (background)
        ├─ ¿XLSX en disco? → NO → descarga del portal CDMX
        ├─ Parsea con Apache POI
        └─ Inserta 35k+ registros en lotes de 500
```

### Manejo de errores (funcional)

Todas las capas retornan `Either[AppError, A]` — nunca se lanzan excepciones en código de negocio.

| `AppError` variant | HTTP status |
| ------------------ | ----------- |
| `NotFound`         | 404         |
| `ValidationError`  | 400         |
| `DatabaseError`    | 500         |
| `ImportError`      | 500         |

### Programación funcional con Cats

El service layer usa `EitherT[Future, AppError, A]` — el monad transformer de Cats que combina `Future` y `Either` en una sola mónada componible:

```scala
// Sin EitherT (imperativo, nested match)
validateCoordinates(lat, lon) match {
  case Left(err) => Future.successful(Left(err))
  case Right((lt, ln)) =>
    validatePagination(page, pageSize) match {
      case Left(err) => Future.successful(Left(err))
      case Right(pgn) => repo.findNearby(lt, ln, pgn)
    }
}

// Con EitherT (funcional, for-comprehension)
val result = for {
  coords <- EitherT.fromEither[Future](validateCoordinates(lat, lon))
  pgn    <- EitherT.fromEither[Future](validatePagination(page, pageSize))
  (lt, ln) = coords
  data   <- EitherT(repo.findNearby(lt, ln, pgn))
} yield data
result.value
```

---

## Stack tecnológico

| Componente    | Tecnología                | Versión |
| ------------- | ------------------------- | ------- |
| Lenguaje      | **Scala** (tipado fuerte) | 2.13.14 |
| HTTP          | Akka HTTP                 | 10.5.3  |
| Serialización | spray-json                | 10.5.3  |
| GraphQL       | Sangria                   | 4.1.0   |
| FP            | Cats Core                 | 2.12.0  |
| DB Access     | Slick + HikariCP          | 3.5.1   |
| Base de datos | PostgreSQL                | 15      |
| Migraciones   | Flyway                    | 10      |
| XLSX parsing  | Apache POI                | 5.3.0   |
| Logging       | Logback + scala-logging   | 1.5.6   |
| Config        | Typesafe Config           | 1.4.3   |
| Empaquetado   | sbt-assembly (fat JAR)    | 2.2.0   |
| Contenedores  | Docker + docker-compose   | —       |
| Testing       | ScalaTest + Akka TestKit  | 3.2.18  |

---

## Estructura del proyecto

```
wifi-cdmx/
├── build.sbt
├── Dockerfile                  # Multistage: JDK builder → JRE runtime
├── docker-compose.yml          # postgres + flyway + app
├── Makefile                    # make run / test / clean
├── sql/
│   └── V1__create_wifi_points.sql
├── data/                       # XLSX se descarga aquí automáticamente
└── src/
    ├── main/
    │   ├── resources/
    │   │   ├── application.conf
    │   │   ├── logback.xml
    │   │   └── openapi.yaml    # Especificación OpenAPI 3.0
    │   └── scala/com/arkondata/wificdmx/
    │       ├── Main.scala
    │       ├── domain/
    │       │   ├── WifiPoint.scala     # case classes inmutables
    │       │   └── AppError.scala      # sealed trait de errores
    │       ├── repository/
    │       │   ├── DatabaseConfig.scala
    │       │   ├── WifiPointTable.scala
    │       │   └── WifiPointRepository.scala  # trait + Slick impl
    │       ├── service/
    │       │   └── WifiPointService.scala     # Cats EitherT
    │       ├── api/
    │       │   ├── models/ApiModels.scala     # DTOs + JSON
    │       │   ├── graphql/GraphQLSchema.scala # Sangria schema
    │       │   └── routes/
    │       │       ├── WifiPointRoutes.scala  # REST + Swagger
    │       │       └── GraphQLRoute.scala     # GraphQL + GraphiQL
    │       └── util/
    │           └── DataSeeder.scala   # Download + parse + insert
    └── test/
        └── scala/com/arkondata/wificdmx/
            ├── api/WifiPointRoutesSpec.scala
            ├── service/
            │   ├── WifiPointServiceSpec.scala
            │   └── DataSeederSpec.scala
            └── repository/InMemoryWifiPointRepository.scala
```

---

## Modelo de datos

### Tabla `wifi_points`

| Columna             | Tipo               | Descripción          |
| ------------------- | ------------------ | -------------------- |
| `id`                | `BIGSERIAL PK`     | Autoincremental      |
| `colonia`           | `VARCHAR(255)`     | Colonia              |
| `alcaldia`          | `VARCHAR(255)`     | Alcaldía (indexada)  |
| `calle`             | `TEXT`             | Dirección            |
| `programa`          | `VARCHAR(255)`     | Programa / proveedor |
| `fecha_instalacion` | `DATE`             | Nullable             |
| `lat`               | `DOUBLE PRECISION` | Latitud WGS84        |
| `lon`               | `DOUBLE PRECISION` | Longitud WGS84       |

**Índices:**

- `idx_wifi_alcaldia` → `LOWER(alcaldia)` — filtro por alcaldía case-insensitive
- `idx_wifi_lat_lon` → `(lat, lon)` — soporte al `ORDER BY` de Haversine

---

## Endpoints REST

Base URL: `http://localhost:8080`

| Método | Ruta                            | Descripción          |
| ------ | ------------------------------- | -------------------- |
| GET    | `/health`                       | Health check         |
| GET    | `/swagger`                      | Swagger UI           |
| GET    | `/openapi.yaml`                 | Spec OpenAPI 3.0     |
| GET    | `/api/v1/wifi`                  | Lista paginada       |
| GET    | `/api/v1/wifi?alcaldia=X`       | Filtrar por alcaldía |
| GET    | `/api/v1/wifi/:id`              | Punto por ID         |
| GET    | `/api/v1/wifi/nearby?lat=&lon=` | Por proximidad       |

### Respuesta paginada

```json
{
  "data": [
    {
      "id": 1,
      "colonia": "Centro",
      "alcaldia": "Cuauhtémoc",
      "calle": "Av. Juárez 12",
      "programa": "GRATIS CDMX",
      "fechaInstalacion": "2020-01-15",
      "coordinates": { "lat": 19.4326, "lon": -99.1332 }
    }
  ],
  "total": 35344,
  "page": 1,
  "pageSize": 20,
  "totalPages": 1768
}
```

---

## API GraphQL

**Endpoint:** `POST /api/v1/graphql`  
**Playground:** `GET /api/v1/graphql` (abre GraphiQL en el navegador)

### Queries disponibles

```graphql
# Lista paginada (con filtro opcional por alcaldía)
{
  wifiPoints(page: 1, pageSize: 5, alcaldia: "Cuauhtémoc") {
    total
    totalPages
    data {
      id
      alcaldia
      colonia
      programa
      coordinates {
        lat
        lon
      }
    }
  }
}

# Punto por ID
{
  wifiPoint(id: 42) {
    id
    alcaldia
    calle
    coordinates {
      lat
      lon
    }
  }
}

# Búsqueda por proximidad (Zócalo CDMX)
{
  nearbyPoints(lat: 19.4326, lon: -99.1332, pageSize: 5) {
    total
    data {
      id
      alcaldia
      coordinates {
        lat
        lon
      }
    }
  }
}
```

---

## Documentación Swagger

Swagger UI disponible en:

```
http://localhost:8080/swagger
```

Spec completo en OpenAPI 3.0:

```
http://localhost:8080/openapi.yaml
```

---

## Levantar el proyecto

> **Nota importante — se requiere VPN con servidor en México**
>
> El endpoint del portal de datos abiertos de la CDMX está geo-restringido a México:
>
> ```
> https://datos.cdmx.gob.mx/dataset/.../download/00-2025-wifi_gratuito_en_cdmx.xlsx
> ```
>
> **Si se encuentra fuera de México:**
>
> 1. Instalar [UrbanVPN](https://www.urban-vpn.com/) o cualquier VPN de preferencia
> 2. Conectarse a un servidor en **México**
> 3. En Docker Desktop → Settings → Resources → Network, habilitar:
>    - _Use kernel networking for UDP_
>    - _Enable host networking_
> 4. Reiniciar Docker Desktop
> 5. Ejecutar `make run` — el seeder descargará el archivo automáticamente
>
> **Si se encuentra en México:** se puede omitir esta nota por completo.

### Requisitos

| Herramienta       | Versión    | Instalación                              |
| ----------------- | ---------- | ---------------------------------------- |
| Docker Desktop    | 24+        | https://docs.docker.com/get-docker/      |
| Docker Compose v2 | incluido   | —                                        |
| Make              | cualquiera | `brew install make` / `apt install make` |

### Con Docker (recomendado)

```bash
# 1. Clonar el repositorio
git clone https://github.com/EmmanuelFullStack/wifi-cdmx.git && cd wifi-cdmx

# 2. Levantar todo
make run

# 3. Verificar
curl http://localhost:8080/health
```

- Swagger UI: http://localhost:8080/swagger
- GraphiQL: http://localhost:8080/api/v1/graphql

El seeder descarga el XLSX de la CDMX automáticamente en el primer arranque. Si ya existe en el volumen, lo reutiliza.

---

## Desarrollo local

### Requisitos adicionales

| Herramienta | Instalación               |
| ----------- | ------------------------- |
| JDK 17      | https://adoptium.net      |
| sbt 1.10+   | https://www.scala-sbt.org |

### Mac / Linux

```bash
docker compose up -d postgres flyway

export DB_URL=jdbc:postgresql://localhost:5433/wificdmx
export DB_USER=postgres
export DB_PASSWORD=postgres

sbt run
```

### Windows (CMD)

```cmd
docker compose up -d postgres flyway

set DB_URL=jdbc:postgresql://localhost:5433/wificdmx
set DB_USER=postgres
set DB_PASSWORD=postgres

sbt run
```

---

## Variables de entorno

| Variable          | Default                                     | Descripción              |
| ----------------- | ------------------------------------------- | ------------------------ |
| `HTTP_HOST`       | `0.0.0.0`                                   | Dirección de escucha     |
| `HTTP_PORT`       | `8080`                                      | Puerto HTTP              |
| `DB_URL`          | `jdbc:postgresql://localhost:5433/wificdmx` | JDBC URL                 |
| `DB_USER`         | `postgres`                                  | Usuario BD               |
| `DB_PASSWORD`     | `postgres`                                  | Contraseña BD            |
| `DATA_URL`        | URL portal CDMX                             | URL de descarga del XLSX |
| `DATA_LOCAL_PATH` | `data/wificdmx.xlsx`                        | Ruta local de caché      |

---

## Pruebas

```bash
# No requiere Docker ni base de datos
make test

# o directamente:
sbt test
```

Los tests usan `InMemoryWifiPointRepository` y `ScalatestRouteTest` (servidor in-process de Akka HTTP).

| Spec                   | Cobertura                                        |
| ---------------------- | ------------------------------------------------ |
| `WifiPointServiceSpec` | Validaciones, paginación, filtro, proximidad     |
| `WifiPointRoutesSpec`  | Todos los endpoints REST, status codes, JSON     |
| `DataSeederSpec`       | Idempotencia, URL inalcanzable, archivo corrupto |

---

## Consideraciones de seguridad

**API pública** — esta API expone datos del portal de datos abiertos de la CDMX, por lo que no implementa autenticación. En un entorno productivo se agregaría JWT o API keys mediante la directiva `authenticate` de Akka HTTP en `WifiPointRoutes`.

**Rate limiting** — el endpoint GraphQL tiene límites de profundidad (10 niveles) y complejidad (1000 puntos) para prevenir queries abusivas. En producción se complementaría con throttling por IP a nivel de infraestructura (nginx, API Gateway).

**Credenciales** — las credenciales de base de datos se leen exclusivamente de variables de entorno; nunca están hardcodeadas en el código fuente. Las URLs de conexión se enmascaran en los logs.

**SQL** — la búsqueda de proximidad usa `PreparedStatement` con parámetros posicionales (`?`), nunca interpolación directa de input del usuario. Las coordenadas `lat`/`lon` se validan como `Double` en el service layer antes de llegar al repositorio.

---

## Decisiones técnicas

**Scala** — el tipado fuerte con `sealed trait`, `case class` e `Either` lleva los errores al sistema de tipos; el compilador obliga a manejarlos en cada capa.

**Cats `EitherT`** — elimina el pattern matching anidado en el service layer. `EitherT[Future, AppError, A]` es una mónada componible que expresa el flujo de validación como una secuencia de pasos en un `for`.

**`Either` en lugar de excepciones** — las firmas de función son honestas: si algo puede fallar, el tipo de retorno lo dice. Sin sorpresas en producción.

**PostgreSQL con Haversine en SQL** — la búsqueda de proximidad delega el cálculo de distancia al motor de base de datos mediante una fórmula Haversine en raw SQL, evitando cargar todos los registros en memoria.

**Sangria para GraphQL** — es el estándar de facto en Scala. El schema es type-safe: los tipos GraphQL reflejan el modelo de dominio sin duplicación.

**Swagger sin dependencias extra** — Swagger UI se sirve como HTML con assets de CDN. El spec `openapi.yaml` vive en `src/main/resources` y se sirve con `getFromResource`, sin agregar ninguna librería al classpath.

**Apache POI para XLSX** — el seeder detecta columnas por nombre (no por posición) para resistir cambios de formato en futuras versiones del dataset de la CDMX.

**Flyway** — versiona el esquema igual que Git versiona el código. Cada cambio es reproducible y auditable.
