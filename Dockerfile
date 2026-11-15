FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY servicerequest/pom.xml .
COPY servicerequest/src ./src
RUN mvn -B package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/service-request-0.0.1-SNAPSHOT.jar app.jar
ENV SERVER_PORT=8080 \
    SERVER_SERVLET_CONTEXT_PATH=/servicerequest \
    SERVER_SERVLET_SESSION_COOKIE_NAME=SERVICEREQUESTSESSION \
    SERVER_SERVLET_SESSION_TRACKING_MODES=cookie \
    SERVER_TOMCAT_USE_RELATIVE_REDIRECTS=true \
    SPRING_H2_CONSOLE_ENABLED=false \
    SERVER_ERROR_INCLUDE_MESSAGE=never
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
