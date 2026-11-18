# Implementation Plan

**Version:** 0.1 (draft). Timeline assumes 1–2 developers working full time on the Spring Boot 3.3 (Java 21) + PostgreSQL/Supabase stack. Durations are estimates; adjust once team size is known.

---

## 1. Delivery Strategy

Build the thinnest end-to-end flow first (post → claim → collect → report), then layer the map, score, and public features on top.

Ship to a small pilot early (about week 8) and tune the scoring with real data.

Keep every feature behind the MVP scope in the PRD; defer everything else.

---

## 2. Phases

### Phase 0: Setup (Week 1)

- Create repo; initialise **Maven** project with Spring Boot 3.3 (Java 21), Spring Web, Spring Data JPA, Spring Security, Spring WebSocket, Flyway, Lombok.
- Add dependencies: `hibernate-spatial` (PostGIS), `spring-boot-starter-actuator`, Sentry.
- Create Supabase projects (staging, production); enable PostGIS extension.
- Configure `application.yml` profiles (`dev`, `staging`, `prod`); set up environment variables for secrets.
- Set up CI (GitHub Actions): compile, lint (Checkstyle/SpotBugs), test on pull request; auto-deploy to staging on merge.
- Write Flyway baseline migration (`V1__init.sql`) with all enums and core tables.
- Define design tokens (colours, type, spacing) from the UI/UX doc for the frontend layer.

**Done when:** a "hello world" Spring Boot REST endpoint (`GET /api/health`) returns 200 and the app deploys to staging.

---

### Phase 1: Accounts, Roles, Admin Approval (Week 2)

- Integrate **Supabase Auth (JWT validated by Spring Security)**: configure `JwtDecoder` to validate Supabase-issued JWTs; extract `user_role` claim and map to Spring `GrantedAuthority`.
- Create JPA entities: `Profile`, `Hotel`, `Ngo`, `Volunteer` with Flyway migrations.
- Hotel and NGO registration REST endpoints (`POST /api/auth/register/hotel`, `/ngo`) with multipart document upload proxied to Supabase Storage.
- Admin approval endpoints (`PATCH /api/admin/orgs/{id}/approve|reject|suspend`) with `admin_actions` audit log.
- Approval email sent via Spring Mail / Resend REST client.
- `@PreAuthorize` role guards on all controllers.

**Done when:** a hotel and an NGO can register and be approved by an admin via REST calls (tested with Postman / integration test).

---

### Phase 2: Listings and Claiming (Week 3)

- JPA entity: `Listing`, `Pickup`, `Tag`, `PickupTag`; Flyway migrations.
- Listing CRUD: `POST /api/listings`, `GET /api/listings`, `PATCH /api/listings/{id}/cancel`.
- NGO discovery: `GET /api/listings/nearby?ngoId=` using native PostGIS `ST_DWithin` query via `@Query(nativeQuery=true)`.
- Atomic claim: `POST /api/listings/{id}/claim` calls the `claim_listing` DB function in a `@Transactional` service; returns clear error on race loss.
- Auto-expiry: `@Scheduled(fixedRate = 60000)` updates expired listings.
- Volunteer invite and assignment: `POST /api/ngos/{id}/volunteers`, `PATCH /api/pickups/{id}/assign`.
- In-app notification table + SSE endpoint (`GET /api/notifications/stream`) for real-time delivery.
- Web Push setup (VAPID key pair; `POST /api/push/subscribe`).

**Done when:** a hotel posts, an NGO is notified, claims atomically, and assigns a volunteer (integration test covers the full flow).

---

### Phase 3: Map and Live Tracking (Weeks 4–5)

- `location_pings` JPA entity + Flyway migration.
- Ping ingestion: `POST /api/pickups/{id}/pings` (rate-limited, skip if movement < ~10 m).
- Spring WebSocket (or SSE) channel per pickup: hotel/NGO clients subscribe to `/topic/pickup/{id}`; server forwards new pings.
- Pickup status transitions: `PATCH /api/pickups/{id}/status` with state-machine validation.
- Routing API integration (OSRM / OpenRouteService HTTP client via `RestClient`): fetch route line and ETA on status change and every ~60 s.
- Stale-location handling: "last updated X min ago" label when ping age > 2 min.
- Frontend: Leaflet map with urgency-coloured pins, clustering, bottom sheet; trip screen with `watchPosition`, throttling, wake lock.
- Simulated-movement script (Spring `CommandLineRunner` dev profile) for testing without physical travel.

**Done when:** a hotel can watch the volunteer's marker move in a real browser during a test trip.

---

### Phase 4: Collection, Report and Flags (Week 6)

- Geofence check on "Collected": server-side `ST_DWithin(collector_location, hotel.location, geofence_radius_m)`.
- Photo upload: Spring controller proxies multipart to Supabase Storage private bucket; returns signed URL.
- Completion report: `POST /api/pickups/{id}/report` (portions, tags, left-behind); validates required fields with Bean Validation `@Valid`.
- Auto-flag logic in `PickupReportService`: quantity mismatch, missing photo, out-of-geofence, late report, outlier NGO → inserts into `flags`.
- Report-due reminders: `@Scheduled(cron = "0 */15 * * * *")` checks pickups 1 h and 3 h past collection without a report.
- Admin flag queue: `GET /api/admin/flags`, `PATCH /api/admin/flags/{id}/keep|correct|void`; void triggers score recompute for the hotel; audit entry saved.

**Done when:** a full pickup can be completed and an admin can void a report (integration test).

---

### Phase 5: Score, Leaderboard and Impact Passport (Week 7)

- Score calculation implemented as a `@Scheduled(cron = "0 0 2 * * SUN")` Spring service method using native SQL or JPQL; reads weights/constants from `settings` table; writes `score_snapshots`.
- Admin can trigger manual recompute: `POST /api/admin/scores/recompute/{hotelId}`.
- Leaderboard endpoint: `GET /api/leaderboard?category=` reads latest eligible snapshots.
- Hotel private dashboard: `GET /api/hotels/{id}/dashboard`.
- Monthly certificate: `@Scheduled(cron = "0 0 3 1 * *")` generates PDF via **OpenPDF or iText library**; stores in Supabase Storage; writes to `certificates`; sends email.
- Embeddable badge: `GET /badge/{hotel_slug}.svg` served by a Spring controller with a few-minute cache (`Cache-Control`).
- Admin settings screen: `GET/PATCH /api/admin/settings`.

**Done when:** seeded data produces a believable leaderboard; certificates and badges render.

---

### Phase 6: News and Public Experience (Week 8)

- Public sign-up (`role = public`) with no approval step.
- News entities: `NewsItem`; Flyway migration.
- RSS ingestion: `@Scheduled(cron = "0 0 * * * *")` fetches configured RSS feeds, inserts items as `draft` using Rome or similar Java RSS library.
- Admin news management: approve, pin, hide endpoints.
- Follow and kudos: `POST/DELETE /api/follows`, `POST /api/kudos` (once per hotel per week enforced by unique constraint).
- Hotel public pages: `GET /api/hotels/{slug}/public`; Open Graph meta tags served from Spring controller.
- City counter: `GET /api/stats/city` aggregating meals rescued.

**Done when:** a public user can sign up, read news, follow a hotel and send kudos.

---

### Phase 7: Hardening and Pilot Launch (Weeks 9–10)

- Device testing: iOS Safari and Android Chrome — location permission, wake lock, push, camera upload, PWA install.
- Security review: Spring Security audit, rate limiting (`Bucket4j` or similar), signed URL expiry, Bean Validation coverage, SQL injection checks.
- Accessibility pass on frontend; performance profiling (Spring Actuator metrics, slow query log).
- Legal: terms of use, privacy policy, food-safety checklist wording reviewed by counsel.
- Support email and complaint procedure documented.
- Onboard 3–5 hotels and 2–3 NGOs; training session for volunteers.

**Done when:** pilot is live with real pickups.

---

## 3. Pilot (Weeks 10–14)

Run for about 4 weeks; review metrics weekly (claim rate, time to claim, report completion, repeat posting).

Collect feedback from hotels, NGO volunteers and a few public users.

Tune score weights, benchmark constants, notification radius and reminder timing via the `settings` table (no code change needed).

Decide on next steps: wider launch, WhatsApp/SMS alerts, independent volunteers, native app.

---

## 4. Milestones and Checkpoints

| Milestone | Target | Demo-able output |
|---|---|---|
| M1: Accounts and approval | End of week 2 | Register and approve hotel + NGO |
| M2: Core flow | End of week 3 | Post, notify, claim |
| M3: Live map | End of week 5 | Moving marker in a browser |
| M4: Report and moderation | End of week 6 | Complete pickup, admin void |
| M5: Score and incentives | End of week 7 | Leaderboard, certificate, badge |
| M6: Public features | End of week 8 | News, follow, kudos |
| M7: Pilot launch | Week 10 | Real pickups with partners |

---

## 5. Task Breakdown by Area

| Area | Key tasks |
|---|---|
| Backend (Spring Boot) | Maven project setup, JPA entities, Spring Security/JWT, REST controllers, `@Scheduled` jobs, WebSocket/SSE, PDF generation, Flyway migrations |
| Database | PostGIS schema, RLS policies, `claim_listing` function, indexes, Supabase Storage config |
| Frontend | Design system, Leaflet map, listing/claim/report forms, PWA manifest + service worker, public pages |
| Integrations | Routing API (RestClient), Web Push (VAPID), email (Spring Mail / Resend), RSS (Rome), OSM tiles |
| QA | JUnit 5 + Mockito unit tests, Spring Boot Test + Testcontainers integration tests, device matrix, seed data, simulated trips |
| Ops | GitHub Actions CI/CD, Flyway migrations, Supabase backups, Spring Actuator + Sentry monitoring, environment secrets |
| Content | Tag list, safety checklist wording, terms, onboarding copy, first news stories |
| Partners | Recruit pilot hotels and NGOs, onboarding sessions, feedback loop |

---

## 6. Team and Roles (suggested)

- 1 full-stack Java/Spring developer (core backend + API).
- 1 frontend developer (optional but helpful — Leaflet, PWA, responsive UI).
- 1 product owner for decisions, partner relationships, content.
- Part-time designer for wireframes and brand (or use the token set as-is).
- Part-time legal review for terms and food-safety wording.

---

## 7. Risks to Watch During the Build

| Risk | Early signal | Response |
|---|---|---|
| Live tracking unreliable on iOS | Test trips drop updates when screen locks | Emphasise wake lock; status fallback; "last seen" label |
| Hotels don't post after week 1 | Falling weekly post count | Personal onboarding, reminders to kitchen staff, certificates and badges early |
| NGO reports late or skipped | Report-due flags rising | Auto-reminders via `@Scheduled`, show the report step right after collection, trust score |
| Scope creep | New requests mid-phase | Park in a backlog; only revisit after pilot |
| Score feels unfair | Complaint emails | Review constants, void/correct via admin, adjust smoothing k in `settings` |
| Spring/JPA performance on geo queries | Slow `ST_DWithin` queries | Ensure PostGIS spatial index; consider native queries; cache leaderboard reads |

---

## 8. Post-MVP Backlog (not in scope now)

- WhatsApp/SMS alerts for NGOs.
- Independent volunteers and delivery routing across multiple pickups.
- Multi-language UI (Spring MessageSource i18n).
- Native Android/iOS apps.
- Connected kitchen scales or surplus forecasting.
- Corporate sponsorship and diner-points redemption at partner hotels.
- Multi-branch hotel chains.
- Dark mode.

---

## 9. Immediate Next Steps (this week)

1. Answer the open questions (listed with this document set) so the PRD and schema can be finalised.
2. Confirm the Spring Boot 3.3 (Java 21) stack and who will build it.
3. Choose a product name and register the domain.
4. Shortlist 3–5 pilot hotels and 2–3 NGOs and start conversations.
5. Draft the terms of use and food-safety checklist for legal review.
6. Begin Phase 0: `mvn archetype:generate` → Spring Initializr → first Flyway migration → CI pipeline.
