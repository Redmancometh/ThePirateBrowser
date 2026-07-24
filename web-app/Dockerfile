FROM node:22-alpine AS frontend
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm test && npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml dependency:go-offline -q
COPY backend/ backend/
COPY --from=frontend /workspace/frontend/dist frontend/dist
RUN mvn -f backend/pom.xml verify -q

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S pirate && adduser -S pirate -G pirate
WORKDIR /app
COPY --from=backend /workspace/backend/target/pirate-browser-web-1.0.0-SNAPSHOT.jar app.jar
RUN mkdir /app/data && chown -R pirate:pirate /app
USER pirate
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
