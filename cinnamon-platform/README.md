# Cinnamon-Platform

This is the main application for the cinnamon platform. 

It is responsible for:
- The data management
- The process management
- The Angular frontend

## Build & Run

### Docker
It is recommended to use the [docker-compose.yml](../docker-compose.yml) in the root directory of this repository for building and running the platform with Docker.

### WAR

For the installation as a `.war` file, the following software has been verified to work:
- Java 17 (LTS)
- Tomcat 10.1.15 (LTS)
- PostgreSql

To build this project as a WAR file simply run

```bash
mvn clean install
```

The project will automatically build the angular web application for deployment and serve it as a static resource. 
The build process will generate a `.war` archive that is deployable with a tomcat server.


## Development
The following setup has been tested for developing the project:
- Java 17 (LTS)
- Tomcat 10.1.15 (LTS)
- Node 22.14.0 (LTS)
- NPM 10.9.2 (Latest)
- Angular CLI 17.0.0 (Latest)

### Spring and Angular
The project works by including the built angular web application as a static resource into the Spring webapp. 
Everything is handled by the Spring project. To compile the angular app, the `frontend-maven-plugin` is used that installs a local version of node and npm and compiles the frontend. The distribution folder `cinnamon-frontend/dist/cinnamon-frontend` is then added as a resource in the [`pom.xml`](pom.xml):
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

For this, a local version of node, npm and the angular cli has to be installed. The build process however should work without any additional installations.

### Angular Frontend
More detailed information can be found [**here**](angular-info.md).

The angular frontend is structured to comply to best practices. This means that under `cinnamon-frontend/src/app` several folders were created to structure the application. To find out, what every subdirectory should be used for and how the components inside should be designed, refer to the `ABOUT` documents inside the folders.
