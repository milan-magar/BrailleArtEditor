import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BrailleArtCanvas extends JFrame {

    // ---------- Configuration ----------
    private int rows = 16;
    private int cols = 32;
    private static final char BLANK = '\u2800'; // ⠀ braille blank

    // Expanded Palettes
    private static final char[] PALETTE_BRAILLE = {
            '\u28FF', '\u28FE', '\u2877', '\u2866', '\u2804', '\u2870', '\u284F', '\u2807',
            '\u281B', '\u281F', '\u281E', '\u2827', '\u2824', '\u2802', '\u2801', BLANK
    };
    
    private static final char[] PALETTE_LETTERS = 
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
            
    private static final char[] PALETTE_SYMBOLS = 
            "0123456789.,!?-_,=+*/\\|()[]{}<>#@$%& ".toCharArray();

    // Colors Configuration
    private static final Color[] COLORS = {
        null,
        new Color(245, 224, 220), new Color(242, 205, 205), new Color(245, 194, 231), new Color(250, 179, 135),
        new Color(249, 226, 175), new Color(166, 227, 161), new Color(148, 226, 213), new Color(137, 220, 235),
        new Color(116, 199, 236)
    };

    // ---------- State ----------
    private char[][] grid;
    private char activeBrush = '\u28FF';
    private int[] rowColors;
    private int activeColorIndex = 0;
    private boolean isPreviewMode = false;
    private boolean showColorsInCreateMode = false; // Track "Show Color" toggle

    // ---------- Undo/Redo State ----------
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

    // ---------- UI Components ----------
    private final CanvasPanel canvasPanel = new CanvasPanel();
    private final JLabel statusLabel = new JLabel(" Ready");
    private final JScrollPane scrollPane;
    private final ButtonGroup paletteButtonGroup = new ButtonGroup();
    private final ButtonGroup colorButtonGroup = new ButtonGroup();
    private final JPanel viewportPanel;
    
    private JPanel topPanel;
    private JPanel palettePanel;
    
    private JSpinner rowSpinner;
    private JSpinner colSpinner;
    private JToggleButton showColorBtn;
    private JButton undoBtn;
    private JButton redoBtn;
    private JButton clearBtn;
    private JToggleButton eraserBtn;
    private JButton copyBtn;
    private JButton pasteBtn;
    private JButton saveBtn;
    private JButton previewBtn;
    private JButton backBtn;

    private JPanel colorPanel;
    private JButton[] axisColorBoxes;

    public BrailleArtCanvas() {
        super("Braille & ASCII Art Drawing Canvas");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        initGrid(rows, cols);
        setupKeyBindings();

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.setBackground(new Color(22, 22, 24));

        viewportPanel = new JPanel(new BorderLayout());
        viewportPanel.setBackground(new Color(18, 18, 20));
        
        colorPanel = new JPanel(null);
        colorPanel.setBackground(new Color(18, 18, 20));
        
        // Wrap canvas in a centering panel
        JPanel canvasWrapper = new JPanel(new GridBagLayout());
        canvasWrapper.setBackground(new Color(18, 18, 20));
        canvasWrapper.add(canvasPanel);
        
        viewportPanel.add(colorPanel, BorderLayout.WEST);
        viewportPanel.add(canvasWrapper, BorderLayout.CENTER);

        scrollPane = new JScrollPane(viewportPanel);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 80), 1));
        scrollPane.getViewport().setBackground(new Color(18, 18, 20));
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        topPanel = buildTopPanel();
        palettePanel = buildPalette();
        JPanel bottomPanel = buildBottomControls();

        root.add(topPanel, BorderLayout.NORTH);
        root.add(palettePanel, BorderLayout.WEST);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);
        refreshDimensions();
        
        pack();
        setMinimumSize(new Dimension(1050, 700));
        setLocationRelativeTo(null);
    }

    private void initGrid(int r, int c) {
        grid = new char[r][c];
        rowColors = new int[r];
        for (int i = 0; i < r; i++) {
            rowColors[i] = 0;
            for (int j = 0; j < c; j++) {
                grid[i][j] = BLANK;
            }
        }
    }

    // ====================== KEY BINDINGS ======================
    private void setupKeyBindings() {
        InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getRootPane().getActionMap();
        
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        am.put("undo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { undo(); }
        });
        
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        am.put("redo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { redo(); }
        });
    }

    // ====================== MODE SWITCHING ======================
    private void togglePreviewMode(boolean preview) {
        isPreviewMode = preview;
        topPanel.setVisible(!preview);
        palettePanel.setVisible(!preview);
        
        if (preview) {
            colorPanel.setVisible(false);
            scrollPane.setBorder(null); 
            viewportPanel.setBackground(new Color(18, 18, 20));
            scrollPane.setBackground(new Color(18, 18, 20));
        } else {
            colorPanel.setVisible(true);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 80), 1));
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

    // ====================== TOP PANEL ======================
    private JPanel buildTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBackground(new Color(22, 22, 24));

        rowSpinner = new JSpinner(new SpinnerNumberModel(rows, 4, 200, 1));
        colSpinner = new JSpinner(new SpinnerNumberModel(cols, 4, 200, 1));
        styleSpinner(rowSpinner);
        styleSpinner(colSpinner);

        JButton applyBtn = new JButton("Apply Dimensions");
        styleButton(applyBtn);
        applyBtn.addActionListener(e -> {
            int newRows = (int) rowSpinner.getValue();
            int newCols = (int) colSpinner.getValue();
            resizeGrid(newRows, newCols);
        });

        // New "Show Color" Toggle Button
        showColorBtn = new JToggleButton("Show Color");
        showColorBtn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        showColorBtn.setFocusPainted(false);
        showColorBtn.setForeground(Color.WHITE);
        showColorBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 80), 1),
                new EmptyBorder(6, 12, 6, 12)
        ));
        showColorBtn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                AbstractButton b = (AbstractButton) c;
                if (b.isSelected()) {
                    g.setColor(new Color(0, 120, 215));
                } else if (b.getModel().isRollover()) {
                    g.setColor(new Color(60, 60, 70));
                } else {
                    g.setColor(new Color(45, 45, 52));
                }
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
                super.paint(g, c);
            }
        });
        showColorBtn.addActionListener(e -> {
            showColorsInCreateMode = showColorBtn.isSelected();
            canvasPanel.repaint();
        });

        JLabel lblRows = new JLabel("Rows:");
        lblRows.setForeground(Color.LIGHT_GRAY);
        JLabel lblCols = new JLabel("Columns:");
        lblCols.setForeground(Color.LIGHT_GRAY);

        panel.add(lblRows);
        panel.add(rowSpinner);
        panel.add(lblCols);
        panel.add(colSpinner);
        panel.add(applyBtn);
        panel.add(showColorBtn); // Added here
        
        return panel;
    }

    private void styleSpinner(JSpinner spinner) {
        spinner.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(new Color(45, 45, 52));
            tf.setForeground(Color.WHITE);
            tf.setCaretColor(Color.WHITE);
        }
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
        rows = newRows;
        cols = newCols;
        
        rowSpinner.setValue(rows);
        colSpinner.setValue(cols);
        
        clearHistory();
        refreshDimensions();
        statusLabel.setText("Canvas resized to " + rows + " rows × " + cols + " cols.");
    }

    private void refreshDimensions() {
        canvasPanel.refreshSize();
        
        colorPanel.removeAll();
        axisColorBoxes = new JButton[rows];
        
        int cellH = canvasPanel.cellH;
        int padTop = canvasPanel.padTop;
        
        for (int r = 0; r < rows; r++) {
            JButton btn = new JButton();
            btn.setFocusPainted(false);
            btn.setFont(new Font("SansSerif", Font.BOLD, 12));
            btn.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 65)));
            
            btn.setOpaque(true);
            btn.setContentAreaFilled(false); 
            btn.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
                @Override
                public void paint(Graphics g, JComponent c) {
                    AbstractButton b = (AbstractButton) c;
                    g.setColor(b.getBackground());
                    g.fillRect(0, 0, c.getWidth(), c.getHeight());
                    super.paint(g, c);
                }
            });

            btn.setBounds(5, padTop + r * cellH + 1, 45, cellH - 2);
            
            int rowIdx = r;
            btn.addActionListener(ev -> {
                rowColors[rowIdx] = activeColorIndex;
                updateColorButton(rowIdx);
                canvasPanel.repaint();
            });
            
            axisColorBoxes[r] = btn;
            colorPanel.add(btn);
            updateColorButton(r);
        }
        
        colorPanel.setPreferredSize(new Dimension(70, canvasPanel.getPreferredSize().height));
        viewportPanel.revalidate();
        viewportPanel.repaint();
    }

    private void updateColorButton(int r) {
        JButton btn = axisColorBoxes[r];
        int idx = rowColors[r];
        
        if (idx == 0) {
            btn.setBackground(new Color(30, 30, 35));
            btn.setText("");
            btn.setForeground(Color.LIGHT_GRAY);
        } else {
            btn.setBackground(COLORS[idx]);
            btn.setText(String.valueOf(idx));
            btn.setForeground(isDark(COLORS[idx]) ? Color.BLACK : Color.WHITE);
        }
    }

    private boolean isDark(Color c) {
        return (c.getRed() * 0.299 + c.getGreen() * 0.587 + c.getBlue() * 0.114) < 128;
    }

    // ====================== PALETTE ======================
    private JPanel buildPalette() {
        JPanel outer = new JPanel(new BorderLayout(0, 10));
        outer.setBackground(new Color(22, 22, 24));
        outer.setBorder(new EmptyBorder(0, 0, 0, 10));

        JLabel title = new JLabel("Symbols & Colors");
        title.setForeground(new Color(220, 220, 230));
        title.setFont(pickFont(Font.BOLD, 16));
        outer.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override
            protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                g.setColor(isSelected ? new Color(45, 45, 52) : new Color(30, 30, 35));
                g.fillRect(x, y, w, h);
            }
            @Override
            protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {}
            @Override
            protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {}
        });
        
        tabs.addTab("Braille", createPalettePanel(PALETTE_BRAILLE));
        tabs.addTab("Letters", createPalettePanel(PALETTE_LETTERS));
        tabs.addTab("Symbols", createPalettePanel(PALETTE_SYMBOLS));
        tabs.addTab("Colors", createColorPalettePanel());
        
        outer.add(tabs, BorderLayout.CENTER);
        return outer;
    }

    private JScrollPane createPalettePanel(char[] symbols) {
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
                @Override
                public void paint(Graphics g, JComponent c) {
                    AbstractButton b = (AbstractButton) c;
                    g.setColor(b.isSelected() ? new Color(0, 120, 215) : b.getBackground());
                    g.fillRect(0, 0, c.getWidth(), c.getHeight());
                    super.paint(g, c);
                }
            });

            if (sym == activeBrush) btn.setSelected(true);
            btn.addActionListener(e -> {
                activeBrush = sym;
                eraserBtn.setSelected(false);
                updateStatus();
            });
            paletteButtonGroup.add(btn);
            gridPanel.add(btn);
        }
        
        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(new Color(30, 30, 35));
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
                @Override
                public void paint(Graphics g, JComponent c) {
                    AbstractButton b = (AbstractButton) c;
                    g.setColor(b.getBackground());
                    g.fillRect(0, 0, c.getWidth(), c.getHeight());
                    if (b.isSelected()) {
                        g.setColor(Color.WHITE);
                        g.drawRect(1, 1, c.getWidth()-3, c.getHeight()-3);
                        g.drawRect(2, 2, c.getWidth()-5, c.getHeight()-5);
                    }
                    super.paint(g, c);
                }
            });

            if (i == activeColorIndex) btn.setSelected(true);
            btn.addActionListener(e -> {
                activeColorIndex = idx;
                updateStatus();
            });
            colorButtonGroup.add(btn);
            gridPanel.add(btn);
        }
        
        JScrollPane scroll = new JScrollPane(gridPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(new Color(30, 30, 35));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // ====================== BOTTOM CONTROLS ======================
    private JPanel buildBottomControls() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(new Color(22, 22, 24));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftControls.setBackground(new Color(22, 22, 24));

        undoBtn = new JButton("Undo");
        styleButton(undoBtn);
        undoBtn.setEnabled(false);
        undoBtn.addActionListener(e -> undo());

        redoBtn = new JButton("Redo");
        styleButton(redoBtn);
        redoBtn.setEnabled(false);
        redoBtn.addActionListener(e -> redo());

        clearBtn = new JButton("Clear");
        styleButton(clearBtn);
        clearBtn.addActionListener(e -> {
            for (int r = 0; r < rows; r++) {
                rowColors[r] = 0;
                updateColorButton(r);
                for (int c = 0; c < cols; c++) {
                    grid[r][c] = BLANK;
                }
            }
            clearHistory();
            canvasPanel.repaint();
            statusLabel.setText("Canvas and colors cleared.");
        });

        eraserBtn = new JToggleButton("Eraser");
        eraserBtn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        eraserBtn.setFocusPainted(false);
        eraserBtn.setForeground(Color.WHITE);
        eraserBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 80), 1),
                new EmptyBorder(6, 12, 6, 12)
        ));
        eraserBtn.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                AbstractButton b = (AbstractButton) c;
                if (b.isSelected()) {
                    g.setColor(new Color(0, 120, 215));
                } else if (b.getModel().isRollover()) {
                    g.setColor(new Color(60, 60, 70));
                } else {
                    g.setColor(new Color(45, 45, 52));
                }
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
                super.paint(g, c);
            }
        });
        eraserBtn.addActionListener(e -> {
            if (eraserBtn.isSelected()) {
                activeBrush = BLANK;
                paletteButtonGroup.clearSelection();
            }
            updateStatus();
            canvasPanel.requestFocusInWindow();
        });

        copyBtn = new JButton("Copy");
        styleButton(copyBtn);
        copyBtn.addActionListener(e -> copyToClipboard());

        pasteBtn = new JButton("Paste");
        styleButton(pasteBtn);
        pasteBtn.addActionListener(e -> pasteFromClipboard());

        saveBtn = new JButton("Save .txt");
        styleButton(saveBtn);
        saveBtn.addActionListener(e -> saveToFile());

        previewBtn = new JButton("Preview");
        styleButton(previewBtn);
        previewBtn.addActionListener(e -> togglePreviewMode(true));

        backBtn = new JButton("Return to Create Mode");
        styleButton(backBtn);
        backBtn.setVisible(false);
        backBtn.addActionListener(e -> togglePreviewMode(false));

        leftControls.add(undoBtn);
        leftControls.add(redoBtn);
        leftControls.add(clearBtn);
        leftControls.add(eraserBtn);
        leftControls.add(copyBtn);
        leftControls.add(pasteBtn);
        leftControls.add(saveBtn);
        leftControls.add(previewBtn);
        leftControls.add(backBtn);

        statusLabel.setForeground(new Color(0, 229, 255));
        statusLabel.setFont(pickFont(Font.PLAIN, 14));

        bar.add(leftControls, BorderLayout.WEST);
        bar.add(statusLabel, BorderLayout.CENTER);
        updateStatus();
        return bar;
    }

    private void styleButton(JButton btn) {
        btn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        btn.setFocusPainted(false);
        btn.setForeground(Color.WHITE);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 80), 1),
                new EmptyBorder(6, 12, 6, 12)
        ));
        
        btn.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                AbstractButton b = (AbstractButton) c;
                if (!b.isEnabled()) {
                    g.setColor(new Color(30, 30, 35));
                } else if (b.getModel().isPressed()) {
                    g.setColor(new Color(35, 35, 40));
                } else if (b.getModel().isRollover()) {
                    g.setColor(new Color(60, 60, 70));
                } else {
                    g.setColor(new Color(45, 45, 52));
                }
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
                super.paint(g, c);
            }
        });
    }

    private void updateStatus() {
        String brushStr = (activeBrush == ' ') ? "␣" : String.valueOf(activeBrush);
        statusLabel.setText(String.format("Brush: '%s' | Active Color: %s | Canvas: %d × %d", 
                brushStr, activeColorIndex == 0 ? "None" : String.valueOf(activeColorIndex), rows, cols));
    }

    // ====================== UNDO / REDO LOGIC ======================
    private void clearHistory() {
        undoHistory.clear();
        redoHistory.clear();
        updateUndoRedoButtons();
    }

    private void undo() {
        if (undoHistory.isEmpty()) return;
        List<CellEdit> stroke = undoHistory.remove(undoHistory.size() - 1);
        redoHistory.add(stroke);
        
        for (int i = stroke.size() - 1; i >= 0; i--) {
            CellEdit edit = stroke.get(i);
            grid[edit.r][edit.c] = edit.prev;
        }
        canvasPanel.repaint();
        updateUndoRedoButtons();
        statusLabel.setText("Undo performed.");
    }

    private void redo() {
        if (redoHistory.isEmpty()) return;
        List<CellEdit> stroke = redoHistory.remove(redoHistory.size() - 1);
        undoHistory.add(stroke);
        
        for (CellEdit edit : stroke) {
            grid[edit.r][edit.c] = edit.next;
        }
        canvasPanel.repaint();
        updateUndoRedoButtons();
        statusLabel.setText("Redo performed.");
    }

    private void updateUndoRedoButtons() {
        if (undoBtn != null) undoBtn.setEnabled(!undoHistory.isEmpty());
        if (redoBtn != null) redoBtn.setEnabled(!redoHistory.isEmpty());
    }

    // ====================== FILE & CLIPBOARD OPERATIONS ======================
    private String exportText() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            if (rowColors[r] > 0) {
                sb.append("$").append(rowColors[r]).append(" ");
            }
            for (int c = 0; c < cols; c++) {
                sb.append(grid[r][c]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void copyToClipboard() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(exportText()), null);
        statusLabel.setText("Copied canvas to clipboard (with color markers).");
    }

    private void pasteFromClipboard() {
        try {
            String data = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (data == null || data.isEmpty()) {
                statusLabel.setText("Clipboard is empty.");
                return;
            }

            String[] lines = data.split("\\r?\\n");
            int newRows = lines.length;
            int newCols = 0;
            
            int[] extractedColors = new int[newRows];

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                extractedColors[i] = 0; // Default to None

                if (line.matches("^\\$[1-9]\\s+.*")) {
                    int spaceIdx = line.indexOf(' ');
                    extractedColors[i] = Integer.parseInt(line.substring(1, spaceIdx));
                    line = line.substring(spaceIdx + 1);
                } 
                else if (line.matches("^\\$(?:None|#\\w+)\\s+.*")) {
                    int spaceIdx = line.indexOf(' ');
                    line = line.substring(spaceIdx + 1);
                }
                else if (line.matches("^\\$\\S+\\s+.*")) {
                    int spaceIdx = line.indexOf(' ');
                    line = line.substring(spaceIdx + 1);
                }
                
                lines[i] = line;
                newCols = Math.max(newCols, lines[i].length());
            }

            newRows = Math.max(4, newRows);
            newCols = Math.max(4, newCols);

            resizeGrid(newRows, newCols);
            
            for (int r = 0; r < newRows; r++) {
                rowColors[r] = extractedColors[r];
                updateColorButton(r);
                
                String line = lines[r];
                for (int c = 0; c < newCols; c++) {
                    if (c < line.length()) {
                        grid[r][c] = line.charAt(c);
                    } else {
                        grid[r][c] = BLANK;
                    }
                }
            }
            clearHistory();
            canvasPanel.repaint();
            statusLabel.setText("Pasted " + newRows + " rows with color data.");
        } catch (Exception ex) {
            statusLabel.setText("Failed to paste: " + ex.getMessage());
        }
    }

    private void saveToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Canvas as Text File");
        chooser.setFileFilter(new FileNameExtensionFilter("Text Files (*.txt)", "txt"));
        chooser.setSelectedFile(new File("braille_art.txt"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".txt")) {
                file = new File(file.getAbsolutePath() + ".txt");
            }
            try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
                out.print(exportText());
                statusLabel.setText("Saved to: " + file.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ====================== CANVAS ======================
    private class CanvasPanel extends JPanel {
        public final int cellW = 12; 
        public final int cellH = 24; 
        
        // These values change in preview mode
        public int padLeft = 30; 
        public int padTop = 24;  
        public int padRight = 10;
        public int padBottom = 10;

        private boolean dragging = false;
        private int hoverRow = -1, hoverCol = -1;
        private int cursorR = 0, cursorC = 0;

        CanvasPanel() {
            setBackground(new Color(18, 18, 20));
            setFont(pickFont(Font.PLAIN, 16)); 
            setFocusable(true);
            refreshSize();

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (isPreviewMode) return;
                    requestFocusInWindow();
                    dragging = true;
                    currentStroke = new ArrayList<>();
                    
                    int c = (e.getX() - padLeft) / cellW;
                    int r = (e.getY() - padTop) / cellH;
                    if (r >= 0 && r < rows && c >= 0 && c < cols) {
                        cursorR = r; cursorC = c;
                    }
                    
                    paintAt(e);
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (isPreviewMode) return;
                    if (dragging) paintAt(e);
                    updateHover(e);
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (isPreviewMode) return;
                    dragging = false;
                    if (currentStroke != null && !currentStroke.isEmpty()) {
                        undoHistory.add(currentStroke);
                        redoHistory.clear();
                        currentStroke = null;
                        updateUndoRedoButtons();
                    }
                }
                @Override public void mouseMoved(MouseEvent e) {
                    if (isPreviewMode) return;
                    updateHover(e);
                }
                @Override public void mouseExited(MouseEvent e) {
                    hoverRow = -1; hoverCol = -1;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (isPreviewMode) return;
                    if (e.getKeyCode() == KeyEvent.VK_UP) { cursorR = Math.max(0, cursorR - 1); repaint(); }
                    else if (e.getKeyCode() == KeyEvent.VK_DOWN) { cursorR = Math.min(rows - 1, cursorR + 1); repaint(); }
                    else if (e.getKeyCode() == KeyEvent.VK_LEFT) { cursorC = Math.max(0, cursorC - 1); repaint(); }
                    else if (e.getKeyCode() == KeyEvent.VK_RIGHT) { cursorC = Math.min(cols - 1, cursorC + 1); repaint(); }
                }
                @Override
                public void keyTyped(KeyEvent e) {
                    if (isPreviewMode) return;
                    char c = e.getKeyChar();
                    if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
                        c = activeBrush;
                    } else if (c < 32 || c >= 127) {
                        return;
                    }
                    
                    activeBrush = c;
                    eraserBtn.setSelected(false);
                    
                    boolean found = false;
                    for (AbstractButton btn : Collections.list(paletteButtonGroup.getElements())) {
                        String txt = btn.getText();
                        if (txt.length() > 0 && txt.charAt(0) == c) {
                            btn.setSelected(true);
                            found = true; break;
                        }
                    }
                    if (!found) paletteButtonGroup.clearSelection();
                    updateStatus();
                    
                    char prev = grid[cursorR][cursorC];
                    grid[cursorR][cursorC] = activeBrush;
                    
                    List<CellEdit> stroke = new ArrayList<>();
                    stroke.add(new CellEdit(cursorR, cursorC, prev, activeBrush));
                    undoHistory.add(stroke);
                    redoHistory.clear();
                    updateUndoRedoButtons();
                    
                    cursorC = Math.min(cols - 1, cursorC + 1);
                    repaint();
                }
            });
        }

        public void refreshSize() {
            if (isPreviewMode) {
                padLeft = 0; 
                padTop = 0; 
                padRight = 0; 
                padBottom = 0;
            } else {
                padLeft = 30; 
                padTop = 24;  
                padRight = 10;
                padBottom = 10;
            }
            
            int w = padLeft + cols * cellW + padRight;
            int h = padTop + rows * cellH + padBottom;
            setPreferredSize(new Dimension(w, h));
            revalidate();
            repaint();
        }

        private void updateHover(MouseEvent e) {
            int c = (e.getX() - padLeft) / cellW;
            int r = (e.getY() - padTop) / cellH;
            if (r >= 0 && r < rows && c >= 0 && c < cols) {
                if (r != hoverRow || c != hoverCol) {
                    hoverRow = r; hoverCol = c;
                    repaint();
                }
            } else {
                if (hoverRow != -1 || hoverCol != -1) {
                    hoverRow = -1; hoverCol = -1;
                    repaint();
                }
            }
        }

        private void paintAt(MouseEvent e) {
            int c = (e.getX() - padLeft) / cellW;
            int r = (e.getY() - padTop) / cellH;
            if (r < 0 || r >= rows || c < 0 || c >= cols) return;
            if (grid[r][c] == activeBrush) return; 
            
            char prev = grid[r][c];
            grid[r][c] = activeBrush;
            
            if (currentStroke != null) {
                currentStroke.add(new CellEdit(r, c, prev, activeBrush));
            }
            
            repaint(cellRect(c, r));
        }

        private Rectangle cellRect(int c, int r) {
            return new Rectangle(padLeft + c * cellW, padTop + r * cellH, cellW, cellH);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw Grid & Axes (Only in Create Mode)
            if (!isPreviewMode) {
                g.setColor(new Color(45, 45, 55));
                for (int r = 0; r <= rows; r++) {
                    int y = padTop + r * cellH;
                    g.drawLine(padLeft, y, padLeft + cols * cellW, y);
                }
                for (int c = 0; c <= cols; c++) {
                    int x = padLeft + c * cellW;
                    g.drawLine(x, padTop, x, padTop + rows * cellH);
                }

                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                FontMetrics fm = g.getFontMetrics();
                
                for (int c = 0; c < cols; c++) {
                    if (c % 5 != 0 && cols > 40) continue; 
                    g.setColor(c % 5 == 0 ? new Color(180, 180, 190) : new Color(100, 100, 110));
                    String num = String.valueOf(c);
                    int x = padLeft + c * cellW + (cellW - fm.stringWidth(num)) / 2;
                    g.drawString(num, x, padTop - 6); 
                }
                
                for (int r = 0; r < rows; r++) {
                    if (r % 5 != 0 && rows > 40) continue;
                    g.setColor(r % 5 == 0 ? new Color(180, 180, 190) : new Color(100, 100, 110));
                    String num = String.valueOf(r + 1);
                    int y = padTop + r * cellH + (cellH + fm.getAscent()) / 2 - 2;
                    g.drawString(num, padLeft - 8 - fm.stringWidth(num), y); 
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

            // Draw Symbols
            if (isPreviewMode) {
                // Use larger font and no centering to connect the dots
                g.setFont(pickFont(Font.PLAIN, 24));
                FontMetrics symFm = g.getFontMetrics();
                int ascent = symFm.getAscent();

                for (int r = 0; r < rows; r++) {
                    g.setColor(rowColors[r] == 0 ? new Color(0, 229, 255) : COLORS[rowColors[r]]);
                    for (int c = 0; c < cols; c++) {
                        char ch = grid[r][c];
                        if (ch == BLANK || ch == '\0') continue;
                        String s = String.valueOf(ch);
                        
                        // Draw edge-to-edge with no cell centering
                        int x = padLeft + c * cellW;
                        int y = padTop + r * cellH + ascent - 4; 
                        g.drawString(s, x, y);
                    }
                }
            } else {
                g.setFont(getFont());
                FontMetrics symFm = g.getFontMetrics();
                int ascent = symFm.getAscent();

                for (int r = 0; r < rows; r++) {
                    // Apply row color ONLY if Show Color is toggled on
                    if (showColorsInCreateMode) {
                        g.setColor(rowColors[r] == 0 ? new Color(0, 229, 255) : COLORS[rowColors[r]]);
                    } else {
                        g.setColor(new Color(0, 229, 255)); // Default cyan
                    }

                    for (int c = 0; c < cols; c++) {
                        char ch = grid[r][c];
                        if (ch == BLANK || ch == '\0') continue;
                        String s = String.valueOf(ch);
                        
                        int w = symFm.stringWidth(s);
                        int x = padLeft + c * cellW + (cellW - w) / 2;
                        int y = padTop + r * cellH + (cellH - symFm.getHeight()) / 2 + ascent;
                        g.drawString(s, x, y);
                    }
                }
            }
            g.dispose();
        }
    }

    // ====================== UTILITIES ======================
    private static Font pickFont(int style, int size) {
        String[] candidates = {
                "Segoe UI Symbol", "Segoe UI", "DejaVu Sans", 
                "Noto Sans Symbols 2", "Noto Sans Symbols", "Dialog", "SansSerif"
        };
        String probe = "\u28FF\u2801\u2800";
        for (String name : candidates) {
            Font f = new Font(name, style, size);
            if (f.canDisplay('\u28FF') && f.canDisplay('\u2801') && f.canDisplay('\u2800')) {
                if (f.canDisplayUpTo(probe) == -1) return f;
            }
        }
        return new Font("SansSerif", style, size);
    }

    // ====================== MAIN ======================
    public static void main(String[] args) {
        UIManager.put("TabbedPane.selected", new Color(45, 45, 52));
        UIManager.put("TabbedPane.contentAreaColor", new Color(22, 22, 24));
        UIManager.put("TabbedPane.focus", new Color(22, 22, 24));
        UIManager.put("TabbedPane.background", new Color(30, 30, 35));
        UIManager.put("TabbedPane.foreground", Color.WHITE);
        UIManager.put("TabbedPane.shadow", new Color(22, 22, 24));
        
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new BrailleArtCanvas().setVisible(true);
        });
    }
}