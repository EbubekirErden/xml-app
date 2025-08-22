# XML Parser - Distribution Package

## How to Run

### Method 1: Double-click the JAR file
1. Make sure Java is installed on your system
2. Double-click `xml-parser.jar` to run the application

### Method 2: Use the batch file
1. Double-click `run-xml-parser.bat`

### Method 3: Command line
1. Open command prompt in this folder
2. Run: `java -jar xml-parser.jar`

## Requirements
- Java 8 or higher must be installed
- **IMPORTANT**: The lib/ folder must stay in the same directory as xml-parser.jar

## Files Included
- `xml-parser.jar` - **Main application (DOUBLE-CLICK THIS TO RUN)**
- `run-xml-parser.bat` - Windows batch file to run the app
- `lib/` folder - **Required dependency libraries (DO NOT MOVE OR DELETE)**
  - `poi-3.17.jar` - Apache POI (Excel file handling)
  - `poi-ooxml-3.17.jar` - POI OOXML support
  - `poi-ooxml-schemas-3.17.jar` - POI OOXML schemas
  - `commons-collections4-4.1.jar` - Apache Commons Collections
  - `xmlbeans-2.6.0.jar` - XML processing
  - `junit-platform-console-standalone-1.13.0-M3.jar` - Testing framework

## Folder Structure
```
xml-parser/
├── xml-parser.jar        ← MAIN APPLICATION (double-click this!)
├── run-xml-parser.bat    ← Alternative launcher
└── lib/                  ← Dependencies (keep this folder!)
    ├── poi-3.17.jar
    ├── poi-ooxml-3.17.jar
    └── ... (other libraries)
```

## Troubleshooting
- If double-clicking doesn't work, Java might not be properly installed or associated with .jar files
- Use the batch file or command line method as an alternative
- Make sure the lib/ folder stays in the same directory as xml-parser.jar
