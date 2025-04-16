# FFmpeg批量处理工具
## 批量转成 竖版 1080p命令
-vf "scale=1080:1920" -c:v h264_amf -b:v 8000k -c:a copy "${output_aac}" -y

## 