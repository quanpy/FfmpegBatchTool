@echo off
echo 正在启动FFmpeg批量处理工具...
echo 请确保已安装Java和FFmpeg，并且FFmpeg已添加到系统PATH中

if exist "target\ffmpeg-batch-processor-1.0-SNAPSHOT.jar" (
    java -jar target\ffmpeg-batch-processor-1.0-SNAPSHOT.jar
) else (
    echo 正在构建项目...
    call mvn clean package
    if %ERRORLEVEL% == 0 (
        echo 构建成功！正在启动应用程序...
        java -jar target\ffmpeg-batch-processor-1.0-SNAPSHOT.jar
    ) else (
        echo 构建失败，请确保已安装Maven并正确配置。
        pause
    )
) 