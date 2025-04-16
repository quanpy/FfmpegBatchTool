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

public class FFmpegBatchProcessor extends JFrame {
    private JTextField folderPathField;
    private JTextField ffmpegCommandField;
    private JTextArea logArea;
    private JButton browseButton;
    private JButton processButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private SwingWorker<Void, String> currentWorker;
    private ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();
    private Timer logUpdateTimer;

    public FFmpegBatchProcessor() {
        // 设置窗口标题和关闭操作
        super("FFmpeg批量缩小处理工具 @ocean.quan@wiitrans.com");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // 创建界面组件
        initComponents();
        
        // 布局组件
        layoutComponents();
        
        // 添加监听器
        addListeners();
        
        // 初始化日志更新计时器
        initLogUpdateTimer();
    }

    private void initComponents() {
        folderPathField = new JTextField(20);
        ffmpegCommandField = new JTextField("-c:v libx264 -b:v 8000k -crf 23 -y", 20);
        logArea = new JTextArea();
        logArea.setEditable(false);
        browseButton = new JButton("浏览...");
        processButton = new JButton("开始处理");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        statusLabel = new JLabel("就绪");
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
        // 顶部面板 - 文件夹路径
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        topPanel.add(new JLabel("文件夹路径:"), BorderLayout.WEST);
        topPanel.add(folderPathField, BorderLayout.CENTER);
        topPanel.add(browseButton, BorderLayout.EAST);

        // 命令面板 - FFmpeg命令
        JPanel commandPanel = new JPanel(new BorderLayout(5, 0));
        commandPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        commandPanel.add(new JLabel("FFmpeg参数:"), BorderLayout.WEST);
        commandPanel.add(ffmpegCommandField, BorderLayout.CENTER);
        
        // 日志区域
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        // 控制面板（包含按钮和状态）
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(processButton);
        
        // 状态面板
        JPanel statusPanel = new JPanel(new BorderLayout(5, 0));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        
        // 底部面板（合并按钮和状态面板）
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(statusPanel, BorderLayout.SOUTH);
        
        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(commandPanel, BorderLayout.CENTER);
        
        // 将所有组件添加到窗口
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainPanel, BorderLayout.NORTH);
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);
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
                
                String ffmpegCommand = ffmpegCommandField.getText().trim();
                
                // 禁用按钮防止重复点击
                processButton.setEnabled(false);
                
                // 清空日志
                logArea.setText("");
                
                // 在后台线程中执行处理
                new Thread(() -> {
                    try {
                        processFiles(folderPath, ffmpegCommand);
                    } finally {
                        SwingUtilities.invokeLater(() -> {
                            processButton.setEnabled(true);
                            statusLabel.setText("处理完成");
                            progressBar.setValue(100);
                        });
                    }
                }).start();
            }
        });
    }
    
    private void processFiles(String folderPath, String ffmpegArgs) {
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
                processFile(file, ffmpegArgs);
            } catch (Exception e) {
                final String errorMessage = "处理文件 " + fileName + " 时出错: " + e.getMessage();
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("错误: " + fileName);
                });
                addLogMessage(errorMessage);
            }
            
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(currentCount);
            });
        }
    }
    
    private void processFile(File inputFile, String ffmpegArgs) throws Exception {
        String inputPath = inputFile.getAbsolutePath();
        String outputPath = generateOutputPath(inputPath);
        
        // 构建FFmpeg命令
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-i");
        command.add(inputPath);
        
        // 添加用户指定的参数
        String[] args = ffmpegArgs.split("\\s+");
        for (String arg : args) {
            if (!arg.trim().isEmpty()) {
                command.add(arg.trim());
            }
        }
        
        command.add(outputPath);
        
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
    
    private String generateOutputPath(String inputPath) {
        int dotIndex = inputPath.lastIndexOf('.');
        if (dotIndex > 0) {
            String basePath = inputPath.substring(0, dotIndex);
            String extension = inputPath.substring(dotIndex);
            return basePath + "_small" + extension;
        } else {
            return inputPath + "_small";
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
                FFmpegBatchProcessor app = new FFmpegBatchProcessor();
                app.setVisible(true);
            }
        });
    }
} 