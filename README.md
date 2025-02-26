# KI-AIM

This is the main repository for the [KI-AIM](https://www.forschung-it-sicherheit-kommunikationssysteme.de/projekte/ki-aim) project.
A platform for providing anonymized data by combining anonymization and synthetization methods.

## Submodules

This project contains the following submodules.
For more information, read the README.md of the submodules.

### KI-AIM-Model

The [KI-AIM-Model](./ki-aim-model/README.md) submodule contains commonly used model classes like configurations.

### KI-AIM-Anon
Submodule providing an API for anonymizing a dataset.

Running the anonymization modules requires to install arx:
```bash
mvn install:install-file -Dfile=ki-aim-anon/src/main/resources/lib/libarx-3.9.1.jar -DgroupId=org.deidentifier -DartifactId=arx -Dversion=3.9.1 -Dpackaging=jar
```

### KI-AIM-Platform

The [KI-AIM-Platform](./ki-aim-platform/README.md) provides data management as well as the front end of the project.

### KI-AIM-Test

Submodule containing all tests for the other modules as well as test utilities.

## Build & Run
To build the entire project with all submodules, run

```bash
mvn clean install
```

You can also build only specific submodules by executing the command in the directory of the submodule.
The README.md of the submodules will also provide additional instructions.

## Deployment

For deploying the entire project, a docker compose file is provided.

To build all images, run

```bash
docker-compose build
```

### Configuration

When changing the configuration of the database (e.g. the password) make sure to also change the configuration of the platform accordingly.

```yaml
services:
  ki-aim-db:
    environment:
      # Has to match the datasource settings of the ki-aim-platform container
      - POSTGRES_DB=ki_aim_db
      - POSTGRES_USER=ki_aim_user
      - POSTGRES_PASSWORD=changeme
    ki-aim-platform:
      environment:
        # Datasource has to match with the configuration of the ki-aim-db container
        SPRING_DATASOURCE_PASSWORD: changeme
        SPRING_DATASOURCE_URL: jdbc:postgresql://ki-aim-db:5432/ki_aim_db
        SPRING.DATASOURCE.USERNAME: ki_aim_user
```

> [!WARNING]
> Please change the password of the PostgreSQL user before deploying.

When changing container names or ports, the configurations for the external server have to be changed as well.

```yaml
services:
  ki-aim-platform:
    environment:
      KI-AIM.EXTERNAL-SERVER.0.CALLBACK-HOST: ki-aim-platform
      KI-AIM.EXTERNAL-SERVER.0.URL-SERVER: http://ki-aim-anon:8080
```
