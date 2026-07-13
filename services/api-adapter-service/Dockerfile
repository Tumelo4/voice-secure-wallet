FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN mvn --batch-mode --no-transfer-progress -pl services/api-adapter-service -am package -DskipTests \
    && mkdir -p /out/lib \
    && find services -path '*/target/*.jar' -type f ! -name '*-sources.jar' ! -name '*-javadoc.jar' -exec cp {} /out/lib/ \;

FROM eclipse-temurin:17.0.16_8-jre-jammy
RUN groupadd --system voicesecure && useradd --system --gid voicesecure --home /app voicesecure
WORKDIR /app
COPY --from=build --chown=voicesecure:voicesecure /out/lib/ /app/lib/
USER voicesecure
ENV PORT=8080 VSW_ENVIRONMENT=local
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD ["java", "-cp", "/app/lib/*", "com.voicesecure.api.HealthProbe"]
ENTRYPOINT ["java", "-cp", "/app/lib/*", "com.voicesecure.api.ApiApplication"]
