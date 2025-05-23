package data.service.excel;

import app.AppConsoleStyle;
import data.model.TableAndColumnNameExcelData;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Types;
import java.util.*;

public class ExcelFileManager {

    //*************************************************************************
    //* EXCEL FILES NAMES                                                     *
    //*************************************************************************

    private final String ESTRUCTURE_EXCEL_FILE = "configuracion\\estructura.xlsx";

    private String getFileURL() {
        JFileChooser fileChooser = new JFileChooser(new File("").getAbsolutePath());

        FileNameExtensionFilter filter = new FileNameExtensionFilter("Archivos de excel",
                "xlsx", "xls");
        fileChooser.setFileFilter(filter);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int returnVal = fileChooser.showOpenDialog(null);
        fileChooser.setMultiSelectionEnabled(false);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile().getAbsolutePath();
        } else {
            System.out.println(AppConsoleStyle.RED + "\n[ERROR] No se seleccionó el archivo" + AppConsoleStyle.RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException q) {
                System.out.print(" ");
            }
            System.exit(1);
            return null;
        }
    }

    //*************************************************************************
    //* STREAMS                                                               *
    //*************************************************************************

    @Getter
    public FileInputStream FIS_ESTRUCTURAL_EXCEL_FILE;

    @Getter
    public FileInputStream FIS_DATA_EXCEL_FILE;

    private XSSFWorkbook wbDataExcel;
    private XSSFWorkbook wbEstructureExcel;

    public void closeStreamsFromEstructuralExcelFile() {
        try {
            wbEstructureExcel.close();
            FIS_ESTRUCTURAL_EXCEL_FILE.close();
        } catch (IOException e) {
            System.out.println(AppConsoleStyle.RED
                    + "[ERROR] No se pudo cerrar el archivo de estructura"
                    + AppConsoleStyle.RESET);
        }
    }

    public void closeStreamsFromDataExcelFile() {
        try {
            wbDataExcel.close();
            FIS_DATA_EXCEL_FILE.close();
        } catch (IOException e) {
            System.out.println(AppConsoleStyle.RED
                    + "[ERROR] No se pudo cerrar el archivo de los datos"
                    + AppConsoleStyle.RESET);
        }
    }

    //*************************************************************************
    //* HEADER'S INFO                                                         *
    //*************************************************************************

    @Getter
    public int headersRow = 0;

    @Getter
    public int estructureHeadersRow = 0;

    private List<Integer> excelColumnsWithHeader;
    private List<Integer> estructureExcelColumnsWithHeader;

    //*************************************************************************
    //* HEADER STYLE                                                          *
    //*************************************************************************

    private CellStyle HEADER_STYLE;

    private void setColumnHeaderStyle(XSSFWorkbook wb) {
        HEADER_STYLE = wb.createCellStyle();
        HEADER_STYLE.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        HEADER_STYLE.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        HEADER_STYLE.setBorderBottom(BorderStyle.MEDIUM);
        HEADER_STYLE.setAlignment(HorizontalAlignment.CENTER);
    }

    private boolean isHeaderRow(Row row) {
        for (Cell cell : row) {
            CellStyle style = cell.getCellStyle();
            if (style != null) {
                boolean hasGreyBackground = style.getFillForegroundColor() == IndexedColors.GREY_25_PERCENT.getIndex();
                boolean hasCenterAlignment = style.getAlignment() == HorizontalAlignment.CENTER;
                boolean hasBottomBorder = style.getBorderBottom() == BorderStyle.MEDIUM;

                if (hasGreyBackground && hasCenterAlignment && hasBottomBorder) {
                    return true;
                }
            }
        }
        return false;
    }

    //*************************************************************************
    //* CONSTRUCTOR                                                           *
    //*************************************************************************

    public ExcelFileManager() {
        try {
            FIS_ESTRUCTURAL_EXCEL_FILE = new FileInputStream(ESTRUCTURE_EXCEL_FILE);
        } catch (FileNotFoundException e) {
            System.out.println(AppConsoleStyle.RED
                    + "[ERROR] No se encontró el archivo " + ESTRUCTURE_EXCEL_FILE
                    + ".\n(Detalle): " + e.getMessage() + AppConsoleStyle.RESET);
            try {
                Thread.sleep(4000);
            } catch (InterruptedException q) {
                System.out.print(" ");
            }
            System.exit(2);
        }
    }

    //*************************************************************************
    //* METHODS                                                               *
    //*************************************************************************

    public List<List<Object[]>> readHeaderFromFile(boolean isEstructuralExcel) {
        XSSFWorkbook wb = null;
        try {
            if (!isEstructuralExcel) {
                System.out.print(AppConsoleStyle.YELLOW
                        + "> Seleccione el archivo de datos en la pantalla emergente"
                        + AppConsoleStyle.RESET);
                FIS_DATA_EXCEL_FILE = new FileInputStream(getFileURL());
            }
            if (isEstructuralExcel) {
                wbEstructureExcel = new XSSFWorkbook(FIS_ESTRUCTURAL_EXCEL_FILE);
                wb = wbEstructureExcel;
            } else {
                wbDataExcel = new XSSFWorkbook(FIS_DATA_EXCEL_FILE);
                wb = wbDataExcel;
            }
        } catch (IOException e) {
            System.out.println(AppConsoleStyle.RED
                    + "[ERROR] No se pudo leer el Excel " + (isEstructuralExcel ? "de Estructura" : "de Datos")
                    + ".\n(Detalle): " + e.getMessage() + AppConsoleStyle.RESET);
            try {
                Thread.sleep(4000);
            } catch (InterruptedException q) {
                System.out.print(" ");
            }
            System.exit(2);
        }

        Sheet sheet = wb.getSheetAt(0);
        Row row;

        List<List<Object[]>> dataFromFile = new ArrayList<>();
        List<Object[]> rowData;
        List<Integer> columnsWithHeader;

        getHeadersRowAndColumnsUsed(sheet, isEstructuralExcel);

        if (isEstructuralExcel) {
            columnsWithHeader = estructureExcelColumnsWithHeader;

            for (int i = 0; i < estructureHeadersRow; i++) {
                row = sheet.getRow(i);
                rowData = new ArrayList<>();
                getCellTypeAndData(row, columnsWithHeader, rowData, null);

                dataFromFile.add(rowData);

            }
            row = sheet.getRow(estructureHeadersRow);


        } else {
            columnsWithHeader = excelColumnsWithHeader;
            row = sheet.getRow(headersRow);
        }

        rowData = new ArrayList<>();
        getCellTypeAndData(row, columnsWithHeader, rowData, null);
        dataFromFile.add(rowData);

        return dataFromFile;
    }

    public List<List<Object[]>> readDataFile() {
        List<List<Object[]>> dataFromFile = new ArrayList<>();

        Sheet sheet = wbDataExcel.getSheetAt(0);

        for (int i = headersRow + 1; i < sheet.getPhysicalNumberOfRows(); i++) {
            Row row = sheet.getRow(i);

            List<Object[]> rowData = new ArrayList<>();

            getCellTypeAndData(row, excelColumnsWithHeader, rowData, null);

            boolean todosNull = rowData.stream().allMatch(Objects::isNull);
            if (!todosNull) {
                dataFromFile.add(rowData);
            }
        }
        return dataFromFile;
    }

    public List<List<Object[]>> processOfGettingParsedDataFromEstructureFile(Queue<Map<TableAndColumnNameExcelData, Object[]>> data) {
        List<List<Object[]>> dataFromFile = new ArrayList<>();
        Sheet sheet = wbEstructureExcel.getSheetAt(0);

        while (!data.isEmpty()) {
            Map<TableAndColumnNameExcelData, Object[]> entry = data.poll();
            insertDataInEstructureFile(sheet, entry);

            FormulaEvaluator evaluator = wbEstructureExcel.getCreationHelper().createFormulaEvaluator();
            evaluator.clearAllCachedResultValues();
            wbEstructureExcel.setForceFormulaRecalculation(true);
            dataFromFile.add(readEstructuralFile(wbEstructureExcel, evaluator));
        }

        return dataFromFile;
    }

    private void insertDataInEstructureFile(Sheet sheet, Map<TableAndColumnNameExcelData, Object[]> dataToInsert) {
        //First row is the header
        Row row = sheet.getRow(estructureHeadersRow);

        //Second row is where to insert
        Row rowToInsert = sheet.getRow(estructureHeadersRow + 1);
        int columnToInsert = -1;

        for (Map.Entry<TableAndColumnNameExcelData, Object[]> entry : dataToInsert.entrySet()) {
            for (int i = 0; i < row.getPhysicalNumberOfCells(); i++) {
                Cell cell = row.getCell(i);
                StringBuilder nombreTabla = new StringBuilder(entry.getKey().getTableName()
                        .concat(".")
                        .concat(entry.getKey().getColumnName()));
                if (entry.getKey().getColumnNumber() != 0)
                    nombreTabla.append(".").append(entry.getKey().getColumnNumber());
                if (cell.getStringCellValue().equals(nombreTabla.toString())) {
                    columnToInsert = i;
                    break;
                }
            }
            Cell cellToInsert = rowToInsert.createCell(columnToInsert);
            insertValue(cellToInsert, entry.getValue());
        }
    }

    private List<Object[]> readEstructuralFile(XSSFWorkbook wb, FormulaEvaluator evaluator) {
        List<Object[]> rowData = new ArrayList<>();
        Sheet sheet = wb.getSheetAt(0);
        Row row = sheet.getRow(estructureHeadersRow + 1);

        getCellTypeAndData(row, estructureExcelColumnsWithHeader, rowData, evaluator);

        return rowData;
    }

    private void getCellTypeAndData(Row row,
                                    List<Integer> columnsWithHeader,
                                    List<Object[]> rowData,
                                    FormulaEvaluator evaluator) {

        for (Integer cellNum : columnsWithHeader) {

            Cell cell = row.getCell(cellNum);
            if (cell == null) rowData.add(null);
            else {
                switch (cell.getCellType()) {
                    case STRING -> rowData.add(new Object[]{
                            Types.VARCHAR,
                            cell.getStringCellValue().trim().toUpperCase()
                    });

                    case NUMERIC -> rowData.add(formatCellNumberValue(cell, null));

                    case BOOLEAN -> rowData.add(new Object[]{
                            Types.INTEGER,
                            (cell.getBooleanCellValue()) ? 1 : 0
                    });

                    case FORMULA -> {
                        CellValue cellValue = evaluator.evaluate(cell);
                        if (cellValue.getCellType() == CellType.NUMERIC) {
                            rowData.add(formatCellNumberValue(cell, evaluator));
                        } else {
                            String stringCellValue = cellValue.getStringValue();
                            if (!stringCellValue.isEmpty())
                                rowData.add(new Object[]{
                                        Types.VARCHAR,
                                        stringCellValue.trim().toUpperCase()
                                });
                            else {
                                rowData.add(null);
                            }
                        }
                    }
                    default -> rowData.add(null);
                }
            }
        }
    }


    private Object[] formatCellNumberValue(Cell cell, FormulaEvaluator formulaEvaluator) {
        Object[] newValue;
        if (DateUtil.isCellDateFormatted(cell)) {
            newValue = new Object[]{
                    Types.DATE,
                    cell.getDateCellValue()
            };
        } else {
            DataFormatter dataFormatter = new DataFormatter();
            String stringCellValue;
            Double value;

            if (formulaEvaluator != null) {
                stringCellValue = dataFormatter.formatCellValue(cell, formulaEvaluator);
                CellValue cellValue = formulaEvaluator.evaluate(cell);
                value = cellValue.getNumberValue();
            } else {
                stringCellValue = dataFormatter.formatCellValue(cell);
                value = cell.getNumericCellValue();
            }

            try {
                if (Objects.equals(stringCellValue, String.valueOf(value))) {
                    newValue = new Object[]{
                            (value == Math.floor(value)) ? Types.INTEGER : Types.DOUBLE,
                            (value == Math.floor(value)) ? value.intValue() : value,
                    };
                } else {
                    newValue = new Object[]{
                            Types.VARCHAR,
                            stringCellValue.replace(',', '.')
                    };
                }
            } catch (Exception e) {
                if (!stringCellValue.isBlank())
                    newValue = new Object[]{
                            Types.VARCHAR,
                            stringCellValue
                    };
                else newValue = null;
            }
        }
        return newValue;
    }

    private void getHeadersRowAndColumnsUsed(Sheet sheet, boolean isEstructuralExcel) {
        for (Row row : sheet) {
            if (isHeaderRow(row)) {
                int rowNum = row.getRowNum();

                if (isEstructuralExcel)
                    estructureHeadersRow = rowNum;
                else
                    headersRow = rowNum;

                ArrayList<Integer> columnsWithHeader = new ArrayList<>();
                for (Cell cell : row) {
                    if (cell == null) continue;
                    String cellValue = cell.getStringCellValue();
                    if ((cellValue != null) && (!cellValue.isEmpty())) {
                        columnsWithHeader.add(cell.getColumnIndex());
                    }
                }
                List<Integer> columnsWithHeaderList = new ArrayList<>(columnsWithHeader.stream().toList());

                if (isEstructuralExcel)
                    estructureExcelColumnsWithHeader = columnsWithHeaderList;
                else
                    excelColumnsWithHeader = columnsWithHeaderList;
                break;
            }
        }
    }

    private void insertValue(Cell cell, Object[] value) {
        if (cell == null || value == null) return;
        switch ((int) value[0]) {
            case Types.VARCHAR:
                cell.setCellValue((String) value[1]);
                break;
            case Types.INTEGER:
                cell.setCellValue((int) value[1]);
                break;
            case Types.DOUBLE:
                cell.setCellValue((double) value[1]);
                break;
            case Types.DATE:
                cell.setCellValue((Date) value[1]);
                break;
            default:
                cell.setBlank();
        }
    }
}
