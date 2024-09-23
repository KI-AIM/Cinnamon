# build
FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /ki-aim-platform

COPY ../pom.xml .
COPY ../ki-aim-anon/pom.xml ki-aim-anon/
COPY ../ki-aim-model/pom.xml ki-aim-model/
COPY ../ki-aim-platform/pom.xml ki-aim-platform/
COPY ../ki-aim-test/pom.xml ki-aim-test/

RUN mvn dependency:go-offline -B

COPY ../ki-aim-model/src ki-aim-model/src
COPY ../ki-aim-platform/src ki-aim-platform/src
COPY ../ki-aim-platform/ki-aim-frontend ki-aim-platform/ki-aim-frontend

RUN mvn clean package -pl ki-aim-platform -am -DskipTests

# package
FROM tomcat:10-jdk17-temurin

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build ki-aim-platform/ki-aim-platform/target/ki-aim-platform.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

ENTRYPOINT ["catalina.sh", "run"]
