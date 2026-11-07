# syntax=docker/dockerfile:1

# Build stage: compiles and packages the Spring Boot jar, then splits it into layers.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
# Tests run in CI (mvn verify); the image build only packages.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests -Dspotless.apply.skip=true \
    && java -Djarmode=tools -jar target/time-deposit-kata-*.jar extract --layers --launcher --destination extracted

# Runtime stage: JRE only, non-root, one image layer per Spring Boot layer for better caching.
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
# 8080: API and Swagger UI. 8081: actuator, only with the local or dev profile.
EXPOSE 8080 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
