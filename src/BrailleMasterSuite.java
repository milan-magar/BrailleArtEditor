import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;

public class BrailleMasterSuite extends JFrame {

    public BrailleMasterSuite() {
        super("Braille Master Suite - Ultimate Edition");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 850);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 14));

        tabs.addTab("Image to Braille", new ImageToBraillePanel());
        tabs.addTab("Paint to Braille", new PaintToBraillePanel());
        tabs.addTab("Manual Grid Editor", new ManualGridEditorPanel());
        tabs.addTab("Debugger", new DebuggerPanel());

        add(tabs, BorderLayout.CENTER);
    }

    // ==================================================================================
    // TAB 1: IMAGE TO BRAILLE
    // ==================================================================================
    private class ImageToBraillePanel extends JPanel {
        private JTextField colsField, rowsField;
        private JButton loadBtn, autoCropBtn, manualCropBtn, resetCropBtn, convertBtn, copyBtn;
        private JLabel imageLabel;
        private JTextArea outputArea;
        private JCheckBox invertCheck;
        private JSlider thresholdSlider;
        
        private BufferedImage uncroppedImage, originalImage;

        public ImageToBraillePanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            loadBtn = new JButton("Load Image...");
            loadBtn.addActionListener(e -> loadImage());

            autoCropBtn = new JButton("Auto-Crop");
            autoCropBtn.setEnabled(false);
            autoCropBtn.addActionListener(e -> applyAutoCrop());

            manualCropBtn = new JButton("Manual Crop");
            manualCropBtn.setEnabled(false);
            manualCropBtn.addActionListener(e -> openManualCropDialog());

            resetCropBtn = new JButton("Reset Crop");
            resetCropBtn.setEnabled(false);
            resetCropBtn.addActionListener(e -> {
                if (uncroppedImage != null) {
                    originalImage = uncroppedImage;
                    updatePreview();
                }
            });

            colsField = new JTextField("25", 4);
            rowsField = new JTextField("19", 4);
            invertCheck = new JCheckBox("Invert");
            
            thresholdSlider = new JSlider(1, 255, 128);
            thresholdSlider.setPreferredSize(new Dimension(100, 30));
            
            ChangeListener previewListener = e -> updatePreview();
            thresholdSlider.addChangeListener(previewListener);
            invertCheck.addActionListener(e -> updatePreview());

            convertBtn = new JButton("Convert");
            convertBtn.setEnabled(false);
            convertBtn.addActionListener(e -> convertToBraille());

            copyBtn = new JButton("Copy");
            copyBtn.setEnabled(false);
            copyBtn.addActionListener(e -> {
                if (!outputArea.getText().isEmpty()) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(outputArea.getText()), null);
                    JOptionPane.showMessageDialog(this, "Copied to clipboard!");
                }
            });

            topPanel.add(loadBtn); topPanel.add(autoCropBtn); topPanel.add(manualCropBtn); topPanel.add(resetCropBtn);
            topPanel.add(new JLabel("Cols:")); topPanel.add(colsField);
            topPanel.add(new JLabel("Rows:")); topPanel.add(rowsField);
            topPanel.add(invertCheck); topPanel.add(new JLabel("Threshold:")); topPanel.add(thresholdSlider);
            topPanel.add(convertBtn); topPanel.add(copyBtn);
            add(topPanel, BorderLayout.NORTH);

            imageLabel = new JLabel("", SwingConstants.CENTER);
            imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            imageLabel.setPreferredSize(new Dimension(450, 350));
            JScrollPane imageScroll = new JScrollPane(imageLabel);
            imageScroll.setBorder(BorderFactory.createTitledBorder("Image Preview"));

            outputArea = new JTextArea();
            outputArea.setFont(pickBrailleFont(14));
            outputArea.setEditable(false);
            outputArea.setBackground(new Color(30, 30, 30));
            outputArea.setForeground(new Color(0, 229, 255)); 
            JScrollPane outputScroll = new JScrollPane(outputArea);
            outputScroll.setBorder(BorderFactory.createTitledBorder("Braille Output"));

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, imageScroll, outputScroll);
            splitPane.setDividerLocation(550);
            splitPane.setResizeWeight(0.5);
            add(splitPane, BorderLayout.CENTER);
        }

        private void loadImage() {
            JFileChooser chooser = new JFileChooser();
            File dl = new File(System.getProperty("user.home"), "Downloads");
            chooser.setCurrentDirectory(dl.exists() ? dl : new File(System.getProperty("user.home")));
            chooser.setFileFilter(new FileNameExtensionFilter("Image Files", "jpg", "jpeg", "png", "gif", "bmp"));
            
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    BufferedImage loaded = ImageIO.read(chooser.getSelectedFile());
                    if (loaded != null) {
                        uncroppedImage = loaded;
                        originalImage = loaded;
                        autoCropBtn.setEnabled(true);
                        manualCropBtn.setEnabled(true);
                        resetCropBtn.setEnabled(true);
                        convertBtn.setEnabled(true);
                        copyBtn.setEnabled(true);
                        updatePreview();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error loading image.");
                }
            }
        }

        private void applyAutoCrop() {
            if (originalImage == null) return;
            int width = originalImage.getWidth(), height = originalImage.getHeight();
            int top = height, left = width, bottom = 0, right = 0;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = originalImage.getRGB(x, y);
                    int alpha = (rgb >> 24) & 0xFF;
                    if (alpha > 0) { // Ignore transparent pixels
                        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                        if (r < 240 || g < 240 || b < 240) {
                            if (x < left) left = x; if (x > right) right = x;
                            if (y < top) top = y; if (y > bottom) bottom = y;
                        }
                    }
                }
            }
            if (left >= right || top >= bottom) return;
            
            int cw = right - left + 1, ch = bottom - top + 1;
            BufferedImage cropped = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = cropped.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0, 0, cw, ch);
            g.drawImage(originalImage.getSubimage(left, top, cw, ch), 0, 0, null);
            g.dispose();
            originalImage = cropped;
            updatePreview();
        }

        private void openManualCropDialog() {
            if (originalImage == null) return;
            JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Crop", true);
            dialog.setLayout(new BorderLayout());

            CropPanel cropPanel = new CropPanel(originalImage);
            JScrollPane scroll = new JScrollPane(cropPanel);
            scroll.setBorder(null);
            dialog.add(scroll, BorderLayout.CENTER);

            JPanel btnPanel = new JPanel();
            JButton applyBtn = new JButton("Apply Crop");
            applyBtn.addActionListener(e -> {
                Rectangle rect = cropPanel.getSelection();
                if (rect != null && rect.width > 0 && rect.height > 0) {
                    double scaleX = (double) originalImage.getWidth() / cropPanel.getDisplayedWidth();
                    double scaleY = (double) originalImage.getHeight() / cropPanel.getDisplayedHeight();
                    
                    int ox = (int) (rect.x * scaleX), oy = (int) (rect.y * scaleY);
                    int ow = (int) (rect.width * scaleX), oh = (int) (rect.height * scaleY);

                    ox = Math.max(0, Math.min(ox, originalImage.getWidth() - 1));
                    oy = Math.max(0, Math.min(oy, originalImage.getHeight() - 1));
                    ow = Math.min(originalImage.getWidth() - ox, ow);
                    oh = Math.min(originalImage.getHeight() - oy, oh);

                    if (ow > 0 && oh > 0) {
                        BufferedImage cropped = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = cropped.createGraphics();
                        g.drawImage(originalImage.getSubimage(ox, oy, ow, oh), 0, 0, null);
                        g.dispose();
                        originalImage = cropped;
                        updatePreview();
                        dialog.dispose();
                    }
                }
            });
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.addActionListener(e -> dialog.dispose());
            btnPanel.add(applyBtn); btnPanel.add(cancelBtn);
            dialog.add(btnPanel, BorderLayout.SOUTH);
            dialog.setSize(900, 750);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        }

        private void updatePreview() {
            if (originalImage == null) return;
            BufferedImage scaled = getScaledImage(originalImage, 450, 350);
            int threshold = thresholdSlider.getValue();
            boolean invert = invertCheck.isSelected();
            
            BufferedImage bwImage = new BufferedImage(450, 350, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 350; y++) {
                for (int x = 0; x < 450; x++) {
                    int rgb = scaled.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    boolean isDark = ((0.299 * r) + (0.587 * g) + (0.114 * b)) < threshold;
                    if (invert) isDark = !isDark;
                    bwImage.setRGB(x, y, isDark ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
                }
            }
            imageLabel.setIcon(new ImageIcon(bwImage));
        }

        private void convertToBraille() {
            if (originalImage == null) return;
            try {
                int tc = Integer.parseInt(colsField.getText().trim());
                int tr = Integer.parseInt(rowsField.getText().trim());
                int threshold = thresholdSlider.getValue();
                boolean invert = invertCheck.isSelected();

                int tw = tc * 2, th = tr * 4;
                BufferedImage scaled = getScaledImage(originalImage, tw, th);
                StringBuilder sb = new StringBuilder();
                int[][] map = {{0x01, 0x08}, {0x02, 0x10}, {0x04, 0x20}, {0x40, 0x80}};

                for (int r = 0; r < tr; r++) {
                    for (int c = 0; c < tc; c++) {
                        int code = 0x2800;
                        for (int dy = 0; dy < 4; dy++) {
                            for (int dx = 0; dx < 2; dx++) {
                                int rgb = scaled.getRGB((c * 2) + dx, (r * 4) + dy);
                                int red = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                                boolean isDark = ((0.299 * red) + (0.587 * g) + (0.114 * b)) < threshold;
                                if (invert) isDark = !isDark;
                                if (isDark) code |= map[dy][dx];
                            }
                        }
                        sb.append((char) code);
                    }
                    sb.append("\n");
                }
                outputArea.setText(sb.toString());
                outputArea.selectAll();
            } catch (Exception ex) { }
        }
    }

    // ==================================================================================
    // TAB 2: PAINT TO BRAILLE
    // ==================================================================================
    private class PaintToBraillePanel extends JPanel {
        private PaintCanvas canvas;
        private JTextArea outputArea;
        private String currentTool = "PENCIL";
        private int currentStroke = 3;

        public PaintToBraillePanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            ButtonGroup bg = new ButtonGroup();
            JToggleButton pen = createToolBtn("Pencil", "PENCIL", bg); pen.setSelected(true);
            JToggleButton rect = createToolBtn("Rectangle", "RECTANGLE", bg);
            JToggleButton oval = createToolBtn("Oval", "OVAL", bg);
            JToggleButton line = createToolBtn("Line", "LINE", bg);

            top.add(new JLabel("Tools:")); top.add(pen); top.add(rect); top.add(oval); top.add(line);
            top.add(new JSeparator(SwingConstants.VERTICAL));
            top.add(new JLabel("Stroke:"));
            JComboBox<String> strokeCombo = new JComboBox<>(new String[]{"Fine (1px)", "Medium (3px)", "Thick (6px)"});
            strokeCombo.setSelectedIndex(1);
            strokeCombo.addActionListener(e -> {
                switch(strokeCombo.getSelectedIndex()) { case 0 -> currentStroke=1; case 1 -> currentStroke=3; case 2 -> currentStroke=6; }
            });
            top.add(strokeCombo);
            add(top, BorderLayout.NORTH);

            canvas = new PaintCanvas(720, 576);
            JPanel canvasWrapper = new JPanel(new GridBagLayout());
            canvasWrapper.setBackground(Color.LIGHT_GRAY);
            canvasWrapper.add(canvas);
            JScrollPane canvasScroll = new JScrollPane(canvasWrapper);
            add(canvasScroll, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(10, 10));
            JPanel controls = new JPanel();
            JButton clearBtn = new JButton("Clear Canvas");
            clearBtn.addActionListener(e -> canvas.clear());
            JButton convertBtn = new JButton("Convert to Braille (33% Threshold)");
            convertBtn.addActionListener(e -> promptConvert());
            controls.add(clearBtn); controls.add(convertBtn);

            outputArea = new JTextArea(8, 50);
            outputArea.setFont(pickBrailleFont(14));
            outputArea.setEditable(false);
            outputArea.setBackground(new Color(30, 30, 30));
            outputArea.setForeground(new Color(0, 229, 255));
            JScrollPane outScroll = new JScrollPane(outputArea);

            bottom.add(controls, BorderLayout.NORTH);
            bottom.add(outScroll, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);
        }

        private JToggleButton createToolBtn(String text, String tool, ButtonGroup group) {
            JToggleButton btn = new JToggleButton(text);
            btn.addActionListener(e -> currentTool = tool);
            group.add(btn);
            return btn;
        }

        private void promptConvert() {
            JPanel p = new JPanel(new GridLayout(2, 2, 5, 5));
            JSpinner cs = new JSpinner(new SpinnerNumberModel(64, 8, 200, 1));
            JSpinner rs = new JSpinner(new SpinnerNumberModel(32, 8, 100, 1));
            p.add(new JLabel("Cols:")); p.add(cs); p.add(new JLabel("Rows:")); p.add(rs);
            
            if (JOptionPane.showConfirmDialog(this, p, "Dimensions", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                int tc = (int) cs.getValue(), tr = (int) rs.getValue();
                BufferedImage img = canvas.getImage();
                StringBuilder sb = new StringBuilder();
                int[][] map = {{0x01, 0x08}, {0x02, 0x10}, {0x04, 0x20}, {0x40, 0x80}};
                double cw = (double) 720 / tc, ch = (double) 576 / tr;

                for (int r = 0; r < tr; r++) {
                    for (int c = 0; c < tc; c++) {
                        int sx = (int) Math.round(c * cw), sy = (int) Math.round(r * ch);
                        int ex = (int) Math.round((c + 1) * cw), ey = (int) Math.round((r + 1) * ch);
                        int w = Math.max(1, ex - sx), h = Math.max(1, ey - sy);
                        int code = 0x2800;

                        for (int dy = 0; dy < 4; dy++) {
                            for (int dx = 0; dx < 2; dx++) {
                                int dsx = sx + (dx * w) / 2, dex = sx + ((dx + 1) * w) / 2;
                                int dsy = sy + (dy * h) / 4, dey = sy + ((dy + 1) * h) / 4;
                                int total = 0, dark = 0;
                                for (int y = dsy; y < dey && y < 576; y++) {
                                    for (int x = dsx; x < dex && x < 720; x++) {
                                        total++;
                                        int rgb = img.getRGB(x, y);
                                        if ((((rgb>>16)&0xFF)+((rgb>>8)&0xFF)+(rgb&0xFF))/3 < 200) dark++;
                                    }
                                }
                                if (total > 0 && (double) dark / total > 0.33) code |= map[dy][dx];
                            }
                        }
                        sb.append((char) code);
                    }
                    sb.append("\n");
                }
                outputArea.setText(sb.toString());
            }
        }

        private class PaintCanvas extends JPanel {
            private BufferedImage image;
            private Graphics2D g2d;
            private Point start, end;

            public PaintCanvas(int w, int h) {
                setPreferredSize(new Dimension(w, h));
                setMinimumSize(new Dimension(w, h));
                setBackground(Color.WHITE);
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                g2d = image.createGraphics();
                clear();

                MouseAdapter ma = new MouseAdapter() {
                    public void mousePressed(MouseEvent e) {
                        start = e.getPoint(); end = start;
                        if (currentTool.equals("PENCIL")) drawLine(start, start);
                    }
                    public void mouseDragged(MouseEvent e) {
                        end = e.getPoint();
                        if (currentTool.equals("PENCIL")) { drawLine(start, end); start = end; }
                        repaint();
                    }
                    public void mouseReleased(MouseEvent e) {
                        if (!currentTool.equals("PENCIL") && start != null) {
                            Graphics2D g = (Graphics2D) g2d.create();
                            setup(g); int x = Math.min(start.x, e.getX()), y = Math.min(start.y, e.getY());
                            int w = Math.abs(start.x - e.getX()), h = Math.abs(start.y - e.getY());
                            if (currentTool.equals("RECTANGLE")) g.drawRect(x, y, w, h);
                            else if (currentTool.equals("OVAL")) g.drawOval(x, y, w, h);
                            else if (currentTool.equals("LINE")) g.drawLine(start.x, start.y, e.getX(), e.getY());
                            g.dispose();
                        }
                        start = null; end = null; repaint();
                    }
                };
                addMouseListener(ma); addMouseMotionListener(ma);
            }

            public void clear() {
                g2d.setColor(Color.WHITE); 
                g2d.fillRect(0, 0, image.getWidth(), image.getHeight()); 
                repaint();
            }

            public BufferedImage getImage() { return image; }

            private void setup(Graphics2D g) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(currentStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            }

            private void drawLine(Point p1, Point p2) {
                Graphics2D g = (Graphics2D) g2d.create();
                setup(g); g.drawLine(p1.x, p1.y, p2.x, p2.y); g.dispose(); repaint();
            }

            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(image, 0, 0, this);
                if (start != null && end != null && !currentTool.equals("PENCIL")) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    setup(g2); g2.setColor(Color.GRAY);
                    int x = Math.min(start.x, end.x), y = Math.min(start.y, end.y);
                    int w = Math.abs(start.x - end.x), h = Math.abs(start.y - end.y);
                    if (currentTool.equals("RECTANGLE")) g2.drawRect(x, y, w, h);
                    else if (currentTool.equals("OVAL")) g2.drawOval(x, y, w, h);
                    else if (currentTool.equals("LINE")) g2.drawLine(start.x, start.y, end.x, end.y);
                    g2.dispose();
                }
            }
        }
    }

    // ==================================================================================
    // TAB 3: MANUAL GRID EDITOR
    // ==================================================================================
    private class ManualGridEditorPanel extends JPanel {
        private int rows = 16, cols = 32;
        private char[][] grid;
        private int[] rowColors;
        private int activeColorIndex = 0;
        private char activeBrush = '\u28FF';
        private boolean isPreviewMode = false;
        private boolean showColors = false;
        
        private final int[] DOT_OFFSETS = {0x01, 0x08, 0x02, 0x10, 0x04, 0x20, 0x40, 0x80};
        private JToggleButton[] brailleDots = new JToggleButton[8];
        
        private GridCanvas canvasPanel;
        private JPanel colorPanel, topPanel, palettePanel;
        private JToggleButton eraserBtn, showColorBtn;
        private JLabel statusLabel;
        
        private List<List<CellEdit>> undoHistory = new ArrayList<>();
        private List<List<CellEdit>> redoHistory = new ArrayList<>();
        private List<CellEdit> currentStroke = null;
        private JButton undoBtn, redoBtn, copyBtn, pasteBtn, saveBtn, previewBtn, backBtn;

        private class CellEdit {
            int r, c; char prev, next;
            CellEdit(int r, int c, char p, char n) { this.r=r; this.c=c; prev=p; next=n; }
        }

        private class GridCanvas extends JPanel {
            public int cellW = 16, cellH = 26;
            public int padL = 30, padT = 24;
            private int hoverR = -1, hoverC = -1, curR = 0, curC = 0;
            private boolean dragging = false;

            public GridCanvas() {
                setBackground(new Color(18, 18, 20));
                setFont(pickBrailleFont(16));
                setFocusable(true);
                refreshSize();
                
                MouseAdapter ma = new MouseAdapter() {
                    public void mousePressed(MouseEvent e) {
                        if (isPreviewMode) return;
                        requestFocusInWindow(); dragging = true; currentStroke = new ArrayList<>();
                        int c = (e.getX() - padL) / cellW, r = (e.getY() - padT) / cellH;
                        if (r >= 0 && r < rows && c >= 0 && c < cols) { curR = r; curC = c; }
                        paintAt(e);
                    }
                    public void mouseDragged(MouseEvent e) {
                        if (isPreviewMode) return;
                        if (dragging) paintAt(e); updateHover(e);
                    }
                    public void mouseReleased(MouseEvent e) {
                        if (isPreviewMode) return;
                        dragging = false;
                        if (currentStroke != null && !currentStroke.isEmpty()) {
                            undoHistory.add(currentStroke); redoHistory.clear(); currentStroke = null; updateUndoRedo();
                        }
                    }
                    public void mouseMoved(MouseEvent e) { if (!isPreviewMode) updateHover(e); }
                    public void mouseExited(MouseEvent e) { hoverR = -1; hoverC = -1; repaint(); }
                };
                addMouseListener(ma); addMouseMotionListener(ma);
                
                addKeyListener(new KeyAdapter() {
                    public void keyPressed(KeyEvent e) {
                        if (isPreviewMode) return;
                        if (e.getKeyCode() == KeyEvent.VK_UP) { curR = Math.max(0, curR - 1); repaint(); }
                        else if (e.getKeyCode() == KeyEvent.VK_DOWN) { curR = Math.min(rows - 1, curR + 1); repaint(); }
                        else if (e.getKeyCode() == KeyEvent.VK_LEFT) { curC = Math.max(0, curC - 1); repaint(); }
                        else if (e.getKeyCode() == KeyEvent.VK_RIGHT) { curC = Math.min(cols - 1, curC + 1); repaint(); }
                    }
                    public void keyTyped(KeyEvent e) {
                        if (isPreviewMode) return;
                        char c = e.getKeyChar();
                        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) c = activeBrush;
                        else if (c < 32 || c >= 127) return;
                        
                        activeBrush = c; eraserBtn.setSelected(false);
                        
                        char prev = grid[curR][curC]; grid[curR][curC] = activeBrush;
                        List<CellEdit> stroke = new ArrayList<>(); stroke.add(new CellEdit(curR, curC, prev, activeBrush));
                        undoHistory.add(stroke); redoHistory.clear(); updateUndoRedo();
                        curC = Math.min(cols - 1, curC + 1); repaint();
                    }
                });
            }

            public void refreshSize() {
                if (isPreviewMode) { 
                    setPreferredSize(null); 
                } else { 
                    padL = 30; padT = 24;
                    setPreferredSize(new Dimension(padL + cols * cellW + 10, padT + rows * cellH + 10));
                }
                revalidate(); repaint();
            }

            private void updateHover(MouseEvent e) {
                int c = (e.getX() - padL) / cellW, r = (e.getY() - padT) / cellH;
                if (r >= 0 && r < rows && c >= 0 && c < cols) {
                    if (r != hoverR || c != hoverC) { hoverR = r; hoverC = c; repaint(); }
                } else { hoverR = -1; hoverC = -1; repaint(); }
            }

            private void paintAt(MouseEvent e) {
                int c = (e.getX() - padL) / cellW, r = (e.getY() - padT) / cellH;
                if (r < 0 || r >= rows || c < 0 || c >= cols || grid[r][c] == activeBrush) return;
                char prev = grid[r][c]; grid[r][c] = activeBrush;
                if (currentStroke != null) currentStroke.add(new CellEdit(r, c, prev, activeBrush));
                repaint(padL + c * cellW, padT + r * cellH, cellW, cellH);
            }

            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                if (isPreviewMode) {
                    double scale = Math.min((double) getWidth() / (cols * cellW), (double) getHeight() / (rows * cellH));
                    if (scale <= 0) return;
                    
                    g.translate((getWidth() - cols * cellW * scale) / 2.0, (getHeight() - rows * cellH * scale) / 2.0);
                    g.scale(scale, scale);
                    g.setFont(pickBrailleFont(24));
                    
                    FontMetrics symFm = g.getFontMetrics(); int ascent = symFm.getAscent();
                    for (int r = 0; r < rows; r++) {
                        g.setColor(rowColors[r] == 0 ? new Color(0, 229, 255) : COLORS[rowColors[r]]);
                        for (int c = 0; c < cols; c++) {
                            if (grid[r][c] == BLANK || grid[r][c] == '\0') continue;
                            String s = String.valueOf(grid[r][c]);
                            g.drawString(s, c * cellW, r * cellH + ascent - 4);
                        }
                    }
                } else {
                    g.setColor(new Color(45, 45, 55));
                    for (int r = 0; r <= rows; r++) g.drawLine(padL, padT + r * cellH, padL + cols * cellW, padT + r * cellH);
                    for (int c = 0; c <= cols; c++) g.drawLine(padL + c * cellW, padT, padL + c * cellW, padT + rows * cellH);
                    
                    g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                    FontMetrics fm = g.getFontMetrics();
                    for (int c = 0; c < cols; c++) {
                        if (c % 5 != 0 && cols > 40) continue; 
                        g.setColor(c % 5 == 0 ? Color.WHITE : Color.LIGHT_GRAY);
                        g.drawString(String.valueOf(c), padL + c * cellW + (cellW - fm.stringWidth(String.valueOf(c))) / 2, padT - 6); 
                    }
                    for (int r = 0; r < rows; r++) {
                        if (r % 5 != 0 && rows > 40) continue;
                        g.setColor(r % 5 == 0 ? Color.WHITE : Color.LIGHT_GRAY);
                        g.drawString(String.valueOf(r + 1), padL - 8 - fm.stringWidth(String.valueOf(r + 1)), padT + r * cellH + (cellH + fm.getAscent()) / 2 - 2); 
                    }
                    if (hoverR >= 0) { g.setColor(new Color(0, 150, 255, dragging ? 80 : 40)); g.fillRect(padL + hoverC * cellW, padT + hoverR * cellH, cellW, cellH); }
                    if (hasFocus()) { g.setColor(Color.WHITE); g.drawRect(padL + curC * cellW, padT + curR * cellH, cellW - 1, cellH - 1); }

                    g.setFont(pickBrailleFont(16));
                    FontMetrics symFm = g.getFontMetrics(); int ascent = symFm.getAscent();
                    for (int r = 0; r < rows; r++) {
                        g.setColor(showColors ? (rowColors[r] == 0 ? new Color(0, 229, 255) : COLORS[rowColors[r]]) : new Color(0, 229, 255));
                        for (int c = 0; c < cols; c++) {
                            if (grid[r][c] == BLANK || grid[r][c] == '\0') continue;
                            String s = String.valueOf(grid[r][c]);
                            g.drawString(s, padL + c * cellW + (cellW - symFm.stringWidth(s)) / 2, padT + r * cellH + (cellH - symFm.getHeight()) / 2 + ascent);
                        }
                    }
                }
                g.dispose();
            }
        }

        public ManualGridEditorPanel() {
            setLayout(new BorderLayout(8, 8));
            setBackground(new Color(22, 22, 24));
            initGrid();
            setupKeyBindings();

            JPanel root = new JPanel(new BorderLayout(8, 8));
            root.setBackground(new Color(22, 22, 24));
            
            canvasPanel = new GridCanvas();
            colorPanel = new JPanel(null);
            colorPanel.setBackground(new Color(18, 18, 20));
            
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setBackground(new Color(18, 18, 20));
            wrapper.add(canvasPanel, BorderLayout.CENTER);

            JPanel viewport = new JPanel(new BorderLayout());
            viewport.add(colorPanel, BorderLayout.WEST); viewport.add(wrapper, BorderLayout.CENTER);
            
            JScrollPane scroll = new JScrollPane(viewport);
            scroll.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 80)));
            scroll.getViewport().setBackground(new Color(18, 18, 20));
            
            topPanel = buildTop();
            palettePanel = buildPalette();
            
            root.add(topPanel, BorderLayout.NORTH);
            root.add(palettePanel, BorderLayout.WEST);
            root.add(scroll, BorderLayout.CENTER);
            root.add(buildBottom(), BorderLayout.SOUTH);

            add(root, BorderLayout.CENTER);
            refreshColors();
        }

        private JPanel buildTop() {
            topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            topPanel.setBackground(new Color(22, 22, 24));
            JSpinner rs = new JSpinner(new SpinnerNumberModel(rows, 4, 200, 1));
            JSpinner cs = new JSpinner(new SpinnerNumberModel(cols, 4, 200, 1));
            styleSpinner(rs); styleSpinner(cs);
            JButton apply = new JButton("Apply"); styleBtn(apply);
            apply.addActionListener(e -> resizeGrid((int) rs.getValue(), (int) cs.getValue()));
            
            showColorBtn = new JToggleButton("Show Color");
            styleToggle(showColorBtn);
            showColorBtn.addActionListener(e -> { showColors = showColorBtn.isSelected(); canvasPanel.repaint(); });
            
            topPanel.add(new JLabel("Rows:") {{ setForeground(Color.LIGHT_GRAY); }}); topPanel.add(rs);
            topPanel.add(new JLabel("Cols:") {{ setForeground(Color.LIGHT_GRAY); }}); topPanel.add(cs);
            topPanel.add(apply); topPanel.add(showColorBtn);
            return topPanel;
        }

        private JPanel buildBottom() {
            JPanel bar = new JPanel(new BorderLayout(10, 0));
            bar.setBackground(new Color(22, 22, 24));
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setBackground(new Color(22, 22, 24));

            undoBtn = new JButton("Undo"); styleBtn(undoBtn); undoBtn.setEnabled(false); undoBtn.addActionListener(e -> undo());
            redoBtn = new JButton("Redo"); styleBtn(redoBtn); redoBtn.setEnabled(false); redoBtn.addActionListener(e -> redo());
            
            JButton clearBtn = new JButton("Clear"); styleBtn(clearBtn);
            clearBtn.addActionListener(e -> {
                for (int r = 0; r < rows; r++) { rowColors[r] = 0; updateColorBtn(r); for (int c = 0; c < cols; c++) grid[r][c] = BLANK; }
                undoHistory.clear(); redoHistory.clear(); updateUndoRedo(); canvasPanel.repaint();
            });

            eraserBtn = new JToggleButton("Eraser"); styleToggle(eraserBtn);
            eraserBtn.addActionListener(e -> {
                if (eraserBtn.isSelected()) { activeBrush = BLANK; for (JToggleButton b : brailleDots) b.setSelected(false); }
                canvasPanel.requestFocusInWindow();
            });

            copyBtn = new JButton("Copy"); styleBtn(copyBtn); copyBtn.addActionListener(e -> {
                StringBuilder sb = new StringBuilder();
                for (int r = 0; r < rows; r++) {
                    if (rowColors[r] > 0) sb.append("$").append(rowColors[r]).append(" ");
                    for (int c = 0; c < cols; c++) sb.append(grid[r][c]);
                    sb.append("\n");
                }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
                JOptionPane.showMessageDialog(this, "Copied to clipboard!");
            });

            pasteBtn = new JButton("Paste"); styleBtn(pasteBtn); pasteBtn.addActionListener(e -> {
                try {
                    String data = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                    if (data != null && !data.isEmpty()) {
                        String[] lines = data.split("\\r?\\n");
                        int newRows = lines.length;
                        int newCols = 0;
                        int[] parsedColors = new int[newRows];

                        for (int i = 0; i < newRows; i++) {
                            String line = lines[i];
                            parsedColors[i] = 0; 
                            
                            if (line.matches("^\\$[1-9]\\s+.*")) {
                                int spaceIdx = line.indexOf(' ');
                                parsedColors[i] = Integer.parseInt(line.substring(1, spaceIdx));
                                line = line.substring(spaceIdx + 1);
                            } else if (line.matches("^\\$\\S+\\s+.*")) {
                                int spaceIdx = line.indexOf(' ');
                                line = line.substring(spaceIdx + 1);
                            }
                            
                            lines[i] = line;
                            newCols = Math.max(newCols, line.length());
                        }

                        resizeGrid(newRows, newCols);
                        for (int r = 0; r < newRows; r++) {
                            rowColors[r] = parsedColors[r];
                            updateColorBtn(r); 
                            
                            String line = lines[r];
                            for (int c = 0; c < newCols; c++) {
                                grid[r][c] = (c < line.length()) ? line.charAt(c) : BLANK;
                            }
                        }
                        canvasPanel.repaint();
                        refreshColors(); 
                    }
                } catch (Exception ex) { 
                    ex.printStackTrace();
                }
            });

            saveBtn = new JButton("Save"); styleBtn(saveBtn); saveBtn.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File("braille_art.txt"));
                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    try (PrintWriter out = new PrintWriter(chooser.getSelectedFile(), "UTF-8")) {
                        for (int r = 0; r < rows; r++) {
                            if (rowColors[r] > 0) out.print("$" + rowColors[r] + " ");
                            for (int c = 0; c < cols; c++) out.print(grid[r][c]);
                            out.println();
                        }
                        JOptionPane.showMessageDialog(this, "Saved successfully!");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "Error saving file.");
                    }
                }
            });
            
            previewBtn = new JButton("Preview"); styleBtn(previewBtn); previewBtn.addActionListener(e -> togglePreview(true));
            backBtn = new JButton("Back"); styleBtn(backBtn); backBtn.setVisible(false); backBtn.addActionListener(e -> togglePreview(false));

            left.add(undoBtn); left.add(redoBtn); left.add(clearBtn); left.add(eraserBtn); left.add(copyBtn); left.add(pasteBtn); left.add(saveBtn); left.add(previewBtn); left.add(backBtn);
            
            statusLabel = new JLabel(" Ready"); statusLabel.setForeground(new Color(0, 229, 255)); statusLabel.setFont(pickBrailleFont(14));
            bar.add(left, BorderLayout.WEST); bar.add(statusLabel, BorderLayout.CENTER);
            return bar;
        }

        private JPanel buildPalette() {
            JPanel outer = new JPanel(new BorderLayout(0, 10));
            outer.setBackground(new Color(22, 22, 24));
            outer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
            JLabel title = new JLabel("Palette"); title.setForeground(Color.WHITE); title.setFont(pickBrailleFont(16).deriveFont(Font.BOLD));
            outer.add(title, BorderLayout.NORTH);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Presets", createPresetsPanel());
            tabs.addTab("Custom Dot", createDotBuilder());
            tabs.addTab("Colors", createColorPalette());
            outer.add(tabs, BorderLayout.CENTER);
            return outer;
        }

        private JPanel createPresetsPanel() {
            char[] symbols = {
                '\u28FF', '\u28FE', '\u2877', '\u2866', '\u2804', '\u2870', '\u284F', '\u2807',
                '\u281B', '\u281F', '\u281E', '\u2827', '\u2824', '\u2802', '\u2801', BLANK
            };
            JPanel p = new JPanel(new GridLayout(0, 4, 6, 6));
            p.setBackground(new Color(30, 30, 35));
            p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            ButtonGroup bg = new ButtonGroup();
            for (char sym : symbols) {
                JToggleButton btn = new JToggleButton(String.valueOf(sym));
                btn.setFont(pickBrailleFont(26)); 
                btn.setMargin(new Insets(0,0,0,0)); 
                btn.setPreferredSize(new Dimension(44, 44));
                btn.setFocusPainted(false);
                btn.setBackground(new Color(40, 40, 45));
                btn.setForeground(new Color(235, 235, 240));
                btn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI() {
                    public void paint(Graphics g, JComponent c) {
                        AbstractButton b = (AbstractButton) c;
                        g.setColor(b.isSelected() ? new Color(0, 120, 215) : b.getBackground());
                        g.fillRect(0, 0, c.getWidth(), c.getHeight());
                        super.paint(g, c);
                    }
                });
                btn.addActionListener(e -> {
                    activeBrush = sym;
                    eraserBtn.setSelected(false);
                    int code = sym - 0x2800;
                    for (int i = 0; i < 8; i++) {
                        brailleDots[i].setSelected((code & DOT_OFFSETS[i]) != 0);
                    }
                });
                bg.add(btn);
                p.add(btn);
            }
            return p;
        }

        private JPanel createDotBuilder() {
            JPanel p = new JPanel(new GridLayout(4, 2, 15, 15));
            p.setBackground(new Color(30, 30, 35));
            p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            JPanel wrapper = new JPanel(new GridBagLayout());
            wrapper.setBackground(new Color(30, 30, 35));
            wrapper.add(p);

            for (int i = 0; i < 8; i++) {
                JToggleButton btn = new JToggleButton();
                btn.setPreferredSize(new Dimension(44, 44)); btn.setFocusPainted(false);
                btn.setBackground(new Color(45, 45, 52));
                btn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI() {
                    public void paint(Graphics g, JComponent c) {
                        AbstractButton b = (AbstractButton) c;
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setColor(b.isSelected() ? new Color(0, 120, 215) : b.getBackground()); g2.fillRect(0,0,c.getWidth(),c.getHeight());
                        int cx = c.getWidth()/2, cy = c.getHeight()/2, rad = 10;
                        if (b.isSelected()) { g2.setColor(Color.WHITE); g2.fillOval(cx-rad, cy-rad, rad*2, rad*2); }
                        else { g2.setColor(new Color(100,100,110)); g2.drawOval(cx-rad, cy-rad, rad*2, rad*2); }
                    }
                });
                if ((DOT_OFFSETS[i] & (int)activeBrush - 0x2800) != 0) btn.setSelected(true);
                btn.addActionListener(e -> {
                    int code = 0x2800;
                    for (int j = 0; j < 8; j++) if (brailleDots[j].isSelected()) code += DOT_OFFSETS[j];
                    activeBrush = (char) code;
                    eraserBtn.setSelected(false);
                });
                brailleDots[i] = btn; p.add(btn);
            }
            return wrapper;
        }

        private JPanel createColorPalette() {
            JPanel p = new JPanel(new GridLayout(0, 3, 6, 6));
            p.setBackground(new Color(30, 30, 35));
            p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            ButtonGroup bg = new ButtonGroup();
            for (int i = 0; i <= 9; i++) {
                final int idx = i;
                JToggleButton btn = new JToggleButton(i == 0 ? "None" : String.valueOf(i));
                btn.setPreferredSize(new Dimension(56, 44)); btn.setFocusPainted(false);
                btn.setBackground(i == 0 ? new Color(40,40,45) : COLORS[i]);
                btn.setForeground(i == 0 || isDark(COLORS[i]) ? Color.WHITE : Color.BLACK);
                btn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI() {
                    public void paint(Graphics g, JComponent c) {
                        AbstractButton b = (AbstractButton) c;
                        g.setColor(b.getBackground()); g.fillRect(0,0,c.getWidth(),c.getHeight());
                        if (b.isSelected()) { g.setColor(Color.WHITE); g.drawRect(1,1,c.getWidth()-3,c.getHeight()-3); }
                        super.paint(g,c);
                    }
                });
                if (i == 0) btn.setSelected(true);
                btn.addActionListener(e -> activeColorIndex = idx);
                bg.add(btn); p.add(btn);
            }
            return p;
        }

        private void initGrid() {
            grid = new char[rows][cols]; rowColors = new int[rows];
            for (int i = 0; i < rows; i++) { rowColors[i] = 0; for (int j = 0; j < cols; j++) grid[i][j] = BLANK; }
        }

        private void resizeGrid(int r, int c) {
            int[] oldC = rowColors.clone(); char[][] oldG = grid;
            rows = r; cols = c; grid = new char[r][c]; rowColors = new int[r];
            for (int i = 0; i < r; i++) {
                rowColors[i] = (i < oldC.length) ? oldC[i] : 0;
                for (int j = 0; j < c; j++) grid[i][j] = (i < oldG.length && j < oldG[0].length) ? oldG[i][j] : BLANK;
            }
            undoHistory.clear(); redoHistory.clear(); updateUndoRedo();
            refreshColors(); canvasPanel.refreshSize();
        }

        private void refreshColors() {
            colorPanel.removeAll();
            int cH = 26, pT = 24;
            for (int r = 0; r < rows; r++) {
                JButton btn = new JButton(); 
                btn.setFocusPainted(false); 
                btn.setOpaque(true);
                btn.setContentAreaFilled(true);
                btn.setBounds(5, pT + r*cH + 1, 45, cH - 2);
                btn.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
                    public void paint(Graphics g, JComponent c) {
                        AbstractButton b = (AbstractButton) c;
                        g.setColor(b.getBackground());
                        g.fillRect(0, 0, c.getWidth(), c.getHeight());
                        super.paint(g, c);
                    }
                });
                int idx = r;
                btn.addActionListener(e -> { rowColors[idx] = activeColorIndex; updateColorBtn(idx); canvasPanel.repaint(); });
                colorPanel.add(btn); updateColorBtn(r);
            }
            colorPanel.setPreferredSize(new Dimension(70, canvasPanel.getPreferredSize().height));
            colorPanel.revalidate(); colorPanel.repaint();
        }

        private void updateColorBtn(int r) {
            JButton b = (JButton) colorPanel.getComponent(r);
            int idx = rowColors[r];
            if (idx == 0) { b.setBackground(new Color(30,30,35)); b.setText(""); }
            else { b.setBackground(COLORS[idx]); b.setText(String.valueOf(idx)); b.setForeground(isDark(COLORS[idx]) ? Color.BLACK : Color.WHITE); }
        }

        private void togglePreview(boolean preview) {
            isPreviewMode = preview;
            topPanel.setVisible(!preview); palettePanel.setVisible(!preview); colorPanel.setVisible(!preview);
            undoBtn.setVisible(!preview); redoBtn.setVisible(!preview); eraserBtn.setVisible(!preview);
            copyBtn.setVisible(!preview); pasteBtn.setVisible(!preview); saveBtn.setVisible(!preview); previewBtn.setVisible(!preview); backBtn.setVisible(preview);
            canvasPanel.refreshSize();
            canvasPanel.revalidate(); canvasPanel.repaint();
        }

        private void undo() {
            if (undoHistory.isEmpty()) return;
            List<CellEdit> s = undoHistory.remove(undoHistory.size()-1); redoHistory.add(s);
            for (int i = s.size()-1; i >= 0; i--) grid[s.get(i).r][s.get(i).c] = s.get(i).prev;
            updateUndoRedo(); canvasPanel.repaint();
        }
        private void redo() {
            if (redoHistory.isEmpty()) return;
            List<CellEdit> s = redoHistory.remove(redoHistory.size()-1); undoHistory.add(s);
            for (CellEdit e : s) grid[e.r][e.c] = e.next;
            updateUndoRedo(); canvasPanel.repaint();
        }
        private void updateUndoRedo() { undoBtn.setEnabled(!undoHistory.isEmpty()); redoBtn.setEnabled(!redoHistory.isEmpty()); }
        private void setupKeyBindings() {
            InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW); ActionMap am = getActionMap();
            im.put(KeyStroke.getKeyStroke("control Z"), "undo"); am.put("undo", new AbstractAction() { public void actionPerformed(ActionEvent e) { undo(); }});
            im.put(KeyStroke.getKeyStroke("control Y"), "redo"); am.put("redo", new AbstractAction() { public void actionPerformed(ActionEvent e) { redo(); }});
        }
    }

    // ==================================================================================
    // TAB 4: DEBUGGER
    // ==================================================================================
    private class DebuggerPanel extends JPanel {
        private DebugCanvas canvas;
        private int currentCols = -1, currentRows = -1;
        private String currentTool = "PENCIL";
        private int currentStroke = 3;

        public DebuggerPanel() {
            setLayout(new BorderLayout(10, 10));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
            ButtonGroup bg = new ButtonGroup();
            JToggleButton pen = createToolBtn("Pencil", "PENCIL", bg); pen.setSelected(true);
            JToggleButton rect = createToolBtn("Rectangle", "RECTANGLE", bg);
            JToggleButton oval = createToolBtn("Oval", "OVAL", bg);
            JToggleButton line = createToolBtn("Line", "LINE", bg);
            
            toolbar.add(new JLabel("Tools:")); toolbar.add(pen); toolbar.add(rect); toolbar.add(oval); toolbar.add(line);
            toolbar.add(new JSeparator(SwingConstants.VERTICAL));
            toolbar.add(new JLabel("Stroke:"));
            JComboBox<String> strokeCombo = new JComboBox<>(new String[]{"Fine (1px)", "Medium (3px)", "Thick (6px)"});
            strokeCombo.setSelectedIndex(1);
            strokeCombo.addActionListener(e -> {
                switch(strokeCombo.getSelectedIndex()) { case 0 -> currentStroke=1; case 1 -> currentStroke=3; case 2 -> currentStroke=6; }
            });
            toolbar.add(strokeCombo);
            
            add(toolbar, BorderLayout.NORTH);

            canvas = new DebugCanvas(720, 576);
            JPanel canvasWrapper = new JPanel(new GridBagLayout());
            canvasWrapper.setBackground(Color.LIGHT_GRAY);
            canvasWrapper.add(canvas);
            JScrollPane scroll = new JScrollPane(canvasWrapper);
            add(scroll, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
            JButton clearBtn = new JButton("Clear Canvas");
            clearBtn.addActionListener(e -> { canvas.clear(); currentCols=-1; currentRows=-1; });
            JButton exportBtn = new JButton("Export Debug Screenshots...");
            exportBtn.addActionListener(e -> exportImages());
            bottom.add(clearBtn); bottom.add(exportBtn);
            add(bottom, BorderLayout.SOUTH);
        }

        private JToggleButton createToolBtn(String t, String tool, ButtonGroup bg) {
            JToggleButton b = new JToggleButton(t); b.addActionListener(e -> currentTool = tool); bg.add(b); return b;
        }

        private void exportImages() {
            JPanel p = new JPanel(new GridLayout(2, 2, 5, 5));
            JSpinner cs = new JSpinner(new SpinnerNumberModel(32, 4, 150, 1));
            JSpinner rs = new JSpinner(new SpinnerNumberModel(16, 4, 100, 1));
            p.add(new JLabel("Cols:")); p.add(cs); p.add(new JLabel("Rows:")); p.add(rs);
            
            if (JOptionPane.showConfirmDialog(this, p, "Dimensions", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                currentCols = (int) cs.getValue(); currentRows = (int) rs.getValue();
                canvas.repaint();

                File dir = new File("ss"); dir.mkdirs();
                BufferedImage img = canvas.getImage();
                double cw = (double) 720 / currentCols, ch = (double) 576 / currentRows;
                int[][] map = {{0x01, 0x08}, {0x02, 0x10}, {0x04, 0x20}, {0x40, 0x80}};

                int count = 0;
                for (int r = 0; r < currentRows; r++) {
                    for (int c = 0; c < currentCols; c++) {
                        int sx = (int) Math.round(c * cw), sy = (int) Math.round(r * ch);
                        int ex = (int) Math.round((c + 1) * cw), ey = (int) Math.round((r + 1) * ch);
                        int w = Math.max(1, ex - sx), h = Math.max(1, ey - sy);
                        BufferedImage cellImg = img.getSubimage(sx, sy, w, h);
                        int code = 0x2800;

                        for (int dy = 0; dy < 4; dy++) {
                            for (int dx = 0; dx < 2; dx++) {
                                int dsx = sx + (dx * w) / 2, dex = sx + ((dx + 1) * w) / 2;
                                int dsy = sy + (dy * h) / 4, dey = sy + ((dy + 1) * h) / 4;
                                int total = 0, dark = 0;
                                for (int y = dsy; y < dey && y < 576; y++) {
                                    for (int x = dsx; x < dex && x < 720; x++) {
                                        total++;
                                        int rgb = img.getRGB(x, y);
                                        if ((((rgb>>16)&0xFF)+((rgb>>8)&0xFF)+(rgb&0xFF))/3 < 200) dark++;
                                    }
                                }
                                if (total > 0 && (double) dark / total > 0.33) code |= map[dy][dx];
                            }
                        }

                        BufferedImage dbg = new BufferedImage(340, 120, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = dbg.createGraphics();
                        g.setColor(Color.WHITE); g.fillRect(0,0,340,120);
                        g.drawImage(cellImg, 10, 10, 100, 100, null);
                        g.setColor(Color.RED); g.drawRect(10, 10, 100, 100);
                        g.drawLine(60, 10, 60, 110); for (int i=1; i<4; i++) g.drawLine(10, 10+i*25, 110, 10+i*25);
                        
                        g.setColor(Color.BLACK); g.setFont(pickBrailleFont(72));
                        FontMetrics fm = g.getFontMetrics();
                        g.drawString(String.valueOf((char)code), 130 + (100-fm.stringWidth(String.valueOf((char)code)))/2, 10 + (100-fm.getHeight())/2 + fm.getAscent());
                        g.setColor(Color.GRAY); g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                        g.drawString("R:"+r+" C:"+c, 10, 8); g.drawString(String.format("U+%04X", code), 130, 8);
                        g.dispose();

                        try { ImageIO.write(dbg, "png", new File(dir, String.format("cell_r%02d_c%02d.png", r, c))); count++; } catch (IOException e) {}
                    }
                }
                JOptionPane.showMessageDialog(this, "Exported " + count + " images to 'ss' folder.");
            }
        }

        private class DebugCanvas extends JPanel {
            private BufferedImage img; private Graphics2D g2d; private Point start, end;
            public DebugCanvas(int w, int h) {
                setPreferredSize(new Dimension(w, h));
                setMinimumSize(new Dimension(w, h));
                setBackground(Color.WHITE); setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); g2d = img.createGraphics(); clear();
                MouseAdapter ma = new MouseAdapter() {
                    public void mousePressed(MouseEvent e) { start = e.getPoint(); end = start; if (currentTool.equals("PENCIL")) draw(start, start); }
                    public void mouseDragged(MouseEvent e) { end = e.getPoint(); if (currentTool.equals("PENCIL")) { draw(start, end); start = end; } repaint(); }
                    public void mouseReleased(MouseEvent e) {
                        if (!currentTool.equals("PENCIL") && start != null) {
                            Graphics2D g = (Graphics2D) g2d.create(); setup(g);
                            int x=Math.min(start.x,e.getX()), y=Math.min(start.y,e.getY()), w=Math.abs(start.x-e.getX()), h=Math.abs(start.y-e.getY());
                            if (currentTool.equals("RECTANGLE")) g.drawRect(x,y,w,h); 
                            else if (currentTool.equals("OVAL")) g.drawOval(x,y,w,h);
                            else if (currentTool.equals("LINE")) g.drawLine(start.x, start.y, e.getX(), e.getY());
                            g.dispose();
                        } start=null; end=null; repaint();
                    }
                };
                addMouseListener(ma); addMouseMotionListener(ma);
            }
            public void clear() { 
                g2d.setColor(Color.WHITE); 
                g2d.fillRect(0, 0, img.getWidth(), img.getHeight()); 
                repaint(); 
            }
            public BufferedImage getImage() { return img; }
            private void setup(Graphics2D g) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(currentStroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); }
            private void draw(Point p1, Point p2) { Graphics2D g = (Graphics2D) g2d.create(); setup(g); g.drawLine(p1.x, p1.y, p2.x, p2.y); g.dispose(); repaint(); }
            protected void paintComponent(Graphics g) {
                super.paintComponent(g); g.drawImage(img, 0, 0, this);
                if (start != null && end != null && !currentTool.equals("PENCIL")) {
                    Graphics2D g2 = (Graphics2D) g.create(); setup(g2); g2.setColor(Color.GRAY);
                    int x=Math.min(start.x,end.x), y=Math.min(start.y,end.y), w=Math.abs(start.x-end.x), h=Math.abs(start.y-end.y);
                    if (currentTool.equals("RECTANGLE")) g2.drawRect(x,y,w,h); 
                    else if (currentTool.equals("OVAL")) g2.drawOval(x,y,w,h);
                    else if (currentTool.equals("LINE")) g2.drawLine(start.x, start.y, end.x, end.y);
                    g2.dispose();
                }
                if (currentCols > 0 && currentRows > 0) {
                    Graphics2D g2 = (Graphics2D) g.create(); g2.setColor(new Color(255,0,0,100));
                    double cw = (double)getWidth()/currentCols, ch = (double)getHeight()/currentRows;
                    for (int c=0; c<=currentCols; c++) { int x=(int)Math.round(c*cw); if (x <= getWidth()) g2.drawLine(x,0,x,getHeight()); }
                    for (int r=0; r<=currentRows; r++) { int y=(int)Math.round(r*ch); if (y <= getHeight()) g2.drawLine(0,y,getWidth(),y); }
                    g2.dispose();
                }
            }
        }
    }

    // ====================== UTILITIES & SHARED CONSTANTS ======================
    private static final char BLANK = '\u2800';
    private static final Color[] COLORS = {
        null, new Color(245, 224, 220), new Color(242, 205, 205), new Color(245, 194, 231), new Color(250, 179, 135),
        new Color(249, 226, 175), new Color(166, 227, 161), new Color(148, 226, 213), new Color(137, 220, 235), new Color(116, 199, 236)
    };

    private static boolean isDark(Color c) {
        return c != null && (c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114) < 128;
    }

    private static void styleBtn(JButton b) {
        b.setFont(new Font("SansSerif", Font.PLAIN, 14)); b.setFocusPainted(false); b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(70,70,80)), BorderFactory.createEmptyBorder(6,12,6,12)));
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            public void paint(Graphics g, JComponent c) {
                AbstractButton a = (AbstractButton) c;
                if (!a.isEnabled()) g.setColor(new Color(30,30,35));
                else if (a.getModel().isPressed()) g.setColor(new Color(35,35,40));
                else if (a.getModel().isRollover()) g.setColor(new Color(60,60,70));
                else g.setColor(new Color(45,45,52));
                g.fillRect(0,0,c.getWidth(),c.getHeight()); super.paint(g,c);
            }
        });
    }

    private static void styleToggle(JToggleButton b) {
        b.setFont(new Font("SansSerif", Font.PLAIN, 14)); b.setFocusPainted(false); b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(70,70,80)), BorderFactory.createEmptyBorder(6,12,6,12)));
        b.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI() {
            public void paint(Graphics g, JComponent c) {
                AbstractButton a = (AbstractButton) c;
                if (a.isSelected()) g.setColor(new Color(0,120,215));
                else if (a.getModel().isRollover()) g.setColor(new Color(60,60,70));
                else g.setColor(new Color(45,45,52));
                g.fillRect(0,0,c.getWidth(),c.getHeight()); super.paint(g,c);
            }
        });
    }

    private static void styleSpinner(JSpinner s) {
        s.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JComponent e = s.getEditor();
        if (e instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) e).getTextField();
            tf.setBackground(new Color(45,45,52)); tf.setForeground(Color.WHITE); tf.setCaretColor(Color.WHITE);
        }
    }

    private static Font pickBrailleFont(int size) {
        String[] candidates = {"Segoe UI Symbol", "Segoe UI", "DejaVu Sans", "Dialog", "Monospaced"};
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, size);
            if (f.canDisplay('\u28FF') && f.canDisplay('\u2801')) return f;
        }
        return new Font("Monospaced", Font.PLAIN, size);
    }

    private static BufferedImage getScaledImage(Image src, int w, int h) {
        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = res.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double r1 = (double)src.getWidth(null) / src.getHeight(null), r2 = (double)w / h;
        int nw, nh;
        if (r1 > r2) { nw = w; nh = (int)(w / r1); } else { nh = h; nw = (int)(h * r1); }
        g.drawImage(src, (w-nw)/2, (h-nh)/2, nw, nh, null);
        g.dispose(); return res;
    }

    private class CropPanel extends JPanel {
        private BufferedImage img; private Image dispImg; private int dw, dh;
        private Point start, end; private Rectangle sel;

        public CropPanel(BufferedImage img) {
            this.img = img; setBackground(Color.DARK_GRAY); setOpaque(true);
            double r = Math.min(860.0 / img.getWidth(), 600.0 / img.getHeight());
            dw = (int)(img.getWidth() * r); dh = (int)(img.getHeight() * r);
            setPreferredSize(new Dimension(dw, dh));
            dispImg = img.getScaledInstance(dw, dh, Image.SCALE_SMOOTH);

            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) { start = e.getPoint(); end = start; sel = null; repaint(); } }
                public void mouseDragged(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && start != null) {
                        int ex = Math.max(0, Math.min(e.getX(), dw)), ey = Math.max(0, Math.min(e.getY(), dh));
                        end = new Point(ex, ey);
                        sel = new Rectangle(Math.min(start.x,end.x), Math.min(start.y,end.y), Math.abs(start.x-end.x), Math.abs(start.y-end.y));
                        repaint();
                    }
                }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
        }
        public int getDisplayedWidth() { return dw; }
        public int getDisplayedHeight() { return dh; }
        public Rectangle getSelection() { return sel; }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); g.drawImage(dispImg, 0, 0, this);
            if (sel != null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(255,0,0,70)); g2.fillRect(sel.x, sel.y, sel.width, sel.height);
                g2.setColor(Color.RED); g2.setStroke(new BasicStroke(2)); g2.drawRect(sel.x, sel.y, sel.width, sel.height);
            }
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new BrailleMasterSuite().setVisible(true));
    }
}