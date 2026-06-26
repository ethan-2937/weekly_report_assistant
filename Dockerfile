FROM node:24-alpine AS frontend-build
WORKDIR /build/web/frontend
COPY web/frontend/package*.json ./
RUN npm ci
COPY web/frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /build/web/backend-spring
COPY web/backend-spring/pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY web/backend-spring/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=backend-build /build/web/backend-spring/target/weekly-report-backend-*.jar /app/app.jar
COPY --from=frontend-build /build/web/frontend/dist /app/frontend
COPY scripts /app/scripts
COPY config/.env.example /app/config/.env.example
COPY README.md requirements.txt /app/
COPY weekly_report_template.txt team_leader_extra_duties.txt /app/

RUN mkdir -p /app/config /app/output /app/logs \
    && chmod +x /app/scripts/*.py || true

ENV WEEKLY_REPORT_ROOT=/app
ENV WEEKLY_FRONTEND_DIST=/app/frontend
ENV PYTHON_BIN=python3
ENV WEEKLY_REPORT_PORT=8088

EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
