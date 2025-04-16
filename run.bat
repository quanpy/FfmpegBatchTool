@echo off
echo Starting FFmpeg batch processing tool...
echo Please ensure that Java and FFmpeg are installed and that FFmpeg is added to the system PATH.

if exist "target\ffmpeg-batch-processor-1.0-SNAPSHOT.jar" (
    java -jar target\ffmpeg-batch-processor-1.0-SNAPSHOT.jar
) else (
    echo Building project...
    call mvn clean package
    if %ERRORLEVEL% == 0 (
        echo Build successful! Starting the application...
        java -jar target\ffmpeg-batch-processor-1.0-SNAPSHOT.jar
    ) else (
        echo Build failed. Please ensure Maven is installed and properly configured.
        pause
    )
) 