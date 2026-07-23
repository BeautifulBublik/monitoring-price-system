# Price Monitor

A backend service for monitoring prices in online shops. A user adds a product by its link,
the service periodically parses its price, stores the history and sends a notification (Email / Telegram)
when the price drops below a given threshold.

**Stack:** Java 21, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA, PostgreSQL, Flyway,
Jsoup + Selenium, springdoc-openapi, Docker Compose. Built with Gradle (Kotlin DSL).

---

## Quick start

```bash
cp .env.example .env
# JWT_SECRET must be set in .env (at least 32 characters)
docker compose up --build
```

Three containers come up: `postgres`, `selenium` (headless Chrome) and `app`.

- Swagger UI — http://localhost:8080/swagger-ui.html
- OpenAPI JSON — http://localhost:8080/v3/api-docs

### Try it in a minute

```bash
# 1. Registration — returns a JWT right away
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"P@ssw0rd123"}' | jq -r .token)

# 2. Add a product: the price is parsed immediately and becomes the first history point
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"url":"https://example-shop.com/product/42","thresholdType":"PERCENT","thresholdValue":10}'

# 3. Price history for a chart
curl "http://localhost:8080/api/products/1/history?from=2026-07-01T00:00:00Z" \
  -H "Authorization: Bearer $TOKEN"

## Configuration

There are no secrets in the code: everything goes through environment variables
(`.env` → `docker-compose.yml` → `application.yml`).

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | — (**required**) | JWT signing key, at least 32 characters |
| `SCHEDULER_CRON` | `0 0 * * * *` | Price sweep interval (Spring cron, 6 fields) |
| `SCHEDULER_ENABLED` | `true` | Turns the background sweep off |
| `HISTORY_STORE_MODE` | `ON_CHANGE` | `ON_CHANGE` or `ALWAYS` — see below |
| `SELENIUM_ENABLED` | `true` | Turns the Selenium fallback off |
| `MAIL_ENABLED` | `false` | While `false`, no emails are sent |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USERNAME` / `SMTP_PASSWORD` | — | SMTP access |
| `TELEGRAM_ENABLED` | `false` | Enables the Telegram channel and the bot |
| `TELEGRAM_BOT_TOKEN` | — | Token from @BotFather |


---

## How to get a Telegram bot token

1. Send the `/newbot` command to [@BotFather](https://t.me/BotFather).
2. Set the bot's name and username — you will get a token like `123456789:AAE...` in reply.
3. Put it into `.env`: `TELEGRAM_BOT_TOKEN=...`, `TELEGRAM_ENABLED=true`.
4. Restart: `docker compose up -d`.

Then, on the user's side:

1. Open your bot in Telegram and send `/start`.
2. The bot replies with the **chat ID**.
3. Save it to the profile:


The bot works over long polling in a separate daemon thread — no webhook and no public HTTPS needed.

> The `telegrambots` library is deliberately not used: exactly two Bot API methods are needed
> (`sendMessage` and `getUpdates`), and two `RestClient` calls read more simply than another
> dependency with its own lifecycle.

---

## Architecture

```
dev.beautifulbublik.monitoringsystem
├── config          SecurityConfig, SchedulingConfig, AsyncConfig, OpenApiConfig,
│                   WebDriverFactory, PriceMonitorProperties
├── controller      AuthController, ProductController, NotificationSettingsController
├── dto             requests/responses (records) with validation and Swagger examples
├── entity          User, Product, PriceHistory, NotificationSettings
├── repository      Spring Data JPA
├── service         AuthService, ProductService, PriceCheckService,
│                   NotificationService, ThresholdEvaluator
├── parser          PriceParser + JsoupPriceParser / SeleniumPriceParser,
│                   PriceParsingService (facade), JsonLdPriceExtractor,
│                   DomainRateLimiter, PriceTextParser, shop/ (per-shop strategies)
├── scheduler       PriceUpdateScheduler
├── notification    Notifier + EmailNotifier / TelegramNotifier, TelegramBotPoller
├── security        JwtService, JwtAuthenticationFilter, CustomUserDetailsService
└── exception       GlobalExceptionHandler + typed exceptions
```

### How a price is checked

```
Scheduler ──► PriceCheckService
                 │
                 ├─ 1. PriceParsingService.parse(url)      ← network, OUTSIDE the transaction
                 │      ├─ per-domain rate limit
                 │      ├─ Jsoup (by default)
                 │      └─ Selenium (fallback, if Jsoup found no price)
                 │
                 ├─ 2. write to the DB                      ← short transaction
                 │      ├─ history point (per store-mode)
                 │      ├─ product.lastCheckedAt
                 │      └─ ThresholdEvaluator: send a notification or not?
                 │
                 └─ 3. NotificationService.send(...)        ← SMTP/Telegram, OUTSIDE the transaction
```

The network calls and notification delivery are kept outside the transaction boundary on purpose:
these are seconds of waiting, and holding a DB connection for them is wasteful. Only the short
write lives inside the transaction.

Delivery itself is dispatched with `@Async` (see `AsyncConfig`), so a slow SMTP or Telegram
round-trip does not hold up the price-check worker thread. Each channel failure is caught and
logged individually, so one broken channel never breaks the other.

---

## Parsing

`PriceParser` is an abstraction with two implementations:

| | When | What it costs |
|---|---|---|
| **JsoupPriceParser** | by default | one HTTP request, milliseconds |
| **SeleniumPriceParser** | fallback, if Jsoup found no price | headless Chrome, ~1–2 s and hundreds of MB of RAM |

Both implementations share the same extraction code (`JsoupPriceParser.extract`) — only the way
the HTML is obtained differs.

**Two ways to extract a price, in this order:**

1. **The shop's CSS selector** (`price-selector`) — if it is set and matched an element. It reads
   both text and microdata attributes (`content`, `data-price`, `value`). Explicit config wins.
2. **schema.org JSON-LD** (`JsonLdPriceExtractor`) — the fallback used when CSS yielded nothing.
   It parses `<script type="application/ld+json">` blocks with `Product`/`Offer` (including
   `@graph`, an array of offers, `AggregateOffer`) and takes `name`, `price`, `priceCurrency`
   from there. This is exactly what makes `default-rule` work for an **arbitrary** shop without
   a dedicated selector — nearly every large shop already ships this markup for SEO.

The WebDriver is created **per single parse** and is guaranteed to be closed: it is not
thread-safe, and a long-lived instance leaks memory over time and gets stuck on a broken page.


## Notification thresholds

| `thresholdType` | Meaning | Needs `thresholdValue` |
|---|---|---|
| `ANY_DROP` | any drop | no |
| `PERCENT` | a drop of ≥ N % | yes |
| `ABSOLUTE` | a drop of ≥ N currency units | yes |

`thresholdBase` defines **what** the drop is measured from:

- `LAST_PRICE` — from the last known price (reacts to every drop);
- `MIN_PRICE` — from the historical minimum (notifies only about a genuinely new low).

**Spam protection.** `products.last_notified_price` stores the price that has already been
reported. A repeat notification is only sent if the new price is **strictly lower** than it.
If the price bounces between 100 and 90, the email about 90 arrives once.

---

## REST API

All `/api/products` and `/api/settings` endpoints require the `Authorization: Bearer <token>`
header. Every user has their own isolated list: someone else's product returns **404**, not 403 —
a 403 would confirm that a product with that id exists.

| Method | Path | What it does |
|---|---|---|
| `POST` | `/api/auth/register` | Registration, returns a JWT right away |
| `POST` | `/api/auth/login` | Login |
| `POST` | `/api/products` | Add a product by URL (parses the price immediately) |
| `GET` | `/api/products` | List your products with the current price |
| `GET` | `/api/products/{id}` | Details: settings, minimum, full history |
| `GET` | `/api/products/{id}/history?from=&to=` | History for a period (for a chart) |
| `PATCH` | `/api/products/{id}` | Pause/resume, change the threshold |
| `DELETE` | `/api/products/{id}` | Delete the product along with its history |
| `GET` | `/api/settings/notifications` | Current channels |
| `PUT` | `/api/settings/notifications` | Enable/disable Email and Telegram |

The full description with request and response examples is in the Swagger UI.

```

A `422` when adding a product means the price could not be obtained from the page (the shop is
unsupported, is down, or turned on an anti-bot). In this case the product is **not created**:
silently creating a broken tracker is the worst option — the user would only learn about the
problem after a week of silence.

---






