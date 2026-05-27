# syntax=docker/dockerfile:1.7
# 多阶段构建：
#   builder    - JDK 21 编译应用（Vaadin productionMode + Spring Boot fat jar）
#   jre-builder - jlink 生成最小 JRE，仅包含运行时所需 JDK 模块
#   runtime    - Alpine 基础 + 自定义 JRE + app.jar（镜像约 ~170MB）

# ============================================================
# Stage 1: Build application
# ============================================================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 第一层：Gradle 配置与 wrapper（变更频率最低，最大化缓存命中）
COPY gradlew ./
COPY gradle/ gradle/
COPY settings.gradle build.gradle gradle.properties ./

# 第二层：依赖预热（利用 BuildKit cache mount，节省后续重复下载）
RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew --no-daemon dependencies

# 第三层：复制源码与前端资源（Vaadin Gradle 插件会在 vaadinPrepareFrontend 阶段
# 自动生成 package.json、vite.config.ts、tsconfig.json 等前端工程文件，
# 因此不需要也不应当将这些生成产物 COPY 进来）
COPY src/ src/
COPY frontend/ frontend/

# 生产模式构建（productionMode 触发 vaadinBuildFrontend，产出 hashed bundle 嵌入 jar）
RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew --no-daemon -Pvaadin.productionMode clean build -x test

# ============================================================
# Stage 2: jlink 自定义 JRE
# 列出 Spring Boot 4 + Vaadin 25 + Oracle JDBC + JJWT 实际需要的模块。
# Spring/Vaadin 大量使用反射和 SPI，无法仅靠 jdeps 推导；以下为保守覆盖集，
# 漏模块会在启动时报 ClassNotFoundException / NoClassDefFoundError，可按需追加。
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS jre-builder

RUN jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.jdi,jdk.jfr,jdk.management,jdk.management.agent,jdk.naming.dns,jdk.naming.rmi,jdk.net,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.unsupported.desktop,jdk.zipfs,jdk.httpserver,jdk.localedata \
    --include-locales=en,zh,es,ar,fr,ru \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-9 \
    --output /opt/jre

# ============================================================
# Stage 3: Runtime（Alpine + 自定义 JRE）
# ============================================================
FROM alpine:3.20
WORKDIR /app

ENV JAVA_HOME=/opt/jre \
    PATH=/opt/jre/bin:${PATH} \
    LANG=en_US.UTF-8

# musl 兼容层 + 时区（Alpine 默认无 tzdata，Spring 日志/数据库时间会用 UTC）
RUN apk add --no-cache tzdata libstdc++ curl && \
    addgroup -S appuser && \
    adduser -S -G appuser -h /app appuser && \
    mkdir -p /app/data /app/wallet && \
    chown -R appuser:appuser /app

# 复制自定义 JRE 与应用 jar
COPY --from=jre-builder /opt/jre /opt/jre
COPY --from=builder --chown=appuser:appuser /app/build/libs/*.jar app.jar

USER appuser

EXPOSE 8082

# 内置 HEALTHCHECK 便于本地/非 K8s 场景；Kubernetes 仍以 deployment.yaml 中的 probe 为准
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8082/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
