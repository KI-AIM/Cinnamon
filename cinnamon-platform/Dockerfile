# build
FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /cinnamon-platform

COPY ../pom.xml .
COPY ../ki-aim-anon/pom.xml ki-aim-anon/
COPY ../cinnamon-model/pom.xml cinnamon-model/
COPY ../cinnamon-platform/pom.xml cinnamon-platform/
COPY ../cinnamon-test/pom.xml cinnamon-test/

COPY ../cinnamon-model/src cinnamon-model/src
COPY ../cinnamon-platform/src cinnamon-platform/src
COPY ../cinnamon-platform/cinnamon-frontend cinnamon-platform/cinnamon-frontend

RUN mvn clean package -pl cinnamon-platform -am -DskipTests

# package
FROM tomcat:10-jdk17-temurin

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build cinnamon-platform/cinnamon-platform/target/cinnamon-platform.war /usr/local/tomcat/webapps/ROOT.war

ENV JPDA_ADDRESS=*:8000
ENV JPDA_TRANSPORT=dt_socket

EXPOSE 8080
EXPOSE 8000

ENTRYPOINT ["catalina.sh", "jpda","run"]
