package data.repository;

import app.AppConsoleStyle;
import data.model.*;
import data.service.config.ClassType;
import data.service.excel.ExcelFileManager;

import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@lombok.Data
public class Repository {

    //*************************************************************************
    //* ARGUMENTS                                                             *
    //*************************************************************************

    private final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss");

    //***************
    //* CONFIG FILE *
    //***************

    private final Map<String, Object> SECTIONS_CONFIG;

    private Map<String, IncreaseData> DEFAULT_INCREASE_FROM_CONFIG;
    private Map<String, String[]> DEFAULT_UPDATE_FROM_CONFIG;
    private Map<String, Data[]> DEFAULT_TABLES_DATA_FROM_CONFIG;

    //***************
    //* EXCEL FILES *
    //***************

    /***** ESTRUCTURE FILE *****/

    final private Map<TableAndColumnNameExcelData, String[]> FOREIGN_KEY_HEADERS_FROM_EXCEL;                                // Estos son aquellos que tienen un foreign key
    final private Map<TableAndColumnNameExcelData, Object[]> INNER_DATA_HEADERS_FROM_EXCEL;                                 // Estos son aquellos que necesitan de otra columna para funcionar

    /***** DATA FILE *****/

    private List<TableAndColumnNameExcelData> COLUMN_AND_TABLE_FROM_EXCEL;                                                  // Combinación del nombre de la columna con la tabla
    private List<String> ALL_TABLES_EXCEL;                                                                                  // Vendrá de un set para que solo aparezcan las que existen

    private Queue<Map<TableAndColumnNameExcelData, Object[]>> DATA_FROM_EXCEL;                                              // Los datos del excel

    //*************************************************************************
    //* CONSTRUCTOR                                                           *
    //*************************************************************************


    public Repository(Map<String, Object> sectionsConfig) {
        // GETTING DATA FROM CONFIG
        this.SECTIONS_CONFIG = sectionsConfig;
        this.DEFAULT_INCREASE_FROM_CONFIG = setIncreaseConfig();
        this.DEFAULT_TABLES_DATA_FROM_CONFIG = setTablesConfig();
        this.DEFAULT_UPDATE_FROM_CONFIG = setUpdateConfig();

        // GETTING DATA FROM ESTRUCTURE-EXCEL
        this.FOREIGN_KEY_HEADERS_FROM_EXCEL = setExcelsHeaderForeignKeys(dataFromExcel, EXCEL_FILE_MANAGER);
        this.INNER_DATA_HEADERS_FROM_EXCEL = ;
    }

    //*************************************************************************
    //* FILL CONFIG DATA                                                      *
    //*************************************************************************

    //***************
    //* SETTERS     *
    //***************


    private Map<String, String[]> setUpdateConfig() {
        Map<String, String[]> updateConfig = new LinkedHashMap<>();
        for (UpdateData eachUpdate : (UpdateData[]) SECTIONS_CONFIG.get("Actualización")) {
            updateConfig.put(eachUpdate.getName(), eachUpdate.getData());
        }
        return updateConfig;
    }

    private Map<String, IncreaseData> setIncreaseConfig() {
        Map<String, IncreaseData> increaseConfig = new LinkedHashMap<>();
        for (IncreaseData increaseData : (IncreaseData[]) SECTIONS_CONFIG.get("Incrementos")) {
            Double auxData = (Double) increaseData.getData();
            increaseData.setData(auxData.intValue());
            increaseConfig.put(increaseData.getName(), increaseData);
        }
        return increaseConfig;
    }

    private Map<String, Data[]> setTablesConfig() {
        Map<String, Data[]> tablesConfig = new LinkedHashMap<>();

        for (TableData eachTableData : (TableData[]) SECTIONS_CONFIG.get("Tablas")) {
            for (Data eachColumn : eachTableData.getData()) {
                String type = (String) eachColumn.getType();
                int classType = ClassType.getClassType(type);
                // AÑADIR LA HORA DE AHORA
                if (classType == Types.TIMESTAMP
                        && eachColumn.getData() != null
                        && eachColumn.getData().equals("AHORA()")) {
                    eachColumn.setData(LocalDateTime.now().format(DATE_FORMAT));
                } else if (classType == Types.INTEGER && eachColumn.getData() != null) {
                    Double auxData = (Double) eachColumn.getData();
                    eachColumn.setData(auxData.intValue());
                }
                eachColumn.setType(ClassType.getClassType(type));
            }
            tablesConfig.put(eachTableData.getName(), eachTableData.getData());
        }
        return tablesConfig;
    }

    //***************
    //* GETTERS     *
    //***************

    public DbData[] getDataBaseConfig() {
        return (DbData[]) SECTIONS_CONFIG.get("DbConfig");
    }

    //*************************************************************************
    //* FILL EXCEL DATA                                                       *
    //*************************************************************************

    public void setRepositoryData(ExcelFileManager EXCEL_FILE_MANAGER) {
        List<List<Object[]>> dataFromExcel = EXCEL_FILE_MANAGER.readFile();
        setCOLUMN_AND_TABLE_FROM_EXCEL(setExcelsHeaderTables(dataFromExcel, EXCEL_FILE_MANAGER));
        setALL_TABLES_EXCEL(setExcelsTables(dataFromExcel, EXCEL_FILE_MANAGER));
        setFOREIGN_KEY_HEADERS_FROM_EXCEL(setExcelsHeaderForeignKeys(dataFromExcel, EXCEL_FILE_MANAGER));
        setINNER_DATA_HEADERS_FROM_EXCEL(setExcelHeaderInnerConnections(dataFromExcel, EXCEL_FILE_MANAGER));
        setDATA_FROM_EXCEL(setExcelsData(dataFromExcel, EXCEL_FILE_MANAGER));
    }

    private List<String> setExcelsTables(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Set<String> tables = new LinkedHashSet<>();
        for (int i = 0; i < data.get(EXCEL_FILE_MANAGER.headersRow).size(); i++) {
            tables.add(data.get(EXCEL_FILE_MANAGER.headersRow).get(i)[1].toString().split("\\.")[0]);
        }
        return tables.stream().toList();
    }

    private List<TableAndColumnNameExcelData> setExcelsHeaderTables(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        List<TableAndColumnNameExcelData> tableAndColumnNameExcelData = new ArrayList<>();
        for (int i = 0; i < data.get(EXCEL_FILE_MANAGER.headersRow).size(); i++) {
            String[] tableDataFromExcel = data.get(EXCEL_FILE_MANAGER.headersRow)
                    .get(i)[1].toString().split("\\.");

            int numberOfElement = (tableDataFromExcel.length == 2) ? 0 : Integer.parseInt(tableDataFromExcel[2]);
            tableAndColumnNameExcelData.add(new TableAndColumnNameExcelData(
                    tableDataFromExcel[1],
                    tableDataFromExcel[0],
                    numberOfElement
            ));
        }
        return tableAndColumnNameExcelData;
    }

    private Map<TableAndColumnNameExcelData, String[]> setExcelsHeaderForeignKeys(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Map<TableAndColumnNameExcelData, String[]> header = new HashMap<>();

        for (int i = 0; i < data.getFirst().size(); i++) {
            if (data.get(EXCEL_FILE_MANAGER.headersRow - 1).get(i) != null) {
                header.put(getCOLUMN_AND_TABLE_FROM_EXCEL().get(i),
                        new String[]{
                                (String) data.get(EXCEL_FILE_MANAGER.headersRow - 1).get(i)[1],                             // TABLA REFERENCIADA
                                (String) data.get(EXCEL_FILE_MANAGER.headersRow - 2).get(i)[1],                             // COLUMNA DE LA TABLA REFERENCIADA
                                (String) data.get(EXCEL_FILE_MANAGER.headersRow - 3).get(i)[1]                              // COLUMNA QUE IGUALA EL VALOR DE LA COLUMNA DEL EXCEL
                });
            }
        }
        return header;
    }

    private Map<TableAndColumnNameExcelData, Object[]> setExcelHeaderInnerConnections(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Map<TableAndColumnNameExcelData, Object[]> header = new HashMap<>();

        if (EXCEL_FILE_MANAGER.headersRow != 4) {
            for (int i = 0; i < data.getFirst().size(); i++) {

                if (data.get(EXCEL_FILE_MANAGER.headersRow - 4).get(i) == null) continue;

                String[] tableDataFromExcel = data.get(EXCEL_FILE_MANAGER.headersRow - 4)
                        .get(i)[1].toString().split("\\.");

                int numberOfElement = (tableDataFromExcel.length == 2) ? 0 : Integer.parseInt(tableDataFromExcel[2]);

                header.put(getCOLUMN_AND_TABLE_FROM_EXCEL().get(i), new Object[]{
                        new TableAndColumnNameExcelData(
                                tableDataFromExcel[1],
                                tableDataFromExcel[0],
                                numberOfElement
                        ),
                        data.get(EXCEL_FILE_MANAGER.headersRow - 5).get(i)[1].toString()
                });
            }
        }
        return header;
    }

    private Queue<Map<TableAndColumnNameExcelData, Object[]>> setExcelsData(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Queue<Map<TableAndColumnNameExcelData, Object[]>> registryData = new LinkedList<>();
        if (data.size() > EXCEL_FILE_MANAGER.headersRow + 1) {
            for (int i = EXCEL_FILE_MANAGER.headersRow + 1; i < data.size(); i++) {
                Map<TableAndColumnNameExcelData, Object[]> dataRow = new HashMap<>();
                for (int j = 0; j < data.get(i).size(); j++) {
                    dataRow.put(getCOLUMN_AND_TABLE_FROM_EXCEL().get(j), data.get(i).get(j));
                }
                registryData.add(dataRow);
            }
        } else {
            System.out.println(AppConsoleStyle.RED + "[ERROR] No hay datos en el excel" + AppConsoleStyle.RESET);
            System.exit(1);
        }
        return registryData;
    }
}
