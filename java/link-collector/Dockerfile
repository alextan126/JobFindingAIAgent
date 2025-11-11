# Multi-stage build for Java Link Collector
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml first for better caching
COPY pom.xml .

# Download dependencies (cached if pom.xml hasn't changed)
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build the fat JAR
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre

WORKDIR /app

# Install dependencies for Playwright browsers
# Note: Ubuntu 24.04 uses -t64 suffixes for time64 transition packages
RUN apt-get update && apt-get install -y \
    wget \
    ca-certificates \
    fonts-liberation \
    libasound2t64 \
    libatk-bridge2.0-0t64 \
    libatk1.0-0t64 \
    libatspi2.0-0t64 \
    libcups2t64 \
    libdbus-1-3 \
    libdrm2 \
    libgbm1 \
    libgtk-3-0t64 \
    libnspr4 \
    libnss3 \
    libwayland-client0 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxkbcommon0 \
    libxrandr2 \
    xdg-utils \
    && rm -rf /var/lib/apt/lists/*

# Copy the built JAR from builder stage
COPY --from=builder /build/target/link-collector-*.jar /app/link-collector.jar

# Install Playwright browsers
RUN java -jar /app/link-collector.jar || true && \
    mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium" || \
    wget -q https://playwright.azureedge.net/builds/chromium/1091/chromium-linux.zip && \
    unzip -q chromium-linux.zip -d /root/.cache/ms-playwright/ && \
    rm chromium-linux.zip || true

# Create directory for database
RUN mkdir -p /app/data

# Set environment variable for database location
ENV DATABASE_PATH=/app/data/jobs.db

# Default command
CMD ["java", "-jar", "/app/link-collector.jar"]
