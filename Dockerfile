# Build stage using Eclipse Temurin JDK 17
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

# Copy repo files
COPY . .

# Build application distribution
RUN chmod +x gradlew && ./gradlew :keyfort-server:installDist --no-daemon -x test

# Lightweight runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy built distribution from builder stage
COPY --from=builder /workspace/keyfort-server/build/install/keyfort-server .

EXPOSE 8080
ENV PORT=8080

ENTRYPOINT ["./bin/keyfort-server"]
