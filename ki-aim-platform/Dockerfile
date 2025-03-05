# build
FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /ki-aim-platform

COPY ../pom.xml .
COPY ../ki-aim-anon/pom.xml ki-aim-anon/
COPY ../ki-aim-model/pom.xml ki-aim-model/
COPY ../ki-aim-platform/pom.xml ki-aim-platform/
COPY ../ki-aim-test/pom.xml ki-aim-test/

COPY ../ki-aim-model/src ki-aim-model/src
COPY ../ki-aim-platform/src ki-aim-platform/src
COPY ../ki-aim-platform/cinnamon-frontend ki-aim-platform/cinnamon-frontend

RUN mvn clean package -pl ki-aim-platform -am -DskipTests

# package
FROM tomcat:10-jdk17-temurin

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build ki-aim-platform/ki-aim-platform/target/ki-aim-platform.war /usr/local/tomcat/webapps/ROOT.war

ENV JPDA_ADDRESS=*:8000
ENV JPDA_TRANSPORT=dt_socket

EXPOSE 8080
EXPOSE 8000

ENTRYPOINT ["catalina.sh", "jpda","run"]
