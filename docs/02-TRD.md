# Technical Requirements Document (TRD)

**Version:** 0.1 (draft). Stack choices below are adapted for Spring Boot 3.3 (Java 21); change them if your team prefers something else.

---

## 1. Architecture Overview

```
 Browser (PWA, mobile + desktop)
   Spring Boot REST API  ── Leaflet + OpenStreetMap
        │  HTTPS                │ WebSocket / SSE
        ▼                       ▼
 ┌──────────────────────────────────────────────┐
 │ Spring Boot 3.3 (Java 21)                    │
 │  • Spring Data JPA + PostGIS (data, geo)     │
 │  • Spring Security (JWT from Supabase Auth)  │
 │  • Spring WebSocket / SSE (live tracking)    │
 │  • Supabase Storage (photos, certificates)   │
 │  • Spring @Scheduled tasks (jobs)            │
 └──────────────────────────────────────────────┘
        │
        ├─ PostgreSQL + PostGIS (via Supabase or self-hosted)
        ├─ Routing API (OSRM / OpenRouteService) – ETA + route line
        ├─ Web Push (VAPID) – notifications
        ├─ Email provider (Resend / SendGrid / SES) – approvals, reports
        └─ RSS sources – news ingestion (admin approved)
```

## 2. Technology Stack (proposed)

| Layer | Choice | Reason |
|---|---|---|
| Backend | Spring Boot 3.3 (Java 21) + PostgreSQL/Supabase | Production-grade REST API; mature ecosystem; JPA for ORM |
| Security | Spring Security + Supabase Auth (JWT validated by Spring Security) | Supabase issues JWTs; Spring Security validates and enforces RBAC |
| Maps | Leaflet + OpenStreetMap tiles (served to frontend) | Free; sufficient for pins, routes, live marker |
| Database | PostgreSQL + PostGIS (via Supabase) | "NGOs within X km", geofence checks |
| ORM | Spring Data JPA + Hibernate Spatial | Entity management; PostGIS geometry types |
| Routing | OSRM or OpenRouteService | Free-tier ETA and route line |
| Real-time | Spring WebSocket/SSE (Server-Sent Events) | Live location push to connected clients |
| Hosting | Railway / Render / AWS (Spring Boot JAR), Supabase cloud (DB + Storage) | Low cost, container-friendly deploys |
| Push | Web Push API (VAPID) | No app store needed |
| Monitoring | Spring Actuator + Sentry | Health endpoints, error tracking |
| Email | Resend or similar (via Spring Mail / REST client) | Transactional email |
| PDF | OpenPDF or iText library (server-side, in a Spring @Scheduled job) | Monthly certificates |
| Build | Maven (or Gradle) | Standard Java build tooling |

## 3. Key Technical Requirements

### 3.1 PWA
- Web app manifest, icons, `display: standalone`, install prompt.
- Service worker: cache app shell and static assets; network-first for data. Full offline mode is out of scope for MVP.
- HTTPS required (geolocation, push, service worker).

### 3.2 Live Location Tracking
- Use `navigator.geolocation.watchPosition` in the collector's browser only while the pickup status is *On the way*.
- Request Screen Wake Lock (`navigator.wakeLock`) to keep the screen on during the pickup, and show a prompt: "Keep this screen open while collecting."
- Send a ping every 5–10 s (throttled; skip if movement < ~10 m): `{pickup_id, lat, lng, accuracy, ts}` to a Spring REST endpoint (`POST /pickups/{id}/pings`).
- Write pings to `location_pings`; connected clients receive updates via Spring WebSocket/SSE subscription for that pickup.
- Stop watching at *Collected* or on cancel.
- **Limitation:** browsers cannot reliably track in the background, especially iOS Safari. Fallback is status milestones plus last-known position with a "last updated X min ago" label.
- Retention: delete pings 30 days after the pickup completes (Spring @Scheduled daily job).

### 3.3 Map
- Layers: open listings (pins by urgency: green > 2 h, amber 1–2 h, red < 1 h), NGOs, active pickup (collector marker, route line, ETA).
- Cluster pins when zoomed out.
- ETA from routing API on status change and every ~60 s, not on every ping.

### 3.4 Atomic Claiming
- Claiming uses a single database function (or a `SELECT ... FOR UPDATE` with a conditional update `WHERE status = 'posted'`) so two NGOs cannot claim the same listing simultaneously. Return a clear error to the loser.

### 3.5 Handoff Verification
- Geofence: collector's position must be within ~150 m of the hotel (configurable) when marking *Collected*; otherwise show a warning and flag the report.
- Pickup photo required at completion.
- Optional static QR at the hotel kitchen door (printed) that the collector scans; this needs no hotel action.

### 3.6 Scoring Engine
- Spring @Scheduled job (weekly) computes per-hotel signals over a 60-day window and writes `score_snapshots`.
- Constants (weights, smoothing k, minimum pickups, benchmark coefficients) live in a `settings` table editable by admin.
- Voiding or correcting a report triggers recompute for the affected hotel.
- Public leaderboard reads only from the latest snapshot.

### 3.7 Notifications
- In-app notification centre (table + SSE push via Spring).
- Web Push for supported browsers. Note: on iOS, push works only after the PWA is installed to the home screen (iOS 16.4+).
- Email for approvals, monthly certificates and complaint replies (Spring Mail / REST email provider).
- Fallback (later): SMS/WhatsApp via a provider if push adoption is low.

### 3.8 News Ingestion
- Admin-managed list of RSS sources; a Spring @Scheduled task (hourly) fetches items into a `news_items` table in *draft* status.
- Admin approves/pins. Store title, link, source, short snippet (not full article text) to respect copyright.
- Internal stories and rescue milestones are created by admin or auto-generated from data (e.g., "Hotel X reached 5,000 meals").

### 3.9 Impact Passport
- Monthly Spring @Scheduled job generates a PDF certificate per hotel from verified data using OpenPDF or iText library and stores it in Supabase Storage.
- Public badge: an embeddable image endpoint (e.g., `/badge/{hotel_slug}.svg`) showing live meals rescued and tier, served by a Spring controller. Cache response for a few minutes.

## 4. API Surface (Spring REST Controllers)

| Area | Endpoint / Method | Role |
|---|---|---|
| Auth | Supabase Auth (JWT validated by Spring Security) | all |
| Listings | `POST /api/listings`, `GET /api/listings?bbox&status`, `PATCH /api/listings/{id}/cancel` | hotel / ngo |
| Claim | `POST /api/listings/{id}/claim` (atomic, transactional) | ngo |
| Pickup | `PATCH /api/pickups/{id}/status`, `POST /api/pickups/{id}/pings`, `POST /api/pickups/{id}/report` | volunteer |
| Photos | Signed upload URLs to Supabase private bucket (via Spring controller proxy) | volunteer |
| Leaderboard | `GET /api/leaderboard?category` | public |
| Hotel dashboard | `GET /api/hotels/{id}/dashboard` | hotel (own) |
| News | `GET /api/news`, admin CRUD | public / admin |
| Social | `POST /api/kudos`, `POST/DELETE /api/follows` | public user |
| Admin | approve org, flag queue, void/correct report, settings | admin |
| Badge | `GET /badge/{hotel_slug}.svg` | public |

## 5. Security & Privacy

- Role-based access control enforced via Spring Security; JWT claims from Supabase Auth decoded and mapped to Spring roles.
- Row-Level Security on every table in Postgres; policies per role (see Schema document).
- Hotels see only their own listings and score; NGOs see open listings and their own pickups; public sees only public aggregates and news.
- Collector location visible only to the hotel, NGO and admin for that pickup, and only while active.
- Photos in a private Supabase Storage bucket with short-lived signed URLs.
- Rate limiting on auth, pings and kudos; basic bot protection (CAPTCHA) on public sign-up.
- Input validation (server-side, via Bean Validation / `@Valid`) for all writes; sanitize any user-entered text.
- Audit log for admin actions (void, correct, suspend) in `admin_actions` table.
- Store the minimum personal data; document a privacy policy; allow account deletion.

## 6. Performance & Scale (pilot)

- Target: a few hundred concurrent users; free/low-tier hosting is adequate.
- Index geospatial and status columns; paginate lists (Spring Data `Pageable`).
- SSE subscriptions scoped to a specific pickup or listing area to limit traffic.

## 7. Testing

- Unit tests (JUnit 5 + Mockito): score calculations, claim logic, geofence check.
- Integration tests (Spring Boot Test + Testcontainers): end-to-end flow (post → claim → track → report → score) against a real Postgres + PostGIS container.
- Manual device testing on iOS Safari and Android Chrome (location permission, wake lock, push, camera upload).
- Seed script (Spring Boot `CommandLineRunner` or Flyway data migration) producing demo hotels, NGOs and pickups for QA and demos.
- Simulated movement script to test the live map without walking around.

## 8. Deployment & Environments

- Environments: local, staging, production (separate database schemas or Supabase projects).
- CI: lint, compile, test on pull request; auto-deploy to staging on merge (GitHub Actions / GitLab CI).
- Secrets via environment variables (`application.properties` / Spring profiles); never hardcoded.
- Database migrations versioned in the repo (Flyway).
- Daily automated database backups (Supabase managed or pg_dump cron).

## 9. Technical Risks

| Risk | Mitigation |
|---|---|
| iOS background location and push limits | Foreground tracking during pickups only; install-to-home-screen prompt; status fallback |
| GPS inaccuracy | Use accuracy value; geofence tolerance; photo as secondary proof |
| Free map/routing tier limits | Cache routes; consider paid tier or self-hosted OSRM later |
| SSE / WebSocket load | Scoped channels; batch pings; async Spring controllers |
| Score tuning | Settings table; snapshot history; re-run capability via admin endpoint |

## 10. Open Technical Questions

- Is Supabase Auth acceptable as the identity provider, or should Spring Security manage its own user store?
- Who will build and maintain the app (team size and skills)?
- Is there a budget for paid map, SMS or WhatsApp services?
- Data residency requirements for hosting region?
