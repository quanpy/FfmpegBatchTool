package com.ffmpegui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import java.util.Map;

public class FFmpegBatchProcessor extends JFrame {
    // 主题颜色
    private static final Color PRIMARY_COLOR = new Color(48, 63, 159); // 深蓝
    private static final Color SECONDARY_COLOR = new Color(63, 81, 181); // 蓝紫
    private static final Color ACCENT_COLOR = new Color(197, 202, 233); // 淡蓝
    private static final Color BACKGROUND_COLOR = new Color(237, 240, 252); // 浅蓝背景
    private static final Color BUTTON_HOVER_COLOR = new Color(92, 107, 192); // 悬停颜色
    private static final Color TEXT_COLOR = new Color(33, 33, 33); // 深灰
    private static final Color HIGHLIGHT_COLOR = new Color(255, 87, 34); // 橙色高亮
    
    // 字体
    private static final Font TITLE_FONT = new Font("微软雅黑", Font.BOLD, 14);
    private static final Font NORMAL_FONT = new Font("微软雅黑", Font.PLAIN, 12);
    private static final Font BUTTON_FONT = new Font("微软雅黑", Font.BOLD, 12);
    
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
    
    // 默认的压缩参数
    private static final String DEFAULT_COMPRESS_PARAMS = "-c:v libx264 -b:v 8000k -crf 23 -y";
    
    // 记录类型存储解析后的坐标参数
    private record DelogoParams(int x, int y, int width, int height) {
        public static DelogoParams parse(String params) {
            String[] parts = params.split(",");
            return new DelogoParams(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3])
            );
        }
    }

    public FFmpegBatchProcessor() {
        // 设置窗口标题和关闭操作
        super("FFmpeg多功能批处理工具 @ocean.quan@wiitrans.com");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 650);
        setLocationRelativeTo(null);
        
        // 设置窗口图标
        try {
            ImageIcon icon = new ImageIcon(getClass().getResource("/icon.png"));
            if (icon.getImageLoadStatus() == MediaTracker.COMPLETE) {
                setIconImage(icon.getImage());
            } else {
                System.out.println("提示：未找到图标文件，请在 src/main/resources 目录下添加 icon.png 文件");
            }
        } catch (Exception e) {
            System.out.println("加载图标时出错: " + e.getMessage());
        }

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
        
        // 设置窗口背景颜色
        getContentPane().setBackground(BACKGROUND_COLOR);
    }

    private void initComponents() {
        // 初始化共享组件
        folderPathField = createStyledTextField();
        browseButton = createStyledButton("浏览...");
        processButton = createStyledButton("开始处理");
        processButton.setBackground(PRIMARY_COLOR);
        processButton.setForeground(Color.WHITE);
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(PRIMARY_COLOR);
        progressBar.setBackground(ACCENT_COLOR);
        
        statusLabel = new JLabel("就绪");
        statusLabel.setFont(NORMAL_FONT);
        statusLabel.setForeground(TEXT_COLOR);
        
        // 使用支持中文显示良好的字体
        Font logFont = new Font("Microsoft YaHei Mono", Font.PLAIN, 12);
        if (isFontAvailable("Microsoft YaHei Mono")) {
            logFont = new Font("Microsoft YaHei Mono", Font.PLAIN, 12);
        } else if (isFontAvailable("微软雅黑")) {
            logFont = new Font("微软雅黑", Font.PLAIN, 12);
        } else if (isFontAvailable("宋体")) {
            logFont = new Font("宋体", Font.PLAIN, 12);
        } else {
            logFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        }
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(logFont);
        logArea.setBackground(new Color(250, 250, 250));
        logArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // 初始化转小页面的输入字段
        compressParamsField = createStyledTextField();
        compressParamsField.setText(DEFAULT_COMPRESS_PARAMS);
        
        // 初始化去小字页面的输入字段
        subtitleDelogoParamsField = createStyledTextField();
        subtitleDelogoParamsField.setToolTipText("输入格式：x,y,w,h （例如：98,1169,879,155）注意：涂抹边界不要紧贴视频边界");
        subtitleCompressParamsField = createStyledTextField();
        subtitleCompressParamsField.setText(DEFAULT_COMPRESS_PARAMS);
        
        // 初始化去未完待续页面的输入字段
        trailerDelogoParamsField = createStyledTextField();
        trailerDelogoParamsField.setToolTipText("输入格式：x,y,w,h （例如：98,1169,879,155）");
        trailerDurationField = createStyledTextField();
        trailerDurationField.setText("2.2");
        trailerDurationField.setToolTipText("视频结尾处理时长（秒），如2.2表示处理视频最后2.2秒");
        trailerCompressParamsField = createStyledTextField();
        trailerCompressParamsField.setText(DEFAULT_COMPRESS_PARAMS);
        
        // 初始化页面布局管理器
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);
    }
    
    // 创建风格化的文本框
    private JTextField createStyledTextField() {
        JTextField field = new JTextField(20);
        field.setFont(NORMAL_FONT);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_COLOR, 1, true),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        return field;
    }
    
    // 创建风格化的按钮
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (getModel().isPressed()) {
                    g2.setColor(SECONDARY_COLOR.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(BUTTON_HOVER_COLOR);
                } else {
                    g2.setColor(getBackground());
                }
                
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                
                g2.dispose();
            }
        };
        
        button.setFont(BUTTON_FONT);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setBackground(SECONDARY_COLOR);
        button.setForeground(ACCENT_COLOR);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(8, 15, 8, 15));
        
        return button;
    }
    
    // 创建风格化的标签
    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(NORMAL_FONT);
        label.setForeground(TEXT_COLOR);
        return label;
    }
    
    private void initLogUpdateTimer() {
        // 创建一个定时器，定期从消息队列中获取消息并显示
        logUpdateTimer = new Timer(100, this::updateLogFromQueue);
        logUpdateTimer.start();
    }
    
    private void updateLogFromQueue(ActionEvent e) {
        if (!messageQueue.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            String message;
            while ((message = messageQueue.poll()) != null) {
                sb.append(message).append("\n");
            }
            
            final String logText = sb.toString();
            SwingUtilities.invokeLater(() -> {
                // 将文本转换为UTF-8编码处理，避免显示乱码
                try {
                    String normalizedText = new String(logText.getBytes("UTF-8"), "UTF-8");
                    logArea.append(normalizedText);
                } catch (Exception ex) {
                    // 如果转换失败，直接添加原始文本
                    logArea.append(logText);
                }
                
                // 自动滚动到底部
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }
    
    // 添加日志消息到队列
    private void addLogMessage(String message) {
        // 确保日志消息使用UTF-8编码
        try {
            String normalizedMessage = new String(message.getBytes("UTF-8"), "UTF-8");
            messageQueue.add(normalizedMessage);
        } catch (Exception e) {
            // 如果转换失败，直接添加原始消息
            messageQueue.add(message);
        }
    }

    private void layoutComponents() {
        // 创建主面板，使用边框布局
        JPanel mainPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                
                // 创建渐变背景
                GradientPaint gp = new GradientPaint(
                    0, 0, BACKGROUND_COLOR, 
                    0, getHeight(), new Color(220, 225, 245)
                );
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        mainPanel.setOpaque(false);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // 创建顶部面板 - 文件夹路径
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setOpaque(false);
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 15, 10));
        
        JLabel pathLabel = createStyledLabel("文件夹路径:");
        topPanel.add(pathLabel, BorderLayout.WEST);
        topPanel.add(folderPathField, BorderLayout.CENTER);
        topPanel.add(browseButton, BorderLayout.EAST);
        
        // 创建切换页面的按钮面板
        JPanel tabButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        tabButtonPanel.setOpaque(false);
        tabButtonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        
        for (PageType pageType : PageType.values()) {
            JButton pageButton = createStyledButton(pageType.getTitle());
            pageButton.setMargin(new Insets(8, 25, 8, 25));
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
        buttonPanel.setOpaque(false);
        buttonPanel.add(processButton);
        
        // 创建状态面板
        JPanel statusPanel = new JPanel(new BorderLayout(10, 0));
        statusPanel.setOpaque(false);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        
        // 创建底部面板（合并按钮和状态面板）
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(statusPanel, BorderLayout.SOUTH);
        
        // 创建日志区域带标题
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setOpaque(false);
        
        JLabel logLabel = new JLabel("处理日志");
        logLabel.setFont(TITLE_FONT);
        logLabel.setForeground(PRIMARY_COLOR);
        logLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_COLOR, 1, true),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        
        logPanel.add(logLabel, BorderLayout.NORTH);
        logPanel.add(scrollPane, BorderLayout.CENTER);
        logPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 创建功能页面顶部面板（包含标签页按钮和卡片面板）
        JPanel functionTopPanel = new JPanel(new BorderLayout());
        functionTopPanel.setOpaque(false);
        functionTopPanel.add(tabButtonPanel, BorderLayout.NORTH);
        functionTopPanel.add(cardPanel, BorderLayout.CENTER);
        
        // 将所有组件添加到主面板
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(functionTopPanel, BorderLayout.CENTER);
        
        // 将主面板和日志面板添加到内容窗格
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainPanel, BorderLayout.NORTH);
        getContentPane().add(logPanel, BorderLayout.CENTER);
        getContentPane().add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createCompressPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        
        // 命令面板 - FFmpeg命令
        JPanel commandPanel = new JPanel(new BorderLayout(10, 0));
        commandPanel.setOpaque(false);
        commandPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));
        commandPanel.add(createStyledLabel("压缩参数:"), BorderLayout.WEST);
        commandPanel.add(compressParamsField, BorderLayout.CENTER);
        
        // 添加说明面板
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setOpaque(false);
        descPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel descLabel = new JLabel("此功能用于压缩视频文件，处理后的文件会在原文件名后添加\"_c\"后缀");
        descLabel.setFont(NORMAL_FONT);
        descLabel.setForeground(new Color(90, 90, 90));
        descPanel.add(descLabel, BorderLayout.CENTER);
        
        panel.add(commandPanel, BorderLayout.NORTH);
        panel.add(descPanel, BorderLayout.CENTER);
        return panel;
    }
    
    private JPanel createRemoveSubtitlePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        
        JPanel inputsPanel = new JPanel(new GridLayout(2, 1, 0, 10));
        inputsPanel.setOpaque(false);
        inputsPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));
        
        // 压缩参数面板
        JPanel compressPanel = new JPanel(new BorderLayout(10, 0));
        compressPanel.setOpaque(false);
        compressPanel.add(createStyledLabel("压缩参数:"), BorderLayout.WEST);
        compressPanel.add(subtitleCompressParamsField, BorderLayout.CENTER);
        
        // 去水印参数面板
        JPanel delogoPanel = new JPanel(new BorderLayout(10, 0));
        delogoPanel.setOpaque(false);
        delogoPanel.add(createStyledLabel("去小字参数(x,y,w,h):"), BorderLayout.WEST);
        delogoPanel.add(subtitleDelogoParamsField, BorderLayout.CENTER);
        
        inputsPanel.add(compressPanel);
        inputsPanel.add(delogoPanel);
        
        // 提示面板
        JPanel tipPanel = new JPanel(new BorderLayout(5, 0));
        tipPanel.setOpaque(false);
        tipPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ACCENT_COLOR),
            BorderFactory.createEmptyBorder(15, 10, 10, 10)
        ));
        
        JLabel tipLabel = new JLabel("提示：涂抹边界不要紧贴视频边界，留出一点点距离，否则会报错！");
        tipLabel.setFont(NORMAL_FONT);
        tipLabel.setForeground(new Color(32, 61, 135));
        tipLabel.setIcon(createInfoIcon());
        tipPanel.add(tipLabel, BorderLayout.CENTER);
        
        // 添加说明面板
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setOpaque(false);
        descPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        JLabel descLabel = new JLabel("此功能用于去除视频中的小型水印，处理后的文件会添加\"_s\"后缀");
        descLabel.setFont(NORMAL_FONT);
        descLabel.setForeground(new Color(90, 90, 90));
        descPanel.add(descLabel, BorderLayout.CENTER);
        
        panel.add(inputsPanel, BorderLayout.NORTH);
        panel.add(descPanel, BorderLayout.CENTER);
        panel.add(tipPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createRemoveTrailerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        
        JPanel inputsPanel = new JPanel(new GridLayout(3, 1, 0, 10));
        inputsPanel.setOpaque(false);
        inputsPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));
        
        // 压缩参数面板
        JPanel compressPanel = new JPanel(new BorderLayout(10, 0));
        compressPanel.setOpaque(false);
        compressPanel.add(createStyledLabel("压缩参数:"), BorderLayout.WEST);
        compressPanel.add(trailerCompressParamsField, BorderLayout.CENTER);
        
        // 去水印参数面板
        JPanel delogoPanel = new JPanel(new BorderLayout(10, 0));
        delogoPanel.setOpaque(false);
        delogoPanel.add(createStyledLabel("去未完待续参数(x,y,w,h):"), BorderLayout.WEST);
        delogoPanel.add(trailerDelogoParamsField, BorderLayout.CENTER);
        
        // 结尾处理时长面板
        JPanel durationPanel = new JPanel(new BorderLayout(10, 0));
        durationPanel.setOpaque(false);
        durationPanel.add(createStyledLabel("结尾处理时长(秒):"), BorderLayout.WEST);
        durationPanel.add(trailerDurationField, BorderLayout.CENTER);
        
        inputsPanel.add(compressPanel);
        inputsPanel.add(delogoPanel);
        inputsPanel.add(durationPanel);
        
        // 添加说明面板
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setOpaque(false);
        descPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel descLabel = new JLabel("此功能用于去除视频末尾的\"未完待续\"等水印，处理后的文件会添加\"_w\"后缀");
        descLabel.setFont(NORMAL_FONT);
        descLabel.setForeground(new Color(90, 90, 90));
        descPanel.add(descLabel, BorderLayout.CENTER);
        
        panel.add(inputsPanel, BorderLayout.NORTH);
        panel.add(descPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private ImageIcon createInfoIcon() {
        // 创建信息图标
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 绘制圆形
        g2.setColor(new Color(32, 61, 135));
        g2.fillOval(0, 0, size, size);
        
        // 绘制"i"
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("i", (size - fm.stringWidth("i")) / 2, size - 4);
        
        g2.dispose();
        return new ImageIcon(image);
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
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = fileChooser.showOpenDialog(FFmpegBatchProcessor.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFolder = fileChooser.getSelectedFile();
                folderPathField.setText(selectedFolder.getAbsolutePath());
            }
        });
        
        // 处理按钮监听器
        processButton.addActionListener(e -> {
            String folderPath = folderPathField.getText().trim();
            if (folderPath.isEmpty()) {
                JOptionPane.showMessageDialog(FFmpegBatchProcessor.this, 
                    "请输入有效的文件夹路径", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 根据当前页面执行不同的处理
            switch (currentPage) {
                case COMPRESS -> processCompress(folderPath);
                case REMOVE_SUBTITLE -> processRemoveSubtitle(folderPath);
                case REMOVE_TRAILER -> processRemoveTrailer(folderPath);
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
        String cmdLine = String.join(" ", command);
        addLogMessage("执行命令: " + cmdLine);
        
        // 执行命令
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        // 设置环境变量，确保正确处理中文路径和输出
        Map<String, String> env = pb.environment();
        env.put("LC_ALL", "zh_CN.UTF-8");
        env.put("PYTHONIOENCODING", "utf-8");
        
        Process process = pb.start();
        
        // 读取输出
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
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
            try {
                DelogoParams params = DelogoParams.parse(delogoParams);
                
                // 根据是否指定了结尾处理时长来构建不同的delogo参数
                String delogoFilter;
                if (endTime != null && !lastDuration.isEmpty()) {
                    double duration = Double.parseDouble(endTime);
                    double lastDurationValue = Double.parseDouble(lastDuration);
                    double startTime = Math.max(0, duration - lastDurationValue);
                    
                    addLogMessage(String.format("应用水印去除：从 %.2f 秒到 %.2f 秒", startTime, duration));
                    
                    delogoFilter = """
                        "delogo=x=%d:y=%d:w=%d:h=%d:enable='between(t,%.2f,%.2f)'" """
                            .formatted(params.x(), params.y(), params.width(), params.height(), startTime, duration);
                } else {
                    delogoFilter = """
                        "delogo=x=%d:y=%d:w=%d:h=%d" """.formatted(params.x(), params.y(), params.width(), params.height());
                }
                
                command.add("-vf");
                command.add(delogoFilter);
            } catch (Exception e) {
                addLogMessage("解析去水印参数时出错: " + e.getMessage());
                throw e;
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
        String cmdLine = String.join(" ", command);
        addLogMessage("执行命令: " + cmdLine);
        
        // 执行命令
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        // 设置环境变量，确保正确处理中文路径和输出
        Map<String, String> env = pb.environment();
        env.put("LC_ALL", "zh_CN.UTF-8");
        env.put("PYTHONIOENCODING", "utf-8");
        
        Process process = pb.start();
        
        // 读取和显示输出
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
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
    
    // 检查字体是否可用
    private boolean isFontAvailable(String fontName) {
        Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        for (Font font : fonts) {
            if (font.getName().equals(fontName)) {
                return true;
            }
        }
        return false;
    }
    
    public static void main(String[] args) {
        // 在EDT中运行GUI
        SwingUtilities.invokeLater(() -> {
            try {
                // 设置外观为系统外观
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            FFmpegBatchProcessor app = new FFmpegBatchProcessor();
            app.setVisible(true);
        });
    }
} 