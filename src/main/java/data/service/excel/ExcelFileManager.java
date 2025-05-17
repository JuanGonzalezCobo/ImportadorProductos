package data.service.excel;

import app.AppConsoleStyle;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ExcelFileManager {

    //*************************************************************************
    //* ARGUMENTS                                                             *
    //*************************************************************************

    @Getter
    public int headersRow = 0;

    @Getter
    public int estructureHeadersRow = 0;

    public final String ESTRUCTURE_EXCEL_FILE = "estructura/estructura.xlsx";

    private List<Integer> excelColumnsWithHeader;
    private List<Integer> estructureExcelColumnsWithHeader;

    File estructuralExcelFile;
    File dataExcelFile;

    public final FileInputStream FIS_ESTRUCTURAL_EXCEL_FILE;


    private CellStyle HEADER_STYLE;

    public ExcelFileManager() {
        try {
            FIS_ESTRUCTURAL_EXCEL_FILE = new FileInputStream("estructura/estructura.xlsx");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

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
            System.out.println(AppConsoleStyle.RED + "[ERROR] No se seleccionó el archivo" + AppConsoleStyle.RESET);
            System.exit(1);
            return null;
        }
    }

    public List<List<Object[]>> readHeaderFromFile(XSSFWorkbook wb, boolean isEstructuralExcel) {
        Sheet sheet = wb.getSheetAt(0);
        Row row;

        List<List<Object[]>> dataFromFile = new ArrayList<>();
        List<Object[]> rowData = new ArrayList<>();
        List<Integer> columnsWithHeader;

        getHeadersRowAndColumnsUsed(sheet, isEstructuralExcel);

        if (isEstructuralExcel) {
            columnsWithHeader = estructureExcelColumnsWithHeader;

            for (int i = 0; i < estructureHeadersRow - 1; i++) {  //TODO: MIRAR QUE ESTO NO EMPIECE POR 1
                row = sheet.getRow(i);

                getCellTypeAndData(wb, row, columnsWithHeader, rowData);

                dataFromFile.add(rowData);

            }
            row = sheet.getRow(estructureHeadersRow);

        } else {
            columnsWithHeader = excelColumnsWithHeader;
            row = sheet.getRow(headersRow);
        }

        getCellTypeAndData(wb, row, columnsWithHeader, rowData);
        dataFromFile.add(rowData);

        return dataFromFile;
    }



    public List<List<Object[]>> readFile() {
        List<List<Object[]>> dataFromFile = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(getFileURL());
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheetAt(0);

            getHeadersRowAndColumnsUsed(sheet, true);

            for (Row row : sheet) {

                List<Object[]> rowData = new ArrayList<>();

                getCellTypeAndData(wb, row, excelColumnsWithHeader, rowData);

                boolean todosNull = rowData.stream().allMatch(Objects::isNull);
                if (!todosNull) {
                    dataFromFile.add(rowData);
                }
            }

        } catch (FileNotFoundException e) {
            System.out.println(AppConsoleStyle.RED + "[ERROR] No se pudo encontrar el archivo" + AppConsoleStyle.RESET);
        } catch (IOException e) {
            System.out.println(AppConsoleStyle.RED + "[ERROR] No se pudo leer el archivo" + AppConsoleStyle.RESET);
        }
        return dataFromFile;
    }

    public Void writeFile(String fileName, String[][] header) {
        try (FileOutputStream fos = new FileOutputStream(fileName.concat(".xlsx"));
             XSSFWorkbook wb = new XSSFWorkbook()) {
            setColumnHeaderStyle(wb);

            Sheet sheet = wb.createSheet();

            for (int i = 0; i < header.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < header[0].length; j++) {
                    Cell cell = row.createCell(j);
                    if (i == 2) {
                        cell.setCellStyle(HEADER_STYLE);
                    }
                    cell.setCellValue(header[i][j]);
                }
            }

            wb.write(fos);

        } catch (FileNotFoundException e) {
            System.out.println(AppConsoleStyle.RED
                    + "[ERROR] No se pudo encontrar el archivo o está abierto con otro programa"
                    + AppConsoleStyle.RESET);
        } catch (IOException e) {
            System.out.println(AppConsoleStyle.RED + "[ERROR] No se pudo escribir el archivo" + AppConsoleStyle.RESET);
        }
        return null;
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


    private void getCellTypeAndData(XSSFWorkbook wb,
                                    Row row,
                                    List<Integer> columnsWithHeader,
                                    List<Object[]> rowData) {

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
                        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
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
                List<Integer> columnsWithHeaderList = columnsWithHeader.stream().toList();

                if (isEstructuralExcel)
                    estructureExcelColumnsWithHeader = columnsWithHeaderList;
                else
                    excelColumnsWithHeader = columnsWithHeaderList;
                break;
            }
        }
    }


    //TODO: CREAR EL QUE LEE, Y POSTERIORMENTE RECOGE (DOS FUNCIONES DIFERENTES) PARA SER EXACTOS
}
