# XML Parser & File Manager

[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/)
[![GUI](https://img.shields.io/badge/GUI-Swing-green.svg)](https://docs.oracle.com/javase/tutorial/uiswing/)

A powerful and user-friendly Java Swing application for parsing XML files and converting them to Excel (.xlsx) or CSV formats, with comprehensive file management capabilities.

## 🚀 Features

### Core Functionality
- **XML to Excel/CSV Conversion**: Intelligently parses XML files and converts them to structured spreadsheets
- **Smart Tag Processing**: Handles XML elements with or without child elements appropriately
- **Format Options**: Supports both Excel (.xlsx) and CSV output formats
- **Progress Tracking**: Real-time progress indication with cancellation support

### File Management
- **Dual-Panel Interface**: Separate views for input XML files and output results
- **Context Menus**: Right-click menus with comprehensive file operations
- **File Operations**: Open, delete, rename, copy path, view properties
- **Bulk Operations**: Clear all files, delete multiple files at once
- **Drag & Drop**: Easy file loading (coming soon)

### User Interface
- **Modern Design**: Clean, professional interface with intuitive layout
- **Dark/Light Theme**: Toggle between dark and light modes
- **Responsive Layout**: Resizable window with minimum size constraints
- **Status Bar**: Real-time file count and operation status
- **Detailed View**: Optional detailed file information display

### Keyboard & Mouse Shortcuts
- **DEL Key**: Delete selected file from disk
- **Double-Click**: Open file with default application
- **Single-Click**: Select file
- **Right-Click**: Context menu with file operations
- **Click Empty Space**: Deselect all files

### Configuration Options
- **Automatic Naming**: Auto-generate output filenames with "_out" suffix
- **Custom Output Path**: Choose where to save converted files
- **File Validation**: Large file warnings and error handling
- **Memory Management**: Efficient processing for large XML files

### Large File Handling
- Files over 50MB trigger confirmation dialogs
- Progress bars show processing status
- Cancel operation anytime during processing
- Memory-efficient streaming for large datasets

### Error Handling
- Invalid XML files are detected and reported
- Permission errors provide clear feedback
- File corruption is handled gracefully
- Detailed error messages help troubleshooting

## 🔨 Quick Start

### For End Users (Pre-built)
If you have the distribution package:
1. **Download** the latest release from the releases page
2. **Extract** the zip file to any folder
3. **Double-click** `xml-parser.jar` to run the application
   - Or use `run-xml-parser.bat` as an alternative
   - Or run from command line: `java -jar xml-parser.jar`

### For Developers (Build from Source)

#### Prerequisites
- Java 8 or higher installed
- Git (to clone the repository)

#### Build Steps
1. **Clone the repository**:
   ```bash
   git clone https://github.com/EbubekirErden/xml-app.git
   cd xml-app
   ```

2. **Build the application**:
   ```bash
   .\build.bat
   ```

3. **Run the application**:
   ```bash
   cd dist
   java -jar xml-parser.jar
   ```

#### What the Build Creates
- `build/` - Compilation artifacts (.class files)
- `dist/` - Distribution package ready for end users
  - `xml-parser.jar` - Main executable
  - `lib/` - Runtime dependencies
  - `README.txt` - User instructions
  - `run-xml-parser.bat` - Windows launcher

#### Project Structure
```
xml-app/
├── XmlAppGUI.java          # Main GUI application
├── XmlParser.java          # XML processing logic
├── build.bat               # Build script
├── lib/                    # Dependencies (Apache POI, etc.)
├── MANIFEST.MF             # JAR manifest configuration
└── dist/                   # Distribution folder (created by build)
    ├── xml-parser.jar      # ← Main executable
    ├── lib/                # ← Runtime dependencies
    ├── run-xml-parser.bat  # ← Windows launcher
    └── README.txt          # ← User instructions
```

#### Development Notes
- Dependencies are included in the `lib/` folder (no external downloads needed)
- The `build/` folder contains temporary compilation files
- Only distribute the contents of the `dist/` folder to end users
- The root directory stays clean during development

## 🔧 Dependencies

- **Apache POI**: Excel file generation and manipulation
- **Java Swing**: GUI framework (built-in)
- **XML Stream API**: XML parsing (built-in)

## 💻 System Requirements

- **Java**: Version 8 or higher
- **Operating System**: Windows (batch files), Linux/Mac (command line)
- **Memory**: Minimum 512MB RAM (more for large XML files)
- **Disk Space**: ~50MB for application and dependencies

## 🖥️ Usage

### Getting Started
1. **Launch the Application**: Run the JAR file or execute the main class
2. **Load XML Files**: Click "Load File" or use File → Add Files
3. **Select File**: Click on a file in the Input list
4. **Configure Options**: Use Options menu to set preferences
5. **Process File**: Click "Process File" to convert
6. **Access Results**: Converted files appear in the Output list

### Menu Options

#### File Menu
- **Change Default Folder**: Set default directory for file browser
- **Clear Input Files**: Remove all files from input list
- **Clear Output Files**: Remove all files from output list
- **Exit**: Close the application

#### Options Menu
- **Enable CSV Export**: Toggle between Excel and CSV output
- **Dark Mode**: Switch between light and dark themes
- **Automatic File Naming**: Enable/disable auto-generated names
- **Use Default Output Path**: Save files in same folder as input
- **Detailed File View**: Show extended file information

#### Help Menu
- **How to Use**: Quick start guide
- **System Info & Tips**: Performance and usage tips
- **About**: Application information

## 🎯 Use Cases

- **Data Migration**: Convert XML databases to spreadsheet format
- **Report Generation**: Transform XML reports to Excel for analysis
- **Data Analysis**: Prepare XML data for statistical software
- **Legacy System Integration**: Bridge XML and spreadsheet workflows
- **Batch Processing**: Convert multiple XML files efficiently
