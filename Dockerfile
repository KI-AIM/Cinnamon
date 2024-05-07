# build
FROM maven:3.8.5-openjdk-17

WORKDIR /ki-aim-platform

COPY pom.xml .

RUN mvn dependency:go-offline

COPY src ./src
COPY ki-aim-frontend ./ki-aim-frontend

RUN mvn clean package

# package
FROM tomcat:10-jdk17-temurin

WORKDIR /ki-aim-platform

COPY target/ki-aim-platform.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

ENTRYPOINT ["catalina.sh", "run"]
