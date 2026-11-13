# Inventory System

Spring Boot 4 · Java 21 · PostgreSQL 17 · Spring Data JPA · Flyway

## Run

```bash
docker compose up -d          # PostgreSQL on :5432 (inventory_db)
./mvnw spring-boot:run        # API on http://localhost:8081
./mvnw test                   # unit tests
```

Flyway creates the schema on startup (`V1` inventory, `V2` orders/reservations);
Hibernate only validates it (`ddl-auto=validate`).

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/products` | Create product + initial stock |
| GET | `/api/products/{id}` | Product with live stock |
| GET | `/api/products?page=0&size=20` | List products (max size 100) |
| PATCH | `/api/products/{id}` | Update name / price / active |
| GET | `/api/inventory/{productId}` | on-hand / reserved / available |
| POST | `/api/inventory/adjustments` | Add or remove stock (`delta`), never below reserved |
| GET | `/api/inventory/{productId}/movements` | Movement history |
| POST | `/api/orders` | Create order + reserve stock (**`Idempotency-Key` header required**) |
| GET | `/api/orders/{id}` | Order status |
| POST | `/api/orders/{id}/cancel` | Cancel unpaid order, release stock |
| POST | `/api/orders/{id}/payment/success` | Dev-only: confirm payment, deduct stock |
| POST | `/api/orders/{id}/payment/failure` | Dev-only: fail payment, release stock |

Unpaid orders hold stock for 15 minutes; a background job
(`inventory.order-expiry-delay-ms`, default 30 s) then releases it.

## Manual test walkthrough

```bash
H='Content-Type: application/json'; B=http://localhost:8081/api

# 1. product with 10 units
curl -s -X POST $B/products -H "$H" \
  -d '{"sku":"SKU-1","name":"Widget","price":100.00,"initialStock":10}'

# 2. order 3 units  -> RESERVED / PENDING, available 7
curl -s -X POST $B/orders -H "$H" -H 'Idempotency-Key: order-1' \
  -d '{"items":[{"productId":1,"quantity":3}]}'

# 3. same key + same body -> same order replayed, stock NOT reserved twice
curl -s -X POST $B/orders -H "$H" -H 'Idempotency-Key: order-1' \
  -d '{"items":[{"productId":1,"quantity":3}]}'

# 4. same key + different body -> 409
curl -s -X POST $B/orders -H "$H" -H 'Idempotency-Key: order-1' \
  -d '{"items":[{"productId":1,"quantity":4}]}'

# 5. pay twice -> CONFIRMED; on-hand 7, reserved 0; second call changes nothing
curl -s -X POST $B/orders/1/payment/success
curl -s -X POST $B/orders/1/payment/success
curl -s $B/inventory/1

# 6. over-order -> 409 "Insufficient stock"
curl -s -X POST $B/orders -H "$H" -H 'Idempotency-Key: order-2' \
  -d '{"items":[{"productId":1,"quantity":99}]}'

# 7. cancel releases the reservation
curl -s -X POST $B/orders -H "$H" -H 'Idempotency-Key: order-3' \
  -d '{"items":[{"productId":1,"quantity":2}]}'
curl -s -X POST $B/orders/3/cancel
curl -s $B/inventory/1
```

## Known limitations

- `POST /api/inventory/{id}/reservations` places a manual hold that is not tied
  to an order, so nothing ever releases it. Use orders for real reservations.
- The payment endpoints are placeholders; replace them with verified
  payment-provider webhooks before going to production.
