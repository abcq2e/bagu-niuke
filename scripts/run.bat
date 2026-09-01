@echo off
:: ============================================
:: qian-ai-agent 启动脚本
:: ============================================
:: 项目需要 JDK 21+，而系统默认 JAVA_HOME 指向 Java 8，
:: 因此这里显式设置 JDK 21 路径。
set JAVA_HOME=D:\Java

:: 切回仓库根目录，保证 mvnw 相对路径正确
cd /d "%~dp0.."

echo ============================================
echo   JAVA_HOME = %JAVA_HOME%
echo ============================================
"%JAVA_HOME%\bin\java" -version 2>&1
echo.

call mvnw spring-boot:run %*
