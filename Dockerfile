FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/target/jobpulse-*.jar app.jar
RUN useradd --uid 10001 --no-create-home --shell /usr/sbin/nologin jobpulse
USER jobpulse
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
