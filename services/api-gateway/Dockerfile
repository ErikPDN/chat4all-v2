FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
