-- Takes back what Supabase grants its Data API roles in public.
--
-- JobBuddy has no row-level security, so while anon or authenticated hold privileges here, the
-- project's public anon key reads her profile and applications as soon as the Data API is on.
-- docs/guides/db.md says to turn the Data API off; this keeps her data closed if someone forgets.
--
-- Each role is checked on its own: on plain Postgres (dev, CI, tests) neither exists, and a
-- REVOKE naming a missing role fails the migration.
--
-- Functions too: the Data API exposes them as RPC, and a SECURITY DEFINER one runs with its
-- owner's rights. ALL on a function is EXECUTE, its only privilege. EXECUTE through PUBLIC is
-- Postgres's own default and stays.
--
-- ALTER DEFAULT PRIVILEGES without FOR ROLE changes only the defaults for objects the running role
-- creates later. That covers every later migration, because the desktop app migrates as the same
-- postgres user every time.

DO $$
DECLARE
    api_role text;
BEGIN
    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM %I', api_role);
        END IF;
    END LOOP;
END
$$;
