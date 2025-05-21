package data.repository;

import app.AppConsoleStyle;
import data.model.*;
import data.service.config.ClassType;
import data.service.config.ConfigFileManager;
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

    final public DbData[] DEFAULT_DB_FROM_CONFIG;

    final private Map<String, IncreaseData> DEFAULT_INCREASE_FROM_CONFIG;
    final private Map<String, String[]> DEFAULT_UPDATE_FROM_CONFIG;
    final private Map<String, Data[]> DEFAULT_TABLES_DATA_FROM_CONFIG;

    //***************
    //* EXCEL FILES *
    //***************

    /***** ESTRUCTURE FILE *****/
    final private List<TableAndColumnNameExcelData> COLUMN_AND_TABLE_FROM_ESTRUCTURE_EXCEL;                                 // Combinación del nombre de la columna con la tabla del excel de estructura

    final private List<String> ALL_TABLES_EXCEL;                                                                            // Vendrá de un set para que solo aparezcan las que existen
    final private Map<TableAndColumnNameExcelData, String[]> FOREIGN_KEY_HEADERS_FROM_EXCEL;                                // Estos son aquellos que tienen un foreign key
    final private Map<TableAndColumnNameExcelData, Object[]> INNER_DATA_HEADERS_FROM_EXCEL;                                 // Estos son aquellos que necesitan de otra columna para funcionar

    final private Queue<Map<TableAndColumnNameExcelData, Object[]>> DATA_FROM_EXCEL =                                       // Los datos pasados del excel
            new LinkedList<>();


    /***** DATA FILE *****/

    private List<TableAndColumnNameExcelData> COLUMN_AND_TABLE_FROM_DATA_EXCEL;                                             // Combinación del nombre de la columna con la tabla del excel de datos

    final private Queue<Map<TableAndColumnNameExcelData, Object[]>> DATA_FROM_DATA_EXCEL =
            new LinkedList<>();                                                                                             // Los datos del excel de datos

    //*************************************************************************
    //* CONSTRUCTOR                                                           *
    //*************************************************************************

    public Repository(ConfigFileManager configFileManager, ExcelFileManager excelFileManager) {
        // GETTING DATA FROM CONFIG
        Map<String, Object> sectionsConfig = configFileManager.loadConfig();

        this.DEFAULT_DB_FROM_CONFIG = (DbData[]) sectionsConfig.get("DbConfig");
        this.DEFAULT_INCREASE_FROM_CONFIG = setIncreaseConfig((IncreaseData[]) sectionsConfig.get("Incrementos"));
        this.DEFAULT_TABLES_DATA_FROM_CONFIG = setTablesConfig((TableData[]) sectionsConfig.get("Tablas"));
        this.DEFAULT_UPDATE_FROM_CONFIG = setUpdateConfig((UpdateData[]) sectionsConfig.get("Actualización"));

        // GETTING DATA FROM ESTRUCTURE-EXCEL
        List<List<Object[]>> dataFromEstructureExcel = excelFileManager.readHeaderFromFile(true);

        this.COLUMN_AND_TABLE_FROM_ESTRUCTURE_EXCEL = setExcelsHeaderTables(dataFromEstructureExcel, excelFileManager, true);
        this.ALL_TABLES_EXCEL = setExcelsTables(dataFromEstructureExcel, excelFileManager);
        this.FOREIGN_KEY_HEADERS_FROM_EXCEL = setExcelsHeaderForeignKeys(dataFromEstructureExcel, excelFileManager);
        this.INNER_DATA_HEADERS_FROM_EXCEL = setExcelHeaderInnerConnections(dataFromEstructureExcel, excelFileManager);
    }

    //*************************************************************************
    //* FILL CONFIG DATA                                                      *
    //*************************************************************************

    //***************
    //* SETTERS     *
    //***************


    private Map<String, String[]> setUpdateConfig(UpdateData[] updateDatas) {
        Map<String, String[]> updateConfig = new LinkedHashMap<>();
        for (UpdateData eachUpdate : updateDatas) {
            updateConfig.put(eachUpdate.getName(), eachUpdate.getData());
        }
        return updateConfig;
    }

    private Map<String, IncreaseData> setIncreaseConfig(IncreaseData[] increaseDatas) {
        Map<String, IncreaseData> increaseConfig = new LinkedHashMap<>();
        for (IncreaseData increaseData : increaseDatas) {
            Double auxData = (Double) increaseData.getData();
            increaseData.setData(auxData.intValue());
            increaseConfig.put(increaseData.getName(), increaseData);
        }
        return increaseConfig;
    }

    private Map<String, Data[]> setTablesConfig(TableData[] tableData) {
        Map<String, Data[]> tablesConfig = new LinkedHashMap<>();

        for (TableData eachTableData : tableData) {
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


    //*************************************************************************
    //* FILL EXCEL DATA                                                       *
    //*************************************************************************

    public void setRepositoryData(ExcelFileManager EXCEL_FILE_MANAGER) {
        setCOLUMN_AND_TABLE_FROM_DATA_EXCEL(setExcelsHeaderTables(EXCEL_FILE_MANAGER.readHeaderFromFile(false),
                EXCEL_FILE_MANAGER,
                false));

        setExcelsData(EXCEL_FILE_MANAGER.readDataFile(), EXCEL_FILE_MANAGER);
    }

    private List<String> setExcelsTables(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Set<String> tables = new LinkedHashSet<>();
        for (int i = 0; i < data.get(EXCEL_FILE_MANAGER.estructureHeadersRow).size(); i++) {
            tables.add(data.get(EXCEL_FILE_MANAGER.estructureHeadersRow).get(i)[1].toString().split("\\.")[0]);
        }
        return tables.stream().toList();
    }

    private List<TableAndColumnNameExcelData> setExcelsHeaderTables(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER, boolean isEstructuralExcel) {
        List<TableAndColumnNameExcelData> tableAndColumnNameExcelData = new ArrayList<>();
        int headersRow = (isEstructuralExcel) ? EXCEL_FILE_MANAGER.estructureHeadersRow : EXCEL_FILE_MANAGER.headersRow;
        for (int i = 0; i < data.get(headersRow).size(); i++) {
            String[] tableDataFromExcel = data.get(headersRow)
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
            if (data.get(EXCEL_FILE_MANAGER.estructureHeadersRow - 1).get(i) != null) {
                header.put(getCOLUMN_AND_TABLE_FROM_ESTRUCTURE_EXCEL().get(i),
                        new String[]{(String) data.get(EXCEL_FILE_MANAGER.estructureHeadersRow - 1).get(i)[1],               // TABLA REFERENCIADA
                                (String) data.get(EXCEL_FILE_MANAGER.estructureHeadersRow - 2).get(i)[1],                    // COLUMNA DE LA TABLA REFERENCIADA
                                (String) data.get(EXCEL_FILE_MANAGER.estructureHeadersRow - 3).get(i)[1]                     // COLUMNA QUE IGUALA EL VALOR DE LA COLUMNA DEL EXCEL
                });
            }
        }
        return header;
    }

    private Map<TableAndColumnNameExcelData, Object[]> setExcelHeaderInnerConnections(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Map<TableAndColumnNameExcelData, Object[]> header = new HashMap<>();

        if (EXCEL_FILE_MANAGER.estructureHeadersRow != 4) {
            for (int i = 0; i < data.getFirst().size(); i++) {

                if (data.get(EXCEL_FILE_MANAGER.estructureHeadersRow - 4).get(i) == null) continue;

                String[] tableDataFromExcel = data.get(EXCEL_FILE_MANAGER.estructureHeadersRow - 4)
                        .get(i)[1].toString().split("\\.");

                int numberOfElement = (tableDataFromExcel.length == 2) ? 0 : Integer.parseInt(tableDataFromExcel[2]);

                header.put(getCOLUMN_AND_TABLE_FROM_ESTRUCTURE_EXCEL().get(i), new Object[]{
                        new TableAndColumnNameExcelData(
                                tableDataFromExcel[1],
                                tableDataFromExcel[0],
                                numberOfElement
                        ), data.get(EXCEL_FILE_MANAGER.estructureHeadersRow - 5).get(i)[1].toString()
                });
            }
        }
        return header;
    }

    private void setExcelsData(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        if (data.size() > EXCEL_FILE_MANAGER.headersRow) {
            for (int i = EXCEL_FILE_MANAGER.headersRow; i < data.size(); i++) {
                Map<TableAndColumnNameExcelData, Object[]> dataRow = new HashMap<>();
                for (int j = 0; j < data.get(i).size(); j++) {
                    dataRow.put(getCOLUMN_AND_TABLE_FROM_DATA_EXCEL().get(j), data.get(i).get(j));
                }
                DATA_FROM_DATA_EXCEL.add(dataRow);
            }
        } else {
            System.out.println(AppConsoleStyle.RED + "[ERROR] No hay datos en el excel" + AppConsoleStyle.RESET);
            System.exit(1);
        }
    }

    public void setDATA_FROM_EXCEL(List<List<Object[]>> data) {
        if (!data.isEmpty()) {
            for (List<Object[]> datum : data) {
                Map<TableAndColumnNameExcelData, Object[]> dataRow = new LinkedHashMap<>();
                for (int j = 0; j < datum.size(); j++) {
                    dataRow.put(getCOLUMN_AND_TABLE_FROM_ESTRUCTURE_EXCEL().get(j), datum.get(j));
                }
                DATA_FROM_EXCEL.add(dataRow);
            }
        } else {
            System.out.println(AppConsoleStyle.RED + "[ERROR] No hay datos en el excel" + AppConsoleStyle.RESET);
            System.exit(1);
        }
    }
}
