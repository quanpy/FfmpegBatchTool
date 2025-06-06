package com.ffmpegui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
        REMOVE_TRAILER("去未完待续"),
        VIDEO_SPLICE_ADVANCED("高级拼接");

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
    private JPanel videoSpliceAdvancedPanel;

    // 各页面的输入字段
    private JTextField compressParamsField;
    private JTextField subtitleDelogoParamsField;
    private JTextField subtitleCompressParamsField;
    private JTextField trailerDelogoParamsField;
    private JTextField trailerDurationField;
    private JTextField trailerCompressParamsField;
    private JTextField spliceTailDurationField;
    private JTextField spliceHeadDurationField;
    private JTextField spliceCompressParamsField;
    private JCheckBox useNvencCheckBox;
    private JCheckBox spliceHeadCheckBox;
    private JCheckBox spliceTailCheckBox;

    // 硬件加速选择组件
    private ButtonGroup accelerationGroup;
    private JRadioButton cpuRadioButton;
    private JRadioButton nvencRadioButton;
    private JRadioButton intelRadioButton;
    private JRadioButton amdRadioButton;
    private JPanel accelerationPanel;

    // 页面容器
    private JPanel cardPanel;
    private CardLayout cardLayout;

    // 默认的压缩参数
    private static final String DEFAULT_COMPRESS_PARAMS = "-c:v libx264 -b:v 8000k -crf 23 -c:a aac -b:a 192k -y";
    private static final String DEFAULT_NVENC_PARAMS = "-c:v h264_nvenc -profile:v high -b:v 8000k -crf 23 -y";
    private static final String DEFAULT_INTEL_PARAMS = "-c:v h264_qsv  -b:v 8000k -crf 23 -y";
    private static final String DEFAULT_AMD_PARAMS = "-c:v h264_amf  -b:v 8000k -crf 23 -y";
    private static final String DEFAULT_UI_PARAMS = DEFAULT_INTEL_PARAMS;
    private static final String VERSION = "2.1";

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

        public static List<DelogoParams> parseList(String delogoParams) {
            String[] paramSets = delogoParams.split("&");
            List<DelogoParams> list = new ArrayList<>(paramSets.length);
            for (String paramSet : paramSets) {
                list.add(parse(paramSet));
            }
            return list;
        }
    }

    // 添加新的类成员变量来存储日志滚动窗格
    private JScrollPane logScrollPane;

    public FFmpegBatchProcessor() {
        // 设置窗口标题和关闭操作
        super("FFmpeg多功能批处理工具 Version %s ocean.quan@wiitrans.com".formatted(VERSION));
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

//        addLogMessage("====   start success.   =====");
//        addLogMessage("把这段加到压制参数前面 -vf \"scale=1080:1920\" 可将4K/2k视频转为竖版1080p");

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

        // 硬件加速选择组件
        createAccelerationComponents();

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
        compressParamsField.setText(DEFAULT_UI_PARAMS);

        // 初始化去小字页面的输入字段
        subtitleDelogoParamsField = createStyledTextField();
        subtitleDelogoParamsField.setToolTipText("输入格式：x,y,w,h （例如：98,1169,879,155）注意：涂抹边界不要紧贴视频边界");
        subtitleCompressParamsField = createStyledTextField();
        subtitleCompressParamsField.setText(DEFAULT_UI_PARAMS);

        // 初始化去未完待续页面的输入字段
        trailerDelogoParamsField = createStyledTextField();
        trailerDelogoParamsField.setToolTipText("输入格式：x,y,w,h （例如：98,1169,879,155）");
        trailerDurationField = createStyledTextField();
        trailerDurationField.setText("2.2");
        trailerDurationField.setToolTipText("视频结尾处理时长（秒），如2.2表示处理视频最后2.2秒");
        trailerCompressParamsField = createStyledTextField();
        trailerCompressParamsField.setText(DEFAULT_UI_PARAMS);

        // 初始化视频拼接页面的输入字段
        spliceTailDurationField = createStyledTextField();
        spliceTailDurationField.setText("1.5");
        spliceTailDurationField.setToolTipText("视频拼接处理时长（秒），表示取第一个视频的结尾多少秒");
        spliceCompressParamsField = createStyledTextField();
        spliceCompressParamsField.setText(DEFAULT_UI_PARAMS);

        // 初始化片头拼接页面的输入字段
        spliceHeadDurationField = createStyledTextField();
        spliceHeadDurationField.setText("1.5");
        spliceHeadDurationField.setToolTipText("片头拼接处理时长（秒），表示取第一个视频的前多少秒");
        spliceCompressParamsField = createStyledTextField();
        spliceCompressParamsField.setText(DEFAULT_UI_PARAMS);

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
                // 判断日志区是否已滚动到底部
                JScrollBar verticalBar = logScrollPane.getVerticalScrollBar();
                boolean isAtBottom = verticalBar.getValue() + verticalBar.getVisibleAmount() >= verticalBar.getMaximum() - 10;
                
                // 记住当前的滚动位置
                int currentPosition = verticalBar.getValue();
                
                // 将文本转换为UTF-8编码处理，避免显示乱码
                try {
                    String normalizedText = new String(logText.getBytes("UTF-8"), "UTF-8");
                    logArea.append(normalizedText);
                } catch (Exception ex) {
                    // 如果转换失败，直接添加原始文本
                    logArea.append(logText);
                }

                // 仅当之前位于底部时，才自动滚动到底部
                if (isAtBottom) {
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                } else {
                    // 否则保持原来的滚动位置
                    verticalBar.setValue(currentPosition);
                }
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

        // 创建视频拼接页面
        videoSpliceAdvancedPanel = createVideoSpliceAdvancedPanel();

        // 添加页面到卡片布局
        cardPanel.add(compressPanel, PageType.COMPRESS.name());
        cardPanel.add(removeSubtitlePanel, PageType.REMOVE_SUBTITLE.name());
        cardPanel.add(removeTrailerPanel, PageType.REMOVE_TRAILER.name());
        cardPanel.add(videoSpliceAdvancedPanel, PageType.VIDEO_SPLICE_ADVANCED.name());

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

        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_COLOR, 1, true),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));

        logPanel.add(logLabel, BorderLayout.NORTH);
        logPanel.add(logScrollPane, BorderLayout.CENTER);
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

        // 创建硬件加速选项面板的副本
        JPanel accelerationPanelCopy = createAccelerationPanelCopy();

        // 添加硬件加速选项面板到原面板
        panel.add(commandPanel, BorderLayout.NORTH);
        panel.add(accelerationPanelCopy, BorderLayout.CENTER);
        panel.add(descPanel, BorderLayout.SOUTH);
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

        JLabel descLabel = new JLabel("此功能用于去除视频中的小型水印，如果有多个水印用&分隔，处理后的文件会添加\"_s\"后缀");
        descLabel.setFont(NORMAL_FONT);
        descLabel.setForeground(new Color(90, 90, 90));
        descPanel.add(descLabel, BorderLayout.CENTER);

        // 创建硬件加速选项面板的副本
        JPanel accelerationPanelCopy = createAccelerationPanelCopy();

        // 添加硬件加速选项到面板
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(accelerationPanelCopy, BorderLayout.NORTH);
        centerPanel.add(descPanel, BorderLayout.CENTER);

        panel.add(inputsPanel, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);
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

        JLabel descLabel = new JLabel("<html>此功能用于去除视频末尾的\"未完待续\"等水印，需要同时处理小字把小字框放在前面用&分隔，<br>最后一个框为未完待续框，处理后的文件会添加\"_w\"后缀</html>");
        descLabel.setFont(NORMAL_FONT);
        descLabel.setForeground(new Color(90, 90, 90));
        descPanel.add(descLabel, BorderLayout.CENTER);

        // 创建硬件加速选项面板的副本
        JPanel accelerationPanelCopy = createAccelerationPanelCopy();

        // 添加硬件加速选项到面板
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(accelerationPanelCopy, BorderLayout.NORTH);
        centerPanel.add(descPanel, BorderLayout.CENTER);

        panel.add(inputsPanel, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createVideoSpliceAdvancedPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JPanel inputsPanel = new JPanel(new GridLayout(4, 1, 0, 10));
        inputsPanel.setOpaque(false);
        inputsPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));

        // 压缩参数面板
        JPanel compressPanel = new JPanel(new BorderLayout(10, 0));
        compressPanel.setOpaque(false);
        compressPanel.add(createStyledLabel("压缩参数:"), BorderLayout.WEST);
        compressPanel.add(spliceCompressParamsField, BorderLayout.CENTER);
        
        // 选项复选框面板
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        optionsPanel.setOpaque(false);
        spliceHeadCheckBox = new JCheckBox("拼接片头");
        spliceHeadCheckBox.setFont(NORMAL_FONT);
        spliceHeadCheckBox.setOpaque(false);
        spliceHeadCheckBox.setSelected(false);
        
        spliceTailCheckBox = new JCheckBox("拼接片尾");
        spliceTailCheckBox.setFont(NORMAL_FONT);
        spliceTailCheckBox.setOpaque(false);
        spliceTailCheckBox.setSelected(true);
        
        optionsPanel.add(spliceHeadCheckBox);
        optionsPanel.add(spliceTailCheckBox);

        // 片头时长面板
        JPanel headDurationPanel = new JPanel(new BorderLayout(10, 0));
        headDurationPanel.setOpaque(false);
        headDurationPanel.add(createStyledLabel("片头拼接时长(秒):"), BorderLayout.WEST);
        headDurationPanel.add(spliceHeadDurationField, BorderLayout.CENTER);

        // 片尾时长面板
        JPanel tailDurationPanel = new JPanel(new BorderLayout(10, 0));
        tailDurationPanel.setOpaque(false);
        tailDurationPanel.add(createStyledLabel("片尾拼接时长(秒):"), BorderLayout.WEST);
        tailDurationPanel.add(spliceTailDurationField, BorderLayout.CENTER);

        inputsPanel.add(compressPanel);
        inputsPanel.add(optionsPanel);
        inputsPanel.add(headDurationPanel);
        inputsPanel.add(tailDurationPanel);

        // 添加说明面板
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setOpaque(false);
        descPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel descLabel = new JLabel("<html>此功能用于高级拼接视频，文件夹中需要有原视频和带有_no_sub后缀的视频<br>" +
                "可以选择同时拼接片头和片尾，或仅拼接其中一种，处理后的文件会生成到OK文件夹中</html>");
        descLabel.setFont(NORMAL_FONT);
        descLabel.setForeground(new Color(90, 90, 90));
        descPanel.add(descLabel, BorderLayout.CENTER);

        // 提示面板
        JPanel tipPanel = new JPanel(new BorderLayout(5, 0));
        tipPanel.setOpaque(false);
        tipPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ACCENT_COLOR),
                BorderFactory.createEmptyBorder(15, 10, 10, 10)
        ));

        JLabel tipLabel = new JLabel("提示：文件夹中需要有成对的视频，第二个视频名称需要在第一个基础上加上_no_sub后缀");
        tipLabel.setFont(NORMAL_FONT);
        tipLabel.setForeground(new Color(32, 61, 135));
        tipLabel.setIcon(createInfoIcon());
        tipPanel.add(tipLabel, BorderLayout.CENTER);

        // 创建硬件加速选项面板的副本
        JPanel accelerationPanelCopy = createAccelerationPanelCopy();

        // 添加硬件加速选项到面板
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(accelerationPanelCopy, BorderLayout.NORTH);
        centerPanel.add(descPanel, BorderLayout.CENTER);

        panel.add(inputsPanel, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(tipPanel, BorderLayout.SOUTH);
        
        // 添加复选框监听器
        spliceHeadCheckBox.addActionListener(e -> {
            boolean headEnabled = spliceHeadCheckBox.isSelected();
            spliceHeadDurationField.setEnabled(headEnabled);
            if (!headEnabled && !spliceTailCheckBox.isSelected()) {
                spliceTailCheckBox.setSelected(true);
            }
        });
        
        spliceTailCheckBox.addActionListener(e -> {
            boolean tailEnabled = spliceTailCheckBox.isSelected();
            spliceTailDurationField.setEnabled(tailEnabled);
            if (!tailEnabled && !spliceHeadCheckBox.isSelected()) {
                spliceHeadCheckBox.setSelected(true);
            }
        });
        
        // 设置初始状态
        spliceHeadDurationField.setEnabled(spliceHeadCheckBox.isSelected());
        spliceTailDurationField.setEnabled(spliceTailCheckBox.isSelected());

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
        setTitle("FFmpeg多功能批处理工具 Version %s - %s - ocean.quan@wiitrans.com".formatted(VERSION, pageType.getTitle()));

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
                case VIDEO_SPLICE_ADVANCED -> processVideoSpliceAdvanced(folderPath);
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
        if (!delogoParams.isEmpty() && !isValidMultipleDelogoParams(delogoParams)) {
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

    /**
     * @author Ocean
     * @date: 2025/4/21 8:46
     * @description: 去 未完待续 界面操作
     */
    private void processRemoveTrailer(String folderPath) {
        String delogoParams = trailerDelogoParamsField.getText().trim();
        String lastDuration = trailerDurationField.getText().trim();
        String ffmpegCommand = trailerCompressParamsField.getText().trim();

        // 验证去水印参数格式
        if (!delogoParams.isEmpty() && !isValidMultipleDelogoParams(delogoParams)) {
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

    private void processVideoSpliceAdvanced(String folderPath) {
        String tailDuration = spliceTailDurationField.getText().trim();
        String headDuration = spliceHeadDurationField.getText().trim();
        String ffmpegCommand = spliceCompressParamsField.getText().trim();
        boolean doSpliceHead = spliceHeadCheckBox.isSelected();
        boolean doSpliceTail = spliceTailCheckBox.isSelected();
        
        // 验证是否选择了至少一种拼接模式
        if (!doSpliceHead && !doSpliceTail) {
            JOptionPane.showMessageDialog(this,
                    "请至少选择一种拼接模式（片头或片尾）", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 验证片尾处理时长格式
        if (doSpliceTail && !tailDuration.isEmpty()) {
            try {
                Double.parseDouble(tailDuration);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "片尾拼接时长必须是有效的数字（秒）", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        // 验证片头处理时长格式
        if (doSpliceHead && !headDuration.isEmpty()) {
            try {
                Double.parseDouble(headDuration);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "片头拼接时长必须是有效的数字（秒）", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // 禁用按钮防止重复点击
        processButton.setEnabled(false);

        // 清空日志
        logArea.setText("");

        addLogMessage("开始高级视频拼接处理...");
        addLogMessage("拼接模式: " + (doSpliceHead ? "片头 " : "") + (doSpliceTail ? "片尾" : ""));

        // 在后台线程中执行处理
        new Thread(() -> {
            try {
                processVideoSpliceAdvancedFiles(folderPath, ffmpegCommand, headDuration, tailDuration, doSpliceHead, doSpliceTail);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    processButton.setEnabled(true);
                    statusLabel.setText("处理完成");
                    progressBar.setValue(100);
                });
            }
        }).start();
    }

    private void processVideoSpliceAdvancedFiles(String folderPath, String ffmpegArgs, 
            String headDuration, String tailDuration, boolean doSpliceHead, boolean doSpliceTail) {
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
        
        // 找出所有没有_no_sub后缀的媒体文件
        List<File> originalFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && isMediaFile(file.getName()) && !file.getName().contains("_no_sub")) {
                // 检查是否存在对应的_no_sub文件
                String noSubFileName = getNoSubFileName(file.getName());
                File noSubFile = new File(folder, noSubFileName);
                if (noSubFile.exists()) {
                    originalFiles.add(file);
                }
            }
        }
        
        if (originalFiles.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, 
                    "文件夹中没有找到配对的媒体文件（需要有原文件和带_no_sub后缀的文件）", 
                    "警告", JOptionPane.WARNING_MESSAGE);
                statusLabel.setText("警告：没有找到配对的媒体文件");
                processButton.setEnabled(true);
            });
            return;
        }
        
        // 确保输出目录存在
        File okFolder = new File(folder, "OK");
        if (!okFolder.exists()) {
            okFolder.mkdir();
        }
        
        // 设置进度条
        SwingUtilities.invokeLater(() -> {
            progressBar.setMaximum(originalFiles.size());
            progressBar.setValue(0);
        });
        
        // 处理每个文件
        int count = 0;
        for (File file : originalFiles) {
            final int currentCount = ++count;
            final String fileName = file.getName();
            
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("处理中: " + fileName + " (" + currentCount + "/" + originalFiles.size() + ")");
                progressBar.setValue(currentCount - 1);
            });
            
            try {
                processVideoSpliceAdvancedFile(file, folder, okFolder, ffmpegArgs, 
                        headDuration, tailDuration, doSpliceHead, doSpliceTail);
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
    
    private void processVideoSpliceAdvancedFile(File originalFile, File inputFolder, File outputFolder, 
            String ffmpegArgs, String headDuration, String tailDuration, 
            boolean doSpliceHead, boolean doSpliceTail) throws Exception {
        String originalPath = originalFile.getAbsolutePath();
        String fileName = originalFile.getName();
        String noSubFileName = getNoSubFileName(fileName);
        File noSubFile = new File(inputFolder, noSubFileName);
        
        if (!noSubFile.exists()) {
            throw new Exception("找不到对应的无字幕文件: " + noSubFileName);
        }
        
        String noSubPath = noSubFile.getAbsolutePath();
        
        // 获取视频时长
        String endTimeStr = getVideoDuration(originalPath);
        addLogMessage("视频总时长: " + endTimeStr + " 秒");
        
        double duration = Double.parseDouble(endTimeStr);
        double headDurationValue = doSpliceHead ? Double.parseDouble(headDuration) : 0;
        double tailDurationValue = doSpliceTail ? Double.parseDouble(tailDuration) : 0;
        
        // 确保时长不超过视频总时长
        headDurationValue = Math.min(headDurationValue, duration / 2);
        tailDurationValue = Math.min(tailDurationValue, duration / 2);
        
        // 确保头尾时长总和不超过视频总时长
        if (headDurationValue + tailDurationValue > duration) {
            double ratio = duration / (headDurationValue + tailDurationValue);
            headDurationValue *= ratio;
            tailDurationValue *= ratio;
            addLogMessage(String.format("警告：拼接时长总和超过视频时长，已按比例缩减为 %.2f + %.2f 秒", 
                    headDurationValue, tailDurationValue));
        }
        
        double tailStartTime = Math.max(0, duration - tailDurationValue);
        
        // 日志输出拼接策略
        if (doSpliceHead && doSpliceTail) {
            addLogMessage(String.format("拼接策略：取原视频的前 %.2f 秒作为片头，取原视频的后 %.2f 秒作为片尾，中间部分使用无字幕视频", 
                    headDurationValue, tailDurationValue));
        } else if (doSpliceHead) {
            addLogMessage(String.format("拼接策略：取原视频的前 %.2f 秒作为片头，后续部分使用无字幕视频", headDurationValue));
        } else {
            addLogMessage(String.format("拼接策略：取无字幕视频的前 %.2f 秒，再拼接原视频的后 %.2f 秒作为片尾", 
                    tailStartTime, tailDurationValue));
        }
        
        // 临时文件路径
        String tempDir = System.getProperty("java.io.tmpdir");
        List<File> tempFiles = new ArrayList<>();
        String outputPrefix = "";
        
        if (doSpliceHead && doSpliceTail) {
            outputPrefix = "head_tail_";
        } else if (doSpliceHead) {
            outputPrefix = "head_";
        } else {
            outputPrefix = "tail_";
        }
        
        // 生成输出文件名
        String outputFileName = outputPrefix + "spliced_" + fileName;
        File outputFile = new File(outputFolder, outputFileName);
        
        try {
            // 新方案：先准备视频部分，然后提取和准备音频部分，最后合并

            // 1. 提取原始视频的完整音频
            addLogMessage("正在提取原始视频的完整音频...");
            File audioFile = new File(tempDir, "temp_audio_" + System.currentTimeMillis() + ".aac");
            tempFiles.add(audioFile);
            
            List<String> audioCommand = new ArrayList<>();
            audioCommand.add("ffmpeg");
            audioCommand.add("-i");
            audioCommand.add(originalPath);
            audioCommand.add("-vn");  // 不要视频
            audioCommand.add("-acodec");
            audioCommand.add("aac");
            audioCommand.add("-b:a");
            audioCommand.add("192k");
            audioCommand.add("-y");
            audioCommand.add(audioFile.getAbsolutePath());
            
            executeCommand(audioCommand);
            
            // 2. 准备视频部分（不含音频）
            addLogMessage("正在准备视频部分...");
            
            // 各部分视频临时文件
            List<String> videoFiles = new ArrayList<>();
            
            if (doSpliceHead) {
                // 准备片头临时文件 - 原视频的前部分（仅视频）
                File headFile = new File(tempDir, "temp_head_" + System.currentTimeMillis() + ".mp4");
                tempFiles.add(headFile);
                
                List<String> headCommand = new ArrayList<>();
                headCommand.add("ffmpeg");
                headCommand.add("-i");
                headCommand.add(originalPath);
                headCommand.add("-an");  // 不要音频
                headCommand.add("-t");
                headCommand.add(String.format("%.2f", headDurationValue));
                headCommand.add("-c:v");
                headCommand.add("libx264");
                headCommand.add("-crf");
                headCommand.add("23");
                headCommand.add("-y");
                headCommand.add(headFile.getAbsolutePath());
                
                executeCommand(headCommand);
                videoFiles.add(headFile.getAbsolutePath());
            }
            
            // 准备中间部分 - 无字幕视频的中间部分（仅视频）
            File middleFile = new File(tempDir, "temp_middle_" + System.currentTimeMillis() + ".mp4");
            tempFiles.add(middleFile);
            
            List<String> middleCommand = new ArrayList<>();
            middleCommand.add("ffmpeg");
            middleCommand.add("-i");
            middleCommand.add(noSubPath);
            middleCommand.add("-an");  // 不要音频
            
            if (doSpliceHead) {
                // 跳过片头部分
                middleCommand.add("-ss");
                middleCommand.add(String.format("%.2f", headDurationValue));
            }
            
            if (doSpliceTail) {
                // 限制时长，不包括片尾部分
                middleCommand.add("-t");
                double middleDuration = tailStartTime - (doSpliceHead ? headDurationValue : 0);
                middleCommand.add(String.format("%.2f", middleDuration));
            }
            
            middleCommand.add("-c:v");
            middleCommand.add("libx264");
            middleCommand.add("-crf");
            middleCommand.add("23");
            middleCommand.add("-y");
            middleCommand.add(middleFile.getAbsolutePath());
            
            executeCommand(middleCommand);
            videoFiles.add(middleFile.getAbsolutePath());
            
            if (doSpliceTail) {
                // 准备片尾临时文件 - 原视频的后部分（仅视频）
                File tailFile = new File(tempDir, "temp_tail_" + System.currentTimeMillis() + ".mp4");
                tempFiles.add(tailFile);
                
                List<String> tailCommand = new ArrayList<>();
                tailCommand.add("ffmpeg");
                tailCommand.add("-i");
                tailCommand.add(originalPath);
                tailCommand.add("-an");  // 不要音频
                tailCommand.add("-ss");
                tailCommand.add(String.format("%.2f", tailStartTime));
                tailCommand.add("-c:v");
                tailCommand.add("libx264");
                tailCommand.add("-crf");
                tailCommand.add("23");
                tailCommand.add("-y");
                tailCommand.add(tailFile.getAbsolutePath());
                
                executeCommand(tailCommand);
                videoFiles.add(tailFile.getAbsolutePath());
            }
            
            // 3. 合并所有视频流，并与音频合并
            addLogMessage("正在合并视频片段与音频...");
            
            // 创建一个视频片段列表文件
            File videoListFile = new File(tempDir, "video_list_" + System.currentTimeMillis() + ".txt");
            tempFiles.add(videoListFile);
            
            try (java.io.PrintWriter writer = new java.io.PrintWriter(videoListFile)) {
                for (String filePath : videoFiles) {
                    writer.println("file '" + filePath.replace("\\", "\\\\") + "'");
                }
            }
            
            // 先把所有视频合并成一个无声视频
            File mergedVideoFile = new File(tempDir, "temp_merged_video_" + System.currentTimeMillis() + ".mp4");
            tempFiles.add(mergedVideoFile);
            
            List<String> mergeVideoCommand = new ArrayList<>();
            mergeVideoCommand.add("ffmpeg");
            mergeVideoCommand.add("-f");
            mergeVideoCommand.add("concat");
            mergeVideoCommand.add("-safe");
            mergeVideoCommand.add("0");
            mergeVideoCommand.add("-i");
            mergeVideoCommand.add(videoListFile.getAbsolutePath());
            mergeVideoCommand.add("-c");
            mergeVideoCommand.add("copy");
            mergeVideoCommand.add("-y");
            mergeVideoCommand.add(mergedVideoFile.getAbsolutePath());
            
            executeCommand(mergeVideoCommand);
            
            // 4. 最后合并视频和音频
            List<String> finalCommand = new ArrayList<>();
            finalCommand.add("ffmpeg");
            finalCommand.add("-i");
            finalCommand.add(mergedVideoFile.getAbsolutePath());
            finalCommand.add("-i");
            finalCommand.add(audioFile.getAbsolutePath());
            finalCommand.add("-c:v");
            finalCommand.add("copy");
            finalCommand.add("-c:a");
            finalCommand.add("aac");
            finalCommand.add("-strict");
            finalCommand.add("experimental");
            finalCommand.add("-map");
            finalCommand.add("0:v:0");
            finalCommand.add("-map");
            finalCommand.add("1:a:0");
            finalCommand.add("-shortest");
            
//            // 添加用户自定义参数，但排除可能冲突的
//            String[] args = ffmpegArgs.split("\\s+");
//            for (String arg : args) {
//                if (!arg.trim().isEmpty() && !arg.contains("-y")
//                    && !arg.contains("-c:v") && !arg.contains("-c:a")
//                    && !arg.contains("-map") && !arg.contains("-shortest")) {
//                    finalCommand.add(arg.trim());
//                }
//            }
            
            finalCommand.add("-y");
            finalCommand.add(outputFile.getAbsolutePath());
            
            executeCommand(finalCommand);
            
            addLogMessage("成功处理文件: " + fileName);
            
        } finally {
            // 删除所有临时文件
            for (File tempFile : tempFiles) {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }
    }

    private String getNoSubFileName(String originalFileName) {
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String baseName = originalFileName.substring(0, dotIndex);
            String extension = originalFileName.substring(dotIndex);
            return baseName + "_no_sub" + extension;
        } else {
            return originalFileName + "_no_sub";
        }
    }

    private void processVideoSpliceFile(File originalFile, File inputFolder, File outputFolder,
                                        String ffmpegArgs, String lastDuration) throws Exception {
        String originalPath = originalFile.getAbsolutePath();
        String fileName = originalFile.getName();
        String noSubFileName = getNoSubFileName(fileName);
        File noSubFile = new File(inputFolder, noSubFileName);

        if (!noSubFile.exists()) {
            throw new Exception("找不到对应的无字幕文件: " + noSubFileName);
        }

        String noSubPath = noSubFile.getAbsolutePath();

        // 获取视频时长
        String endTime = getVideoDuration(originalPath);
        addLogMessage("视频总时长: " + endTime + " 秒");

        double duration = Double.parseDouble(endTime);
        double lastDurationValue = Double.parseDouble(lastDuration);
        double startTime = Math.max(0, duration - lastDurationValue);

        addLogMessage(String.format("拼接点: %.2f 秒，将取原视频的后 %.2f 秒和无字幕视频的前 %.2f 秒进行拼接",
                startTime, lastDurationValue, startTime));

        // 临时文件路径
        String tempDir = System.getProperty("java.io.tmpdir");
        File tempPart1 = new File(tempDir, "temp_part1_" + System.currentTimeMillis() + ".mp4");
        File tempPart2 = new File(tempDir, "temp_part2_" + System.currentTimeMillis() + ".mp4");

        // 输出文件路径
        String outputFileName = "spliced_" + fileName;
        File outputFile = new File(outputFolder, outputFileName);

        try {
            // 1. 切割原视频的后部分
            addLogMessage("正在切割原视频的后部分...");
            List<String> command1 = new ArrayList<>();
            command1.add("ffmpeg");
            command1.add("-i");
            command1.add(originalPath);
            command1.add("-ss");
            command1.add(String.format("%.2f", startTime));

            // 添加用户指定的参数
            String[] args = ffmpegArgs.split("\\s+");
            for (String arg : args) {
                if (!arg.trim().isEmpty() && !arg.contains("-y")) {
                    command1.add(arg.trim());
                }
            }

            command1.add("-y");
            command1.add(tempPart1.getAbsolutePath());

            executeCommand(command1);

            // 2. 切割无字幕视频的前部分
            addLogMessage("正在切割无字幕视频的前部分...");
            List<String> command2 = new ArrayList<>();
            command2.add("ffmpeg");
            command2.add("-i");
            command2.add(noSubPath);
            command2.add("-t");
            command2.add(String.format("%.2f", startTime));

            // 添加用户指定的参数
            for (String arg : args) {
                if (!arg.trim().isEmpty() && !arg.contains("-y")) {
                    command2.add(arg.trim());
                }
            }

            command2.add("-y");
            command2.add(tempPart2.getAbsolutePath());

            executeCommand(command2);

            // 3. 合并两个部分
            addLogMessage("正在合并视频...");
            List<String> command3 = new ArrayList<>();
            command3.add("ffmpeg");
            command3.add("-i");
            command3.add(tempPart2.getAbsolutePath());
            command3.add("-i");
            command3.add(tempPart1.getAbsolutePath());
            command3.add("-filter_complex");
            command3.add("[0:v][0:a][1:v][1:a]concat=n=2:v=1:a=1[v][a]");
            command3.add("-map");
            command3.add("[v]");
            command3.add("-map");
            command3.add("[a]");
            command3.add("-y");
            command3.add(outputFile.getAbsolutePath());

            executeCommand(command3);

            addLogMessage("成功处理文件: " + fileName);

        } finally {
            // 删除临时文件
            if (tempPart1.exists()) tempPart1.delete();
            if (tempPart2.exists()) tempPart2.delete();
        }
    }

    private void executeCommand(List<String> command) throws Exception {
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

    private boolean isValidDelogoParams(String params) {
        // 检查格式是否为四个数字，用逗号分隔
        String regex = "\\d+,\\d+,\\d+,\\d+";
        return Pattern.matches(regex, params);
    }

    /**
     * @author Ocean
     * @date: 2025/4/18 15:54
     * @description: 验证多个的参数
     * 369,576,280,150&369,576,280,150
     */
    private boolean isValidMultipleDelogoParams(String params) {
        // Split the input string by '&' to handle multiple sets of parameters
        String[] paramSets = params.split("&");
        for (String paramSet : paramSets) {
            if (!isValidDelogoParams(paramSet)) {
                return false;
            }
        }
        return true;
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

        // TODO 生成在 OK 文件夹中
        File okFolder = new File(folder, "OK");
        if (!okFolder.exists()) {
            okFolder.mkdir();
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

                // 根据是否指定了结尾处理时长来构建不同的delogo参数
                String delogoFilter;
                if (endTime != null && !lastDuration.isEmpty()) {
                    double duration = Double.parseDouble(endTime);
                    double lastDurationValue = Double.parseDouble(lastDuration);
                    double startTime = Math.max(0, duration - lastDurationValue);
//                   这里是去未完待续
                    if (!delogoParams.contains("&")) {
                        // 单个区域
                        DelogoParams params = DelogoParams.parse(delogoParams);

                        addLogMessage(String.format("应用水印去除：从 %.2f 秒到 %.2f 秒", startTime, duration));

                        delogoFilter = """
                                "delogo=x=%d:y=%d:w=%d:h=%d:enable='between(t,%.2f,%.2f)'" """
                                .formatted(params.x(), params.y(), params.width(), params.height(), startTime, duration);
                    } else {
                        // 多个区域
                        List<DelogoParams> paramsList = DelogoParams.parseList(delogoParams);
                        List<String> strList = new ArrayList<>(paramsList.size());
                        for (int i = 0; i < paramsList.size() - 1; i++) {
                            DelogoParams params = paramsList.get(i);
                            String delogo = """
                                    delogo=x=%d:y=%d:w=%d:h=%d""".formatted(params.x(), params.y(), params.width(), params.height());
//                            sb.append(delogoFilter.trim());
                            strList.add(delogo);
                        }
                        // 最后一个是未完待续框
                        DelogoParams lastParams = paramsList.getLast();

                        addLogMessage(String.format("应用水印去除：从 %.2f 秒到 %.2f 秒", startTime, duration));
                        String delogoLast = """
                                delogo=x=%d:y=%d:w=%d:h=%d:enable='between(t,%.2f,%.2f)'"""
                                .formatted(lastParams.x(), lastParams.y(), lastParams.width(), lastParams.height(), startTime, duration);
                        strList.add(delogoLast);

                        // join
                        delogoFilter = """
                                "%s" """.formatted(String.join(",", strList));
                    }

                } else {
                    // 这里是去小字
                    if (!delogoParams.contains("&")) {
                        // 单个区域
                        DelogoParams params = DelogoParams.parse(delogoParams);
                        delogoFilter = """
                                "delogo=x=%d:y=%d:w=%d:h=%d" """.formatted(params.x(), params.y(), params.width(), params.height());
                    } else {
                        // 多个区域
                        List<DelogoParams> paramsList = DelogoParams.parseList(delogoParams);
                        List<String> strList = new ArrayList<>(paramsList.size());
                        for (DelogoParams params : paramsList) {
                            String delogo = """
                                    delogo=x=%d:y=%d:w=%d:h=%d""".formatted(params.x(), params.y(), params.width(), params.height());
//                            sb.append(delogoFilter.trim());
                            strList.add(delogo);
                        }
                        // join
                        delogoFilter = """
                                "%s" """.formatted(String.join(",", strList));
                    }

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
            int gangIndex = inputPath.lastIndexOf(File.separator);
            if (gangIndex > 0) {
                String baseFolderPath = basePath.substring(0, gangIndex);
                // 这个里面带一个 /
                String baseName = basePath.substring(gangIndex + 1);

                return baseFolderPath + File.separator + "OK" + File.separator + baseName + "_" + suffix + ".mp4";
            } else{
                return basePath + "_" + suffix + extension;
            }
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
                lowerName.endsWith(".webm") || lowerName.endsWith(".m4a") || lowerName.endsWith(".mxf");
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

    // 创建硬件加速选择组件
    private void createAccelerationComponents() {
        accelerationGroup = new ButtonGroup();

        cpuRadioButton = createStyledRadioButton("CPU (libx264)", "cpu", true);
        nvencRadioButton = createStyledRadioButton("NVIDIA GPU (h264_nvenc)", "nvenc", false);
        intelRadioButton = createStyledRadioButton("Intel GPU (h264_qsv)", "intel", false);
        amdRadioButton = createStyledRadioButton("AMD GPU (h264_amf)", "amd", false);

        accelerationGroup.add(cpuRadioButton);
        accelerationGroup.add(nvencRadioButton);
        accelerationGroup.add(intelRadioButton);
        accelerationGroup.add(amdRadioButton);

        accelerationPanel = new JPanel();
        accelerationPanel.setOpaque(false);
        accelerationPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        accelerationPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ACCENT_COLOR, 1, true),
                "硬件加速选项",
                0,
                0,
                NORMAL_FONT,
                TEXT_COLOR
        ));

        accelerationPanel.add(cpuRadioButton);
        accelerationPanel.add(nvencRadioButton);
        accelerationPanel.add(intelRadioButton);
        accelerationPanel.add(amdRadioButton);

        // 添加切换参数的监听器
        ActionListener paramSwitchListener = e -> {
            updateCompressParamsForAllPages();
        };

        cpuRadioButton.addActionListener(paramSwitchListener);
        nvencRadioButton.addActionListener(paramSwitchListener);
        intelRadioButton.addActionListener(paramSwitchListener);
        amdRadioButton.addActionListener(paramSwitchListener);
    }

    // 根据所选硬件加速选项更新所有页面的压缩参数
    private void updateCompressParamsForAllPages() {
        String params = getSelectedAccelerationParams();

        // 更新各页面的压缩参数
        compressParamsField.setText(params);
        subtitleCompressParamsField.setText(params);
        trailerCompressParamsField.setText(params);
        spliceCompressParamsField.setText(params);

        // 添加日志记录便于调试
        System.out.println("更新压缩参数: " + params);
    }

    // 获取当前选择的加速器对应的参数
    private String getSelectedAccelerationParams() {
        ButtonModel selectedModel = accelerationGroup.getSelection();
        if (selectedModel == null) {
            return DEFAULT_COMPRESS_PARAMS;
        }

        String actionCommand = selectedModel.getActionCommand();
        switch (actionCommand) {
            case "nvenc":
                return DEFAULT_NVENC_PARAMS;
            case "intel":
                return DEFAULT_INTEL_PARAMS;
            case "amd":
                return DEFAULT_AMD_PARAMS;
            case "cpu":
            default:
                return DEFAULT_COMPRESS_PARAMS;
        }
    }

    // 创建硬件加速面板副本
    private JPanel createAccelerationPanelCopy() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ACCENT_COLOR, 1, true),
                "硬件加速选项",
                0,
                0,
                NORMAL_FONT,
                TEXT_COLOR
        ));

        // 创建单选按钮的副本，但仍然添加到同一个按钮组中以保持选择同步
        JRadioButton cpuCopy = createStyledRadioButton("CPU (libx264)", "cpu", cpuRadioButton.isSelected());
        JRadioButton nvencCopy = createStyledRadioButton("NVIDIA GPU (h264_nvenc)", "nvenc", nvencRadioButton.isSelected());
        JRadioButton intelCopy = createStyledRadioButton("Intel GPU (h264_qsv)", "intel", intelRadioButton.isSelected());
        JRadioButton amdCopy = createStyledRadioButton("AMD GPU (h264_amf)", "amd", amdRadioButton.isSelected());

        accelerationGroup.add(cpuCopy);
        accelerationGroup.add(nvencCopy);
        accelerationGroup.add(intelCopy);
        accelerationGroup.add(amdCopy);

        panel.add(cpuCopy);
        panel.add(nvencCopy);
        panel.add(intelCopy);
        panel.add(amdCopy);

        // 添加切换参数的监听器
        ActionListener paramSwitchListener = e -> {
            // 记录选中的按钮的ActionCommand
            String command = ((JRadioButton) e.getSource()).getActionCommand();

            // 根据选中的按钮同步更新主按钮组的选中状态
            switch (command) {
                case "cpu" -> cpuRadioButton.setSelected(true);
                case "nvenc" -> nvencRadioButton.setSelected(true);
                case "intel" -> intelRadioButton.setSelected(true);
                case "amd" -> amdRadioButton.setSelected(true);
            }

            // 更新所有页面的压缩参数
            updateCompressParamsForAllPages();
        };

        cpuCopy.addActionListener(paramSwitchListener);
        nvencCopy.addActionListener(paramSwitchListener);
        intelCopy.addActionListener(paramSwitchListener);
        amdCopy.addActionListener(paramSwitchListener);

        return panel;
    }

    // 创建定制样式的单选按钮
    private JRadioButton createStyledRadioButton(String text, String actionCommand, boolean selected) {
        // 创建自定义按钮，覆盖所有绘制方法
        JRadioButton button = new JRadioButton(text) {
            @Override
            public void paint(Graphics g) {
                // 完全控制按钮的绘制，不调用super.paint
                paintCustomButton(g);
            }

            private void paintCustomButton(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // 计算各部分位置
                Insets insets = getInsets();
                int width = getWidth() - insets.left - insets.right;
                int height = getHeight() - insets.top - insets.bottom;

                // 清除背景
                if (isOpaque()) {
                    g2.setColor(getBackground());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }

                // 绘制背景
                if (isSelected()) {
                    // 选中状态绘制渐变背景
                    GradientPaint gp = new GradientPaint(
                            0, 0, new Color(63, 81, 181, 80),
                            getWidth(), getHeight(), new Color(63, 81, 181, 60)
                    );
                    g2.setPaint(gp);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                    // 添加边框
                    g2.setColor(new Color(63, 81, 181, 150));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
                } else if (getModel().isRollover()) {
                    // 鼠标悬停状态
                    g2.setColor(new Color(63, 81, 181, 30));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                }

                // 获取字体度量用于文本布局
                FontMetrics fm = g2.getFontMetrics(getFont());

                // 绘制文本 - 不再绘制圆形按钮
                int textX = insets.left + 10; // 文本左边距
                int textY = insets.top + (height + fm.getAscent() - fm.getDescent()) / 2; // 垂直居中

                if (isSelected()) {
                    g2.setColor(new Color(25, 25, 112)); // 深蓝色
                    // 选中状态使用粗体
                    Font boldFont = getFont().deriveFont(Font.BOLD);
                    g2.setFont(boldFont);
                } else {
                    g2.setColor(TEXT_COLOR);
                }

                g2.drawString(getText(), textX, textY);

                // 如果禁用，绘制半透明覆盖层
                if (!isEnabled()) {
                    g2.setColor(new Color(255, 255, 255, 120));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }

                g2.dispose();
            }

            @Override
            protected void paintComponent(Graphics g) {
                // 不调用父类方法，完全自定义绘制
            }

            @Override
            protected void paintBorder(Graphics g) {
                // 不绘制边框
            }

            @Override
            public Dimension getPreferredSize() {
                FontMetrics fm = getFontMetrics(getFont());
                int textWidth = fm.stringWidth(getText());
                int width = 10 + textWidth + 10; // 左边距 + 文本宽度 + 右边距
                int height = Math.max(super.getPreferredSize().height, 30); // 确保高度适当
                return new Dimension(width, height);
            }
        };

        // 设置按钮样式
        button.setFont(NORMAL_FONT);
        button.setOpaque(false);
        button.setSelected(selected);
        button.setActionCommand(actionCommand);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setMargin(new Insets(8, 0, 8, 0));

        // 设置鼠标悬停光标
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 添加选择状态监听
        button.addChangeListener(e -> {
            // 强制重绘
            button.repaint();
        });

        // 添加按钮模型监听器，确保状态变化时重绘
        button.getModel().addChangeListener(e -> {
            button.repaint();
        });

        return button;
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