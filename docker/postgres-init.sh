#!/bin/bash
set -euo pipefail

# Creates additional databases at first-time Postgres init.
# The default DB (POSTGRES_DB=mindstock) is created by the postgres image itself.

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE mindstock_test;
    CREATE USER zitadel WITH PASSWORD 'zitadel';
    CREATE DATABASE zitadel OWNER zitadel;
EOSQL
