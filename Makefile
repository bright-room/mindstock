COMPOSE=docker compose

.PHONY: up down clean

up:
	$(COMPOSE) up -d

down:
	$(COMPOSE) down -v

clean:
	$(COMPOSE) down -v --rmi all --remove-orphans
