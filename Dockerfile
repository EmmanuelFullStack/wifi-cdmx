# ── Stage 1: Build ───────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build

RUN apt-get update && apt-get install -y curl gnupg && \
  echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | \
  tee /etc/apt/sources.list.d/sbt.list && \
  curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | \
  apt-key add - && \
  apt-get update && apt-get install -y sbt && \
  rm -rf /var/lib/apt/lists/*

# Copy dependency files first (layer caching)
# If build.sbt doesn't change, sbt update is cached
COPY build.sbt ./
COPY project/  project/
RUN sbt update

# Copy source and build fat JAR
COPY src/ src/
RUN sbt assembly

# ── Stage 2: Runtime ─────────────────────────────────────────
# Use JRE only — much smaller image than JDK (no compiler needed)
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Non-root user for security — never run as root in production
RUN addgroup --system appgroup && \
  adduser  --system --ingroup appgroup appuser

COPY --from=builder /build/target/scala-2.13/wifi-cdmx.jar ./wifi-cdmx.jar
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "wifi-cdmx.jar"]