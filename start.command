#!/bin/bash
cd "$(dirname "$0")"  # 切换到脚本所在目录（可选，确保路径正确）
java -jar target/ffmpeg-batch-processor-1.0-SNAPSHOT.jar &


# mac
# 赋予执行权限
# chmod +x start.command

