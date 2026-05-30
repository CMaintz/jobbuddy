# PostgreSQL Cheatsheet

> To run the backend w/o building frontend etc, use `aac up postgres typesense -d`

> **After wiping volumes (`aac down -v`), the database is completely empty until the backend
> runs Flyway migrations on startup. Run `aac-backend` or `aac up -d` at least once before
> trying to query anything — otherwise `\dt` will return "did not find any relations".

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
