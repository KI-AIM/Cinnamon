# build
FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /ki-aim-platform

COPY ../pom.xml .
COPY ../ki-aim-anon/pom.xml ki-aim-anon/
COPY ../ki-aim-model/pom.xml ki-aim-model/
COPY ../ki-aim-platform/pom.xml ki-aim-platform/
COPY ../ki-aim-test/pom.xml ki-aim-test/

COPY ../ki-aim-anon/src ki-aim-anon/src
COPY ../ki-aim-model/src ki-aim-model/src

RUN mvn clean package -pl ki-aim-anon -am -DskipTests

# package
FROM tomcat:10-jdk17-temurin

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build ki-aim-platform/ki-aim-anon/target/ki-aim-anon.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

ENTRYPOINT ["catalina.sh", "run"]
