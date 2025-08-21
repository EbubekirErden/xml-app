import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.*;
import javax.xml.stream.XMLStreamException;

import java.lang.Exception;
import java.awt.Desktop;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.Timer;
import java.awt.Color;

public class XmlAppGUI {

    // Dark mode colors
    private static final Color DARK_BACKGROUND = new Color(45, 45, 45);
    private static final Color DARK_PANEL = new Color(60, 60, 60);
    private static final Color DARK_TEXT = new Color(220, 220, 220);
    private static final Color DARK_BORDER = new Color(80, 80, 80);
    private static final Color LIGHT_BACKGROUND = Color.WHITE;
    private static final Color LIGHT_PANEL = new Color(240, 240, 240);
    private static final Color LIGHT_TEXT = Color.BLACK;
    
    private static boolean isDarkMode = false;
    private static boolean useAutoNaming = true; // Controls automatic output file naming
    private static boolean useDefaultOutputPath = true; // Controls using default output path (same as input)
    private static volatile boolean isCanceled = false; // Flag for canceling operations
    private static boolean showDetailedView = false; // Controls detailed file properties view
    private static boolean enableCSVExport = false; // Controls CSV export instead of Excel

    /**
     * Formats file size in human readable format
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Formats date in readable format
     */
    private static String formatDate(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm");
        return sdf.format(new java.util.Date(timestamp));
    }

    /**
     * Gets basic file type info
     */
    private static String getFileInfo(File file) {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".xml")) {
            return "XML Document";
        } else if (fileName.endsWith(".xlsx")) {
            return "Excel Workbook";
        } else if (fileName.endsWith(".csv")) {
            return "CSV File";
        } else {
            return "Unknown Type";
        }
    }

    private static String createErrorMessage(Throwable ex) {
        String message = ex.getMessage().toLowerCase();
        if (ex instanceof InterruptedException) {
            return "The process is cancelled.";
        }
        if (ex instanceof FileNotFoundException || message.contains("file not found")) {
            return "The selected file could not be found. It may have been moved or deleted.";
        }
        if (ex instanceof XMLStreamException || message.contains("xml") || message.contains("parse")) {
            return "The file is not a valid XML file or corrupted.";
        }
        if (message.contains("access") || message.contains("permission")) {
            return "Cannot access the file. Please check file permissions.";
        }
        if (message.contains("memory") || message.contains("heap")) {
            return "The file is too large to process. Try processing smaller files.";
        }
        return "An unexpected error occurred while processing the file.";
    }

    /**
     * Applies theme to all components
     */
    private static void applyTheme(JFrame frame, JPanel filePanel, JPanel buttonPanel, JPanel listsPanel, 
                                   JList<File> inputList, JList<File> outputList, 
                                   JButton... buttons) {
        Color bgColor = isDarkMode ? DARK_BACKGROUND : LIGHT_BACKGROUND;
        Color panelColor = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        Color textColor = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        
        // Frame
        frame.getContentPane().setBackground(bgColor);
        
        // Panels
        filePanel.setBackground(panelColor);
        buttonPanel.setBackground(panelColor);
        listsPanel.setBackground(panelColor);
        
        // Lists
        inputList.setBackground(panelColor);
        inputList.setForeground(textColor);
        outputList.setBackground(panelColor);
        outputList.setForeground(textColor);
        
        // Apply theme to all labels in the frame
        applyThemeToLabels(frame, textColor);
        
        // Buttons
        for (JButton button : buttons) {
            button.setBackground(isDarkMode ? DARK_BORDER : Color.LIGHT_GRAY);
            button.setForeground(textColor);
        }
        
        // Menu bar
        JMenuBar menuBar = frame.getJMenuBar();
        if (menuBar != null) {
            menuBar.setBackground(panelColor);
            applyThemeToMenuBar(menuBar, textColor, panelColor);
        }
        
        frame.repaint();
    }

    /**
     * Recursively applies theme to all labels in a container
     */
    private static void applyThemeToLabels(java.awt.Container container, Color textColor) {
        for (java.awt.Component comp : container.getComponents()) {
            if (comp instanceof JLabel) {
                comp.setForeground(textColor);
            } else if (comp instanceof java.awt.Container) {
                applyThemeToLabels((java.awt.Container) comp, textColor);
            }
        }
    }
    
    /**
     * Applies theme to menu bar
     * @param menuBar 
     * @param textColor text color
     * @param bgColor background color
     */
    private static void applyThemeToMenuBar(JMenuBar menuBar, Color textColor, Color bgColor) {
        menuBar.setBackground(bgColor);
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            menu.setForeground(textColor);
            menu.setBackground(bgColor);
            
            for (int j = 0; j < menu.getItemCount(); j++) {
                JMenuItem item = menu.getItem(j);
                if (item != null) {
                    item.setForeground(textColor);
                    item.setBackground(bgColor);
                }
            }
        }
    }

    /**
     * Creates GUI for XML processing application
     * @param isCSV output file type checker
     */
    public static void createFrame(boolean isCSV) {
        // Note: isCSV parameter is now controlled by enableCSVExport checkbox in Options menu
        JFrame frame = new JFrame("XML Parser");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1000, 650); // Reduced height for smaller screens
            frame.setResizable(true); // Made resizable for better user experience
            frame.setMinimumSize(new java.awt.Dimension(800, 500)); // Reduced minimum size
        
        // File chooser with xml filter
        JFileChooser fileChooser = new JFileChooser("C:\\Users\\ebubekir.siddik\\Desktop");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            FileNameExtensionFilter filter = new FileNameExtensionFilter("XML file", new String[] {"xml", "XML"});
            fileChooser.setFileFilter(filter);
            fileChooser.addChoosableFileFilter(filter);
        
        // Selected file display
        JLabel selectedFileLabel = new JLabel("No file selected");

        // For input XML files
        DefaultListModel<File> inputFilesModel = new DefaultListModel<>();
        JList<File> inputFilesList = new JList<>(inputFilesModel);
        inputFilesList.setFixedCellHeight(showDetailedView ? 75 : 50); // Dynamic height based on view mode
        
        // Custom cell renderer to show file details
        inputFilesList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof File) {
                    File file = (File) value;
                    String fileName = file.getName();
                    String folderPath = file.getParent();
                    
                    // Shorten very long paths for display
                    if (folderPath != null && folderPath.length() > 50) {
                        folderPath = "..." + folderPath.substring(folderPath.length() - 47);
                    }
                    
                    if (showDetailedView) {
                        // Detailed view with file properties
                        String fileSize = formatFileSize(file.length());
                        String lastModified = formatDate(file.lastModified());
                        String fileInfo = getFileInfo(file);
                        
                        String displayText = "<html><b>" + fileName + "</b><br>" +
                                           "<small style='color: gray;'>📁 " + (folderPath != null ? folderPath : "Unknown") + "</small><br>" +
                                           "<small style='color: blue;'>📊 " + fileSize + " • 🕒 " + lastModified + " • " + fileInfo + "</small></html>";
                        setText(displayText);
                    } else {
                        // Simple view (original)
                        String displayText = "<html><b>" + fileName + "</b><br>" +
                                           "<small style='color: gray;'>📁 " + (folderPath != null ? folderPath : "Unknown") + "</small></html>";
                        setText(displayText);
                    }
                } 
                
                // Add padding and separator border
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), // Bottom separator line
                    BorderFactory.createEmptyBorder(showDetailedView ? 15 : 12, 15, showDetailedView ? 15 : 12, 15) // Dynamic padding
                ));
                
                return this;
            }
        });
        
        // Double click to open the file (replaced by new mouse listener below)

        // For output Excel/CSV files  
        DefaultListModel<File> outputFilesModel = new DefaultListModel<>();
        JList<File> outputFilesList = new JList<>(outputFilesModel);
        outputFilesList.setFixedCellHeight(showDetailedView ? 75 : 50); // Dynamic height based on view mode

        // Custom cell renderer to show file details
        outputFilesList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof File) {
                    File file = (File) value;
                    String fileName = file.getName();
                    String folderPath = file.getParent();
                    
                    // Shorten very long paths for display
                    if (folderPath != null && folderPath.length() > 50) {
                        folderPath = "..." + folderPath.substring(folderPath.length() - 47);
                    }
                    
                    if (showDetailedView) {
                        // Detailed view with file properties
                        String fileSize = file.exists() ? formatFileSize(file.length()) : "Not found";
                        String lastModified = file.exists() ? formatDate(file.lastModified()) : "Unknown";
                        String fileType = getFileInfo(file); // Use the same method as input files
                        
                        String displayText = "<html><b>" + fileName + "</b><br>" +
                                           "<small style='color: gray;'>📁 " + (folderPath != null ? folderPath : "Unknown") + "</small><br>" +
                                           "<small style='color: green;'>📊 " + fileSize + " • 🕒 " + lastModified + " • " + fileType + "</small></html>";
                        setText(displayText);
                    } else {
                        // Simple view (original)
                        String displayText = "<html><b>" + fileName + "</b><br>" +
                                           "<small style='color: gray;'>📁 " + (folderPath != null ? folderPath : "Unknown") + "</small></html>";
                        setText(displayText);
                    }
                }
                
                // Add padding and separator border
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY), // Bottom separator line
                    BorderFactory.createEmptyBorder(showDetailedView ? 15 : 12, 15, showDetailedView ? 15 : 12, 15) // Dynamic padding
                ));
                
                return this;
            }
        });

        // Add mutual exclusion between input and output list selections
        // Update input list selection listener
        inputFilesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                File selectedFile = inputFilesList.getSelectedValue();
                if (selectedFile != null) {
                    selectedFileLabel.setText("Selected: " + selectedFile.getName());
                    // Clear output list selection when input is selected
                    outputFilesList.clearSelection();
                } else {
                    selectedFileLabel.setText("No file selected");
                }
            }
        });

        // Add selection listener for output list
        outputFilesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                File selectedFile = outputFilesList.getSelectedValue();
                if (selectedFile != null) {
                    selectedFileLabel.setText("Selected Output: " + selectedFile.getName());
                    // Clear input list selection when output is selected
                    inputFilesList.clearSelection();
                } else {
                    selectedFileLabel.setText("No file selected");
                }
            }
        });
        
        // Add click-to-unselect functionality for both lists
        inputFilesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = inputFilesList.locationToIndex(e.getPoint());
                
                if (e.getButton() == MouseEvent.BUTTON3) { // Right click
                    // Select the item under cursor for context menu
                    if (index >= 0 && inputFilesList.getCellBounds(index, index).contains(e.getPoint())) {
                        inputFilesList.setSelectedIndex(index);
                        showInputFileContextMenu(e, inputFilesList, inputFilesModel, frame);
                    } else {
                        // Right click on empty space - show general context menu
                        showInputListContextMenu(e, inputFilesList, inputFilesModel, frame, fileChooser);
                    }
                    return;
                }
                
                if (e.getClickCount() == 1) {
                    // Single click: check if clicked on empty space
                    if (index == -1 || !inputFilesList.getCellBounds(index, index).contains(e.getPoint())) {
                        // Clicked on empty space, unselect
                        inputFilesList.clearSelection();
                        selectedFileLabel.setText("No file selected");
                    }
                    // If clicked on an item, normal selection behavior will handle it
                } else if (e.getClickCount() == 2) {
                    // Double click: open file
                    if (index >= 0) {
                        File selectedFile = inputFilesList.getModel().getElementAt(index);
                        if (selectedFile != null && selectedFile.exists()) {
                            try {
                                Desktop.getDesktop().open(selectedFile);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(frame, "Could not open file: " + createErrorMessage(ex), "Error", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                }
            }
        });
        
        outputFilesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = outputFilesList.locationToIndex(e.getPoint());
                
                if (e.getButton() == MouseEvent.BUTTON3) { // Right click
                    // Select the item under cursor for context menu
                    if (index >= 0 && outputFilesList.getCellBounds(index, index).contains(e.getPoint())) {
                        outputFilesList.setSelectedIndex(index);
                        showOutputFileContextMenu(e, outputFilesList, outputFilesModel, frame);
                    } else {
                        // Right click on empty space - show general context menu
                        showOutputListContextMenu(e, outputFilesList, outputFilesModel, frame);
                    }
                    return;
                }
                
                if (e.getClickCount() == 1) {
                    // Single click: check if clicked on empty space
                    if (index == -1 || !outputFilesList.getCellBounds(index, index).contains(e.getPoint())) {
                        // Clicked on empty space, unselect
                        outputFilesList.clearSelection();
                        selectedFileLabel.setText("No file selected");
                    }
                    // If clicked on an item, normal selection behavior will handle it
                } else if (e.getClickCount() == 2) {
                    // Double click: open file
                    if (index >= 0) {
                        File selectedFile = outputFilesList.getModel().getElementAt(index);
                        if (selectedFile != null && selectedFile.exists()) {
                            try {
                                Desktop.getDesktop().open(selectedFile);
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(frame, "Could not open file: " + createErrorMessage(ex), "Error", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                }
            }
        });
        
        // Add DEL key support for input files
        inputFilesList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    File selectedFile = inputFilesList.getSelectedValue();
                    if (selectedFile != null) {
                        deleteSelectedFile(selectedFile, inputFilesModel, frame, "input");
                    }
                }
            }
        });
        
        // Add DEL key support for output files
        outputFilesList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    File selectedFile = outputFilesList.getSelectedValue();
                    if (selectedFile != null) {
                        deleteSelectedFile(selectedFile, outputFilesModel, frame, "output");
                    }
                }
            }
        });
        
        // Select File button
        JButton loadFileButton = new JButton("Load File");
        loadFileButton.addActionListener(e -> {
            int returnValue = fileChooser.showOpenDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                inputFilesModel.addElement(fileChooser.getSelectedFile());
            }
        });
        
        // Process File button
        JButton processButton = new JButton("Process File");
        processButton.addActionListener(e -> {
            File selectedFileFromList = inputFilesList.getSelectedValue();
            if (selectedFileFromList == null) {
                JOptionPane.showMessageDialog(frame, "Please select a file from the list first!", "No File Selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Optional: warn for very large files (>50MB)
            if (selectedFileFromList.length() > 50 * 1024 * 1024) {
                int choice = JOptionPane.showConfirmDialog(frame, 
                    "This file is quite large (" + (selectedFileFromList.length() / (1024 * 1024)) + " MB). Processing may take some time. Continue?",
                    "Large File Warning", JOptionPane.YES_NO_OPTION);
                if (choice != JOptionPane.YES_OPTION) return;
            }
            
            // Handle output path selection if default path is disabled
            final File outputDirectory;
            if (!useDefaultOutputPath) {
                JFileChooser outputFolderChooser = new JFileChooser();
                outputFolderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                outputFolderChooser.setDialogTitle("Select Output Folder");
                outputFolderChooser.setCurrentDirectory(selectedFileFromList.getParentFile()); // Start from input file location
                
                int result = outputFolderChooser.showSaveDialog(frame);
                if (result != JFileChooser.APPROVE_OPTION) {
                    return; // User canceled folder selection
                }
                outputDirectory = outputFolderChooser.getSelectedFile();
            } else {
                outputDirectory = null; // Use default path
            }
            
            // Create progress dialog
            JDialog progressDialog = new JDialog(frame, "Processing File", true);
            progressDialog.setSize(400, 180);
            progressDialog.setLocationRelativeTo(frame);
            progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            
            JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
            progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            JLabel progressLabel = new JLabel("Processing: " + selectedFileFromList.getName());
            progressLabel.setHorizontalAlignment(SwingConstants.CENTER);
            progressLabel.setFont(progressLabel.getFont().deriveFont(java.awt.Font.BOLD, 14f)); // Make title bold and larger
            
            JLabel statusLabel = new JLabel("Preparing to process...");
            statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
            statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
            
            // Add separator between title and status
            JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
            separator.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
            
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setStringPainted(false); // Don't show text inside the bar
            
            // Create a panel for status and progress bar
            JPanel progressSubPanel = new JPanel(new BorderLayout(5, 5));
            progressSubPanel.add(statusLabel, BorderLayout.NORTH);
            progressSubPanel.add(progressBar, BorderLayout.CENTER);
            
            // Create a panel for title and separator
            JPanel titlePanel = new JPanel(new BorderLayout());
            titlePanel.add(progressLabel, BorderLayout.NORTH);
            titlePanel.add(separator, BorderLayout.SOUTH);
            
            // Reset cancel flag
            isCanceled = false;
            
            // Declare worker variable to be accessible in cancel button
            @SuppressWarnings("unchecked")
            final SwingWorker<XmlParser, String>[] workerRef = new SwingWorker[1];
            
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(cancelEvent -> {
                isCanceled = true;
                if (workerRef[0] != null && !workerRef[0].isDone()) {
                    workerRef[0].cancel(true);
                }
                progressDialog.dispose();
            });
            
            progressPanel.add(titlePanel, BorderLayout.NORTH);
            progressPanel.add(progressSubPanel, BorderLayout.CENTER);
            progressPanel.add(cancelButton, BorderLayout.SOUTH);
            
            progressDialog.add(progressPanel);
            
            // Process file in background thread
            SwingWorker<XmlParser, String> worker = new SwingWorker<XmlParser, String>() {
                private XmlParser parser;
                
                @Override
                protected XmlParser doInBackground() throws Exception {
                    try {
                        publish("Initializing parser...");
                        
                        if (useDefaultOutputPath) {
                            // Use default path (same as input file location)
                            if (useAutoNaming) {
                                parser = new XmlParser(selectedFileFromList.toPath(), enableCSVExport);
                            } else {
                                // For manual naming with default path, we'll use a simpler approach
                                parser = new XmlParser(selectedFileFromList.toPath(), enableCSVExport); // Fallback to auto naming for now
                            }
                        } else {
                            // Use custom output directory
                            String inputFileName = selectedFileFromList.getName();
                            String nameWithoutExtension = inputFileName.contains(".") ? 
                                inputFileName.substring(0, inputFileName.lastIndexOf('.')) : inputFileName;
                            String outputFileName = nameWithoutExtension + "_out." + (enableCSVExport ? "csv" : "xlsx");
                            
                            java.nio.file.Path customOutputPath = outputDirectory.toPath().resolve(outputFileName);
                            parser = new XmlParser(selectedFileFromList.toPath(), customOutputPath, enableCSVExport);
                        }
                        
                        if (isCanceled) {
                            parser.cancel();
                            return null;
                        }
                        
                        publish("Processing XML data...");
                        parser.processFile();
                        
                        if (isCanceled) {
                            parser.cancel();
                            return null;
                        }
                        
                        publish("Finalizing output...");
                        
                        return parser;
                    } catch (Exception ex) {
                        throw ex;
                    }
                }
                
                @Override
                protected void process(java.util.List<String> chunks) {
                    if (!chunks.isEmpty()) {
                        statusLabel.setText(chunks.get(chunks.size() - 1));
                    }
                }
                
                @Override
                protected void done() {
                    progressDialog.dispose();
                    
                    if (isCanceled) {
                        JOptionPane.showMessageDialog(frame, "Operation canceled by user.", "Canceled", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    
                    try {
                        XmlParser result = get();
                        if (result != null) {
                            // Add to output list
                            File processedFile = result.getOutputPath().toFile();
                            if (outputFilesModel.contains(processedFile)) {
                                outputFilesModel.removeElement(processedFile);
                            }
                            outputFilesModel.addElement(processedFile);
                            
                            JOptionPane.showMessageDialog(frame, "File processed successfully!\nOutput: " + result.getOutputPath().getFileName(), "Success", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } catch (Exception ex) {
                        // Create custom dialog with "Remove from List" option
                        String errorMessage = "Error processing file: " + createErrorMessage(ex);
                        String[] options = {"Remove from List", "OK"};
                        int choice = JOptionPane.showOptionDialog(frame, 
                            errorMessage, 
                            "Error", 
                            JOptionPane.YES_NO_OPTION, 
                            JOptionPane.ERROR_MESSAGE, 
                            null, 
                            options, 
                            options[1]);
                        
                        if (choice == 0) { // "Remove from List" was selected
                            inputFilesModel.removeElement(selectedFileFromList);
                            showTemporaryMessage(frame, "File removed from input list", "Removed");
                        }
                    }
                }
            };
            
            workerRef[0] = worker;
            worker.execute();
            progressDialog.setVisible(true);
        });

        JButton openLastButton = new JButton("Open Last Output");
        openLastButton.addActionListener(e -> {
            if (!outputFilesModel.isEmpty()) {
                try {
                    File lastFile = outputFilesModel.getElementAt(outputFilesModel.getSize() - 1);
                    Desktop.getDesktop().open(lastFile);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error opening file: " + createErrorMessage(ex), "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(frame, "No files created yet", "No Files", JOptionPane.WARNING_MESSAGE);
            }
        });

        JButton deleteAllButton = new JButton("Delete All");
        deleteAllButton.addActionListener(e -> {
            boolean hasInputFiles = !inputFilesModel.isEmpty();
            boolean hasOutputFiles = !outputFilesModel.isEmpty();
            
            if (!hasInputFiles && !hasOutputFiles) {
                JOptionPane.showMessageDialog(frame, "No files to delete.", "No Files", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            
            String message = "Delete all files from both lists?\n\n";
            if (hasInputFiles) {
                message += "• " + inputFilesModel.size() + " input file(s)\n";
            }
            if (hasOutputFiles) {
                message += "• " + outputFilesModel.size() + " output file(s)\n";
            }
            message += "\nThis will remove files from disk permanently!";
            
            int confirm = JOptionPane.showConfirmDialog(frame, message, 
                "Confirm Delete All", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                
            if (confirm == JOptionPane.YES_OPTION) {
                int deletedCount = 0;
                int failedCount = 0;
                
                // Delete input files
                for (int i = inputFilesModel.size() - 1; i >= 0; i--) {
                    File file = inputFilesModel.getElementAt(i);
                    if (file.delete()) {
                        inputFilesModel.removeElementAt(i);
                        deletedCount++;
                    } else {
                        failedCount++;
                    }
                }
                
                // Delete output files
                for (int i = outputFilesModel.size() - 1; i >= 0; i--) {
                    File file = outputFilesModel.getElementAt(i);
                    if (file.delete()) {
                        outputFilesModel.removeElementAt(i);
                        deletedCount++;
                    } else {
                        failedCount++;
                    }
                }
                
                // Show result
                String resultMessage = deletedCount + " file(s) deleted successfully.";
                if (failedCount > 0) {
                    resultMessage += "\n" + failedCount + " file(s) could not be deleted.";
                }
                
                JOptionPane.showMessageDialog(frame, resultMessage, "Delete Complete", 
                    failedCount > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
            }
        });


        // Name of the selected file panel and its design
        JPanel filePanel = new JPanel();
            filePanel.setLayout(new GridLayout(0, 1));
            filePanel.setBorder(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(8, 8, 8, 8), // Reduced outer margin
                    BorderFactory.createCompoundBorder(
                        BorderFactory.createEtchedBorder(EtchedBorder.RAISED), // Visible border
                        BorderFactory.createEmptyBorder(8, 12, 8, 12) // Reduced inner padding
                    )
                )
            );

            filePanel.add(selectedFileLabel);


        // Panel for buttons and their design
        JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new GridLayout(1, 4, 10, 0)); // 1 row, 4 columns, 10px horizontal gap
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12)); // Reduced padding

            buttonPanel.add(loadFileButton);
            buttonPanel.add(processButton);
            buttonPanel.add(openLastButton);
            buttonPanel.add(deleteAllButton);

        // input-output lists
        JPanel listsPanel = new JPanel();
            listsPanel.setLayout(new GridLayout(1, 2, 10, 0)); // 1 row, 2 columns, 10px gap
            
            // Wrap JLists in JScrollPanes for scrolling
            JScrollPane inputScrollPane = new JScrollPane(inputFilesList);
            inputScrollPane.setBorder(BorderFactory.createTitledBorder("Input XML Files"));
            
            JScrollPane outputScrollPane = new JScrollPane(outputFilesList);
            outputScrollPane.setBorder(BorderFactory.createTitledBorder("Output Files"));
            
            listsPanel.add(inputScrollPane);
            listsPanel.add(outputScrollPane);

        // Layout setup - Use BorderLayout for better control
        frame.setLayout(new BorderLayout()); 
            
            // Create status bar
            JPanel statusPanel = new JPanel(new BorderLayout());
            statusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
            ));
            
            JLabel statusBarLabel = new JLabel("Ready");
            JLabel fileCountLabel = new JLabel("Files: 0 input, 0 output");
            statusPanel.add(statusBarLabel, BorderLayout.WEST);
            statusPanel.add(fileCountLabel, BorderLayout.EAST);
            
            // Create bottom panel that contains both buttons and status bar
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(buttonPanel, BorderLayout.NORTH);  // Buttons on top
            bottomPanel.add(statusPanel, BorderLayout.SOUTH);  // Status bar below buttons
            
            // Update file count when lists change
            inputFilesModel.addListDataListener(new javax.swing.event.ListDataListener() {
                public void intervalAdded(javax.swing.event.ListDataEvent e) { updateFileCount(); }
                public void intervalRemoved(javax.swing.event.ListDataEvent e) { updateFileCount(); }
                public void contentsChanged(javax.swing.event.ListDataEvent e) { updateFileCount(); }
                private void updateFileCount() {
                    fileCountLabel.setText("Files: " + inputFilesModel.size() + " input, " + outputFilesModel.size() + " output");
                }
            });
            
            outputFilesModel.addListDataListener(new javax.swing.event.ListDataListener() {
                public void intervalAdded(javax.swing.event.ListDataEvent e) { updateFileCount(); }
                public void intervalRemoved(javax.swing.event.ListDataEvent e) { updateFileCount(); }
                public void contentsChanged(javax.swing.event.ListDataEvent e) { updateFileCount(); }
                private void updateFileCount() {
                    fileCountLabel.setText("Files: " + inputFilesModel.size() + " input, " + outputFilesModel.size() + " output");
                }
            });
            
            // Add components to frame
            frame.setJMenuBar(createUpperMenu(frame, fileChooser, inputFilesModel, outputFilesModel, 
                                            filePanel, buttonPanel, listsPanel, inputFilesList, outputFilesList, 
                                            selectedFileLabel, loadFileButton, processButton, openLastButton, deleteAllButton));
            frame.add(filePanel, BorderLayout.NORTH);     // Top - small fixed height
            frame.add(listsPanel, BorderLayout.CENTER);   // Middle - takes remaining space
            frame.add(bottomPanel, BorderLayout.SOUTH);   // Bottom - buttons + status bar
            
            // Apply initial theme (including status bar)
            applyTheme(frame, filePanel, buttonPanel, listsPanel, inputFilesList, outputFilesList, 
                      loadFileButton, processButton, openLastButton, deleteAllButton);
            
            frame.setVisible(true);
    }

    /**
     * Helper method to delete a selected file with confirmation
     */
    private static void deleteSelectedFile(File file, DefaultListModel<File> model, JFrame frame, String listType) {
        int confirm = JOptionPane.showConfirmDialog(frame, 
            "Are you sure you want to delete this " + listType + " file from disk?\n" + file.getAbsolutePath(), 
            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
        if (confirm == JOptionPane.YES_OPTION) {
            if (file.delete()) {
                model.removeElement(file);
                showTemporaryMessage(frame, "File deleted successfully.", "Deleted");
            } else {
                JOptionPane.showMessageDialog(frame, "Failed to delete file.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Shows context menu for input file items
     */
    private static void showInputFileContextMenu(MouseEvent e, JList<File> inputFilesList, DefaultListModel<File> inputFilesModel, JFrame frame) {
        File selectedFile = inputFilesList.getSelectedValue();
        if (selectedFile == null) return;
        
        JPopupMenu contextMenu = new JPopupMenu();
        
        // Open file
        JMenuItem openItem = new JMenuItem("Open File");
        openItem.addActionListener(event -> {
            try {
                Desktop.getDesktop().open(selectedFile);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Could not open file: " + createErrorMessage(ex), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        // Show in file explorer
        JMenuItem showInExplorerItem = new JMenuItem("Show in File Explorer");
        showInExplorerItem.addActionListener(event -> {
            try {
                Desktop.getDesktop().open(selectedFile.getParentFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Could not open file explorer: " + createErrorMessage(ex), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        // Copy file path
        JMenuItem copyPathItem = new JMenuItem("Copy File Path");
        copyPathItem.addActionListener(event -> {
            java.awt.datatransfer.StringSelection stringSelection = new java.awt.datatransfer.StringSelection(selectedFile.getAbsolutePath());
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
            showTemporaryMessage(frame, "File path copied to clipboard", "Copied");
        });
        
        // File properties
        JMenuItem propertiesItem = new JMenuItem("Properties");
        propertiesItem.addActionListener(event -> {
            showFileProperties(selectedFile, frame);
        });
        
        // Rename file
        JMenuItem renameItem = new JMenuItem("Rename File");
        renameItem.addActionListener(event -> {
            String currentName = selectedFile.getName();
            String nameWithoutExtension = currentName.contains(".") ? 
                currentName.substring(0, currentName.lastIndexOf('.')) : currentName;
            String extension = currentName.contains(".") ? 
                currentName.substring(currentName.lastIndexOf('.')) : "";
            
            String newName = (String) JOptionPane.showInputDialog(frame, 
                "Enter new filename (without extension):", 
                "Rename File", 
                JOptionPane.PLAIN_MESSAGE, 
                null, 
                null, 
                nameWithoutExtension);
            
            if (newName != null && !newName.trim().isEmpty() && !newName.equals(nameWithoutExtension)) {
                newName = newName.trim() + extension;
                File newFile = new File(selectedFile.getParent(), newName);
                
                if (newFile.exists()) {
                    JOptionPane.showMessageDialog(frame, 
                        "A file with that name already exists!", 
                        "Rename Error", 
                        JOptionPane.ERROR_MESSAGE);
                } else if (selectedFile.renameTo(newFile)) {
                    // Update the model with the renamed file
                    int index = inputFilesModel.indexOf(selectedFile);
                    inputFilesModel.removeElement(selectedFile);
                    inputFilesModel.add(index, newFile);
                    inputFilesList.setSelectedValue(newFile, true);
                    showTemporaryMessage(frame, "File renamed successfully.", "Renamed");
                } else {
                    JOptionPane.showMessageDialog(frame, 
                        "Failed to rename file. Check file permissions.", 
                        "Rename Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        // Remove from list
        JMenuItem removeItem = new JMenuItem("Remove from List");
        removeItem.addActionListener(event -> {
            inputFilesModel.removeElement(selectedFile);
        });
        
        // Delete file
        JMenuItem deleteItem = new JMenuItem("Delete File");
        deleteItem.addActionListener(event -> {
            int confirm = JOptionPane.showConfirmDialog(frame, 
                "Are you sure you want to delete this file from disk?\n" + selectedFile.getAbsolutePath(), 
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                if (selectedFile.delete()) {
                    inputFilesModel.removeElement(selectedFile);
                    showTemporaryMessage(frame, "File deleted successfully.", "Deleted");
                } else {
                    JOptionPane.showMessageDialog(frame, "Failed to delete file.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        contextMenu.add(openItem);
        contextMenu.add(showInExplorerItem);
        contextMenu.addSeparator();
        contextMenu.add(copyPathItem);
        contextMenu.add(propertiesItem);
        contextMenu.add(renameItem);
        contextMenu.addSeparator();
        contextMenu.add(removeItem);
        contextMenu.add(deleteItem);
        
        contextMenu.show(inputFilesList, e.getX(), e.getY());
    }
    
    /**
     * Shows context menu for input list (empty space)
     */
    private static void showInputListContextMenu(MouseEvent e, JList<File> inputFilesList, DefaultListModel<File> inputFilesModel, JFrame frame, JFileChooser fileChooser) {
        JPopupMenu contextMenu = new JPopupMenu();
        
        // Add files
        JMenuItem addFilesItem = new JMenuItem("Add Files...");
        addFilesItem.addActionListener(event -> {
            fileChooser.setMultiSelectionEnabled(true);
            int returnValue = fileChooser.showOpenDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File[] selectedFiles = fileChooser.getSelectedFiles();
                for (File file : selectedFiles) {
                    if (!inputFilesModel.contains(file)) {
                        inputFilesModel.addElement(file);
                    }
                }
            }
            fileChooser.setMultiSelectionEnabled(false);
        });
        
        // Clear all
        JMenuItem clearAllItem = new JMenuItem("Clear All Files");
        clearAllItem.addActionListener(event -> {
            if (!inputFilesModel.isEmpty()) {
                int confirm = JOptionPane.showConfirmDialog(frame, "Clear all input files from list?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    inputFilesModel.clear();
                }
            }
        });
        
        // Refresh list
        JMenuItem refreshItem = new JMenuItem("Refresh List");
        refreshItem.addActionListener(event -> {
            inputFilesList.repaint();
        });
        
        contextMenu.add(addFilesItem);
        if (!inputFilesModel.isEmpty()) {
            contextMenu.addSeparator();
            contextMenu.add(clearAllItem);
        }
        contextMenu.addSeparator();
        contextMenu.add(refreshItem);
        
        contextMenu.show(inputFilesList, e.getX(), e.getY());
    }
    
    /**
     * Shows context menu for output file items
     */
    private static void showOutputFileContextMenu(MouseEvent e, JList<File> outputFilesList, DefaultListModel<File> outputFilesModel, JFrame frame) {
        File selectedFile = outputFilesList.getSelectedValue();
        if (selectedFile == null) return;
        
        JPopupMenu contextMenu = new JPopupMenu();
        
        // Open file
        JMenuItem openItem = new JMenuItem("Open File");
        openItem.addActionListener(event -> {
            try {
                Desktop.getDesktop().open(selectedFile);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Could not open file: " + createErrorMessage(ex), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        // Show in file explorer
        JMenuItem showInExplorerItem = new JMenuItem("Show in File Explorer");
        showInExplorerItem.addActionListener(event -> {
            try {
                Desktop.getDesktop().open(selectedFile.getParentFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Could not open file explorer: " + createErrorMessage(ex), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        // Copy file path
        JMenuItem copyPathItem = new JMenuItem("Copy File Path");
        copyPathItem.addActionListener(event -> {
            java.awt.datatransfer.StringSelection stringSelection = new java.awt.datatransfer.StringSelection(selectedFile.getAbsolutePath());
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
            showTemporaryMessage(frame, "File path copied to clipboard", "Copied");
        });
        
        // File properties
        JMenuItem propertiesItem = new JMenuItem("Properties");
        propertiesItem.addActionListener(event -> {
            showFileProperties(selectedFile, frame);
        });
        
        // Rename file
        JMenuItem renameItem = new JMenuItem("Rename File");
        renameItem.addActionListener(event -> {
            String currentName = selectedFile.getName();
            String nameWithoutExtension = currentName.contains(".") ? 
                currentName.substring(0, currentName.lastIndexOf('.')) : currentName;
            String extension = currentName.contains(".") ? 
                currentName.substring(currentName.lastIndexOf('.')) : "";
            
            String newName = (String) JOptionPane.showInputDialog(frame, 
                "Enter new filename (without extension):", 
                "Rename File", 
                JOptionPane.PLAIN_MESSAGE, 
                null, 
                null, 
                nameWithoutExtension);
            
            if (newName != null && !newName.trim().isEmpty() && !newName.equals(nameWithoutExtension)) {
                newName = newName.trim() + extension;
                File newFile = new File(selectedFile.getParent(), newName);
                
                if (newFile.exists()) {
                    JOptionPane.showMessageDialog(frame, 
                        "A file with that name already exists!", 
                        "Rename Error", 
                        JOptionPane.ERROR_MESSAGE);
                } else if (selectedFile.renameTo(newFile)) {
                    // Update the model with the renamed file
                    int index = outputFilesModel.indexOf(selectedFile);
                    outputFilesModel.removeElement(selectedFile);
                    outputFilesModel.add(index, newFile);
                    outputFilesList.setSelectedValue(newFile, true);
                    showTemporaryMessage(frame, "File renamed successfully.", "Renamed");
                } else {
                    JOptionPane.showMessageDialog(frame, 
                        "Failed to rename file. Check file permissions.", 
                        "Rename Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        // Remove from list
        JMenuItem removeItem = new JMenuItem("Remove from List");
        removeItem.addActionListener(event -> {
            outputFilesModel.removeElement(selectedFile);
        });
        
        // Delete file
        JMenuItem deleteItem = new JMenuItem("Delete File");
        deleteItem.addActionListener(event -> {
            int confirm = JOptionPane.showConfirmDialog(frame, 
                "Are you sure you want to delete this file from disk?\n" + selectedFile.getAbsolutePath(), 
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                if (selectedFile.delete()) {
                    outputFilesModel.removeElement(selectedFile);
                    showTemporaryMessage(frame, "File deleted successfully.", "Deleted");
                } else {
                    JOptionPane.showMessageDialog(frame, "Failed to delete file.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        contextMenu.add(openItem);
        contextMenu.add(showInExplorerItem);
        contextMenu.addSeparator();
        contextMenu.add(copyPathItem);
        contextMenu.add(propertiesItem);
        contextMenu.add(renameItem);
        contextMenu.addSeparator();
        contextMenu.add(removeItem);
        contextMenu.add(deleteItem);
        
        contextMenu.show(outputFilesList, e.getX(), e.getY());
    }
    
    /**
     * Shows context menu for output list (empty space)
     */
    private static void showOutputListContextMenu(MouseEvent e, JList<File> outputFilesList, DefaultListModel<File> outputFilesModel, JFrame frame) {
        JPopupMenu contextMenu = new JPopupMenu();
        
        // Open output folder
        JMenuItem openFolderItem = new JMenuItem("Open Output Folder");
        openFolderItem.addActionListener(event -> {
            File lastFile = outputFilesModel.isEmpty() ? null : outputFilesModel.getElementAt(outputFilesModel.getSize() - 1);
            File folderToOpen = lastFile != null ? lastFile.getParentFile() : new File(System.getProperty("user.home"));
            try {
                Desktop.getDesktop().open(folderToOpen);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Could not open folder: " + createErrorMessage(ex), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        // Clear all
        JMenuItem clearAllItem = new JMenuItem("Clear All Files");
        clearAllItem.addActionListener(event -> {
            if (!outputFilesModel.isEmpty()) {
                int confirm = JOptionPane.showConfirmDialog(frame, "Clear all output files from list?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    outputFilesModel.clear();
                }
            }
        });
        
        // Refresh list
        JMenuItem refreshItem = new JMenuItem("Refresh List");
        refreshItem.addActionListener(event -> {
            outputFilesList.repaint();
        });
        
        contextMenu.add(openFolderItem);
        if (!outputFilesModel.isEmpty()) {
            contextMenu.addSeparator();
            contextMenu.add(clearAllItem);
        }
        contextMenu.addSeparator();
        contextMenu.add(refreshItem);
        
        contextMenu.show(outputFilesList, e.getX(), e.getY());
    }
    
    /**
     * Shows detailed file properties dialog
     */
    private static void showFileProperties(File file, JFrame parent) {
        JDialog propertiesDialog = new JDialog(parent, "File Properties", true);
        propertiesDialog.setSize(450, 350);
        propertiesDialog.setLocationRelativeTo(parent);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // File icon and name
        JPanel headerPanel = new JPanel(new BorderLayout(10, 5));
        JLabel nameLabel = new JLabel("<html><b style='font-size: 14px;'>" + file.getName() + "</b></html>");
        
        String fileTypeInfo = getFileInfo(file);
        JLabel typeLabel = new JLabel(fileTypeInfo);
        typeLabel.setForeground(Color.GRAY);
        
        headerPanel.add(nameLabel, BorderLayout.NORTH);
        headerPanel.add(typeLabel, BorderLayout.SOUTH);
        
        // Properties table
        String[][] data = {
            {"Name:", file.getName()},
            {"Type:", fileTypeInfo},
            {"Size:", file.exists() ? formatFileSize(file.length()) : "File not found"},
            {"Location:", file.getParent()},
            {"Full Path:", file.getAbsolutePath()},
            {"Modified:", file.exists() ? formatDate(file.lastModified()) : "Unknown"},
            {"Readable:", file.canRead() ? "Yes" : "No"},
            {"Writable:", file.canWrite() ? "Yes" : "No"},
            {"Hidden:", file.isHidden() ? "Yes" : "No"}
        };
        
        JPanel propsPanel = new JPanel(new GridLayout(data.length, 1, 5, 5));
        for (String[] row : data) {
            JPanel rowPanel = new JPanel(new BorderLayout());
            JLabel keyLabel = new JLabel(row[0]);
            keyLabel.setFont(keyLabel.getFont().deriveFont(java.awt.Font.BOLD));
            keyLabel.setPreferredSize(new java.awt.Dimension(80, keyLabel.getPreferredSize().height));
            
            JLabel valueLabel = new JLabel(row[1]);
            valueLabel.setFont(valueLabel.getFont().deriveFont(java.awt.Font.PLAIN));
            
            rowPanel.add(keyLabel, BorderLayout.WEST);
            rowPanel.add(valueLabel, BorderLayout.CENTER);
            propsPanel.add(rowPanel);
            propsPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        }
        
        JScrollPane scrollPane = new JScrollPane(propsPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> propertiesDialog.dispose());
        buttonPanel.add(okButton);
        
        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        propertiesDialog.add(panel);
        propertiesDialog.setVisible(true);
    }

    /**
     * Creates upper menu bar
     * @param frame app frame
     * @param fileChooser integrated file chooser
     * @return upper menu bar with added submenus
     */
    public static JMenuBar createUpperMenu(JFrame frame, JFileChooser fileChooser, 
                                          DefaultListModel<File> inputFilesModel, DefaultListModel<File> outputFilesModel,
                                          JPanel filePanel, JPanel buttonPanel, JPanel listsPanel,
                                          JList<File> inputFilesList, JList<File> outputFilesList, JLabel selectedFileLabel,
                                          JButton... buttons) {
        JMenuBar menuBar = new JMenuBar();
        
        // File Menu
        JMenu fileMenu = new JMenu("File");
        
        JMenuItem changeDefFolder = new JMenuItem("Change Default Folder");
        changeDefFolder.addActionListener(e -> {
            JFileChooser folderChooser = new JFileChooser();
            folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            folderChooser.setDialogTitle("Select Default Folder");
            int result = folderChooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                fileChooser.setCurrentDirectory(folderChooser.getSelectedFile());
                showTemporaryMessage(frame, "Default folder changed to: " + folderChooser.getSelectedFile().getAbsolutePath(), "Folder Changed");
            }
        });
        
        JMenuItem exitApp = new JMenuItem("Exit");
        exitApp.addActionListener(e -> System.exit(0));
        
        JMenuItem clearInputList = new JMenuItem("Clear Input Files");
        clearInputList.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(frame, "Clear all input files from list?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                inputFilesModel.clear();
                selectedFileLabel.setText("No file selected");
                showTemporaryMessage(frame, "Input files list cleared", "Cleared");
            }
        });
        
        JMenuItem clearOutputList = new JMenuItem("Clear Output Files");
        clearOutputList.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(frame, "Clear all output files from list?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                outputFilesModel.clear();
                showTemporaryMessage(frame, "Output files list cleared", "Cleared");
            }
        });

        fileMenu.add(changeDefFolder);
        fileMenu.addSeparator();
        fileMenu.add(clearInputList);
        fileMenu.add(clearOutputList);
        fileMenu.addSeparator();
        fileMenu.add(exitApp);
        
        // Options Menu
        JMenu optionsMenu = new JMenu("Options");
        
        JCheckBoxMenuItem enableCSV = new JCheckBoxMenuItem("Enable CSV Export");
        enableCSV.addActionListener(e -> {
            enableCSVExport = enableCSV.isSelected();
            String message = enableCSVExport ? 
                "CSV Export enabled. Files will be saved as .csv format." :
                "CSV Export disabled. Files will be saved as .xlsx format.";
            showTemporaryMessage(frame, message, "CSV Export Setting");
        });
        
        JCheckBoxMenuItem darkModeToggle = new JCheckBoxMenuItem("Dark Mode");
        darkModeToggle.addActionListener(e -> {
            isDarkMode = darkModeToggle.isSelected();
            applyTheme(frame, filePanel, buttonPanel, listsPanel, inputFilesList, outputFilesList, buttons);
            // Refresh the lists to update cell renderers
            inputFilesList.repaint();
            outputFilesList.repaint();
            showTemporaryMessage(frame, "Theme changed to " + (isDarkMode ? "Dark" : "Light") + " Mode", "Theme Changed");
        });
        
        JCheckBoxMenuItem autoNamingToggle = new JCheckBoxMenuItem("Automatic File Naming", true);
        autoNamingToggle.addActionListener(e -> {
            useAutoNaming = autoNamingToggle.isSelected();
            String message = useAutoNaming ? 
                "Automatic naming enabled. Output files will be named automatically with '_out' suffix." :
                "Automatic naming disabled. You will be asked to choose output filename for each file.";
            showTemporaryMessage(frame, message, "File Naming Setting");
        });
        
        JCheckBoxMenuItem defaultOutputPathToggle = new JCheckBoxMenuItem("Use Default Output Path", true);
        defaultOutputPathToggle.addActionListener(e -> {
            useDefaultOutputPath = defaultOutputPathToggle.isSelected();
            String message = useDefaultOutputPath ? 
                "Default output path enabled. Files will be saved in the same folder as input files." :
                "Default output path disabled. You will be asked to choose output folder for each file.";
            showTemporaryMessage(frame, message, "Output Path Setting");
        });
        
        JCheckBoxMenuItem detailedViewToggle = new JCheckBoxMenuItem("Detailed File View");
        detailedViewToggle.addActionListener(e -> {
            showDetailedView = detailedViewToggle.isSelected();
            
            // Update cell heights and refresh displays
            inputFilesList.setFixedCellHeight(showDetailedView ? 75 : 50);
            outputFilesList.setFixedCellHeight(showDetailedView ? 75 : 50);
            inputFilesList.repaint();
            outputFilesList.repaint();
            
            String message = showDetailedView ? 
                "Detailed view enabled. Shows file size, modification date, and XML info." :
                "Simple view enabled. Shows only filename and folder path.";
            showTemporaryMessage(frame, message, "View Mode Changed");
        });
        
        optionsMenu.add(enableCSV);
        optionsMenu.add(darkModeToggle);
        optionsMenu.add(autoNamingToggle);
        optionsMenu.add(defaultOutputPathToggle);
        optionsMenu.add(detailedViewToggle);
        
        // Help Menu
        JMenu helpMenu = new JMenu("Help");
        
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> {
            String aboutText = "XML Parser Application\n\n" +
                            "Professional XML to Excel/CSV converter\n\n" +
                            "Key Features:\n" +
                            "• Smart XML parsing & data validation\n" +
                            "• Excel/CSV output with formatting\n" +
                            "• Progress tracking & cancellation\n" +
                            "• Dark/Light themes\n" +
                            "• Configurable output paths\n" +
                            "• User-friendly error handling\n\n" +
                            "Built with Java Swing & Apache POI\n" +
                            "Designed for efficient data processing";
            
            // Create scrollable dialog
            JTextArea textArea = new JTextArea(aboutText);
            textArea.setEditable(false);
            textArea.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
            textArea.setBackground(frame.getBackground());
            textArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new java.awt.Dimension(450, 400));
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            
            JOptionPane.showMessageDialog(frame, scrollPane, "About the XML Parser", JOptionPane.INFORMATION_MESSAGE);
        });
        
        JMenuItem howToUse = new JMenuItem("How to Use");
        howToUse.addActionListener(e -> {
            String helpText = "XML Parser - Quick Start\n\n" +
                             "Basic Steps:\n" +
                             "1. Click 'Load File' to add XML files\n" +
                             "2. Select file from Input list (left panel)\n" +
                             "3. Click 'Process File' to convert\n" +
                             "4. Double-click files to open them\n\n" +
                             "Configuration (Options Menu):\n" +
                             "• Auto File Naming: ON=auto '_out' suffix, OFF=choose name\n" +
                             "• Default Output Path: ON=same folder, OFF=choose folder\n" +
                             "• Detailed File View: Shows file size, date, XML info\n" +
                             "• Dark Mode & CSV Export available\n\n" +
                             "Tips:\n" +
                             "• Resizable window for better file details viewing\n" +
                             "• Status bar shows file counts at bottom\n" +
                             "• Large files (>50MB) show confirmation\n" +
                             "• Use Cancel button during processing\n" +
                             "• Click empty space to deselect files\n" +
                             "• Press DEL key to delete selected file\n" +
                             "• Right-click for context menu options\n\n" +
                             "Troubleshooting:\n" +
                             "• Ensure XML files are well-formed\n" +
                             "• Check file permissions for errors\n" +
                             "• Invalid rows are auto-documented";
            
            // Create scrollable dialog
            JTextArea textArea = new JTextArea(helpText);
            textArea.setEditable(false);
            textArea.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
            textArea.setBackground(frame.getBackground());
            textArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new java.awt.Dimension(450, 400));
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            
            JOptionPane.showMessageDialog(frame, scrollPane, "How to Use XML Parser", JOptionPane.INFORMATION_MESSAGE);
        });
        
        JMenuItem shortcuts = new JMenuItem("System Info & Tips");
        shortcuts.addActionListener(e -> {
            String shortcutsText = "System Information & Tips\n\n" +
                                 "Mouse Controls:\n" +
                                 "• Single-click: Select file\n" +
                                 "• Double-click: Open file\n" +
                                 "• Click empty space: Deselect\n\n" +
                                 "File Management:\n" +
                                 "• Input/Output lists are exclusive\n" +
                                 "• Long paths auto-shortened\n" +
                                 "• Duplicates auto-replaced\n\n" +
                                 "Performance:\n" +
                                 "• Files >50MB trigger warning\n" +
                                 "• Background processing (no UI freeze)\n" +
                                 "• Memory-efficient streaming\n" +
                                 "• Data validation prevents errors\n\n" +
                                 "Requirements:\n" +
                                 "• Java 8+, 512MB RAM minimum\n" +
                                 "• Write permissions required\n" +
                                 "• Excel/compatible viewer recommended\n\n" +
                                 "Formats:\n" +
                                 "• Input: .xml files\n" +
                                 "• Output: .xlsx, .csv\n" +
                                 "• Unicode support\n\n" +
                                 "Safety:\n" +
                                 "• Original files never modified\n" +
                                 "• Invalid data logged separately\n" +
                                 "• Atomic operations (safe cancellation)";
            
            // Create scrollable dialog
            JTextArea textArea = new JTextArea(shortcutsText);
            textArea.setEditable(false);
            textArea.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
            textArea.setBackground(frame.getBackground());
            textArea.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new java.awt.Dimension(400, 450));
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            
            JOptionPane.showMessageDialog(frame, scrollPane, "System Info & Tips", JOptionPane.INFORMATION_MESSAGE);
        });
        
        helpMenu.add(howToUse);
        helpMenu.addSeparator();
        helpMenu.add(shortcuts);
        helpMenu.addSeparator();
        helpMenu.add(about);
        
        // Add menus to menu bar
        menuBar.add(fileMenu);
        menuBar.add(optionsMenu);
        menuBar.add(helpMenu);
        
        return menuBar;
    }

    /**
     * Shows a temporary message that auto-dismisses after 3 seconds
     * @param parent parent frame
     * @param message message to display
     * @param title dialog title
     */
    private static void showTemporaryMessage(JFrame parent, String message, String title) {
        // Create a non-modal dialog that will auto-close
        JDialog dialog = new JDialog(parent, title, false);
        dialog.setSize(350, 120);
        dialog.setLocationRelativeTo(parent);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        
        JLabel messageLabel = new JLabel("<html><div style='text-align: center;'>" + message + "</div></html>");
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(messageLabel, BorderLayout.CENTER);
        
        // Apply theme
        Color bgColor = isDarkMode ? DARK_PANEL : LIGHT_PANEL;
        Color textColor = isDarkMode ? DARK_TEXT : LIGHT_TEXT;
        
        dialog.getContentPane().setBackground(bgColor);
        panel.setBackground(bgColor);
        messageLabel.setForeground(textColor);
        
        dialog.add(panel);
        dialog.setVisible(true);
        
        // Auto-close after 3 seconds
        Timer timer = new Timer(3000, e -> dialog.dispose());
        timer.setRepeats(false);
        timer.start();
    }

    public static void main(String[] args) throws Exception {
        boolean isCSV = false; // Set to true to ALSO generate CSV files (in addition to Excel file)
        createFrame(isCSV);
    }
}
