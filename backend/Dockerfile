FROM eclipse-temurin:21.0.12.1_1-jdk-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS build
WORKDIR /app
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY backend ./backend
RUN chmod +x gradlew && ./gradlew :backend:bootJar --no-daemon -q

# The document renderer shells out to Typst. Pinned and checksum-verified; keep the version
# equal to backend/mise.toml so the image compiles templates exactly like local dev and CI.
FROM eclipse-temurin:21.0.12.1_1-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555 AS typst
ARG TYPST_VERSION=0.15.1
ARG TYPST_SHA256=a6d077d0a95eed5a2eba715b2dae06be954f624ccbf85758a03f389ded33118c
WORKDIR /tmp/typst
RUN apk add --no-cache curl xz \
    && curl -fsSLo typst.tar.xz \
        "https://github.com/typst/typst/releases/download/v${TYPST_VERSION}/typst-x86_64-unknown-linux-musl.tar.xz" \
    && echo "${TYPST_SHA256}  typst.tar.xz" | sha256sum -c - \
    && tar -xJf typst.tar.xz --strip-components=1 \
    && ./typst --version

FROM eclipse-temurin:21.0.12.1_1-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/backend/build/libs/*.jar app.jar
COPY --from=typst /tmp/typst/typst /usr/local/bin/typst
USER spring
EXPOSE 8080
# Busybox wget ships with the Alpine base; /actuator/health is public (SecurityConfig).
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
