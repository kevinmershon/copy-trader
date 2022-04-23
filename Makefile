SHELL := bash

.PHONY: dev

docker/build-redis:
	cd docker/redis; docker build -t copy-trader-redis .

docker/run-redis:
	docker run -d -p6379:6379 --name copy-trader-redis copy-trader-redis

docker/build:
	docker build -t copy-trader .

docker/build-all: docker/build-redis docker/build

docker/run:
	docker run -d -p 51585:51585 -v $(PWD):/usr/src/app --name copy-trader copy-trader

docker/run-all:
	docker-compose up

dev:
	clj -A:dev

run:
	clj -M:run -m nrepl.cmdline --bind 0.0.0.0 --port 40404
