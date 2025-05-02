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
import java.util.ArrayList;
import java.util.List;

public class ExcelFileManager {

    @Getter
    public int headersRow = 0;

    @Getter
    public String fileName;

    private CellStyle HEADER_STYLE;

    private void setHeaderStyle(XSSFWorkbook wb) {
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

    public List<List<Object>> readFile() {
        List<List<Object>> dataFromFile = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(getFileURL());
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheetAt(0);

            //Comprobamos cual es la fila de las columnas de la base de datos
            for (Row row : sheet) {
                if (isHeaderRow(row)) {
                    headersRow = row.getRowNum();
                    break;
                }
            }


            for (Row row : sheet) {

                List<Object> rowData = new ArrayList<>();

                for (int cellNum = 0; cellNum < sheet.getRow(headersRow).getPhysicalNumberOfCells(); cellNum++) {

                    Cell cell = row.getCell(cellNum);

                    if (cell == null) rowData.add(null);
                    else switch (cell.getCellType()) {
                        case STRING -> {
                            rowData.add(cell.getStringCellValue());
                        }
                        case NUMERIC -> {
                            if (DateUtil.isCellDateFormatted(cell)) {
                                rowData.add(cell.getDateCellValue());
                            } else {
                                rowData.add(cell.getNumericCellValue());
                            }
                        }
                        case BOOLEAN -> {
                            rowData.add(cell.getBooleanCellValue());
                        }
                        default -> rowData.add("");

                    }
                }
                dataFromFile.add(rowData);
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
            setHeaderStyle(wb);

            Sheet sheet = wb.createSheet();

            for (int i = 0; i < header.length; i++) {
                Row row = sheet.createRow(i+1);
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
}
