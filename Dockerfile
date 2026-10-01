FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN apk add --no-cache maven && mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine AS runtime
# Native Tesseract OCR engine — required by document-service's OcrEngineService
# (Tess4J is a JNI binding to this binary, not a self-contained OCR
# implementation). Carried over unchanged from document-service's own
# Dockerfile; every other former service needed no native dependencies at
# all, so this monolith image's only OS-level addition versus a bare JRE
# image is this one package.
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /build/target/insurance-monolith.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/v1/actuator/health || exit 1
USER appuser
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-XX:+UseG1GC","-Djava.security.egd=file:/dev/./urandom","-jar","app.jar"]
