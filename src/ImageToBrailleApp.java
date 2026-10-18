import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ImageToBrailleApp extends JFrame {

    private JTextField colsField;
    private JTextField rowsField;
    private JButton loadBtn;
    private JButton autoCropBtn;
    private JButton manualCropBtn;
    private JButton resetCropBtn;
    private JButton convertBtn;
    private JButton copyBtn;
    private JLabel imageLabel;
    private JTextArea outputArea;
    
    private BufferedImage uncroppedImage; 
    private BufferedImage originalImage;  

    private JCheckBox invertCheck;
    private JSlider thresholdSlider;

    public ImageToBrailleApp() {
        super("Image to Braille Converter");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 700);
        setLayout(new BorderLayout(10, 10));
        setLocationRelativeTo(null);

        // --- Top Control Panel ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        loadBtn = new JButton("Load Image...");
        loadBtn.addActionListener(e -> loadImage());

        autoCropBtn = new JButton("Auto-Crop");
        autoCropBtn.setEnabled(false);
        autoCropBtn.setToolTipText("Removes white space around the image");
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

        convertBtn = new JButton("Convert to Braille");
        convertBtn.setEnabled(false);
        convertBtn.addActionListener(e -> convertToBraille());
        
        copyBtn = new JButton("Copy Text");
        copyBtn.setEnabled(false);
        copyBtn.addActionListener(e -> {
            if (!outputArea.getText().isEmpty()) {
                StringSelection stringSelection = new StringSelection(outputArea.getText());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
                JOptionPane.showMessageDialog(this, "Braille art copied to clipboard!");
            }
        });

        topPanel.add(loadBtn);
        topPanel.add(autoCropBtn);
        topPanel.add(manualCropBtn);
        topPanel.add(resetCropBtn);
        topPanel.add(new JLabel("Cols:"));
        topPanel.add(colsField);
        topPanel.add(new JLabel("Rows:"));
        topPanel.add(rowsField);
        topPanel.add(invertCheck);
        topPanel.add(new JLabel("Threshold:"));
        topPanel.add(thresholdSlider);
        topPanel.add(convertBtn);
        topPanel.add(copyBtn);

        add(topPanel, BorderLayout.NORTH);

        // --- Center Preview & Output Areas (Side-by-Side) ---
        imageLabel = new JLabel("Load an image to see the B&W preview", SwingConstants.CENTER);
        imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        imageLabel.setPreferredSize(new Dimension(450, 350));
        JScrollPane imageScroll = new JScrollPane(imageLabel);
        imageScroll.setBorder(BorderFactory.createTitledBorder("Image Preview"));

        outputArea = new JTextArea();
        outputArea.setFont(pickBrailleFont(14));
        outputArea.setEditable(false);
        outputArea.setBackground(new Color(30, 30, 30));
        outputArea.setForeground(new Color(0, 229, 255)); 
        outputArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("Braille Output"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, imageScroll, outputScroll);
        splitPane.setDividerLocation(550);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);
    }

    private void loadImage() {
        JFileChooser chooser = new JFileChooser();
        File downloadsDir = new File(System.getProperty("user.home"), "Downloads");
        if (downloadsDir.exists() && downloadsDir.isDirectory()) {
            chooser.setCurrentDirectory(downloadsDir);
        } else {
            chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }

        chooser.setFileFilter(new FileNameExtensionFilter("Image Files", "jpg", "jpeg", "png", "gif", "bmp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                BufferedImage loaded = ImageIO.read(file);
                if (loaded != null) {
                    uncroppedImage = loaded;
                    originalImage = loaded;
                    autoCropBtn.setEnabled(true);
                    manualCropBtn.setEnabled(true);
                    resetCropBtn.setEnabled(true);
                    convertBtn.setEnabled(true);
                    copyBtn.setEnabled(true);
                    updatePreview();
                } else {
                    JOptionPane.showMessageDialog(this, "Could not read image file.");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading image: " + ex.getMessage());
            }
        }
    }

    private void applyAutoCrop() {
        if (originalImage == null) return;

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        int top = height, left = width, bottom = 0, right = 0;
        int bgThreshold = 240; 

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = originalImage.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                if (r < bgThreshold || g < bgThreshold || b < bgThreshold) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }

        if (left >= right || top >= bottom) return;

        int cropWidth = right - left + 1;
        int cropHeight = bottom - top + 1;
        
        BufferedImage cropped = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cropped.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, cropWidth, cropHeight);
        g.drawImage(originalImage.getSubimage(left, top, cropWidth, cropHeight), 0, 0, null);
        g.dispose();

        originalImage = cropped;
        updatePreview();
    }

    private void openManualCropDialog() {
        if (originalImage == null) return;

        JDialog dialog = new JDialog(this, "Drag to Select Crop Area", true);
        dialog.setLayout(new BorderLayout());

        CropPanel cropPanel = new CropPanel(originalImage);
        
        // Add directly to scroll pane to ensure mouse events are captured perfectly
        JScrollPane scroll = new JScrollPane(cropPanel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getHorizontalScrollBar().setUnitIncrement(20);
        dialog.add(scroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        JButton applyBtn = new JButton("Apply Crop");
        JButton cancelBtn = new JButton("Cancel");
        
        applyBtn.addActionListener(e -> {
            Rectangle rect = cropPanel.getSelection();
            if (rect != null && rect.width > 0 && rect.height > 0) {
                double scaleX = (double) originalImage.getWidth() / cropPanel.getDisplayedWidth();
                double scaleY = (double) originalImage.getHeight() / cropPanel.getDisplayedHeight();
                
                int origX = (int) (rect.x * scaleX);
                int origY = (int) (rect.y * scaleY);
                int origW = (int) (rect.width * scaleX);
                int origH = (int) (rect.height * scaleY);

                origX = Math.max(0, Math.min(origX, originalImage.getWidth() - 1));
                origY = Math.max(0, Math.min(origY, originalImage.getHeight() - 1));
                origW = Math.min(originalImage.getWidth() - origX, origW);
                origH = Math.min(originalImage.getHeight() - origY, origH);

                if (origW > 0 && origH > 0) {
                    BufferedImage cropped = new BufferedImage(origW, origH, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = cropped.createGraphics();
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, origW, origH);
                    g.drawImage(originalImage.getSubimage(origX, origY, origW, origH), 0, 0, null);
                    g.dispose();
                    
                    originalImage = cropped;
                    updatePreview();
                    dialog.dispose();
                }
            } else {
                JOptionPane.showMessageDialog(dialog, "Please drag a rectangle to select an area.");
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());
        btnPanel.add(applyBtn);
        btnPanel.add(cancelBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        
        dialog.setSize(900, 750);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private class CropPanel extends JPanel {
        private BufferedImage img;
        private Image displayedImg;
        private int dispW, dispH;
        
        private Point startPoint, endPoint;
        private Rectangle selection;

        public CropPanel(BufferedImage img) {
            this.img = img;
            setBackground(Color.DARK_GRAY);
            setOpaque(true);
            
            double ratio = Math.min(860.0 / img.getWidth(), 600.0 / img.getHeight());
            dispW = (int) (img.getWidth() * ratio);
            dispH = (int) (img.getHeight() * ratio);
            
            setPreferredSize(new Dimension(dispW, dispH));
            displayedImg = img.getScaledInstance(dispW, dispH, Image.SCALE_SMOOTH);

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        startPoint = e.getPoint();
                        endPoint = startPoint;
                        selection = null;
                        repaint();
                    }
                }
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && startPoint != null) {
                        // Clamp to image bounds
                        int ex = Math.max(0, Math.min(e.getX(), dispW));
                        int ey = Math.max(0, Math.min(e.getY(), dispH));
                        endPoint = new Point(ex, ey);
                        
                        int x = Math.min(startPoint.x, endPoint.x);
                        int y = Math.min(startPoint.y, endPoint.y);
                        int w = Math.abs(startPoint.x - endPoint.x);
                        int h = Math.abs(startPoint.y - endPoint.y);
                        
                        selection = new Rectangle(x, y, w, h);
                        repaint();
                    }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        public int getDisplayedWidth() { return dispW; }
        public int getDisplayedHeight() { return dispH; }
        public Rectangle getSelection() { return selection; }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (displayedImg != null) {
                g.drawImage(displayedImg, 0, 0, this);
            }
            
            if (selection != null) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Semi-transparent red fill
                g2.setColor(new Color(255, 0, 0, 70));
                g2.fillRect(selection.x, selection.y, selection.width, selection.height);
                
                // Solid red border
                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(2));
                g2.drawRect(selection.x, selection.y, selection.width, selection.height);
            }
        }
    }

    private void updatePreview() {
        if (originalImage == null) return;

        int w = 450;
        int h = 350;
        
        BufferedImage scaled = getScaledImage(originalImage, w, h, Color.WHITE);
        int threshold = thresholdSlider.getValue();
        boolean invert = invertCheck.isSelected();
        
        BufferedImage bwImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = scaled.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                double luminance = (0.299 * red) + (0.587 * green) + (0.114 * blue);
                boolean isDark = luminance < threshold;
                if (invert) isDark = !isDark;

                bwImage.setRGB(x, y, isDark ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }

        imageLabel.setText("");
        imageLabel.setIcon(new ImageIcon(bwImage));
    }

    private void convertToBraille() {
        if (originalImage == null) return;

        try {
            int targetCols = Integer.parseInt(colsField.getText().trim());
            int targetRows = Integer.parseInt(rowsField.getText().trim());
            int threshold = thresholdSlider.getValue();
            boolean invert = invertCheck.isSelected();

            if (targetCols <= 0 || targetRows <= 0) {
                JOptionPane.showMessageDialog(this, "Rows and Columns must be greater than 0.");
                return;
            }

            int targetWidth = targetCols * 2;
            int targetHeight = targetRows * 4;

            BufferedImage scaledImg = getScaledImage(originalImage, targetWidth, targetHeight, Color.WHITE);

            StringBuilder sb = new StringBuilder();
            int[][] dotMap = {
                {0x01, 0x08}, {0x02, 0x10}, {0x04, 0x20}, {0x40, 0x80}
            };

            for (int r = 0; r < targetRows; r++) {
                for (int c = 0; c < targetCols; c++) {
                    int charCode = 0x2800;

                    for (int dy = 0; dy < 4; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            int px = (c * 2) + dx;
                            int py = (r * 4) + dy;

                            int rgb = scaledImg.getRGB(px, py);
                            int red = (rgb >> 16) & 0xFF;
                            int green = (rgb >> 8) & 0xFF;
                            int blue = rgb & 0xFF;

                            double luminance = (0.299 * red) + (0.587 * green) + (0.114 * blue);
                            boolean isDark = luminance < threshold;
                            if (invert) isDark = !isDark;

                            if (isDark) {
                                charCode |= dotMap[dy][dx];
                            }
                        }
                    }
                    sb.append((char) charCode);
                }
                sb.append("\n");
            }

            outputArea.setText(sb.toString());
            outputArea.setCaretPosition(0);
            outputArea.selectAll(); 

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numbers for dimensions.");
        }
    }

    private BufferedImage getScaledImage(Image srcImg, int w, int h, Color bgColor) {
        BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = resizedImg.createGraphics();
        g2.setColor(bgColor);
        g2.fillRect(0, 0, w, h);
        
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        double imgRatio = (double) srcImg.getWidth(null) / srcImg.getHeight(null);
        double boxRatio = (double) w / h;
        int newW, newH;
        if (imgRatio > boxRatio) {
            newW = w; newH = (int) (w / imgRatio);
        } else {
            newH = h; newW = (int) (h * imgRatio);
        }
        
        int x = (w - newW) / 2;
        int y = (h - newH) / 2;
        
        g2.drawImage(srcImg, x, y, newW, newH, null);
        g2.dispose();
        return resizedImg;
    }

    private static Font pickBrailleFont(int size) {
        String[] candidates = {"Segoe UI Symbol", "Segoe UI", "DejaVu Sans", "Dialog", "Monospaced"};
        String probe = "\u2800\u28FF";
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, size);
            if (f.canDisplayUpTo(probe) == -1) return f;
        }
        return new Font("Monospaced", Font.PLAIN, size);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        
        SwingUtilities.invokeLater(() -> new ImageToBrailleApp().setVisible(true));
    }
}