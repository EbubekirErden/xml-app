import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.nio.file.*;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * XmlParser - improved with:
 *  - cooperative cancellation (cancel())
 *  - simple progress callback (ProgressListener)
 *  - robust try/finally cleanup to avoid leaving temporary artifacts
 *  - atomic final write via temp file + Files.move(... ATOMIC_MOVE)
 *
 * Note: to show progress in a SwingWorker use a small ProgressListener implementation
 * that calls publish(...) from doInBackground().
 */
public class XmlParser {

    public interface ProgressListener {
        void onProgress(String message);
    }

    // Date format used in XML (dd.MM.yyyy)
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Locale TURKISH_LOCALE = new Locale("tr", "TR");

    // Style cache key -> CellStyle
    private final Map<String, CellStyle> styleCache = new HashMap<>();

    // Cancellation support
    private volatile boolean isCanceled = false;

    // Optional progress callback (null if not used)
    private ProgressListener listener;

    private Path xmlPath;
    private Path outXlsxPath;
    private Path csvOutPath;

    public XmlParser(Path input, boolean csvExport) throws Exception {
        this.xmlPath = input;
        String inputFileName = input.getFileName().toString();
        String nameWithoutExtension = inputFileName.contains(".") ? inputFileName.substring(0, inputFileName.lastIndexOf('.')) : inputFileName;
        String outputFileName = nameWithoutExtension + "_out." + (csvExport ? "csv" : "xlsx");
        this.outXlsxPath = input.getParent().resolve(outputFileName);
        this.csvOutPath = csvExport ? this.outXlsxPath : null; // When CSV is enabled, outXlsxPath actually points to CSV file
    }

    public XmlParser(Path input, Path outputPath, boolean csvExport) throws Exception {
        this.xmlPath = input;
        this.outXlsxPath = outputPath;
        this.csvOutPath = csvExport ? outputPath : null; // When CSV is enabled, outputPath should already have .csv extension
    }

    public void setProgressListener(ProgressListener l) {
        this.listener = l;
    }

    private void publish(String msg) {
        if (listener != null) {
            try {
                listener.onProgress(msg);
            } catch (Exception ignored) { }
        }
    }

    public Path getOutputPath() {
        return this.outXlsxPath;
    }

    public void cancel() {
        this.isCanceled = true;
    }

    public boolean isCanceled() {
        return this.isCanceled;
    }

    private void log(String s) {
        System.out.println("[INFO] " + s);
    }

    private void err(String s) {
        System.err.println("[ERR] " + s);
    }

    private CellStyle getStyle(Workbook wb, boolean blue, boolean isDate) {
        String key = (blue ? "blue" : "white") + (isDate ? "|date" : "|reg");
        if (styleCache.containsKey(key)) return styleCache.get(key);
        CellStyle style = wb.createCellStyle();
        if (blue) {
            style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        } else {
            style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        style.setAlignment(HorizontalAlignment.CENTER);
        Font f = wb.createFont();
        f.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(f);

        if (isDate) {
            short df = wb.createDataFormat().getFormat("dd.MM.yyyy");
            style.setDataFormat(df);
        }

        styleCache.put(key, style);
        return style;
    }

    private Double tryParseNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String trimmed = raw.trim().replace("\u00A0", "").replaceAll("\\s+", "");
        NumberFormat nf = NumberFormat.getInstance(TURKISH_LOCALE);
        try {
            Number n = nf.parse(trimmed);
            return n.doubleValue();
        } catch (Exception ignored) { }
        try {
            String normalized = trimmed.replace(",", ".");
            return Double.parseDouble(normalized);
        } catch (Exception ignored) { }
        return null;
    }

    private Date tryParseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String t = raw.trim();
        try {
            LocalDate ld = LocalDate.parse(t, DATE_FORMAT);
            Instant inst = ld.atStartOfDay(ZoneId.systemDefault()).toInstant();
            return Date.from(inst);
        } catch (Exception e) {
            return null;
        }
    }

    private String readElementText(XMLEventReader reader, StartElement start) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (reader.hasNext()) {
            if (isCanceled) throw new XMLStreamException("Canceled");
            XMLEvent ev = reader.nextEvent();
            if (ev.isCharacters()) {
                sb.append(ev.asCharacters().getData());
            } else if (ev.isEndElement()) {
                EndElement end = ev.asEndElement();
                if (end.getName().equals(start.getName())) {
                    break;
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * Top-level processing method.
     * This method tries to be robust: it publishes progress messages, checks cancellation
     * frequently and ensures temporary artifacts are cleaned up on failure or cancellation.
     */
    public void processFile() throws Exception {
        boolean csvExport = (csvOutPath != null);
        publish("Starting: " + xmlPath.getFileName());
        log("Processing " + xmlPath + " -> " + outXlsxPath);

        // First pass: collect fields and rows
        publish("Analyzing XML structure...");
        Set<String> allFields = new LinkedHashSet<>();
        List<Map<String, String>> allRows = new ArrayList<>();
        parseXmlToRows(xmlPath, allFields, allRows);

        if (isCanceled) {
            publish("Cancelled during scan");
            throw new InterruptedException("Cancelled");
        }

        if (allRows.isEmpty()) {
            publish("No data found in XML");
            log("No transactions found in XML file");
            return;
        }

        List<String> headers = new ArrayList<>(allFields);

        // Resources we must clean up
        Workbook wb = null;
        BufferedWriter csvWriter = null;
        Path tmpXlsx = null;
        Path tmpCsv = null;
        Sheet sheet = null;
        Sheet rejectedSheet = null;

        try {
            if (csvExport) {
                publish("Creating CSV file...");
                // For CSV export, we only need a minimal workbook for validation logic
                wb = new SXSSFWorkbook(100);
                sheet = wb.createSheet("Data");
                rejectedSheet = wb.createSheet("Rejected");
                
                createHeaderRow(sheet, headers, wb);
                createRejectedHeader(rejectedSheet);
                
                publish("Setting up CSV export...");
                csvWriter = prepareCsvWriter(csvOutPath, outXlsxPath, headers);
                tmpCsv = csvOutPath;
            } else {
                publish("Creating Excel workbook...");
                wb = new SXSSFWorkbook(100);
                sheet = wb.createSheet("Data");
                if (sheet instanceof SXSSFSheet) ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
                rejectedSheet = wb.createSheet("Rejected");

                createHeaderRow(sheet, headers, wb);
                createRejectedHeader(rejectedSheet);
            }

            publish("Writing data rows...");
            int dataRowIdx = 1;
            int rejRowIdx = 1;
            for (Map<String, String> rowData : allRows) {
                if (isCanceled) throw new InterruptedException("Cancelled by user");
                
                // Validate row data before writing
                String rejectionReason = validateRowData(rowData);
                if (rejectionReason != null) {
                    // Write to rejected sheet instead of main data
                    writeRejectedRow(rejectedSheet, xmlPath, rowData, rejectionReason, rejRowIdx);
                    rejRowIdx++;
                } else {
                    // Write to main data sheet
                    writeDataRow(sheet, rowData, headers, dataRowIdx, wb, csvWriter);
                    dataRowIdx++;
                }
                
                if (((dataRowIdx + rejRowIdx) % 500) == 0) {
                    publish("Converted ~" + (dataRowIdx + rejRowIdx) + " rows...");
                }
            }

            if (csvExport) {
                publish("Finalizing CSV file...");
                if (csvWriter != null) {
                    csvWriter.flush();
                    csvWriter.close();
                    csvWriter = null;
                }
            } else {
                publish("Finalizing Excel file...");
                autosizeColumns(wb.getSheet("Data"), headers.size());
                // write workbook atomically (to temp then move)
                tmpXlsx = writeWorkbookAtomic(wb, outXlsxPath);
            }

            // Log processing summary
            int totalProcessed = dataRowIdx - 1; // -1 because we started at 1
            int totalRejected = rejRowIdx - 1;
            int totalRows = totalProcessed + totalRejected;
            
            if (totalRejected > 0) {
                publish("Completed: " + totalProcessed + " rows converted, " + totalRejected + " rejected");
                log("Processing summary: " + totalProcessed + " valid rows, " + totalRejected + " rejected rows out of " + totalRows + " total");
            } else {
                publish("Completed: " + totalProcessed + " rows converted successfully");
                log("Processing summary: All " + totalProcessed + " rows processed successfully");
            }
            
            log((csvExport ? "CSV" : "Workbook") + " written to " + outXlsxPath);
        } catch (InterruptedException ie) {
            // Propagate cancellation
            publish("Operation cancelled");
            throw ie;
        } catch (Exception e) {
            err("Processing failed: " + e.getMessage());
            throw e;
        } finally {
            // Close CSV if still open and remove partial CSV if cancelled/failed
            try {
                if (csvWriter != null) {
                    try { csvWriter.close(); } catch (Exception ignore) {}
                    csvWriter = null;
                }
                if (csvExport && tmpCsv != null && isCanceled) {
                    try { Files.deleteIfExists(tmpCsv); } catch (Exception ignore) {}
                }
            } catch (Exception ignore) {}

            // Dispose SXSSF temporary files
            try {
                if (wb instanceof SXSSFWorkbook) {
                    ((SXSSFWorkbook) wb).dispose();
                }
            } catch (Exception ignore) {}

            // If writeWorkbookAtomic failed to move and tmpXlsx exists, delete it
            if (tmpXlsx != null && Files.exists(tmpXlsx)) {
                try { Files.deleteIfExists(tmpXlsx); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * Validates row data and returns rejection reason if invalid, null if valid
     */
    private String validateRowData(Map<String, String> rowData) {
        // Check if row is completely empty
        boolean hasAnyData = false;
        for (String value : rowData.values()) {
            if (value != null && !value.trim().isEmpty()) {
                hasAnyData = true;
                break;
            }
        }
        if (!hasAnyData) {
            return "Empty row - no data found";
        }
        
        // Check for required fields (common in accounting systems)
        String[] requiredFields = {"ACCOUNT", "AMOUNT", "DATE"}; // Adjust based on your XML structure
        for (String field : requiredFields) {
            String value = rowData.get(field);
            if (value == null || value.trim().isEmpty()) {
                // Only reject if field exists in XML but is empty
                if (rowData.containsKey(field)) {
                    return "Missing required field: " + field;
                }
            }
        }
        
        // Validate date fields
        for (Map.Entry<String, String> entry : rowData.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // Check if field name suggests it's a date
            if (key.toLowerCase().contains("date") || key.toLowerCase().contains("tarih")) {
                if (value != null && !value.trim().isEmpty()) {
                    Date parsedDate = tryParseDate(value);
                    if (parsedDate == null) {
                        return "Invalid date format in field: " + key + " (value: " + value + ")";
                    }
                }
            }
            
            // Check if field name suggests it's an amount/number
            if (key.toLowerCase().contains("amount") || key.toLowerCase().contains("tutar") || 
                key.toLowerCase().contains("miktar") || key.toLowerCase().contains("balance")) {
                if (value != null && !value.trim().isEmpty()) {
                    // Skip if it's clearly text (contains letters other than currency symbols)
                    if (value.matches(".*[a-zA-Z].*") && !value.matches(".*[TL|USD|EUR|\\$|€|₺].*")) {
                        continue; // Skip text fields that happen to have "amount" in name
                    }
                    Double parsedNumber = tryParseNumber(value);
                    if (parsedNumber == null && !value.trim().equals("0")) {
                        return "Invalid number format in field: " + key + " (value: " + value + ")";
                    }
                }
            }
        }
        
        return null; // Row is valid
    }

    private void parseXmlToRows(Path xmlPath, Set<String> allFields, List<Map<String, String>> allRows) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        try (InputStream fis = Files.newInputStream(xmlPath)) {
            XMLEventReader reader = factory.createXMLEventReader(fis);

            Map<String, String> currentParent = new HashMap<>();
            List<Map<String, String>> currentTransactions = new ArrayList<>();
            boolean inVoucher = false;
            boolean inTransactions = false;
            boolean inTransaction = false;
            Map<String, String> currentTransaction = new HashMap<>();

            while (reader.hasNext()) {
                if (isCanceled) throw new InterruptedException("Cancelled");
                XMLEvent ev = reader.nextEvent();

                if (ev.isStartElement()) {
                    StartElement se = ev.asStartElement();
                    String name = se.getName().getLocalPart();

                    if ("GL_VOUCHER".equalsIgnoreCase(name)) {
                        inVoucher = true;
                        currentParent.clear();
                        currentTransactions.clear();
                    } else if (inVoucher && "TRANSACTIONS".equalsIgnoreCase(name)) {
                        inTransactions = true;
                    } else if (inTransactions && "TRANSACTION".equalsIgnoreCase(name)) {
                        inTransaction = true;
                        currentTransaction = new HashMap<>();
                    } else if (inTransaction) {
                        String text = readElementText(reader, se);
                        currentTransaction.put(name, text);
                        allFields.add(name);
                    } else if (inVoucher && !inTransactions) {
                        String text = readElementText(reader, se);
                        currentParent.put(name, text);
                        allFields.add(name);
                    }
                } else if (ev.isEndElement()) {
                    EndElement ee = ev.asEndElement();
                    String endName = ee.getName().getLocalPart();

                    if ("TRANSACTION".equalsIgnoreCase(endName)) {
                        Map<String, String> completeRow = new HashMap<>(currentParent);
                        completeRow.putAll(currentTransaction);
                        currentTransactions.add(completeRow);
                        inTransaction = false;
                    } else if ("TRANSACTIONS".equalsIgnoreCase(endName)) {
                        inTransactions = false;
                    } else if ("GL_VOUCHER".equalsIgnoreCase(endName)) {
                        allRows.addAll(currentTransactions);
                        inVoucher = false;
                    }
                }
            }
            reader.close();
        }
    }

    private void createHeaderRow(Sheet sheet, List<String> headers, Workbook wb) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.BLACK.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        for (int i = 0; i < headers.size(); i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers.get(i));
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 20 * 256);
        }
    }

    private void createRejectedHeader(Sheet rejectedSheet) {
        Row rejHeader = rejectedSheet.createRow(0);
        rejHeader.createCell(0).setCellValue("SourceFile");
        rejHeader.createCell(1).setCellValue("RowContext");
        rejHeader.createCell(2).setCellValue("Reason");
    }

    private BufferedWriter prepareCsvWriter(Path csvOutPath, Path outXlsxPath, List<String> headers) throws IOException {
        if (csvOutPath == null) {
            Path parent = outXlsxPath.getParent();
            csvOutPath = parent.resolve(outXlsxPath.getFileName().toString().replaceAll("\\.xlsx$", "") + ".csv");
        }
        BufferedWriter csvWriter = Files.newBufferedWriter(csvOutPath, java.nio.charset.StandardCharsets.UTF_8);
        csvWriter.write(String.join(",", escapeCsvList(headers)));
        csvWriter.newLine();
        return csvWriter;
    }

    private void writeDataRow(Sheet sheet, Map<String, String> rowData, List<String> headers, int rowIdx, Workbook wb, BufferedWriter csvWriter) throws IOException {
        List<String> csvCells = new ArrayList<>(headers.size());
        
        // If we're only doing CSV export, skip Excel row creation
        if (csvWriter != null && (csvOutPath != null)) {
            // CSV-only mode: just prepare CSV data
            for (int i = 0; i < headers.size(); i++) {
                String value = rowData.getOrDefault(headers.get(i), "");
                csvCells.add(safeCsvCell(value));
            }
        } else {
            // Excel mode (with optional CSV): create Excel row
            Row row = sheet.createRow(rowIdx);
            boolean isBlue = (rowIdx % 2 == 0);

            CellStyle dataStyle = wb.createCellStyle();
            dataStyle.cloneStyleFrom(getStyle(wb, isBlue, false));
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            for (int i = 0; i < headers.size(); i++) {
                String value = rowData.getOrDefault(headers.get(i), "");
                Cell cell = row.createCell(i);
                writeCellValue(cell, value, isBlue, wb);
                cell.setCellStyle(dataStyle);
                csvCells.add(safeCsvCell(value));
            }
        }

        if (csvWriter != null) {
            csvWriter.write(String.join(",", csvCells));
            csvWriter.newLine();
        }
    }

    private void writeRejectedRow(Sheet rejectedSheet, Path xmlPath, Map<String, String> rowData, String reason, int rejRowIdx) {
        Row r = rejectedSheet.createRow(rejRowIdx);
        r.createCell(0).setCellValue(xmlPath.getFileName().toString());
        r.createCell(1).setCellValue(rowData.toString());
        r.createCell(2).setCellValue(reason);
    }

    private void autosizeColumns(Sheet sheet, int columnCount) {
        int autosizeLimit = Math.min(columnCount, 15);
        for (int i = 0; i < autosizeLimit; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // Writes workbook to a temporary file and moves atomically into place.
    // Returns the path of the temp file used (may be deleted/moved by move call).
    private Path writeWorkbookAtomic(Workbook wb, Path outXlsxPath) throws IOException {
        Path parent = outXlsxPath.getParent();
        if (parent == null) parent = Paths.get(".");
        Path tmp = parent.resolve(outXlsxPath.getFileName().toString() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp)) {
            wb.write(os);
            os.flush();
        }
        // Move atomically (will fail if filesystem doesn't support ATOMIC_MOVE)
        Files.move(tmp, outXlsxPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return tmp;
    }

    private void writeCellValue(Cell cell, String value, boolean isBlue, Workbook wb) {
        if (value == null) value = "";
        Date d = tryParseDate(value);
        if (d != null) {
            cell.setCellValue(d);
            cell.setCellStyle(getStyle(wb, isBlue, true));
            return;
        }
        Double n = tryParseNumber(value);
        if (n != null) {
            cell.setCellValue(n);
            cell.setCellStyle(getStyle(wb, isBlue, false));
            return;
        }
        cell.setCellValue(value);
        cell.setCellStyle(getStyle(wb, isBlue, false));
    }

    private static String safeCsvCell(String s) {
        if (s == null) return "";
        String out = s.replace("\"", "\"\"");
        if (out.contains(",") || out.contains("\"") || out.contains("\n")) {
            return "\"" + out + "\"";
        }
        return out;
    }

    private static List<String> escapeCsvList(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(safeCsvCell(s));
        return out;
    }
}
