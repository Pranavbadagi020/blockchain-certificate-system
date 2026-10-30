# ── Build Stage ──
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Runtime Stage ──
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Run as non-privileged user for container security
RUN addgroup -S certchain && adduser -S certchain -G certchain

COPY --from=builder /app/target/*.jar app.jar
RUN chown -R certchain:certchain /app

USER certchain

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
