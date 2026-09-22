# ===== 第一阶段：Maven 构建 =====
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app

# 先复制 pom.xml，利用 Docker 缓存层加速构建
COPY pom.xml .
# 创建 toolchains.xml + settings.xml（阿里云 Maven 镜像，解决服务器连不上国外 Maven 仓库的问题）
RUN mkdir -p /root/.m2 \
    && echo '<?xml version="1.0" encoding="UTF-8"?><toolchains><toolchain><type>jdk</type><provides><version>21</version></provides><configuration><jdkHome>/usr/lib/jvm/java-21-amazon-corretto</jdkHome></configuration></toolchain></toolchains>' > /root/.m2/toolchains.xml \
    && echo '<?xml version="1.0" encoding="UTF-8"?><settings><mirrors><mirror><id>aliyun</id><name>aliyun</name><mirrorOf>*</mirrorOf><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml
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

# 🔴 预创建持久化目录并授权给 appuser，否则非 root 用户无法写入，面试/复习进度无法保存。
# /app/data/evals 必须在这里（chown 之前）创建：named volume 首次挂载时会复制镜像里该目录的属主，
# 镜像里若有目录却被 Docker 以 root:root 创建挂载点，appuser 就写不进去，
# 而 EvaluationRecorder 的 @PostConstruct 只会打一行日志然后静默降级，极难排查。
RUN mkdir -p /app/.quiz-cursor /app/.ability-profiles /app/.review-cursor \
        /app/data/chat-memory /app/data/evals \
    && chown -R appuser:appgroup /app

# 切换到非 root 用户
USER appuser

EXPOSE 8123

CMD ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]