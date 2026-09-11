#!/bin/sh
# Runs once, on first container start (empty data dir), after POSTGRES_DB is created.
# Adds the database used by the cinnamon-test suite alongside the dev database
# (POSTGRES_DB), both owned by the same POSTGRES_USER.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
	CREATE DATABASE cinnamon_test_db OWNER "$POSTGRES_USER";
EOSQL
