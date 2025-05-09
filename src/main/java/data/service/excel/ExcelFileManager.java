package data.service.excel;

import app.AppConsoleStyle;
import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ExcelFileManager {

    @Getter
    public int headersRow = 0;

    @Getter
    public String fileName;

    private List<Integer> excelColumnsWithHeader;

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


    private String getFileURL() {
        JFileChooser fileChooser = new JFileChooser();

        FileNameExtensionFilter filter = new FileNameExtensionFilter("Archivos de excel",
                "xlsx", "xls");
        fileChooser.setFileFilter(filter);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int returnVal = fileChooser.showOpenDialog(null);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            fileName = fileChooser.getSelectedFile().getName();
            return fileChooser.getSelectedFile().getAbsolutePath();
        } else {
            System.out.println(AppConsoleStyle.RED + "[ERROR] No se seleccionó el archivo");
            System.exit(1);
            return null;
        }
    }

    public List<List<Object[]>> readFile() {
        List<List<Object[]>> dataFromFile = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream("C:\\Users\\Practicas\\Documents\\java\\ImportadorProductos\\src\\main\\myExcel\\articulo.xlsx");
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheetAt(0);

            //Comprobamos cual es la fila de las columnas de la base de datos
            for (Row row : sheet) {
                if (isHeaderRow(row)) {
                    headersRow = row.getRowNum();
                    ArrayList<Integer> columnsWithHeader = new ArrayList<>();
                    for (Cell cell : row) {
                        if (cell == null) continue;
                        String cellValue = cell.getStringCellValue();
                        if ((cellValue != null) && (!cellValue.isEmpty())) {
                            columnsWithHeader.add(cell.getColumnIndex());
                        }
                    }
                    excelColumnsWithHeader = columnsWithHeader.stream().toList();
                    break;
                }
            }


            for (Row row : sheet) {

                List<Object[]> rowData = new ArrayList<>();

                for (Integer cellNum : excelColumnsWithHeader) {

                    Cell cell = row.getCell(cellNum);

                    if (cell == null) rowData.add(null);
                    else switch (cell.getCellType()) {
                        case STRING -> rowData.add(new Object[]{
                                Types.VARCHAR,
                                cell.getStringCellValue().trim().toUpperCase()
                        });

                        case NUMERIC -> rowData.add(formatCellNumberValue(cell));

                        case BOOLEAN -> rowData.add(new Object[]{
                                Types.INTEGER,
                                (cell.getBooleanCellValue()) ? 1 : 0
                        });

                        case FORMULA -> {
                            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
                            CellValue cellValue = evaluator.evaluate(cell);
                            if (cellValue.getCellType() == CellType.NUMERIC) {
                                rowData.add(formatCellNumberValue(cell));
                            } else {
                                rowData.add(new Object[]{
                                        Types.VARCHAR,
                                        cellValue.getStringValue()
                                });
                            }
                        }
                        default -> rowData.add(null);
                    }
                }

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

    private Object[] formatCellNumberValue(Cell cell) {
        Object[] newValue;
        if (DateUtil.isCellDateFormatted(cell)) {
            newValue = new Object[]{
                    Types.DATE,
                    cell.getDateCellValue()
            };
        } else {
            DataFormatter dataFormatter = new DataFormatter();
            String stringCellValue = dataFormatter.formatCellValue(cell);
            Double value = cell.getNumericCellValue();

            try {
                if (Objects.equals(stringCellValue, String.valueOf(value))) {
                    double auxDoubleValueC1 = value;
                    double auxDoubleValueC2 = value;
                    newValue = new Object[]{
                            (value == Math.floor(auxDoubleValueC1)) ? Types.INTEGER : Types.DOUBLE,
                            (value == Math.floor(auxDoubleValueC2)) ? value.intValue() : value,
                    };
                } else {
                    newValue = new Object[]{
                            Types.VARCHAR,
                            stringCellValue.replace(',', '.')
                    };
                }
            } catch (Exception e) {
                newValue = new Object[]{
                        Types.VARCHAR,
                        stringCellValue
                };
            }
        }
        return newValue;
    }
}
