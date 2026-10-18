# Braille Master Suite 🎨🔠

A comprehensive, single-repository collection of Java Swing applications designed to create, convert, edit, and export high-detail Unicode Braille art. This suite features 4 distinct tools, including image-to-braille conversion, an MS Paint-style drawing canvas, a manual grid editor, and a pixel-sampling debugger.

## 🚀 Features

### 1. Braille Master Suite (The All-in-One App)
The main application (`BrailleMasterSuite.java`) bundles all features into a single, user-friendly tabbed interface:
*   **Image to Braille:** Load any image, auto-crop or manually crop it, adjust the luminance threshold, and preview the exact 2x4 dot grid before converting.
*   **Paint to Braille:** A freehand drawing canvas (Pencil, Rectangle, Oval, Line) that converts your drawing into Braille using a 33% pixel-coverage threshold.
*   **Manual Grid Editor:** A precision grid editor. Includes 256-pattern custom dot builders, classic presets, row-by-row color assignments, undo/redo, and a full-screen preview mode.
*   **Debugger:** Export an `ss` folder full of `.png` files that visually explain exactly how the 2x4 sub-grid sampling is working on your drawings.

### 2. Fastfetch Integration
All export/copy features automatically include Fastfetch line-color markers (`$1` through `$9`), allowing you to copy the Braille art and paste it directly into your terminal configurations for beautiful, colorized terminal art.

## 📂 Project Structure

```text
BrailleArtEditor/
│
├── src/                  # All Java source files
│   ├── BrailleMasterSuite.java
│   ├── PaintToBrailleApp.java
│   ├── ImageToBrailleApp.java
│   ├── BrailleArtCanvas.java
│   └── BrailleDebuggerApp.java
│
├── bin/                  # Compiled .class files (Ignored by Git)
│
└── Artworks/             # Exported .txt Braille art files
🛠️ How to Compile and Run
Prerequisites
Java Development Kit (JDK) 8 or higher installed and added to your system PATH.

1. Compile the Code
Open Command Prompt (cmd), navigate to the root BrailleArtEditor folder, and run:

cmd
javac -d bin src\*.java
2. Run the Application
To launch the full Master Suite:

cmd
java -cp bin BrailleMasterSuite
(Optional) To run individual tools:

cmd
java -cp bin ImageToBrailleApp
java -cp bin PaintToBrailleApp
java -cp bin BrailleDebuggerApp

⌨️ Usage Tips
Fonts: The app automatically selects Unicode-friendly fonts (like Segoe UI Symbol or DejaVu Sans) to ensure the Braille characters render cleanly.

Threshold Tuning: If your Braille art is too solid, increase the threshold slider. If it's too sparse, lower it.

Invert: Check the "Invert" box if your image has a black background with white lines (instead of white background with black lines).
