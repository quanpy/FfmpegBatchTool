package com.ffmpegui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public class FFmpegBatchProcessor extends JFrame {
    // 共享组件
    private JTextField folderPathField;
    private JButton browseButton;
    private JButton processButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea logArea;
    private ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private Timer logUpdateTimer;
    
    // 页面类型
    private enum PageType {
        COMPRESS("转小"),
        REMOVE_SUBTITLE("去小字"),
        REMOVE_TRAILER("去未完待续");
        
        private final String title;
        
        PageType(String title) {
            this.title = title;
        }
        
        public String getTitle() {
            return title;
        }
    }
    
    // 当前页面
    private PageType currentPage = PageType.COMPRESS;
    
    // 各页面的面板
    private JPanel compressPanel;
    private JPanel removeSubtitlePanel;
    private JPanel removeTrailerPanel;
    
    // 各页面的输入字段
    private JTextField compressParamsField;
    private JTextField subtitleDelogoParamsField;
    private JTextField subtitleCompressParamsField;
    private JTextField trailerDelogoParamsField;
    private JTextField trailerDurationField;
    private JTextField trailerCompressParamsField;
    
    // 页面容器
    private JPanel cardPanel;
    private CardLayout cardLayout;

    public FFmpegBatchProcessor() {
        // 设置窗口标题和关闭操作
        super("FFmpeg多功能批处理工具 @ocean.quan@wiitrans.com");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 620);
        setLocationRelativeTo(null);

        // 创建界面组件
        initComponents();
        
        // 布局组件
        layoutComponents();
        
        // 添加监听器
        addListeners();
        
        // 初始化日志更新计时器
        initLogUpdateTimer();
        
        // 默认显示第一个页面
        updateCurrentPage(PageType.COMPRESS);
    }

    private void initComponents() {
        // 初始化共享组件
        folderPathField = new JTextField(20);
        browseButton = new JButton("浏览...");
        processButton = new JButton("开始处理");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        statusLabel = new JLabel("就绪");
        logArea = new JTextArea();
        logArea.setEditable(false);
        
        // 初始化转小页面的输入字段
        compressParamsField = new JTextField("-c:v libx264 -b:v 8000k -crf 23 -y", 20);
        
        // 初始化去小字页面的输入字段
        subtitleDelogoParamsField = new JTextField(20);
        subtitleDelogoParamsField.setToolTipText("输入格式：x,y,w,h （例如：98,1169,879,155）");
        subtitleCompressParamsField = new JTextField("-c:v libx264 -b:v 8000k -crf 23 -y", 20);
        
        // 初始化去未完待续页面的输入字段
        trailerDelogoParamsField = new JTextField(20);
        trailerDelogoParamsField.setToolTipText("输入格式：x,y,w,h （例如：98,1169,879,155）");
        trailerDurationField = new JTextField("2.2", 20);
        trailerDurationField.setToolTipText("视频结尾处理时长（秒），如2.2表示处理视频最后2.2秒");
        trailerCompressParamsField = new JTextField("-c:v libx264 -b:v 8000k -crf 23 -y", 20);
        
        // 初始化页面布局管理器
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
    }
    
    private void initLogUpdateTimer() {
        // 创建一个定时器，定期从消息队列中获取消息并显示
        logUpdateTimer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateLogFromQueue();
            }
        });
        logUpdateTimer.start();
    }
    
    private void updateLogFromQueue() {
        if (!messageQueue.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            String message;
            while ((message = messageQueue.poll()) != null) {
                sb.append(message).append("\n");
            }
            
            final String logText = sb.toString();
            SwingUtilities.invokeLater(() -> {
                logArea.append(logText);
                // 自动滚动到底部
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }
    
    // 添加日志消息到队列
    private void addLogMessage(String message) {
        messageQueue.add(message);
    }

    private void layoutComponents() {
        // 创建顶部面板 - 文件夹路径
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        topPanel.add(new JLabel("文件夹路径:"), BorderLayout.WEST);
        topPanel.add(folderPathField, BorderLayout.CENTER);
        topPanel.add(browseButton, BorderLayout.EAST);
        
        // 创建切换页面的按钮面板
        JPanel tabButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (PageType pageType : PageType.values()) {
            JButton pageButton = new JButton(pageType.getTitle());
            pageButton.addActionListener(e -> updateCurrentPage(pageType));
            tabButtonPanel.add(pageButton);
        }
        
        // 创建转小页面
        compressPanel = createCompressPanel();
        
        // 创建去小字页面
        removeSubtitlePanel = createRemoveSubtitlePanel();
        
        // 创建去未完待续页面
        removeTrailerPanel = createRemoveTrailerPanel();
        
        // 添加页面到卡片布局
        cardPanel.add(compressPanel, PageType.COMPRESS.name());
        cardPanel.add(removeSubtitlePanel, PageType.REMOVE_SUBTITLE.name());
        cardPanel.add(removeTrailerPanel, PageType.REMOVE_TRAILER.name());
        
        // 创建控制面板（包含按钮和状态）
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(processButton);
        
        // 创建状态面板
        JPanel statusPanel = new JPanel(new BorderLayout(5, 0));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        
        // 创建底部面板（合并按钮和状态面板）
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(statusPanel, BorderLayout.SOUTH);
        
        // 创建日志区域
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        // 创建功能页面顶部面板（包含标签页按钮和卡片面板）
        JPanel functionTopPanel = new JPanel(new BorderLayout());
        functionTopPanel.add(tabButtonPanel, BorderLayout.NORTH);
        functionTopPanel.add(cardPanel, BorderLayout.CENTER);
        
        // 将所有组件添加到窗口
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(functionTopPanel, BorderLayout.CENTER);
        
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainPanel, BorderLayout.NORTH);
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createCompressPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 命令面板 - FFmpeg命令
        JPanel commandPanel = new JPanel(new BorderLayout(5, 0));
        commandPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        commandPanel.add(new JLabel("压缩参数:"), BorderLayout.WEST);
        commandPanel.add(compressParamsField, BorderLayout.CENTER);
        
        panel.add(commandPanel, BorderLayout.NORTH);
        return panel;
    }
    
    private JPanel createRemoveSubtitlePanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 5));
        
        // 压缩参数面板（移到上面）
        JPanel compressPanel = new JPanel(new BorderLayout(5, 0));
        compressPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        compressPanel.add(new JLabel("压缩参数:"), BorderLayout.WEST);
        compressPanel.add(subtitleCompressParamsField, BorderLayout.CENTER);
        
        // 去水印参数面板
        JPanel delogoPanel = new JPanel(new BorderLayout(5, 0));
        delogoPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        delogoPanel.add(new JLabel("去小字参数(x,y,w,h):"), BorderLayout.WEST);
        delogoPanel.add(subtitleDelogoParamsField, BorderLayout.CENTER);
        
        panel.add(compressPanel);
        panel.add(delogoPanel);
        return panel;
    }
    
    private JPanel createRemoveTrailerPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 0, 5));
        
        // 压缩参数面板（移到上面）
        JPanel compressPanel = new JPanel(new BorderLayout(5, 0));
        compressPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        compressPanel.add(new JLabel("压缩参数:"), BorderLayout.WEST);
        compressPanel.add(trailerCompressParamsField, BorderLayout.CENTER);
        
        // 去水印参数面板
        JPanel delogoPanel = new JPanel(new BorderLayout(5, 0));
        delogoPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        delogoPanel.add(new JLabel("去未完待续参数(x,y,w,h):"), BorderLayout.WEST);
        delogoPanel.add(trailerDelogoParamsField, BorderLayout.CENTER);
        
        // 结尾处理时长面板
        JPanel durationPanel = new JPanel(new BorderLayout(5, 0));
        durationPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        durationPanel.add(new JLabel("结尾处理时长(秒):"), BorderLayout.WEST);
        durationPanel.add(trailerDurationField, BorderLayout.CENTER);
        
        panel.add(compressPanel);
        panel.add(delogoPanel);
        panel.add(durationPanel);
        return panel;
    }
    
    private void updateCurrentPage(PageType pageType) {
        currentPage = pageType;
        cardLayout.show(cardPanel, pageType.name());
        setTitle("FFmpeg多功能批处理工具 - " + pageType.getTitle() + " - @ocean.quan@wiitrans.com");
        
        // 根据当前页面更新处理按钮文本
        processButton.setText("开始" + pageType.getTitle());
    }

    private void addListeners() {
        // 浏览按钮监听器
        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int result = fileChooser.showOpenDialog(FFmpegBatchProcessor.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFolder = fileChooser.getSelectedFile();
                    folderPathField.setText(selectedFolder.getAbsolutePath());
                }
            }
        });
        
        // 处理按钮监听器
        processButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String folderPath = folderPathField.getText().trim();
                if (folderPath.isEmpty()) {
                    JOptionPane.showMessageDialog(FFmpegBatchProcessor.this, 
                        "请输入有效的文件夹路径", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // 根据当前页面执行不同的处理
                switch (currentPage) {
                    case COMPRESS:
                        processCompress(folderPath);
                        break;
                    case REMOVE_SUBTITLE:
                        processRemoveSubtitle(folderPath);
                        break;
                    case REMOVE_TRAILER:
                        processRemoveTrailer(folderPath);
                        break;
                }
            }
        });
    }
    
    private void processCompress(String folderPath) {
        String ffmpegCommand = compressParamsField.getText().trim();
        
        // 禁用按钮防止重复点击
        processButton.setEnabled(false);
        
        // 清空日志
        logArea.setText("");
        
        addLogMessage("开始压缩处理...");
        
        // 在后台线程中执行处理
        new Thread(() -> {
            try {
                processFiles(folderPath, ffmpegCommand, "", "", "c");
            } finally {
                SwingUtilities.invokeLater(() -> {
                    processButton.setEnabled(true);
                    statusLabel.setText("处理完成");
                    progressBar.setValue(100);
                });
            }
        }).start();
    }
    
    private void processRemoveSubtitle(String folderPath) {
        String delogoParams = subtitleDelogoParamsField.getText().trim();
        String ffmpegCommand = subtitleCompressParamsField.getText().trim();
        
        // 验证去水印参数格式
        if (!delogoParams.isEmpty() && !isValidDelogoParams(delogoParams)) {
            JOptionPane.showMessageDialog(this, 
                "去小字参数格式不正确，请使用x,y,w,h格式（例如：98,1169,879,155）", 
                "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 禁用按钮防止重复点击
        processButton.setEnabled(false);
        
        // 清空日志
        logArea.setText("");
        
        addLogMessage("开始去小字处理...");
        
        // 在后台线程中执行处理
        new Thread(() -> {
            try {
                processFiles(folderPath, ffmpegCommand, delogoParams, "", "s");
            } finally {
                SwingUtilities.invokeLater(() -> {
                    processButton.setEnabled(true);
                    statusLabel.setText("处理完成");
                    progressBar.setValue(100);
                });
            }
        }).start();
    }
    
    private void processRemoveTrailer(String folderPath) {
        String delogoParams = trailerDelogoParamsField.getText().trim();
        String lastDuration = trailerDurationField.getText().trim();
        String ffmpegCommand = trailerCompressParamsField.getText().trim();
        
        // 验证去水印参数格式
        if (!delogoParams.isEmpty() && !isValidDelogoParams(delogoParams)) {
            JOptionPane.showMessageDialog(this, 
                "去未完待续参数格式不正确，请使用x,y,w,h格式（例如：98,1169,879,155）", 
                "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 验证结尾处理时长格式
        if (!lastDuration.isEmpty()) {
            try {
                Double.parseDouble(lastDuration);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, 
                    "结尾处理时长必须是有效的数字（秒）", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        // 禁用按钮防止重复点击
        processButton.setEnabled(false);
        
        // 清空日志
        logArea.setText("");
        
        addLogMessage("开始去未完待续处理...");
        
        // 在后台线程中执行处理
        new Thread(() -> {
            try {
                processFiles(folderPath, ffmpegCommand, delogoParams, lastDuration, "w");
            } finally {
                SwingUtilities.invokeLater(() -> {
                    processButton.setEnabled(true);
                    statusLabel.setText("处理完成");
                    progressBar.setValue(100);
                });
            }
        }).start();
    }
    
    private boolean isValidDelogoParams(String params) {
        // 检查格式是否为四个数字，用逗号分隔
        String regex = "\\d+,\\d+,\\d+,\\d+";
        return Pattern.matches(regex, params);
    }
    
    private void processFiles(String folderPath, String ffmpegArgs, String delogoParams, 
                             String lastDuration, String outputSuffix) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, 
                    "指定的路径不是有效的文件夹", "错误", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("错误：无效的文件夹路径");
                processButton.setEnabled(true);
            });
            return;
        }
        
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, 
                    "文件夹为空，没有要处理的文件", "警告", JOptionPane.WARNING_MESSAGE);
                statusLabel.setText("警告：文件夹为空");
                processButton.setEnabled(true);
            });
            return;
        }
        
        // 过滤出媒体文件
        List<File> mediaFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && isMediaFile(file.getName())) {
                mediaFiles.add(file);
            }
        }
        
        if (mediaFiles.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, 
                    "文件夹中没有找到媒体文件", "警告", JOptionPane.WARNING_MESSAGE);
                statusLabel.setText("警告：没有找到媒体文件");
                processButton.setEnabled(true);
            });
            return;
        }
        
        // 设置进度条
        SwingUtilities.invokeLater(() -> {
            progressBar.setMaximum(mediaFiles.size());
            progressBar.setValue(0);
        });
        
        // 处理每个文件
        int count = 0;
        for (File file : mediaFiles) {
            final int currentCount = ++count;
            final String fileName = file.getName();
            
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("处理中: " + fileName + " (" + currentCount + "/" + mediaFiles.size() + ")");
                progressBar.setValue(currentCount - 1);
            });
            
            try {
                processFile(file, ffmpegArgs, delogoParams, lastDuration, outputSuffix);
            } catch (Exception e) {
                final String errorMessage = "处理文件 " + fileName + " 时出错: " + e.getMessage();
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("错误: " + fileName);
                });
                addLogMessage(errorMessage);
                addLogMessage(e.toString());
                for (StackTraceElement element : e.getStackTrace()) {
                    addLogMessage(element.toString());
                }
            }
            
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(currentCount);
            });
        }
    }
    
    private String getVideoDuration(String inputPath) throws Exception {
        // 构建ffprobe命令获取视频时长
        List<String> command = new ArrayList<>();
        command.add("ffprobe");
        command.add("-i");
        command.add(inputPath);
        command.add("-show_entries");
        command.add("format=duration");
        command.add("-v");
        command.add("quiet");
        command.add("-of");
        command.add("csv=p=0");
        
        // 显示构建的命令
        StringBuilder cmdLine = new StringBuilder();
        for (String part : command) {
            cmdLine.append(part).append(" ");
        }
        addLogMessage("执行命令: " + cmdLine.toString());
        
        // 执行命令
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // 读取输出
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String duration = reader.readLine();
        
        // 等待进程结束
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("FFprobe进程返回错误代码: " + exitCode);
        }
        
        if (duration == null || duration.trim().isEmpty()) {
            throw new Exception("无法获取视频时长");
        }
        
        addLogMessage("视频时长: " + duration + " 秒");
        return duration.trim();
    }
    
    private void processFile(File inputFile, String ffmpegArgs, String delogoParams, 
                            String lastDuration, String outputSuffix) throws Exception {
        String inputPath = inputFile.getAbsolutePath();
        String outputPath = generateOutputPath(inputPath, outputSuffix);
        
        // 如果指定了结尾处理时长，获取视频总时长
        String endTime = null;
        if (!lastDuration.isEmpty() && !delogoParams.isEmpty()) {
            endTime = getVideoDuration(inputPath);
            addLogMessage("视频总时长: " + endTime);
        }
        
        // 构建FFmpeg命令
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-i");
        command.add(inputPath);
        
        // 添加去水印参数（如果提供）
        if (!delogoParams.isEmpty()) {
            String[] params = delogoParams.split(",");
            if (params.length == 4) {
                String x = params[0];
                String y = params[1];
                String w = params[2];
                String h = params[3];
                
                // 根据是否指定了结尾处理时长来构建不同的delogo参数
                String delogoFilter;
                if (endTime != null && !lastDuration.isEmpty()) {
                    double duration = Double.parseDouble(endTime);
                    double lastDurationValue = Double.parseDouble(lastDuration);
                    double startTime = Math.max(0, duration - lastDurationValue);
                    
                    addLogMessage(String.format("应用水印去除：从 %.2f 秒到 %.2f 秒", startTime, duration));
                    
                    delogoFilter = String.format(
                        "\"delogo=x=%d:y=%d:w=%d:h=%d:enable='between(t,%.2f,%.2f)'\"", 
                        Integer.parseInt(x),
                        Integer.parseInt(y),
                        Integer.parseInt(w),
                        Integer.parseInt(h),
                        startTime,
                        duration
                    );
                } else {
                    delogoFilter = String.format(
                        "\"delogo=x=%d:y=%d:w=%d:h=%d\"", 
                        Integer.parseInt(x),
                        Integer.parseInt(y),
                        Integer.parseInt(w),
                        Integer.parseInt(h)
                    );
                }
                
                command.add("-vf");
                command.add(delogoFilter);
            }
        }
        
        // 添加用户指定的参数
        String[] args = ffmpegArgs.split("\\s+");
        for (String arg : args) {
            if (!arg.trim().isEmpty()) {
                command.add(arg.trim());
            }
        }
        
        command.add(outputPath);
        
        // 显示构建的命令
        StringBuilder cmdLine = new StringBuilder();
        for (String part : command) {
            cmdLine.append(part).append(" ");
        }
        addLogMessage("执行命令: " + cmdLine.toString());
        
        // 执行命令
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        // 读取和显示输出
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            addLogMessage(line);
        }
        
        // 等待进程结束
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("FFmpeg进程返回错误代码: " + exitCode);
        }
        
        addLogMessage("成功处理文件: " + inputFile.getName());
    }
    
    private String generateOutputPath(String inputPath, String suffix) {
        int dotIndex = inputPath.lastIndexOf('.');
        if (dotIndex > 0) {
            String basePath = inputPath.substring(0, dotIndex);
            String extension = inputPath.substring(dotIndex);
            return basePath + "_" + suffix + extension;
        } else {
            return inputPath + "_" + suffix;
        }
    }
    
    private boolean isMediaFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".mp4") || lowerName.endsWith(".avi") || 
               lowerName.endsWith(".mkv") || lowerName.endsWith(".mov") || 
               lowerName.endsWith(".wmv") || lowerName.endsWith(".flv") ||
               lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
               lowerName.endsWith(".webm") || lowerName.endsWith(".m4a");
    }
    
    public static void main(String[] args) {
        // 在EDT中运行GUI
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    // 设置外观为系统外观
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                FFmpegBatchProcessor app = new FFmpegBatchProcessor();
                app.setVisible(true);
            }
        });
    }
} 