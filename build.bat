@echo off
echo Building XML Parser JAR file...
echo.

REM Clean previous build
if exist build rmdir /s /q build
if exist xml-parser.jar del xml-parser.jar
if exist *.class del *.class

REM Create build directory
if not exist build mkdir build

REM Compile Java files to build directory
echo Compiling Java files...
"C:\Program Files\Java\jdk-1.8\bin\javac.exe" -encoding UTF-8 -cp "lib/*" -d build *.java
if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

REM Create JAR file from build directory
echo Creating JAR file...
"C:\Program Files\Java\jdk-1.8\bin\jar.exe" cfm xml-parser.jar MANIFEST.MF -C build .
if errorlevel 1 (
    echo JAR creation failed!
    pause
    exit /b 1
)

echo.
echo Build successful! xml-parser.jar created.

REM Update distribution folder
echo Updating distribution folder...
if not exist dist mkdir dist
if not exist dist\lib mkdir dist\lib
copy xml-parser.jar dist\
copy lib\*.jar dist\lib\

REM Clean up build artifacts from root (keep dist clean)
if exist xml-parser.jar del xml-parser.jar

echo.
echo ========================================
echo BUILD COMPLETED SUCCESSFULLY!
echo ========================================
echo.
echo Your application is ready in the 'dist' folder:
echo - dist\xml-parser.jar (main application)
echo - dist\lib\ (dependencies)
echo - dist\README.txt (user instructions)
echo.
echo To run: Double-click dist\xml-parser.jar
echo ========================================
