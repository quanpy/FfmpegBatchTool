# FFmpeg批量处理工具

一个简单的Java桌面应用程序，用于批量对文件夹中的媒体文件执行FFmpeg命令。

## 功能特点

- 选择文件夹路径（通过浏览按钮或直接输入）
- 自定义FFmpeg命令参数
- 显示处理进度条和状态
- 实时显示FFmpeg执行日志
- 自动检测常见媒体文件类型

## 使用方法

1. 启动应用程序
   - 双击`launch.vbs`可以直接启动应用程序（最简方式，推荐）
   - 双击`start.vbs`可以自动检测JAR文件是否存在，如不存在会询问是否构建
   - 双击`run.bat`可以在控制台窗口中启动应用程序
2. 在"文件夹路径"字段中输入要处理的文件夹路径，或点击"浏览..."按钮选择
3. 在"FFmpeg参数"字段中输入要应用的FFmpeg命令参数（默认为`-c:v libx264 -b:v 8000k -crf 23 -y`）
4. 点击"开始处理"按钮开始批量处理
5. 处理过程中会在日志区域显示FFmpeg的输出信息
6. 处理完成的文件会保存为原文件名+"_small"的形式

## 系统要求

- Java 11或更高版本
- 已安装FFmpeg并添加到系统PATH

## 构建和运行

使用Maven构建项目：

```
mvn clean package
```

运行生成的JAR文件：

```
java -jar target/ffmpeg-batch-processor-1.0-SNAPSHOT.jar
```

或使用无窗口启动（仅Windows）：

```
javaw -jar target/ffmpeg-batch-processor-1.0-SNAPSHOT.jar
```

## 问题排查

如果VBS脚本无法运行，可能是由于编码或权限问题，请尝试：

1. 使用`launch.vbs`（最简单的脚本）
2. 确保已经通过Maven构建了项目（`mvn clean package`）
3. 直接使用命令行运行：`javaw -jar target\ffmpeg-batch-processor-1.0-SNAPSHOT.jar`

## 注意事项

- 处理过程中会在原文件所在目录创建新文件
- 确保有足够的磁盘空间存储处理后的文件
- 处理大文件时可能需要等待较长时间 