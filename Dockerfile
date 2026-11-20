FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
