package domain;

import app.App;
import app.AppConfig;
import app.AppState;
import data.model.Data;
import data.model.IncreaseData;
import data.model.TableNameExcelData;
import data.repository.Repository;
import data.service.config.ConfigFileManager;
import data.service.db.DataBaseConnection;
import data.service.excel.ExcelFileManager;

import java.sql.Types;
import java.util.*;

public class MainController {
    private final AppConfig CONFIG;
    private final AppState STATE;

    private final Repository REPOSITORY;
    private final ExcelFileManager EXCEL_FILE_MANAGER;
    private final ConfigFileManager CONFIG_FILE_MANAGER;
    private final DataBaseConnection DB_CONNECTION;

    private final Map<String, Map<String, Object[]>> DATA_TO_INSERT_INTO_DB = new LinkedHashMap<>();


    public MainController(AppState state, AppConfig config) {
        this.CONFIG_FILE_MANAGER = new ConfigFileManager();
        App.clearConsole();
        System.out.println("""
                ----------------------------------------------------------------
                         INSERCIÓN EN BASE DE DATOS A PARTIR DE EXCEL
                ----------------------------------------------------------------""");

        this.REPOSITORY = new Repository(CONFIG_FILE_MANAGER.loadConfig());

        this.CONFIG = config;
        this.STATE = state;

        CONFIG.setDatabaseConfig(REPOSITORY.getDataBaseConfig());

        this.DB_CONNECTION = new DataBaseConnection(getDataBaseInfo());

        this.EXCEL_FILE_MANAGER = new ExcelFileManager();

        mainThread();
    }

    private String[] getDataBaseInfo() {
        return new String[]{
                CONFIG.URL,
                CONFIG.DATABASE_NAME,
                CONFIG.USER,
                CONFIG.PASSWORD,
                CONFIG.CHARSET
        };
    }

    private void mainThread() {
        REPOSITORY.setRepositoryData(EXCEL_FILE_MANAGER);

        mainInsertInDB();

        DB_CONNECTION.closeConnection();
        App.clearConsole();
    }


    //*************************************************************************
    //* MAIN FUNCTIONALITY                                                    *
    //*************************************************************************

    private void mainInsertInDB() {
        Map<String, Object[]> dataRow;

        List<String> excelAllTablesHeaders;                                                                                 // LISTA QUE VIENE DEL SET DONDE APARECEN TODAS LAS TABLAS
        List<TableNameExcelData> excelHeaderTable;                                                                          // LISTA DE LAS TABLAS REFERENCIADAS A LAS COLUMNAS
        Map<String, String[]> excelHeaderForeignKey = REPOSITORY.getFOREIGN_KEY_HEADERS_FROM_EXCEL();                       // MAPA DE LAS FK, CON _KEYS_ COMO LAS COLUMNAS DEL EXCEL
        Map<String, List<String[]>> excelHeaderInnerConnections = REPOSITORY.getINNER_DATA_HEADERS_FROM_EXCEL();            // MAPA DE LOS INNER_CONNECTIONS, CON _KEYS_ COMO LAS COLUMNAS DEL EXCEL
        Map<String, Data[]> config = REPOSITORY.getDEFAULT_TABLES_DATA_FROM_CONFIG();                                       // MAPA DE LOS DATOS DE LAS TABLAS EN EL ARCHIVO DE config.json

        Map<String, List<Data>> dataPerTableConfig = new LinkedHashMap<>();                                                 // MAPA DE LOS DATOS QUE HAY EN ARCHIVO config.json, CON _KEYS_ COMO LA TABLA

        String tableName = null;                                                                                            // STRING, NOMBRE DE LA TABLA POR COLUMNA

        while ((dataRow = REPOSITORY.getDATA_FROM_EXCEL().poll()) != null) {

            //*************************************************
            //*  INITIALIZATION OF REQUIRED COMPONENTS        *
            //*************************************************

            DATA_TO_INSERT_INTO_DB.clear();

            excelAllTablesHeaders = REPOSITORY.getALL_TABLES_EXCEL();
            excelHeaderTable = new ArrayList<>(REPOSITORY.getTABLE_HEADERS_FROM_EXCEL());

            for (String table : excelAllTablesHeaders) {
                DATA_TO_INSERT_INTO_DB.put(table, new LinkedHashMap<>());
                if (config.get(table) != null) {
                    dataPerTableConfig.put(table, Arrays.stream(config.get(table)).toList());
                }
            }

            //*************************************************
            //*    LOOP FOR EACH COLUMN IN THE EXCEL FILE     *
            //*************************************************

            for (String columnName : REPOSITORY.getCOLUMNS_EXCEL()) {

                //*************************************************
                //*    GETTING THE NAME OF THE COLUMN'S TABLE     *
                //*************************************************

                String lastestTableName = tableName;
                tableName = getTableName(excelHeaderTable, columnName);

                //*************************************************
                //*    INSERT OF REGISTRY WITH SAME TABLE NAME    *
                //*************************************************

                if (lastestTableName != null && !lastestTableName.equals(tableName)) {
                    if (!DATA_TO_INSERT_INTO_DB.get(lastestTableName).isEmpty()) {
                        if (config.get(lastestTableName) != null) {
                            Map<String, Object[]> dataToInsertAux = new HashMap<>(DATA_TO_INSERT_INTO_DB
                                    .get(lastestTableName));
                            addLastInfoFromConfig(dataToInsertAux, dataPerTableConfig.get(lastestTableName));
                            DATA_TO_INSERT_INTO_DB.put(lastestTableName, dataToInsertAux);
                            dataPerTableConfig.remove(lastestTableName);
                        }
                        if (DB_CONNECTION.checkIfAlreadyExists(
                                lastestTableName,
                                DATA_TO_INSERT_INTO_DB.get(lastestTableName))
                        ) createNewRegistry(lastestTableName, DATA_TO_INSERT_INTO_DB.get(lastestTableName));
                    }
                }

                Object[] infoToInsert;

                if (dataRow.get(columnName) == null) {                                                                      // [IF] EMPTY EXCEL CELL
                    Data columnInfoFromConfig = null;

                    if (dataPerTableConfig.get(tableName) != null && !dataPerTableConfig.get(tableName).isEmpty()) {            // WE SEARCH IT ON CONFIG FILE
                        columnInfoFromConfig = dataPerTableConfig.get(tableName).stream()
                                .filter(data -> data.getName().equals(columnName))
                                .findFirst()
                                .orElse(null);
                    }


                    if (columnInfoFromConfig == null || columnInfoFromConfig.getData() == null)                                 // [IF] THERE ISN'T DATA IN CONFIG FILE
                        continue;


                    infoToInsert = createDataToInsert(                                                                          // [ELSE] THERE'S DATA IN CONFIG FILE (ADDS IT)
                            config,
                            excelHeaderForeignKey,
                            excelHeaderInnerConnections,
                            REPOSITORY.getTABLE_HEADERS_FROM_EXCEL(),
                            columnName,
                            new Object[]{columnInfoFromConfig.getType(), columnInfoFromConfig.getData()}
                    );

                } else {                                                                                                    // [ELSE] NOT EMPTY CELL IN EXCEL
                    infoToInsert = createDataToInsert(
                            config,
                            excelHeaderForeignKey,
                            excelHeaderInnerConnections,
                            REPOSITORY.getTABLE_HEADERS_FROM_EXCEL(),
                            columnName,
                            dataRow.get(columnName)
                    );
                }


                //*************************************************
                //*        INSERT INTO THE INSERTION MAP          *
                //*************************************************

                DATA_TO_INSERT_INTO_DB.get(tableName).put(columnName, infoToInsert);

                //*********************************************************
                //* DELETE VALUE FROM CONFIG LIST TO NOT GET DOUBLED DATA *
                //*********************************************************

                try {
                    List<Data> modifiedList;
                    if (dataPerTableConfig.get(tableName) != null) {
                        modifiedList = new ArrayList<>(dataPerTableConfig.get(tableName));
                        eraseFromConfigList(modifiedList, columnName);
                        dataPerTableConfig.put(tableName, modifiedList);
                    }
                } catch (Exception e) {
                    System.out.println("[ERROR] No se ha borrado de la configuración datos de la tabla principal");
                }

                //*********************************************************
                //* DELETE VALUE FROM TABLE LIST TO NOT GET DOUBLED DATA  *
                //*********************************************************

                eraseFromTableList(excelHeaderTable, tableName, columnName);
            }

            //*********************************************************
            //*  THIS ONLY HAPPENS WHEN IS THE LAST TABLE IN EXCEL,   *
            //*  IT ADDS THE DATA FROM CONFIG TABLE THAT WASN'T       *
            //*  DELETED, TO INSERT IT INTO THE INSERTION MAP         *
            //*********************************************************

            if (!DATA_TO_INSERT_INTO_DB.get(tableName).isEmpty()) {
                if (dataPerTableConfig.get(tableName) != null && !dataPerTableConfig.get(tableName).isEmpty()) {
                    Map<String, Object[]> dataToInsertAux = DATA_TO_INSERT_INTO_DB.get(tableName);
                    addLastInfoFromConfig(dataToInsertAux, dataPerTableConfig.get(tableName));
                    DATA_TO_INSERT_INTO_DB.put(tableName, dataToInsertAux);
                }

                if (DB_CONNECTION.checkIfAlreadyExists(tableName, DATA_TO_INSERT_INTO_DB.get(tableName)))
                    // IF EVERYTHING'S OK IT WILL INSERT IN INTO THE DB
                    createNewRegistry(tableName, DATA_TO_INSERT_INTO_DB.get(tableName));
            }
        }
    }


    private Object[] createDataToInsert(
            Map<String, Data[]> config,
            Map<String, String[]> excelHeaderForeignKey,
            Map<String, List<String[]>> excelHeaderInnerConnections,
            List<TableNameExcelData> excelHeaderTable,
            String columnName,
            Object[] data
    ) {
        Object[] infoToInsert = new Object[2];
        String[] foreignKeyInfo;
        List<String[]> innerConnectionInfo;
        int[] newData;

        if ((foreignKeyInfo = excelHeaderForeignKey.get(columnName)) != null
                && (innerConnectionInfo = excelHeaderInnerConnections.get(columnName)) != null) {                           // [IF] EXISTS FK AND INNER_CONNECTIONS


            for (String[] innerConnection : innerConnectionInfo) {      // TODO CAMBIARLO PARA SI HAY MÁS
                Object[] dataFromExcel = DATA_TO_INSERT_INTO_DB
                        .get(getTableName(excelHeaderTable, columnName))
                        .get(innerConnection[0]);

                String[] innerConnectionColumnData = new String[]{
                        excelHeaderForeignKey.get(innerConnection[0])[0],
                        excelHeaderForeignKey.get(innerConnection[0])[2],
                        excelHeaderForeignKey.get(innerConnection[0])[1],
                        dataFromExcel[0].toString(),
                        dataFromExcel[1].toString()
                };

                String[] columnData = new String[]{
                        foreignKeyInfo[0],
                        foreignKeyInfo[1],
                        innerConnection[2],
                        foreignKeyInfo[2],
                        data[0].toString(),
                        data[1].toString()
                };

                newData = DB_CONNECTION.getPKFromFTWithInnerConnection(innerConnectionColumnData, columnData);                  // CHECK IF THE FK EXISTS IN THE FK TABLE

                if (newData[0] == 0) {                                                                                          // [IF] DOES NOT EXIST

                    createNewRegistryInForeignTable(                                                                                // CREATES A NEW REGISTRY IN FK TABLE
                            config,
                            innerConnection,
                            excelHeaderTable,
                            foreignKeyInfo,
                            data,
                            columnName,
                            true
                    );

                    newData = DB_CONNECTION.getPKFromFTWithInnerConnection(innerConnectionColumnData, columnData);
                }

                infoToInsert = new Object[]{Types.INTEGER, newData[1]};
            }
        } else if (foreignKeyInfo != null) {                                                                                // [ELSE-IF] EXISTS ONLY A FK

            newData = DB_CONNECTION                                                                                         // CHECK IF THE FK EXISTS IN THE FK TABLE
                    .getPKFromFTWithoutInnerConnection(data[1],
                            foreignKeyInfo[0],
                            foreignKeyInfo[1],
                            foreignKeyInfo[2]
                    );

            if (newData[0] == 0) {                                                                                              // [IF] DOES NOT EXIST

                createNewRegistryInForeignTable(                                                                                    // CREATES A NEW REGISTRY IN FK TABLE
                        config,
                        null,
                        excelHeaderTable,
                        foreignKeyInfo,
                        data,
                        columnName,
                        false
                );

                newData = DB_CONNECTION.getPKFromFTWithoutInnerConnection(data[1],
                        foreignKeyInfo[0],
                        foreignKeyInfo[1],
                        foreignKeyInfo[2]
                );

            }

            infoToInsert = new Object[]{Types.INTEGER, newData[1]};

        } else {                                                                                                            // [ELSE] DOES NOT EXIST ANY OF THEM

            infoToInsert = new Object[]{
                    data[0],
                    data[1]
            };
        }

        return infoToInsert;
    }

    private void createNewRegistryInForeignTable(
            Map<String, Data[]> config,
            String[] innerConnection,
            List<TableNameExcelData> excelHeaderTable,
            String[] foreignKeyInfo,
            Object[] data,
            String columnName,
            boolean hasInnerConnections
    ) {
        Map<String, Object[]> FOREIGN_KEY_INSERT_NEW_REGISTRY = new LinkedHashMap<>();
        List<Data> foreignKeyDataFromConfig = null;

        if (config.get(foreignKeyInfo[0]) != null) {                                                                        // [IF] CHECK THERE'S INFO IN THE CONFIG FILE
            foreignKeyDataFromConfig = new ArrayList<>(Arrays.stream(config.get(foreignKeyInfo[0])).toList());                  // ADDS IT
        }

        if (hasInnerConnections) {                                                                                          // [IF] THERE'S INNER_CONNECTIONS
            Object[] infoToInsert;

            if ((infoToInsert = DATA_TO_INSERT_INTO_DB                                                                          // [IF] INFO FROM THE OTHER COLUMNS ADDS IT
                    .get(innerConnection[1])
                    .get(innerConnection[0])) != null) {

                FOREIGN_KEY_INSERT_NEW_REGISTRY.put(
                        innerConnection[2],
                        infoToInsert
                );

            } else {                                                                                                            // [ELSE] THERE'S NO DATA FROM IT, ERROR IS THROWN, BAD ORDER IN THE EXCEL FILE
                        System.out.println("[ERROR] Revise el orden del excel porque" +
                                " no se pudo obtener el dato de la columna" + columnName);
                        System.exit(1);
            }

            if (foreignKeyDataFromConfig != null && !foreignKeyDataFromConfig.isEmpty()) {                                      // IT ERASE IT FROM CONFIG FILE LIST DATA
                eraseFromConfigList(foreignKeyDataFromConfig, innerConnection[2]);
            }
        }


        FOREIGN_KEY_INSERT_NEW_REGISTRY.put(foreignKeyInfo[2],                                                              // ADDS DATA
                new Object[]{
                        data[0],
                        data[1]
                });

        try {
            if (foreignKeyDataFromConfig != null) {
                eraseFromConfigList(foreignKeyDataFromConfig, foreignKeyInfo[2]);                                           // IT TRIES TO ERASE IT FROM CONFIG FILE LIST DATA
            }
        } catch (Exception e) {
            System.out.println("[ERROR]: No se eliminó ningún valor de la lista para" +
                    " la creación de un nuevo registro en tabla foránea");
        }

        if (foreignKeyDataFromConfig != null && !foreignKeyDataFromConfig.isEmpty()) {
            addLastInfoFromConfig(FOREIGN_KEY_INSERT_NEW_REGISTRY, foreignKeyDataFromConfig);                               // ADDS THE DATA FROM CONFIG TABLE THAT WASN'T DELETED
        }

        createNewRegistry(foreignKeyInfo[0], FOREIGN_KEY_INSERT_NEW_REGISTRY);                                              // CREATES NEW REGISTRY
    }

    private String getTableName(List<TableNameExcelData> list, String columnName) {
        TableNameExcelData table = list.stream()
                .filter(tableNameExcelData ->
                        Objects.equals(tableNameExcelData.getColumnName(), columnName))
                .findFirst()
                .orElse(null);

        if (table == null) {
            System.out.println("[ERROR] No se ha encontrado la tabla para la columna " + columnName);
            System.exit(1);
        }

        return table.getTableName();
    }

    private void eraseFromTableList(List<TableNameExcelData> tableList, String tableName, String columnName) {
        tableList.remove(tableList.stream()
                .filter(table -> table.getTableName().equals(tableName)
                        && table.getColumnName().equals(columnName))
                .findFirst()
                .orElse(null)
        );
    }

    private void createNewRegistry(String tableName, Map<String, Object[]> dataForNewRegistry) {
        Map<String, IncreaseData> increaseDataMap = REPOSITORY.getDEFAULT_INCREASE_FROM_CONFIG();
        IncreaseData increaseData;
        if ((increaseData = increaseDataMap.get(tableName)) != null) {
            int[] auxDataFromIncreaseTable = getIncrementData(increaseData);
            if (auxDataFromIncreaseTable[0] != 0) {
                dataForNewRegistry.put(increaseData.getColumn(), new Object[]{
                        Types.INTEGER,
                        auxDataFromIncreaseTable[1],
                });
            }
        }

        DB_CONNECTION.insertNewRegistry(tableName, dataForNewRegistry);
    }

    private int[] getIncrementData(IncreaseData increaseData) {
        int[] incrementData = new int[]{0, -1};
        if ((increaseData.getType()).equals("str")) {
            // ********************** EMPTY **************************
        } else {
            if (DB_CONNECTION.setIncreaseThroughInt((Integer) increaseData.getData()))                                      // UPDATE (boolean)
                incrementData = DB_CONNECTION.getIncreaseCodeInDB((Integer) increaseData.getData());                        // SELECT
        }
        return incrementData;
    }

    private void eraseFromConfigList(List<Data> list, String nameDataToErase) {
        list.remove(list.stream()
                .filter(data -> data.getName().equals(nameDataToErase))
                .findFirst()
                .orElse(null)
        );
    }

    private void addLastInfoFromConfig(Map<String, Object[]> insertionMap, List<Data> list) {
        for (Data data : list) {
            if (data.getData() != null) {
                insertionMap.put(data.getName(), new Object[]{
                        data.getType(),
                        data.getData()
                });
            }
        }
    }
}
