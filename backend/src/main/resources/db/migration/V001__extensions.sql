-- Postgres extensions the schema depends on.
--
-- uuid-ossp supplies uuid_generate_v4() for primary keys; vector supplies the embedding type
-- behind job and profile similarity search.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;
