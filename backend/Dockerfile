FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/resume-fit-backend-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
