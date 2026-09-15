# Cinnamon-Test

Module that contains tests for all cinnamon maven modules.
The tests were moved into a separate module to be able to use without having issues of circular dependencies with
the [cinnamon-model](../cinnamon-model) module.

## Build & Run

Building and running the tests require building the [cinnamon-platform](../cinnamon-platform) module first.
Please see the `Build & Run` section of the [README.md](../cinnamon-platform/README.md#build--run) of the Platform
module first.

### Database setup

Running the tests requires a database to be set up.
For this multiple options are available:

- PostgreSQL database running on localhost (user and database have to be created manually and configured in the
  `spring.datasource.url` property in the [application-test.properties](src/test/resources/application-test.properties)) -
  a [docker-compose.yml](docker/docker-compose.yml) is provided to start such a database via
  `cd docker && docker-compose up -d`; it also creates the dev database used when running cinnamon-platform locally
  (see `application-dev.properties.example`)
- PostgreSQL database with TestContainers (docker must be installed and running)

By default, all options are tried in the order listed above.
The database used in the end is logged to the console.
To enable a specific option, set the `cinnamon.test.database` property to `postgres_custom` or
`postgresql_testcontainers` in the [application-test.properties](src/test/resources/application-test.properties) file.
