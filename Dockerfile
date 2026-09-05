FROM maven:3.9.9-eclipse-temurin-17
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=docker
COPY target/volunteer-duration-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
