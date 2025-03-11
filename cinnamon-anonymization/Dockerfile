# build
FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /cinnamon-platform

COPY ../cinnamon-anonymization/src/main/resources/lib/libarx-3.9.1.jar /root/libarx-3.9.1.jar

RUN mvn install:install-file -Dfile=/root/libarx-3.9.1.jar -DgroupId=org.deidentifier -DartifactId=arx -Dversion=3.9.1 -Dpackaging=jar

COPY ../pom.xml .
COPY ../cinnamon-anonymization/pom.xml cinnamon-anonymization/
COPY ../cinnamon-model/pom.xml cinnamon-model/
COPY ../cinnamon-platform/pom.xml cinnamon-platform/
COPY ../cinnamon-test/pom.xml cinnamon-test/

COPY ../cinnamon-anonymization/src cinnamon-anonymization/src
COPY ../cinnamon-model/src cinnamon-model/src

RUN mvn clean package -pl cinnamon-anonymization -am -DskipTests

# package
FROM tomcat:10-jdk17-temurin

RUN rm -rf /usr/local/tomcat/webapps/*

COPY --from=build cinnamon-platform/cinnamon-anonymization/target/cinnamon-anonymization.war /usr/local/tomcat/webapps/ROOT.war

ENV JPDA_ADDRESS=*:8000
ENV JPDA_TRANSPORT=dt_socket

EXPOSE 8080
EXPOSE 8000

ENTRYPOINT ["catalina.sh", "jpda","run"]
