.PHONY: help build run stop test logs clean db-shell

help:
	@echo "Available targets:"
	@echo "  build     Build Docker image"
	@echo "  run       Start all services (postgres + flyway + app)"
	@echo "  stop      Stop all services"
	@echo "  test      Run unit tests (no Docker needed)"
	@echo "  logs      Tail application logs"
	@echo "  clean     Remove containers, images and volumes"
	@echo "  db-shell  Open psql shell"

build:
	docker compose build

run:
	docker compose up -d
	@echo ""
	@echo "API:     http://localhost:8080/api/v1/wifi"
	@echo "GraphQL: http://localhost:8080/api/v1/graphql"
	@echo "Swagger: http://localhost:8080/swagger"
	@echo "Health:  http://localhost:8080/health"

stop:
	docker compose down

test:
	sbt test

logs:
	docker compose logs -f app

clean:
	docker compose down -v --rmi local

db-shell:
	docker compose exec postgres psql -U postgres -d wificdmx