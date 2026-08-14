# ===== 第一阶段：Maven 构建 =====
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app

# 先复制 pom.xml，利用 Docker 缓存层加速构建
COPY pom.xml .
# 创建 toolchains.xml，满足 maven-toolchains-plugin 的要求
RUN mkdir -p /root/.m2 && echo '<?xml version="1.0" encoding="UTF-8"?><toolchains><toolchain><type>jdk</type><provides><version>21</version></provides><configuration><jdkHome>/usr/lib/jvm/java-21-amazon-corretto</jdkHome></configuration></toolchain></toolchains>' > /root/.m2/toolchains.xml
RUN mvn dependency:go-offline -B

# 再复制源码并打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== 第二阶段：运行环境 =====
FROM amazoncorretto:21-alpine
WORKDIR /app

# 创建非 root 用户运行应用
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从构建阶段复制 JAR
COPY --from=build /app/target/*.jar app.jar

# 🔴 预创建持久化目录并授权给 appuser，否则非 root 用户无法写入，面试/复习进度无法保存
RUN mkdir -p /app/.quiz-cursor /app/.ability-profiles /app/.review-cursor /app/data/chat-memory \
    && chown -R appuser:appgroup /app

# 切换到非 root 用户
USER appuser

EXPOSE 8123

CMD ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]