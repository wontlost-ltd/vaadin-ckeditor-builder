# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# 复制 Gradle 配置（利用 Docker 缓存）
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle gradle.properties ./

# 复制 mavenLocal 中的 ckeditor-vaadin addon（未发布到远程仓库）
COPY .m2/ /root/.m2/

# 复制前端配置
COPY package.json package-lock.json tsconfig.json vite.config.ts vite.generated.ts ./

# 下载依赖（缓存层）
RUN ./gradlew dependencies --no-daemon || true

# 复制源码和前端资源
COPY src/ src/
COPY frontend/ frontend/
COPY types.d.ts ./

# 生产模式构建
RUN ./gradlew clean build -Pvaadin.productionMode --no-daemon

# Stage 2: Runtime (Alpine — 比 Ubuntu 基础镜像小约 180MB)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 创建非 root 用户（Alpine 用 addgroup/adduser）
RUN addgroup -S appuser && adduser -S -G appuser -h /app appuser

# 创建数据目录
RUN mkdir -p /app/data /app/wallet && chown -R appuser:appuser /app

# 复制构建产物
COPY --from=builder --chown=appuser:appuser /app/build/libs/*.jar app.jar

USER appuser

EXPOSE 8082

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
