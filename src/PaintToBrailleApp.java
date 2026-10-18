import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PaintToBrailleApp extends JFrame {

    // ==========================================
    // --- TAB 1: IMAGE TO BRAILLE (MS PAINT) ---
    // ==========================================
    private static final int CANVAS_WIDTH = 720;
    private static final int CANVAS_HEIGHT = 576;
    private DrawingPanel drawingPanel;
    private JTextArea outputArea;
    
    private enum Tool { PENCIL, RECTANGLE, OVAL, LINE }
    private Tool currentTool = Tool.PENCIL;
    private int currentStrokeWidth = 3;

    // ==========================================
    // --- TAB 2: MANUAL GRID EDITOR ---
    // ==========================================
    private int rows = 16;
    private int cols = 32;
    private static final char BLANK = '\u2800';
    
    private static final char[] PALETTE_LETTERS = 
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] PALETTE_SYMBOLS = 
            "0123456789.,!?-_,=+*/\\|()[]{}<>#@$%& ".toCharArray();

    private static final Color[] COLORS = {
        null,
        new Color(245, 224, 220), new Color(242, 205, 205), new Color(245, 194, 231), new Color(250, 179, 135),
        new Color(249, 226, 175), new Color(166, 227, 161), new Color(148, 226, 213), new Color(137, 220, 235),
        new Color(116, 199, 236)
    };

    private char[][] grid;
    private char activeBrush = '\u28FF';
    private int[] rowColors;
    private int activeColorIndex = 0;
    private boolean isPreviewMode = false;
    private boolean showColorsInCreateMode = false;

    private JToggleButton[] brailleDots = new JToggleButton[8];
    private final int[] DOT_OFFSETS = {0x01, 0x08, 0x02, 0x10, 0x04, 0x20, 0x40, 0x80};

    private static class CellEdit {
        int r, c;
        char prev, next;
        CellEdit(int r, int c, char prev, char next) {
            this.r = r; this.c = c; this.prev = prev; this.next = next;
        }
    }
    private final List<List<CellEdit>> undoHistory = new ArrayList<>();
    private final List<List<CellEdit>> redoHistory = new ArrayList<>();
    private List<CellEdit> currentStroke = null;

    private GridCanvasPanel canvasPanel;
    private JLabel statusLabel;
    private JScrollPane gridScrollPane;
    private JPanel topPanel, palettePanel, colorPanel;
    private JToggleButton showColorBtn, eraserBtn;
    private JButton undoBtn, redoBtn, clearBtn, copyBtn, pasteBtn, saveBtn, previewBtn, backBtn;
    private JButton[] axisColorBoxes;
    
    private final ButtonGroup paletteButtonGroup = new ButtonGroup();
    private final ButtonGroup colorButtonGroup = new ButtonGroup();
    private JSpinner rowSpinner, colSpinner;
    private JPanel viewportPanel;

    public PaintToBrailleApp() {
        super("Paint to Braille Converter");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 14));
        tabs.addTab("Draw & Convert", createImageToBraillePanel());
        tabs.addTab("Manual Grid Editor", createManualGridEditorPanel());

        add(tabs, BorderLayout.CENTER);
        
        pack();
        setMinimumSize(new Dimension(1100, 850));
        setLocationRelativeTo(null);
    }

    // ==========================================
    // TAB 1: IMAGE TO BRAILLE (MS PAINT)
    // ==========================================
    private JPanel createImageToBraillePanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(new Color(240, 240, 240));

        mainPanel.add(buildPaintToolbar(), BorderLayout.NORTH);

        drawingPanel = new DrawingPanel(CANVAS_WIDTH, CANVAS_HEIGHT);
        
        JScrollPane canvasScroll = new JScrollPane(drawingPanel);
        canvasScroll.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150)));
        canvasScroll.getVerticalScrollBar().setUnitIncrement(16);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(16);
        mainPanel.add(canvasScroll, BorderLayout.CENTER);

        // Bottom Controls
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBackground(new Color(240, 240, 240));
        
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        JButton clearBtn = new JButton("Clear Canvas");
        clearBtn.addActionListener(e -> drawingPanel.clear());
        
        JButton convertBtn = new JButton("Convert to Braille Grid...");
        convertBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        convertBtn.addActionListener(e -> promptAndConvert());
        
        controls.add(clearBtn);
        controls.add(convertBtn);

        outputArea = new JTextArea(10, 50);
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(30, 30, 30));
        outputArea.setForeground(new Color(0, 229, 255));
        outputArea.setFont(pickFont(Font.PLAIN, 16));
        outputArea.setMargin(new Insets(5, 5, 5, 5));
        outputArea.setLineWrap(false);
        
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("Braille Output"));

        bottomPanel.add(controls, BorderLayout.NORTH);
        bottomPanel.add(outputScroll, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        return mainPanel;
    }

    private JPanel buildPaintToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolbar.setBackground(new Color(230, 230, 230));
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                new EmptyBorder(5, 5, 5, 5)
        ));

        ButtonGroup toolGroup = new ButtonGroup();
        JToggleButton pencilBtn = createPaintToolBtn("Pencil", Tool.PENCIL, toolGroup);
        JToggleButton rectBtn = createPaintToolBtn("Rectangle", Tool.RECTANGLE, toolGroup);
        JToggleButton ovalBtn = createPaintToolBtn("Oval", Tool.OVAL, toolGroup);
        JToggleButton lineBtn = createPaintToolBtn("Line", Tool.LINE, toolGroup);
        pencilBtn.setSelected(true);

        toolbar.add(new JLabel("Tools:"));
        toolbar.add(pencilBtn);
        toolbar.add(rectBtn);
        toolbar.add(ovalBtn);
        toolbar.add(lineBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        toolbar.add(new JLabel("Stroke:"));
        JComboBox<String> strokeCombo = new JComboBox<>(new String[]{"Fine (1px)", "Medium (3px)", "Thick (6px)"});
        strokeCombo.setSelectedIndex(1);
        strokeCombo.addActionListener(e -> {
            switch (strokeCombo.getSelectedIndex()) {
                case 0 -> currentStrokeWidth = 1;
                case 1 -> currentStrokeWidth = 3;
                case 2 -> currentStrokeWidth = 6;
            }
        });
        toolbar.add(strokeCombo);

        return toolbar;
    }

    private JToggleButton createPaintToolBtn(String text, Tool tool, ButtonGroup group) {
        JToggleButton btn = new JToggleButton(text);
        btn.setFocusPainted(false);
        btn.addActionListener(e -> currentTool = tool);
        group.add(btn);
        return btn;
    }

    private void promptAndConvert() {
        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        JSpinner cSpin = new JSpinner(new SpinnerNumberModel(64, 8, 200, 1));
        JSpinner rSpin = new JSpinner(new SpinnerNumberModel(32, 8, 100, 1));
        
        inputPanel.add(new JLabel("Braille Columns (Width):"));
        inputPanel.add(cSpin);
        inputPanel.add(new JLabel("Braille Rows (Height):"));
        inputPanel.add(rSpin);

        int result = JOptionPane.showConfirmDialog(this, inputPanel, 
                "Target Braille Dimensions", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            convertToBraille((int) cSpin.getValue(), (int) rSpin.getValue());
        }
    }

    private void convertToBraille(int cTarget, int rTarget) {
        BufferedImage img = drawingPanel.getImage();
        StringBuilder sb = new StringBuilder();

        int[][] dotOffsets = {
            {0x01, 0x08}, {0x02, 0x10}, {0x04, 0x20}, {0x40, 0x80}
        };

        double cellWidth = (double) CANVAS_WIDTH / cTarget;
        double cellHeight = (double) CANVAS_HEIGHT / rTarget;

        for (int r = 0; r < rTarget; r++) {
            for (int c = 0; c < cTarget; c++) {
                int startX = (int) Math.round(c * cellWidth);
                int startY = (int) Math.round(r * cellHeight);
                int endX = (int) Math.round((c + 1) * cellWidth);
                int endY = (int) Math.round((r + 1) * cellHeight);

                int cWidth = endX - startX;
                int cHeight = endY - startY;
                int charCode = 0x2800;

                for (int dy = 0; dy < 4; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int dotStartX = startX + (dx * cWidth) / 2;
                        int dotEndX = startX + ((dx + 1) * cWidth) / 2;
                        int dotStartY = startY + (dy * cHeight) / 4;
                        int dotEndY = startY + ((dy + 1) * cHeight) / 4;

                        if (dotEndX <= dotStartX) dotEndX = dotStartX + 1;
                        if (dotEndY <= dotStartY) dotEndY = dotStartY + 1;

                        int totalPixels = 0;
                        int darkPixels = 0;

                        for (int py = dotStartY; py < dotEndY && py < CANVAS_HEIGHT; py++) {
                            for (int px = dotStartX; px < dotEndX && px < CANVAS_WIDTH; px++) {
                                totalPixels++;
                                int rgb = img.getRGB(px, py);
                                int rCol = (rgb >> 16) & 0xFF;
                                int gCol = (rgb >> 8) & 0xFF;
                                int bCol = rgb & 0xFF;
                                
                                // Threshold < 200 catches anti-aliasing
                                if ((rCol + gCol + bCol) / 3 < 200) {
                                    darkPixels++;
                                }
                            }
                        }

                        // Apply the 33% coverage threshold
                        if (totalPixels > 0 && (double) darkPixels / totalPixels > 0.33) {
                            charCode |= dotOffsets[dy][dx];
                        }
                    }
                }
                sb.append((char) charCode);
            }
            sb.append("\n");
        }
        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }

    private class DrawingPanel extends JPanel {
        private BufferedImage image;
        private Graphics2D g2dImage;
        private Point startPoint = null, currentPoint = null;

        public DrawingPanel(int width, int height) {
            setBackground(Color.WHITE);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            g2dImage = image.createGraphics();
            clear();

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    startPoint = e.getPoint();
                    currentPoint = startPoint;
                    if (currentTool == Tool.PENCIL) drawLine(startPoint, startPoint);
                }
                @Override public void mouseDragged(MouseEvent e) {
                    currentPoint = e.getPoint();
                    if (currentTool == Tool.PENCIL) {
                        drawLine(startPoint, currentPoint);
                        startPoint = currentPoint;
                    }
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (currentTool != Tool.PENCIL && startPoint != null) {
                        Graphics2D g = (Graphics2D) g2dImage.create();
                        setupGraphics(g);
                        drawShape(g, startPoint, e.getPoint());
                        g.dispose();
                    }
                    startPoint = null;
                    currentPoint = null;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT); }
        @Override public Dimension getMinimumSize() { return new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT); }

        public void clear() {
            g2dImage.setColor(Color.WHITE);
            g2dImage.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
            repaint();
        }

        public BufferedImage getImage() { return image; }

        private void setupGraphics(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(currentStrokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }

        private void drawLine(Point p1, Point p2) {
            Graphics2D g = (Graphics2D) g2dImage.create();
            setupGraphics(g);
            g.drawLine(p1.x, p1.y, p2.x, p2.y);
            g.dispose();
            repaint();
        }

        private void drawShape(Graphics2D g, Point p1, Point p2) {
            int x = Math.min(p1.x, p2.x), y = Math.min(p1.y, p2.y);
            int w = Math.abs(p1.x - p2.x), h = Math.abs(p1.y - p2.y);
            switch (currentTool) {
                case RECTANGLE -> g.drawRect(x, y, w, h);
                case OVAL -> g.drawOval(x, y, w, h);
                case LINE -> g.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) g.drawImage(image, 0, 0, this);
            if (startPoint != null && currentPoint != null && currentTool != Tool.PENCIL) {
                Graphics2D g2 = (Graphics2D) g.create();
                setupGraphics(g2);
                drawShape(g2, startPoint, currentPoint);
                g2.dispose();
            }
        }
    }

    // ==========================================
    // TAB 2: MANUAL GRID EDITOR
    // ==========================================
    private JPanel createManualGridEditorPanel() {
        initGrid();
        
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.setBackground(new Color(22, 22, 24));

        viewportPanel = new JPanel(new BorderLayout());
        viewportPanel.setBackground(new Color(18, 18, 20));
        
        colorPanel = new JPanel(null);
        colorPanel.setBackground(new Color(18, 18, 20));
        
        canvasPanel = new GridCanvasPanel();
        JPanel canvasWrapper = new JPanel(new GridBagLayout());
        canvasWrapper.setBackground(new Color(18, 18, 20));
        canvasWrapper.add(canvasPanel);
        
        viewportPanel.add(colorPanel, BorderLayout.WEST);
        viewportPanel.add(canvasWrapper, BorderLayout.CENTER);

        gridScrollPane = new JScrollPane(viewportPanel);
        gridScrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 80), 1));
        gridScrollPane.getViewport().setBackground(new Color(18, 18, 20));
        gridScrollPane.getVerticalScrollBar().setUnitIncrement(20);
        gridScrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        topPanel = buildGridTopPanel();
        palettePanel = buildGridPalette();
        
        root.add(topPanel, BorderLayout.NORTH);
        root.add(palettePanel, BorderLayout.WEST);
        root.add(gridScrollPane, BorderLayout.CENTER);
        root.add(buildGridBottomPanel(), BorderLayout.SOUTH);

        refreshDimensions();
        setupGridKeyBindings();
        
        return root;
    }

    private void initGrid() {
        grid = new char[rows][cols];
        rowColors = new int[rows];
        for (int i = 0; i < rows; i++) {
            rowColors[i] = 0;
            for (int j = 0; j < cols; j++) grid[i][j] = BLANK;
        }
    }

    private void setupGridKeyBindings() {
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        am.put("undo", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { undo(); }});
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        am.put("redo", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { redo(); }});
    }

    private void togglePreviewMode(boolean preview) {
        isPreviewMode = preview;
        topPanel.setVisible(!preview);
        palettePanel.setVisible(!preview);
        
        if (preview) {
            colorPanel.setVisible(false);
            gridScrollPane.setBorder(null); 
            viewportPanel.setBackground(new Color(18, 18, 20));
        } else {
            colorPanel.setVisible(true);
            gridScrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 80), 1));
        }
        
        undoBtn.setVisible(!preview);
        redoBtn.setVisible(!preview);
        clearBtn.setVisible(!preview);
        eraserBtn.setVisible(!preview);
        copyBtn.setVisible(!preview);
        pasteBtn.setVisible(!preview);
        saveBtn.setVisible(!preview);
        previewBtn.setVisible(!preview);
        backBtn.setVisible(preview);
        
        canvasPanel.refreshSize();
        revalidate();
        repaint();
    }

    private JPanel buildGridTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBackground(new Color(22, 22, 24));

        rowSpinner = new JSpinner(new SpinnerNumberModel(rows, 4, 200, 1));
        colSpinner = new JSpinner(new SpinnerNumberModel(cols, 4, 200, 1));
        styleSpinner(rowSpinner);
        styleSpinner(colSpinner);

        JButton applyBtn = new JButton("Apply Dimensions");
        styleButton(applyBtn);
        applyBtn.addActionListener(e -> resizeGrid((int) rowSpinner.getValue(), (int) colSpinner.getValue()));

        showColorBtn = new JToggleButton("Show Color");
        styleToggleButton(showColorBtn);
        showColorBtn.addActionListener(e -> {
            showColorsInCreateMode = showColorBtn.isSelected();
            canvasPanel.repaint();
        });

        JLabel lblRows = new JLabel("Rows:");
        lblRows.setForeground(Color.LIGHT_GRAY);
        JLabel lblCols = new JLabel("Columns:");
        lblCols.setForeground(Color.LIGHT_GRAY);

        panel.add(lblRows); panel.add(rowSpinner);
        panel.add(lblCols); panel.add(colSpinner);
        panel.add(applyBtn); panel.add(showColorBtn);
        return panel;
    }

    private JPanel buildGridBottomPanel() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(new Color(22, 22, 24));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftControls.setBackground(new Color(22, 22, 24));

        undoBtn = new JButton("Undo"); styleButton(undoBtn); undoBtn.setEnabled(false); undoBtn.addActionListener(e -> undo());
        redoBtn = new JButton("Redo"); styleButton(redoBtn); redoBtn.setEnabled(false); redoBtn.addActionListener(e -> redo());
        
        clearBtn = new JButton("Clear"); styleButton(clearBtn);
        clearBtn.addActionListener(e -> {
            for (int r = 0; r < rows; r++) {
                rowColors[r] = 0; updateColorButton(r);
                for (int c = 0; c < cols; c++) grid[r][c] = BLANK;
            }
            clearHistory(); canvasPanel.repaint();
        });

        eraserBtn = new JToggleButton("Eraser");
        styleToggleButton(eraserBtn);
        eraserBtn.addActionListener(e -> {
            if (eraserBtn.isSelected()) {
                activeBrush = BLANK;
                paletteButtonGroup.clearSelection();
                for (JToggleButton btn : brailleDots) btn.setSelected(false);
            }
            updateStatus();
            canvasPanel.requestFocusInWindow();
        });

        copyBtn = new JButton("Copy"); styleButton(copyBtn); copyBtn.addActionListener(e -> copyToClipboard());
        pasteBtn = new JButton("Paste"); styleButton(pasteBtn); pasteBtn.addActionListener(e -> pasteFromClipboard());
        saveBtn = new JButton("Save .txt"); styleButton(saveBtn); saveBtn.addActionListener(e -> saveToFile());
        previewBtn = new JButton("Preview"); styleButton(previewBtn); previewBtn.addActionListener(e -> togglePreviewMode(true));
        
        backBtn = new JButton("Return to Create Mode"); styleButton(backBtn); backBtn.setVisible(false); backBtn.addActionListener(e -> togglePreviewMode(false));

        leftControls.add(undoBtn); leftControls.add(redoBtn); leftControls.add(clearBtn); leftControls.add(eraserBtn);
        leftControls.add(copyBtn); leftControls.add(pasteBtn); leftControls.add(saveBtn); leftControls.add(previewBtn);
        leftControls.add(backBtn);

        statusLabel = new JLabel(" Ready");
        statusLabel.setForeground(new Color(0, 229, 255));
        statusLabel.setFont(pickFont(Font.PLAIN, 14));

        bar.add(leftControls, BorderLayout.WEST);
        bar.add(statusLabel, BorderLayout.CENTER);
        updateStatus();
        return bar;
    }

    private void resizeGrid(int newRows, int newCols) {
        int[] oldColors = rowColors.clone();
        char[][] newGrid = new char[newRows][newCols];
        rowColors = new int[newRows];
        
        for (int r = 0; r < newRows; r++) {
            rowColors[r] = (r < rows) ? oldColors[r] : 0;
            for (int c = 0; c < newCols; c++) {
                newGrid[r][c] = (r < rows && c < cols) ? grid[r][c] : BLANK;
            }
        }
        grid = newGrid;
        rows = newRows; cols = newCols;
        rowSpinner.setValue(rows); colSpinner.setValue(cols);
        clearHistory(); refreshDimensions();
    }

    private void refreshDimensions() {
        canvasPanel.refreshSize();
        colorPanel.removeAll();
        axisColorBoxes = new JButton[rows];
        int cellH = canvasPanel.cellH, padTop = canvasPanel.padTop;
        
        for (int r = 0; r < rows; r++) {
            JButton btn = new JButton();
            btn.setFocusPainted(false); btn.setFont(new Font("SansSerif", Font.BOLD, 12));
            btn.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 65)));
            btn.setOpaque(true); btn.setContentAreaFilled(false); 
            btn.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
                @Override public void paint(Graphics g, JComponent c) {
                    g.setColor(c.getBackground()); g.fillRect(0, 0, c.getWidth(), c.getHeight()); super.paint(g, c);
                }
            });
            btn.setBounds(5, padTop + r * cellH + 1, 45, cellH - 2);
            int rowIdx = r;
            btn.addActionListener(ev -> { rowColors[rowIdx] = activeColorIndex; updateColorButton(rowIdx); canvasPanel.repaint(); });
            axisColorBoxes[r] = btn;
            colorPanel.add(btn);
            updateColorButton(r);
        }
        colorPanel.setPreferredSize(new Dimension(70, canvasPanel.getPreferredSize().height));
        viewportPanel.revalidate(); viewportPanel.repaint();
    }

    private void updateColorButton(int r) {
        JButton btn = axisColorBoxes[r];
        int idx = rowColors[r];
        if (idx == 0) { btn.setBackground(new Color(30, 30, 35)); btn.setText(""); btn.setForeground(Color.LIGHT_GRAY); } 
        else {
            btn.setBackground(COLORS[idx]); btn.setText(String.valueOf(idx));
            btn.setForeground(isDark(COLORS[idx]) ? Color.BLACK : Color.WHITE);
        }
    }

    private boolean isDark(Color c) {
        return (c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114) < 128;
    }

    private JPanel buildGridPalette() {
        JPanel outer = new JPanel(new BorderLayout(0, 10));
        outer.setBackground(new Color(22, 22, 24));
        outer.setBorder(new EmptyBorder(0, 0, 0, 10));

        JLabel title = new JLabel("Symbols & Colors");
        title.setForeground(new Color(220, 220, 230)); title.setFont(pickFont(Font.BOLD, 16));
        outer.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void paintTabBackground(Graphics g, int tp, int ti, int x, int y, int w, int h, boolean s) {
                g.setColor(s ? new Color(45, 45, 52) : new Color(30, 30, 35)); g.fillRect(x, y, w, h);
            }
            @Override protected void paintTabBorder(Graphics g, int tp, int ti, int x, int y, int w, int h, boolean s) {}
            @Override protected void paintContentBorder(Graphics g, int tp, int si) {}
        });
        
        tabs.addTab("Braille", createBrailleBuilderPanel());
        tabs.addTab("Letters", createCharArrayPanel(PALETTE_LETTERS));
        tabs.addTab("Symbols", createCharArrayPanel(PALETTE_SYMBOLS));
        tabs.addTab("Colors", createColorPalettePanel());
        
        outer.add(tabs, BorderLayout.CENTER);
        return outer;
    }

    private JPanel createBrailleBuilderPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBackground(new Color(30, 30, 35));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel infoLabel = new JLabel("Toggle dots to build symbol:");
        infoLabel.setForeground(Color.LIGHT_GRAY);
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(infoLabel, BorderLayout.NORTH);

        JPanel dotGrid = new JPanel(new GridLayout(4, 2, 15, 15));
        dotGrid.setBackground(new Color(30, 30, 35));
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setBackground(new Color(30, 30, 35));
        centerWrapper.add(dotGrid);

        for (int i = 0; i < 8; i++) {
            JToggleButton dotBtn = new JToggleButton();
            dotBtn.setPreferredSize(new Dimension(44, 44));
            dotBtn.setBackground(new Color(45, 45, 52));
            dotBtn.setFocusPainted(false);
            dotBtn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 80), 1));
            
            dotBtn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI() {
                @Override public void paint(Graphics g, JComponent c) {
                    AbstractButton b = (AbstractButton) c;
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (b.isSelected()) g2.setColor(new Color(0, 120, 215));
                    else if (b.getModel().isRollover()) g2.setColor(new Color(60, 60, 70));
                    else g2.setColor(b.getBackground());
                    g2.fillRect(0, 0, c.getWidth(), c.getHeight());
                    
                    int centerX = c.getWidth() / 2, centerY = c.getHeight() / 2, radius = 10;
                    if (b.isSelected()) { g2.setColor(Color.WHITE); g2.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2); } 
                    else { g2.setColor(new Color(100, 100, 110)); g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2); }
                    g2.dispose();
                }
            });

            if ((DOT_OFFSETS[i] & (int)activeBrush - 0x2800) != 0) dotBtn.setSelected(true);

            dotBtn.addActionListener(e -> {
                int code = 0x2800;
                for (int j = 0; j < 8; j++) if (brailleDots[j].isSelected()) code += DOT_OFFSETS[j];
                activeBrush = (char) code;
                eraserBtn.setSelected(false); paletteButtonGroup.clearSelection(); updateStatus();
            });

            brailleDots[i] = dotBtn;
            dotGrid.add(dotBtn);
        }
        
        JButton clearDotsBtn = new JButton("Clear Dots");
        styleButton(clearDotsBtn);
        clearDotsBtn.addActionListener(e -> {
            for (JToggleButton btn : brailleDots) btn.setSelected(false);
            activeBrush = BLANK; eraserBtn.setSelected(true); updateStatus();
        });

        JPanel bottomWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomWrapper.setBackground(new Color(30, 30, 35));
        bottomWrapper.add(clearDotsBtn);

        mainPanel.add(centerWrapper, BorderLayout.CENTER);
        mainPanel.add(bottomWrapper, BorderLayout.SOUTH);
        return mainPanel;
    }

    private JScrollPane createCharArrayPanel(char[] symbols) {
        JPanel gridPanel = new JPanel(new GridLayout(0, 4, 6, 6));
        gridPanel.setBackground(new Color(30, 30, 35));
        gridPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (char sym : symbols) {
            String label = (sym == ' ') ? "␣" : String.valueOf(sym);
            JToggleButton btn = new JToggleButton(label);
            btn.setFont(pickFont(Font.PLAIN, 20));
            btn.setPreferredSize(new Dimension(44, 44));
            btn.setFocusPainted(false);
            btn.setBackground(new Color(40, 40, 45));
            btn.setForeground(new Color(235, 235, 240));
            btn.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 65), 1));
            
            btn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI() {
                @Override public void paint(Graphics g, JComponent c) {
                    AbstractButton b = (AbstractButton) c;
                    g.setColor(b.isSelected() ? new Color(0, 120, 215) : b.getBackground());
                    g.fillRect(0, 0, c.getWidth(), c.getHeight()); super.paint(g, c);
                }
            });

            if (sym == activeBrush) btn.setSelected(true);
            btn.addActionListener(e -> {
                activeBrush = sym; eraserBtn.setSelected(false); updateStatus();
            });
            paletteButtonGroup.add(btn);
            gridPanel.add(btn);
        }
        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBorder(null); scroll.getViewport().setBackground(new Color(30, 30, 35));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JScrollPane createColorPalettePanel() {
        JPanel gridPanel = new JPanel(new GridLayout(0, 3, 6, 6));
        gridPanel.setBackground(new Color(30, 30, 35));
        gridPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (int i = 0; i <= 9; i++) {
            final int idx = i;
            JToggleButton btn = new JToggleButton(i == 0 ? "None" : String.valueOf(i));
            btn.setFont(new Font("SansSerif", Font.BOLD, 14));
            btn.setPreferredSize(new Dimension(56, 44));
            btn.setFocusPainted(false);
            btn.setForeground(i == 0 || isDark(COLORS[i]) ? Color.WHITE : Color.BLACK);
            btn.setBackground(i == 0 ? new Color(40, 40, 45) : COLORS[i]);
            btn.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 65), 1));
            
            btn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI() {
                @Override public void paint(Graphics g, JComponent c) {
                    AbstractButton b = (AbstractButton) c;
                    g.setColor(b.getBackground()); g.fillRect(0, 0, c.getWidth(), c.getHeight());
                    if (b.isSelected()) {
                        g.setColor(Color.WHITE); g.drawRect(1, 1, c.getWidth()-3, c.getHeight()-3);
                        g.drawRect(2, 2, c.getWidth()-5, c.getHeight()-5);
                    }
                    super.paint(g, c);
                }
            });

            if (i == activeColorIndex) btn.setSelected(true);
            btn.addActionListener(e -> { activeColorIndex = idx; updateStatus(); });
            colorButtonGroup.add(btn);
            gridPanel.add(btn);
        }
        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBorder(null); scroll.getViewport().setBackground(new Color(30, 30, 35));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private void styleButton(JButton btn) {
        btn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        btn.setFocusPainted(false); btn.setForeground(Color.WHITE);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 80), 1), new EmptyBorder(6, 12, 6, 12)));
        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override public void paint(Graphics g, JComponent c) {
                AbstractButton b = (AbstractButton) c;
                if (!b.isEnabled()) g.setColor(new Color(30, 30, 35));
                else if (b.getModel().isPressed()) g.setColor(new Color(35, 35, 40));
                else if (b.getModel().isRollover()) g.setColor(new Color(60, 60, 70));
                else g.setColor(new Color(45, 45, 52));
                g.fillRect(0, 0, c.getWidth(), c.getHeight()); super.paint(g, c);
            }
        });
    }

    private void styleToggleButton(JToggleButton btn) {
        btn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        btn.setFocusPainted(false); btn.setForeground(Color.WHITE);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 80), 1), new EmptyBorder(6, 12, 6, 12)));
        btn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI() {
            @Override public void paint(Graphics g, JComponent c) {
                AbstractButton b = (AbstractButton) c;
                if (b.isSelected()) g.setColor(new Color(0, 120, 215));
                else if (b.getModel().isRollover()) g.setColor(new Color(60, 60, 70));
                else g.setColor(new Color(45, 45, 52));
                g.fillRect(0, 0, c.getWidth(), c.getHeight()); super.paint(g, c);
            }
        });
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(new Color(45, 45, 52)); tf.setForeground(Color.WHITE); tf.setCaretColor(Color.WHITE);
        }
    }

    private void updateStatus() {
        String brushStr = (activeBrush == ' ') ? "␣" : String.valueOf(activeBrush);
        statusLabel.setText(String.format("Brush: '%s' (U+%04X) | Active Color: %s | Canvas: %d × %d", 
                brushStr, (int) activeBrush, activeColorIndex == 0 ? "None" : String.valueOf(activeColorIndex), rows, cols));
    }

    private void clearHistory() { undoHistory.clear(); redoHistory.clear(); updateUndoRedoButtons(); }
    private void undo() {
        if (undoHistory.isEmpty()) return;
        List<CellEdit> stroke = undoHistory.remove(undoHistory.size() - 1);
        redoHistory.add(stroke);
        for (int i = stroke.size() - 1; i >= 0; i--) {
            CellEdit edit = stroke.get(i); grid[edit.r][edit.c] = edit.prev;
        }
        canvasPanel.repaint(); updateUndoRedoButtons();
    }
    private void redo() {
        if (redoHistory.isEmpty()) return;
        List<CellEdit> stroke = redoHistory.remove(redoHistory.size() - 1);
        undoHistory.add(stroke);
        for (CellEdit edit : stroke) grid[edit.r][edit.c] = edit.next;
        canvasPanel.repaint(); updateUndoRedoButtons();
    }
    private void updateUndoRedoButtons() {
        if (undoBtn != null) undoBtn.setEnabled(!undoHistory.isEmpty());
        if (redoBtn != null) redoBtn.setEnabled(!redoHistory.isEmpty());
    }

    private String exportText() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            if (rowColors[r] > 0) sb.append("$").append(rowColors[r]).append(" ");
            for (int c = 0; c < cols; c++) sb.append(grid[r][c]);
            sb.append('\n');
        }
        return sb.toString();
    }

    private void copyToClipboard() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(exportText()), null);
        statusLabel.setText("Copied canvas to clipboard.");
    }

    private void pasteFromClipboard() {
        try {
            String data = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (data == null || data.isEmpty()) return;
            String[] lines = data.split("\\r?\\n");
            int newRows = lines.length, newCols = 0;
            int[] extractedColors = new int[newRows];

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i]; extractedColors[i] = 0;
                if (line.matches("^\\$[1-9]\\s+.*")) {
                    int spaceIdx = line.indexOf(' ');
                    extractedColors[i] = Integer.parseInt(line.substring(1, spaceIdx));
                    line = line.substring(spaceIdx + 1);
                } else if (line.matches("^\\$\\S+\\s+.*")) {
                    line = line.substring(line.indexOf(' ') + 1);
                }
                lines[i] = line; newCols = Math.max(newCols, lines[i].length());
            }
            newRows = Math.max(4, newRows); newCols = Math.max(4, newCols);
            resizeGrid(newRows, newCols);
            
            for (int r = 0; r < newRows; r++) {
                rowColors[r] = extractedColors[r]; updateColorButton(r);
                String line = lines[r];
                for (int c = 0; c < newCols; c++) grid[r][c] = (c < line.length()) ? line.charAt(c) : BLANK;
            }
            clearHistory(); canvasPanel.repaint(); statusLabel.setText("Pasted successfully.");
        } catch (Exception ex) { statusLabel.setText("Failed to paste."); }
    }

    private void saveToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("braille_art.txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter out = new PrintWriter(chooser.getSelectedFile(), "UTF-8")) {
                out.print(exportText()); statusLabel.setText("Saved to file.");
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error saving file."); }
        }
    }

    private class GridCanvasPanel extends JPanel {
        public final int cellW = 16, cellH = 26;
        public int padLeft = 30, padTop = 24, padRight = 10, padBottom = 10;
        private boolean dragging = false;
        private int hoverRow = -1, hoverCol = -1, cursorR = 0, cursorC = 0;

        GridCanvasPanel() {
            setBackground(new Color(18, 18, 20));
            setFont(pickFont(Font.PLAIN, 16)); 
            setFocusable(true);
            refreshSize();

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (isPreviewMode) return;
                    requestFocusInWindow(); dragging = true; currentStroke = new ArrayList<>();
                    int c = (e.getX() - padLeft) / cellW, r = (e.getY() - padTop) / cellH;
                    if (r >= 0 && r < rows && c >= 0 && c < cols) { cursorR = r; cursorC = c; }
                    paintAt(e);
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (isPreviewMode) return;
                    if (dragging) paintAt(e); updateHover(e);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (isPreviewMode) return;
                    dragging = false;
                    if (currentStroke != null && !currentStroke.isEmpty()) {
                        undoHistory.add(currentStroke); redoHistory.clear(); currentStroke = null; updateUndoRedoButtons();
                    }
                }
                @Override public void mouseMoved(MouseEvent e) {
                    if (isPreviewMode) return; updateHover(e);
                }
                @Override public void mouseExited(MouseEvent e) { hoverRow = -1; hoverCol = -1; repaint(); }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
            
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (isPreviewMode) return;
                    if (e.getKeyCode() == KeyEvent.VK_UP) { cursorR = Math.max(0, cursorR - 1); repaint(); }
                    else if (e.getKeyCode() == KeyEvent.VK_DOWN) { cursorR = Math.min(rows - 1, cursorR + 1); repaint(); }
                    else if (e.getKeyCode() == KeyEvent.VK_LEFT) { cursorC = Math.max(0, cursorC - 1); repaint(); }
                    else if (e.getKeyCode() == KeyEvent.VK_RIGHT) { cursorC = Math.min(cols - 1, cursorC + 1); repaint(); }
                }
                @Override public void keyTyped(KeyEvent e) {
                    if (isPreviewMode) return;
                    char c = e.getKeyChar();
                    if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) c = activeBrush;
                    else if (c < 32 || c >= 127) return;
                    
                    activeBrush = c; eraserBtn.setSelected(false);
                    boolean found = false;
                    for (AbstractButton btn : Collections.list(paletteButtonGroup.getElements())) {
                        String txt = btn.getText();
                        if (txt.length() > 0 && txt.charAt(0) == c) { btn.setSelected(true); found = true; break; }
                    }
                    if (!found) paletteButtonGroup.clearSelection();
                    updateStatus();
                    
                    char prev = grid[cursorR][cursorC]; grid[cursorR][cursorC] = activeBrush;
                    List<CellEdit> stroke = new ArrayList<>(); stroke.add(new CellEdit(cursorR, cursorC, prev, activeBrush));
                    undoHistory.add(stroke); redoHistory.clear(); updateUndoRedoButtons();
                    cursorC = Math.min(cols - 1, cursorC + 1); repaint();
                }
            });
        }

        public void refreshSize() {
            if (isPreviewMode) { padLeft = 0; padTop = 0; padRight = 0; padBottom = 0; } 
            else { padLeft = 30; padTop = 24; padRight = 10; padBottom = 10; }
            setPreferredSize(new Dimension(padLeft + cols * cellW + padRight, padTop + rows * cellH + padBottom));
            revalidate(); repaint();
        }

        private void updateHover(MouseEvent e) {
            int c = (e.getX() - padLeft) / cellW, r = (e.getY() - padTop) / cellH;
            if (r >= 0 && r < rows && c >= 0 && c < cols) {
                if (r != hoverRow || c != hoverCol) { hoverRow = r; hoverCol = c; repaint(); }
            } else if (hoverRow != -1 || hoverCol != -1) { hoverRow = -1; hoverCol = -1; repaint(); }
        }

        private void paintAt(MouseEvent e) {
            int c = (e.getX() - padLeft) / cellW, r = (e.getY() - padTop) / cellH;
            if (r < 0 || r >= rows || c < 0 || c >= cols || grid[r][c] == activeBrush) return;
            char prev = grid[r][c]; grid[r][c] = activeBrush;
            if (currentStroke != null) currentStroke.add(new CellEdit(r, c, prev, activeBrush));
            repaint(padLeft + c * cellW, padTop + r * cellH, cellW, cellH);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (!isPreviewMode) {
                g.setColor(new Color(45, 45, 55));
                for (int r = 0; r <= rows; r++) g.drawLine(padLeft, padTop + r * cellH, padLeft + cols * cellW, padTop + r * cellH);
                for (int c = 0; c <= cols; c++) g.drawLine(padLeft + c * cellW, padTop, padLeft + c * cellW, padTop + rows * cellH);

                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                FontMetrics fm = g.getFontMetrics();
                
                for (int c = 0; c < cols; c++) {
                    if (c % 5 != 0 && cols > 40) continue; 
                    g.setColor(c % 5 == 0 ? new Color(180, 180, 190) : new Color(100, 100, 110));
                    g.drawString(String.valueOf(c), padLeft + c * cellW + (cellW - fm.stringWidth(String.valueOf(c))) / 2, padTop - 6); 
                }
                for (int r = 0; r < rows; r++) {
                    if (r % 5 != 0 && rows > 40) continue;
                    g.setColor(r % 5 == 0 ? new Color(180, 180, 190) : new Color(100, 100, 110));
                    g.drawString(String.valueOf(r + 1), padLeft - 8 - fm.stringWidth(String.valueOf(r + 1)), padTop + r * cellH + (cellH + fm.getAscent()) / 2 - 2); 
                }

                if (hoverRow >= 0 && hoverCol >= 0) {
                    g.setColor(new Color(0, 150, 255, dragging ? 80 : 40));
                    g.fillRect(padLeft + hoverCol * cellW, padTop + hoverRow * cellH, cellW, cellH);
                }
                if (hasFocus()) {
                    g.setColor(Color.WHITE);
                    g.drawRect(padLeft + cursorC * cellW, padTop + cursorR * cellH, cellW - 1, cellH - 1);
                }
            }

            if (isPreviewMode) {
                g.setFont(pickFont(Font.PLAIN, 24));
                FontMetrics symFm = g.getFontMetrics(); int ascent = symFm.getAscent();
                for (int r = 0; r < rows; r++) {
                    g.setColor(rowColors[r] == 0 ? new Color(0, 229, 255) : COLORS[rowColors[r]]);
                    for (int c = 0; c < cols; c++) {
                        if (grid[r][c] == BLANK || grid[r][c] == '\0') continue;
                        g.drawString(String.valueOf(grid[r][c]), padLeft + c * cellW, padTop + r * cellH + ascent - 4);
                    }
                }
            } else {
                g.setFont(getFont());
                FontMetrics symFm = g.getFontMetrics(); int ascent = symFm.getAscent();
                for (int r = 0; r < rows; r++) {
                    g.setColor(showColorsInCreateMode ? (rowColors[r] == 0 ? new Color(0, 229, 255) : COLORS[rowColors[r]]) : new Color(0, 229, 255));
                    for (int c = 0; c < cols; c++) {
                        if (grid[r][c] == BLANK || grid[r][c] == '\0') continue;
                        String s = String.valueOf(grid[r][c]);
                        g.drawString(s, padLeft + c * cellW + (cellW - symFm.stringWidth(s)) / 2, padTop + r * cellH + (cellH - symFm.getHeight()) / 2 + ascent);
                    }
                }
            }
            g.dispose();
        }
    }

    private static Font pickFont(int style, int size) {
        String[] candidates = {"Segoe UI Symbol", "Segoe UI", "DejaVu Sans", "Noto Sans Symbols 2", "Dialog", "SansSerif"};
        String probe = "\u28FF\u2801\u2800";
        for (String name : candidates) {
            Font f = new Font(name, style, size);
            if (f.canDisplay('\u28FF') && f.canDisplay('\u2801') && f.canDisplay('\u2800')) {
                if (f.canDisplayUpTo(probe) == -1) return f;
            }
        }
        return new Font("SansSerif", style, size);
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new PaintToBrailleApp().setVisible(true));
    }
}