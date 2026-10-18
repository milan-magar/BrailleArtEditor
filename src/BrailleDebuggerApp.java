import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class BrailleDebuggerApp extends JFrame {

    private static final int CANVAS_WIDTH = 720;
    private static final int CANVAS_HEIGHT = 576;
    private DrawingPanel drawingPanel;
    
    private int currentCols = -1;
    private int currentRows = -1;
    private String currentTool = "PENCIL";

    public BrailleDebuggerApp() {
        super("Braille Debugger & Exporter");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.setBackground(new Color(240, 240, 240));

        // --- Toolbar ---
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolbar.setBackground(new Color(230, 230, 230));
        toolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180)),
                new EmptyBorder(5, 5, 5, 5)
        ));

        ButtonGroup toolGroup = new ButtonGroup();
        JToggleButton pencilBtn = createToolBtn("Pencil", "PENCIL", toolGroup);
        JToggleButton rectBtn = createToolBtn("Rectangle", "RECTANGLE", toolGroup);
        JToggleButton ovalBtn = createToolBtn("Oval", "OVAL", toolGroup);
        pencilBtn.setSelected(true);

        toolbar.add(new JLabel("Tools:"));
        toolbar.add(pencilBtn);
        toolbar.add(rectBtn);
        toolbar.add(ovalBtn);
        root.add(toolbar, BorderLayout.NORTH);

        // --- Canvas ---
        drawingPanel = new DrawingPanel(CANVAS_WIDTH, CANVAS_HEIGHT);
        
        JScrollPane canvasScroll = new JScrollPane(drawingPanel);
        canvasScroll.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150)));
        canvasScroll.getVerticalScrollBar().setUnitIncrement(16);
        canvasScroll.getHorizontalScrollBar().setUnitIncrement(16);
        root.add(canvasScroll, BorderLayout.CENTER);

        // --- Bottom Controls ---
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        controls.setBackground(new Color(240, 240, 240));

        JButton clearBtn = new JButton("Clear Canvas");
        clearBtn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        clearBtn.addActionListener(e -> {
            drawingPanel.clear();
            currentCols = -1;
            currentRows = -1;
        });

        JButton exportBtn = new JButton("Export Debug Screenshots...");
        exportBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        exportBtn.setBackground(new Color(200, 230, 255));
        exportBtn.addActionListener(e -> exportDebugScreenshots());

        controls.add(clearBtn);
        controls.add(exportBtn);
        root.add(controls, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(900, 800));
        setLocationRelativeTo(null);
    }

    private JToggleButton createToolBtn(String text, String tool, ButtonGroup group) {
        JToggleButton btn = new JToggleButton(text);
        btn.setFocusPainted(false);
        btn.addActionListener(e -> currentTool = tool);
        group.add(btn);
        return btn;
    }

    private void exportDebugScreenshots() {
        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        JSpinner cSpin = new JSpinner(new SpinnerNumberModel(32, 4, 150, 1));
        JSpinner rSpin = new JSpinner(new SpinnerNumberModel(16, 4, 100, 1));
        
        inputPanel.add(new JLabel("Braille Columns (Width):"));
        inputPanel.add(cSpin);
        inputPanel.add(new JLabel("Braille Rows (Height):"));
        inputPanel.add(rSpin);

        int result = JOptionPane.showConfirmDialog(this, inputPanel, 
                "Target Dimensions for Debug Export", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        currentCols = (int) cSpin.getValue();
        currentRows = (int) rSpin.getValue();
        
        drawingPanel.repaint();

        File dir = new File("ss");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                JOptionPane.showMessageDialog(this, "Failed to create 'ss' folder.");
                return;
            }
        }

        BufferedImage img = drawingPanel.getImage();
        double cellWidth = (double) CANVAS_WIDTH / currentCols;
        double cellHeight = (double) CANVAS_HEIGHT / currentRows;

        int[][] dotOffsets = {
            {0x01, 0x08}, {0x02, 0x10}, {0x04, 0x20}, {0x40, 0x80}
        };

        int count = 0;
        for (int r = 0; r < currentRows; r++) {
            for (int c = 0; c < currentCols; c++) {
                int startX = (int) Math.round(c * cellWidth);
                int startY = (int) Math.round(r * cellHeight);
                int endX = (int) Math.round((c + 1) * cellWidth);
                int endY = (int) Math.round((r + 1) * cellHeight);

                int cw = Math.max(1, endX - startX);
                int ch = Math.max(1, endY - startY);

                BufferedImage cellImg = img.getSubimage(startX, startY, cw, ch);

                int charCode = 0x2800;

                for (int dy = 0; dy < 4; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int dotStartX = startX + (dx * cw) / 2;
                        int dotEndX = startX + ((dx + 1) * cw) / 2;
                        int dotStartY = startY + (dy * ch) / 4;
                        int dotEndY = startY + ((dy + 1) * ch) / 4;

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
                                
                                // Check if pixel is dark (threshold < 200)
                                if ((rCol + gCol + bCol) / 3 < 200) {
                                    darkPixels++;
                                }
                            }
                        }
                        
                        // Assign dot only if dark pixels cover > 33% of the sub-box
                        if (totalPixels > 0 && (double) darkPixels / totalPixels > 0.33) {
                            charCode |= dotOffsets[dy][dx];
                        }
                    }
                }

                // Create the debug screenshot
                int padding = 10;
                int scaledW = 100;
                int scaledH = 100;
                BufferedImage debugImg = new BufferedImage(scaledW * 2 + padding * 3, scaledH + padding * 2, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = debugImg.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, debugImg.getWidth(), debugImg.getHeight());

                // Left side: Image
                g.drawImage(cellImg, padding, padding, scaledW, scaledH, null);
                
                // Draw Red Grid
                g.setColor(Color.RED);
                g.drawRect(padding, padding, scaledW, scaledH); 
                g.drawLine(padding + scaledW / 2, padding, padding + scaledW / 2, padding + scaledH); 
                for (int i = 1; i < 4; i++) {
                    g.drawLine(padding, padding + (i * scaledH) / 4, padding + scaledW, padding + (i * scaledH) / 4); 
                }

                // Right side: Braille Char
                g.setColor(Color.BLACK);
                g.setFont(pickFont(Font.PLAIN, 72));
                String brailleStr = String.valueOf((char) charCode);
                FontMetrics fm = g.getFontMetrics();
                int charX = padding * 2 + scaledW + (scaledW - fm.stringWidth(brailleStr)) / 2;
                int charY = padding + (scaledH - fm.getHeight()) / 2 + fm.getAscent();
                g.drawString(brailleStr, charX, charY);

                // Labels
                g.setColor(Color.GRAY);
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.drawString("Row: " + r + ", Col: " + c, padding, padding - 2);
                g.drawString(String.format("U+%04X", charCode), charX, padding - 2);

                g.dispose();

                try {
                    File outputFile = new File(dir, String.format("cell_r%02d_c%02d.png", r, c));
                    ImageIO.write(debugImg, "png", outputFile);
                    count++;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        JOptionPane.showMessageDialog(this, "Successfully exported " + count + " images to the 'ss' folder!\n"
                + "Coverage threshold set to 33%.");
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
            
            g2dImage.setColor(Color.WHITE);
            g2dImage.fillRect(0, 0, width, height);

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    startPoint = e.getPoint();
                    currentPoint = startPoint;
                    if (currentTool.equals("PENCIL")) {
                        drawPencil(startPoint, startPoint);
                    }
                }
                @Override public void mouseDragged(MouseEvent e) {
                    currentPoint = e.getPoint();
                    if (currentTool.equals("PENCIL")) {
                        drawPencil(startPoint, currentPoint);
                        startPoint = currentPoint;
                    }
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (!currentTool.equals("PENCIL") && startPoint != null) {
                        Graphics2D g = (Graphics2D) g2dImage.create();
                        setupGraphics(g);
                        int x = Math.min(startPoint.x, e.getX());
                        int y = Math.min(startPoint.y, e.getY());
                        int w = Math.abs(startPoint.x - e.getX());
                        int h = Math.abs(startPoint.y - e.getY());
                        
                        if (currentTool.equals("RECTANGLE")) g.drawRect(x, y, w, h);
                        else if (currentTool.equals("OVAL")) g.drawOval(x, y, w, h);
                        
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

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT);
        }

        public void clear() {
            g2dImage.setColor(Color.WHITE);
            g2dImage.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
            repaint();
        }

        public BufferedImage getImage() {
            return image;
        }

        private void setupGraphics(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }

        private void drawPencil(Point p1, Point p2) {
            Graphics2D g = (Graphics2D) g2dImage.create();
            setupGraphics(g);
            g.drawLine(p1.x, p1.y, p2.x, p2.y);
            g.dispose();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image != null) {
                g.drawImage(image, 0, 0, this);
            }
            
            if (startPoint != null && currentPoint != null && !currentTool.equals("PENCIL")) {
                Graphics2D g2 = (Graphics2D) g.create();
                setupGraphics(g2);
                g2.setColor(Color.GRAY); 
                int x = Math.min(startPoint.x, currentPoint.x);
                int y = Math.min(startPoint.y, currentPoint.y);
                int w = Math.abs(startPoint.x - currentPoint.x);
                int h = Math.abs(startPoint.y - currentPoint.y);
                
                if (currentTool.equals("RECTANGLE")) g2.drawRect(x, y, w, h);
                else if (currentTool.equals("OVAL")) g2.drawOval(x, y, w, h);
                
                g2.dispose();
            }
            
            if (currentCols > 0 && currentRows > 0) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(255, 0, 0, 100));
                double cellW = (double) CANVAS_WIDTH / currentCols;
                double cellH = (double) CANVAS_HEIGHT / currentRows;
                
                for (int c = 0; c <= currentCols; c++) {
                    int x = (int) Math.round(c * cellW);
                    g2.drawLine(x, 0, x, CANVAS_HEIGHT);
                }
                for (int r = 0; r <= currentRows; r++) {
                    int y = (int) Math.round(r * cellH);
                    g2.drawLine(0, y, CANVAS_WIDTH, y);
                }
                g2.dispose();
            }
        }
    }

    private static Font pickFont(int style, int size) {
        String[] candidates = {"Segoe UI Symbol", "Segoe UI", "DejaVu Sans", "Dialog", "SansSerif"};
        for (String name : candidates) {
            Font f = new Font(name, style, size);
            if (f.canDisplay('\u28FF')) return f;
        }
        return new Font("SansSerif", style, size);
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new BrailleDebuggerApp().setVisible(true));
    }
}