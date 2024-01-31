# KI-AIM-Platform

This is the main application for the KI-AIM platform. 

It contains all the code for: 

- The data management component
- The anonymization component
- The angular frontend


## Requirements
The project has been created with Spring Boot 3.1.5. It has been tested with the following software: 

- Java 17 (LTS)
- Tomcat 10.1.15 (LTS)
- Node 20.9.0 (Latest version supported by Angular)
- NPM 10.2.3 (Latest)
- Angluar CLI 17.0.0 (Latest)

## Build & Run 

To build this project simply run

```bash
mvn clean install
```

The project will automatically build the angular web application for deployment and serve it as a static resource. 
The build process will generate a `.war` archive that is deployable with a tomcat server. 

<br/>

It is also possible to run the angular application by itself to have an auto-updating web view for development. For this, simply run 

```bash
ng serve --open
```

inside the `/ki-aim-frontend` directory. 

For this, a local version of node, npm and the angluar cli has to be installed. The build process however should work without any additional installations. 

## Spring and Angular
The project works by including the built angular web application as a static resource into the Spring webapp. 
Everything is handled by the Spring project. To compile the angular app, the `frontend-maven-plugin` is used that installs a local version of node and npm and compiles the frontend. The distribution folder `ki-aim-frontend/dist/ki-aim-frontend` is then added as a resource in the [`pom.xml`](pom.xml):
```xml
<resources>
    <resource>
        <directory>./ki-aim-frontend/dist/ki-aim-frontend</directory>
        <targetPath>static</targetPath>
    </resource>
</resources>
```

## Angular Frontend
More detailed information can be found [**here**](angular-info.md).

The angular frontend is structured to comply to best practices. This means that under `ki-aim-frontend/src/app` several folders were created to structure the application. To find out, what every subdirectory should be used for and how the components inside should be designed, refer to the `ABOUT` documents inside the folders.
