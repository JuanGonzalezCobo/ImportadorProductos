package data.repository;

import app.AppConsoleStyle;
import data.model.Data;
import data.model.DbData;
import data.model.IncreaseData;
import data.model.TableData;
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
    private Map<String, Data[]> DEFAULT_TABLES_DATA_FROM_CONFIG;

    //***************
    //* EXCEL FILE  *
    //***************

    private List<String> COLUMNS_EXCEL;
    private List<String> ALL_TABLES_EXCEL;                                      //Vendrá de un set para que solo aparezcan las que existen

    private Map<String, String> TABLE_HEADERS_FROM_EXCEL;                       //Esto es la relación de columna principal con el nombre de la tabla de la que forma parte
    private Map<String, String[]> FOREIGN_KEY_HEADERS_FROM_EXCEL;               //Estos son aquellos que tienen un foreign key
    private Map<String, List<String[]>> INNER_DATA_HEADERS_FROM_EXCEL;          //Estos son aquellos que necesitan de otra columna para funcionar
    private Queue<Map<String, Object[]>> DATA_FROM_EXCEL;                       //Los datos del excel

    //*************************************************************************
    //* CONSTRUCTOR                                                           *
    //*************************************************************************


    public Repository(Map<String, Object> sectionsConfig) {
        this.SECTIONS_CONFIG = sectionsConfig;
        this.DEFAULT_INCREASE_FROM_CONFIG = setIncreaseConfig();
        this.DEFAULT_TABLES_DATA_FROM_CONFIG = setTablesConfig();

    }

    //*************************************************************************
    //* FILL CONFIG DATA                                                      *
    //*************************************************************************

    //***************
    //* SETTERS     *
    //***************

    private Map<String, IncreaseData> setIncreaseConfig() {
        Map<String, IncreaseData> increaseConfig = new HashMap<>();
        for (IncreaseData increaseData : (IncreaseData[]) SECTIONS_CONFIG.get("Incrementos")) {
            Double auxData = (Double) increaseData.getData();
            increaseData.setData(auxData.intValue());
            increaseConfig.put(increaseData.getName(), increaseData);
        }
        return increaseConfig;
    }

    private Map<String, Data[]> setTablesConfig() {
        Map<String, Data[]> tablesConfig = new HashMap<>();

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
        setCOLUMNS_EXCEL(setExcelsColumn(dataFromExcel, EXCEL_FILE_MANAGER));
        setALL_TABLES_EXCEL(setExcelsTables(dataFromExcel, EXCEL_FILE_MANAGER));
        setTABLE_HEADERS_FROM_EXCEL(setExcelsHeaderTables(dataFromExcel, EXCEL_FILE_MANAGER));
        setFOREIGN_KEY_HEADERS_FROM_EXCEL(setExcelsHeaderForeignKeys(dataFromExcel, EXCEL_FILE_MANAGER));
        setINNER_DATA_HEADERS_FROM_EXCEL(setExcelHeaderInnerConnections(dataFromExcel, EXCEL_FILE_MANAGER));
        setDATA_FROM_EXCEL(setExcelsData(dataFromExcel, EXCEL_FILE_MANAGER));
    }

    private List<String> setExcelsColumn(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < data.get(EXCEL_FILE_MANAGER.headersRow).size(); i++) {
            columns.add((String) data.get(EXCEL_FILE_MANAGER.headersRow).get(i)[1]); // COLUMNA
        }
        return columns;
    }

    private List<String> setExcelsTables(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Set<String> tables = new LinkedHashSet<>();
        for (int i = 0; i < data.get(EXCEL_FILE_MANAGER.headersRow).size(); i++) {
            tables.add((String) data.get(EXCEL_FILE_MANAGER.headersRow - 1).get(i)[1]);
        }
        return tables.stream().toList();
    }

    private Map<String, String> setExcelsHeaderTables(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Map<String, String> headerTable = new HashMap<>();
        for (int i = 0; i < data.get(EXCEL_FILE_MANAGER.headersRow).size(); i++) {
            headerTable.put((String) data.get(EXCEL_FILE_MANAGER.headersRow).get(i)[1],
                    (String) data.get(EXCEL_FILE_MANAGER.headersRow - 1).get(i)[1]);
        }

        return headerTable;
    }

    private Map<String, String[]> setExcelsHeaderForeignKeys(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Map<String, String[]> header = new HashMap<>();

        for (int i = 0; i < data.getFirst().size(); i++) {
            if (data.get(EXCEL_FILE_MANAGER.headersRow - 2).get(i) != null) {
                header.put((String) data.get(EXCEL_FILE_MANAGER.headersRow).get(i)[1], new String[]{
                        (String) data.get(EXCEL_FILE_MANAGER.headersRow - 2).get(i)[1],    // Tabla referenciada
                        (String) data.get(EXCEL_FILE_MANAGER.headersRow - 3).get(i)[1],    // Columna de tabla referenciada
                        (String) data.get(EXCEL_FILE_MANAGER.headersRow - 4).get(i)[1]     // Columna que iguala el valor de la columna del excel
                });
            }
        }
        return header;
    }

    private Map<String, List<String[]>> setExcelHeaderInnerConnections(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Map<String, List<String[]>> header = new HashMap<>();

        if (EXCEL_FILE_MANAGER.headersRow != 4) {
            for (int i = 0; i < data.getFirst().size(); i++) {
                List<String[]> innerConnections = new ArrayList<>();
                if (data.get(EXCEL_FILE_MANAGER.headersRow - 5).get(i) == null) continue;

                for (int j = 0; j < (EXCEL_FILE_MANAGER.headersRow - 4) / 2; j++) {
                    if (data.get(EXCEL_FILE_MANAGER.headersRow - 5 - j * 2).get(i) == null) continue;
                    innerConnections.add(new String[]{
                                    (String) data.get(EXCEL_FILE_MANAGER.headersRow - 5 - j * 2).get(i)[1], //Nombre de la columna en excel
                                    (String) data.get(EXCEL_FILE_MANAGER.headersRow - 6 - j * 2).get(i)[1]  //Nombre de la columna en la tabla referenciada
                            }
                    );
                }
                header.put((String) data.get(EXCEL_FILE_MANAGER.headersRow).get(i)[1], innerConnections);
            }
        }
        return header;
    }

    private Queue<Map<String, Object[]>> setExcelsData(List<List<Object[]>> data, ExcelFileManager EXCEL_FILE_MANAGER) {
        Queue<Map<String, Object[]>> registryData = new LinkedList<>();
        if (data.size() > EXCEL_FILE_MANAGER.headersRow + 1) {
            for (int i = EXCEL_FILE_MANAGER.headersRow + 1; i < data.size(); i++) {
                Map<String, Object[]> dataRow = new HashMap<>();
                for (int j = 0; j < data.get(i).size(); j++) {
                    dataRow.put(getCOLUMNS_EXCEL().get(j), data.get(i).get(j));
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
