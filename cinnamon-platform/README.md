# Cinnamon-Platform

This is the main application for the cinnamon platform. 

It is responsible for:
- The data management
- The process management
- The Angular frontend

## Build & Run

Cinnamon Platform uses maven packages hosted on GitHub, so you have to set up authentication for GitHub's package registry first.
This can be done by adding a server entry with ID `github` to your `~/.m2/settings.xml`.
The personal access token needs at least the `read:packages` scope.
Below is a snippet of the server entry.
A minimal example of the entire file can be found in the [settings.xml](../.github/maven/settings.xml) used by GitHub actions.

```xml
<servers>
    <server>
        <id>github</id>
        <username>USERNAME</username>
        <password>PERSONAL_ACCESS_TOKEN</password>
    </server>
</servers>
```

### Docker

It is recommended to use the [docker-compose-build.yml](../docker-compose-build.yml) for building and [docker-compose.yml](../docker-compose.yml) for running the platform with Docker.

Run the following command in the root directory to build Cinnamon Platform:
```sh
docker compose -f docker-compose-build.yml build cinnamon-platform 
```

To start Cinnamon Platform, create a `.env`, paste the content of `.env.example` into the new `.env` file and set the `PG_PASSWORD` variable.
Then start Cinnamon Platform by executing this command:
```sh
docker compose up -d cinnamon-platform
```

### WAR

For the installation as a `.war` file, the following software has been verified to work:
- Java 17 (LTS)
- Tomcat 10.1.15 (LTS)
- PostgreSql

To build this project as a WAR file, run

```bash
./mvnw clean package
```

The project will automatically build the angular web application for deployment and serve it as a static resource. 
The build process will generate a `.war` archive that is deployable with a tomcat server.

Setting up the database can be done running the [create.sql](./src/main/resources/create.sql).

## Development
The following setup has been tested for developing the project:
- Java 17 (LTS)
- Tomcat 10.1.15 (LTS)

Running
```bash
./mvnw clean install
```
downloads and installs the required node and npm versions in the `./cinnamon-frontend/node` directory.

### Development Profile
To run the project in development mode, add the `-Dspring.profiles.active=dev` to the JVM arguments.
The Platform then can be configured by changing creating a `application-dev.properties` file.
An example file can be found in `src/main/resources/application-dev.properties.example` which provides a good starting point.
```bash
cp src/main/resources/application-dev.properties.example src/main/resources/application-dev.properties
```

### Spring and Angular
The project works by including the built angular web application as a static resource in the Spring webapp. 
Everything is handled by the Spring project.
To compile the angular app, the `frontend-maven-plugin` is used that installs a local version of node and npm and compiles the frontend.
The distribution folder `cinnamon-frontend/dist/cinnamon-frontend` is then added as a resource in the [`pom.xml`](pom.xml):

```xml
<resources>
    <resource>
        <directory>./cinnamon-frontend/dist/cinnamon-frontend</directory>
        <targetPath>static</targetPath>
    </resource>
</resources>
```

<br/>

It is also possible to run the angular application by itself to have an auto-updating web view for development. For this, simply run

```bash
ng serve --open
```

inside the `/cinnamon-frontend` directory.


### FHIR Server

Cinnamon Platform provides a FHIR server connection for fetching the data to be anonymized.
Running a local FHIR server can be done by using the [blaze docker image](https://hub.docker.com/r/samply/blaze).

```bash
docker run -d --name cinnamon-blaze -p 127.0.0.1:8082:8080 samply/blaze:latest
```

The easiest way to fill the FHIR server with test data is to use the [sample data bundle](../sample/example-fhir-bundle.json).

```bash
curl -X POST -H "Content-Type: application/fhir+json" -d "@../sample/example-fhir-bundle.json" http://localhost:8082/fhir
```

The data can be accessed by using the following URL in the configuration: `http://localhost:8082/fhir/Observation`

### Angular Frontend
More detailed information can be found [**here**](https://ki-aim.github.io/cinnamon-docs/contribution/platform).

The angular frontend is structured to comply with best practices.
This means that under `cinnamon-frontend/src/app` several folders were created to structure the application.
To find out, what every subdirectory should be used for and how the components inside should be designed, refer to the
`ABOUT` documents inside the folders.
