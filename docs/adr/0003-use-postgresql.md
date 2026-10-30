# ADR 0003: Use CloudBase PostgreSQL for the first-week API

- Status: Accepted
- Date: 2026-10-20
- Owner: 张博淳（D）

## Context

The original first-week plan used the CloudBase document database. The team decided to use a SQL
database instead. The CloudBase development environment now has PostgreSQL 17.11 enabled.

## Decision

Use CloudBase PostgreSQL as the server-side database. Keep the HTTP API paths and JSON field names
unchanged. Database tables and columns use `snake_case`; API JSON uses `camelCase`.

The first-week runtime accesses data through the CloudBase PostgreSQL Data API and stores photos in
PG-mode cloud storage. Client applications never receive database credentials.

## Consequences

- Data is stored in tables and constrained with primary keys, foreign keys and unique indexes.
- `checkins(user_id, landmark_id)` and `checkins(user_id, idempotency_key)` prevent duplicate
  check-ins.
- `fragment_grants(user_id, fragment_id)` prevents duplicate fragment grants.
- Database migrations are stored under `cloudbase/migrations/`.
- The first-week acceptance wording must treat tables and constraints as the SQL equivalent of
  collections and indexes.
