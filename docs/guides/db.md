# Database

The desktop app and the scheduled crawl share one Supabase project per install. Local
development uses the Docker Postgres in the [cheatsheet](#local-postgres-cheatsheet) below.

## Supabase

### Create the project

1. Create a project on [supabase.com](https://supabase.com) in an EU region. Keep the database
   password: the dashboard does not show it again (Connect > **Reset database password** makes a
   new one and breaks every saved connection).
2. Turn the Data API off: Integrations > Data API > **Enable Data API** off. JobBuddy talks to
   Postgres directly and never uses it. Its tables live in `public` without row-level security,
   and Supabase grants `anon` full access to new tables there (checked on `supabase/postgres`:
   `has_table_privilege('anon', 'public.jobs', 'SELECT')` is true after migrating), so with the
   Data API on, anyone holding the project's anon key could read her profile and applications.

Free projects that see no activity for a week are paused; restore one from the dashboard.

### Connect: session pooler, `sslmode=require`

Use the **session pooler** for everything. Find it under **Connect** at the top of the project
page, method "Session pooler". The dashboard shows a URI:

```
postgresql://postgres.<project-ref>:[YOUR-PASSWORD]@aws-<n>-<region>.pooler.supabase.com:5432/postgres
```

JobBuddy takes it as three values:

| Field | Value |
|---|---|
| JDBC URL | `jdbc:postgresql://aws-<n>-<region>.pooler.supabase.com:5432/postgres?sslmode=require` |
| User | `postgres.<project-ref>` (the project ref is part of the user name on the pooler) |
| Password | the database password, on its own, never inside the URL |

Copy the host from the dialog: the `<n>` is a pooler cluster number, not derived from the region.
`sslmode=require` encrypts the connection; it does not check the server certificate.

Why not the others:

- **Direct connection** (`db.<project-ref>.supabase.co:5432`, user `postgres`) is IPv6-only
  unless the project buys the IPv4 add-on. GitHub-hosted runners and many home networks have no
  IPv6, so it fails there with a connection error rather than a clear message.
- **Transaction pooler** (port `6543`) hands each transaction to any server connection, so the
  server-side prepared statements pgjdbc makes after a few executions break. It works only with
  `prepareThreshold=0` in the JDBC URL, and Flyway's migration lock needs one session throughout,
  so never migrate through it. Nothing in JobBuddy needs it.

**Connection budget.** On the smallest computes (Nano, Micro) the pooler gives each database user
15 server connections, and in session mode every open client connection holds one. The desktop
app's pool takes up to 10 (Hikari's default), so the crawl caps its own at 4
(`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`). Anything else that connects as the same user, a
`psql` session or a backup, comes out of the same 15.

### Who migrates

The desktop app is the one migrator: Flyway runs when its backend starts, as the `postgres` user
above. On Supabase `postgres` is not a superuser, but it may create the extensions the schema
needs. The scheduled crawl runs with Flyway off and only validates (`SchemaVersionCheck`): built
from a newer release than the app has migrated to, it stops at startup and says so. So update the
desktop app first, then the crawl's `CRAWLER_REF`.

Verified on `supabase/postgres:17.11.0.018` (Postgres 17.11), connected as `postgres`
(`rolsuper` false), from an empty database by the desktop profile's Flyway:

- all 46 migrations applied, through `V047`;
- `vector` 0.8.2 created by `V001` in `public`; `uuid-ossp` already exists in Supabase's
  `extensions` schema, which is on the default `search_path`;
- the `danish` text search configuration (`V025`) is built in;
- a dump and restore with the commands under [Backups](#backups) into a second fresh container
  kept every row and Flyway's history, and the crawl's schema check passed on the copy.

Flyway logs "Flyway upgrade recommended: PostgreSQL 17.11 is newer than this version of Flyway";
the migrations and the crawl's validation still ran clean.

### First run of the desktop app

The setup screen ("Forbindelse") asks for:

| Field | What to enter |
|---|---|
| Databaseadresse | the JDBC URL above, starting with `jdbc:postgresql://` |
| Brugernavn | `postgres.<project-ref>` |
| Adgangskode | the database password |
| Tjeneste til jobsøgning | Gemini (default) or OpenAI: the embedding provider for the whole install |
| Nøgle til tjenesten | that provider's API key |

The provider choice is stored in the database at startup (`embedding_choice`), and the scheduled
crawl embeds with whatever is stored, so its GitHub secrets must include that provider's key. The
values are kept encrypted for her Windows user (see the README's "Desktop app").

### Scheduled crawl secrets

The crawl workflow (`.github/workflows/crawl.yml`) reads these repository secrets (Settings >
Secrets and variables > Actions):

| Secret | Value |
|---|---|
| `SUPABASE_DB_URL` | the JDBC URL above |
| `SUPABASE_DB_USER` | `postgres.<project-ref>` |
| `SUPABASE_DB_PASSWORD` | the database password |
| `GEMINI_API_KEY` and/or `OPENAI_API_KEY` | the key for the provider chosen in the desktop setup |

Until the three `SUPABASE_DB_*` secrets exist, each run ends green with a notice that setup is
pending. With them set but without the stored provider's key, the run fails at startup and names
the missing secret. The repository variable `CRAWLER_REF` (a tag or branch, default `main`) picks
the code the crawl is built from; set it to the release the desktop app runs.

GitHub switches off scheduled workflows in a public repository after 60 days without commits.
The crawl then stops without failing and the postings slowly go stale; re-enable it under
Actions > Crawl, or push any commit.

### Backups

- **Supabase's own:** Pro and higher plans get a daily backup, kept 7 days on Pro. Restore under
  Database > Backups; the project is offline while it restores. The Free plan has none, so on
  Free the dump below is the only backup.
- **Your own dump** (any plan): everything JobBuddy stores, Flyway's history included, is in
  `public`. With Docker, through the session pooler, writing the dump to the current folder:

  ```bash
  export PGPASSWORD='<database password>'
  docker run --rm -e PGPASSWORD postgres:17 \
    pg_dump -h aws-<n>-<region>.pooler.supabase.com -p 5432 -U postgres.<project-ref> -d postgres \
    --schema=public --no-owner --no-privileges -Fc > "jobbuddy-$(date +%F).dump"
  ```

  To run it on a schedule, use Windows Task Scheduler on the PC that keeps the dumps. Do not
  dump from GitHub Actions: the repo is public, and so are its logs and artifacts. A dump holds
  her CV data, so keep it somewhere private.

**Restore a dump** into a new, empty project (same session pooler recipe):

```bash
export PGPASSWORD='<new database password>'
docker run --rm -e PGPASSWORD postgres:17 \
  psql -h <new pooler host> -p 5432 -U postgres.<new-ref> -d postgres \
  -c 'CREATE EXTENSION IF NOT EXISTS vector'
docker run --rm -i -e PGPASSWORD postgres:17 \
  pg_restore -h <new pooler host> -p 5432 -U postgres.<new-ref> -d postgres \
  --no-owner --no-privileges < jobbuddy-<date>.dump
```

`vector` comes first because a `public`-only dump leaves extensions out. pg_restore then reports
one ignored error, `schema "public" already exists`, which is expected. The dump goes through
stdout and stdin rather than a mounted folder, so the same commands work from Git Bash. Then
point the desktop app's connection and the three `SUPABASE_DB_*` secrets at the new project, and
turn its Data API off as above.

## Local Postgres cheatsheet

> To run the backend w/o building frontend etc, use `aac up postgres -d`

> **After wiping volumes (`aac down -v`), the database is completely empty until the backend
> runs Flyway migrations on startup. Run `aac-backend` or `aac up -d` at least once before
> trying to query anything; otherwise `\dt` will return "did not find any relations".

## Open a psql shell

```powershell
docker exec -it infra-postgres-1 psql -U autoapplicant -d autoapplicant
```

### Inside psql

```
\dt          list all tables
\d jobs      describe the jobs table
\q           quit
```

## Useful queries

**Job counts by source**
```sql
SELECT source, COUNT(*) AS total FROM jobs GROUP BY source ORDER BY total DESC;
```

**Enriched vs unenriched breakdown**
```sql
SELECT
  source,
  COUNT(*)              AS total,
  COUNT(ai_summary)     AS enriched,
  COUNT(*) - COUNT(ai_summary) AS unenriched
FROM jobs
GROUP BY source
ORDER BY total DESC;
```

**Most recently scraped jobs**
```sql
SELECT title, source, scraped_at FROM jobs ORDER BY scraped_at DESC LIMIT 20;
```

**Total job count**
```sql
SELECT COUNT(*) FROM jobs;
```

## Run one-off commands without entering the shell

**List all tables**
```powershell
docker exec infra-postgres-1 psql -U autoapplicant -d autoapplicant -c "\dt"
```

**Run a SQL query**
```powershell
docker exec infra-postgres-1 psql -U autoapplicant -d autoapplicant -c "SELECT source, COUNT(*) FROM jobs GROUP BY source ORDER BY COUNT(*) DESC;"
```
