package domain;

import app.App;
import app.AppConfig;
import app.AppConsoleStyle;
import app.AppState;
import app.threads.ProgressBar;
import data.model.Data;
import data.model.IncreaseData;
import data.model.TableAndColumnNameExcelData;
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
    private Map<String, List<String>> pkPerTable;


    public MainController(AppState state, AppConfig config) {

        this.CONFIG_FILE_MANAGER = new ConfigFileManager();
        this.EXCEL_FILE_MANAGER = new ExcelFileManager();

        AppConsoleStyle.clearConsole();
        System.out.println(AppConsoleStyle.MAIN_CONTROLLER_INIT_PROMPT);

        this.REPOSITORY = new Repository(CONFIG_FILE_MANAGER, EXCEL_FILE_MANAGER);

        this.CONFIG = config;
        this.STATE = state;

        CONFIG.setDatabaseConfig(REPOSITORY.getDEFAULT_DB_FROM_CONFIG());

        this.DB_CONNECTION = new DataBaseConnection(getDataBaseInfo());

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
        try {
            REPOSITORY.setRepositoryData(EXCEL_FILE_MANAGER);
            REPOSITORY.setDATA_FROM_EXCEL(EXCEL_FILE_MANAGER
                    .processOfGettingParsedDataFromEstructureFile(REPOSITORY.getDATA_FROM_DATA_EXCEL()));

            pkPerTable = getPK();
            mainInsertInDB();

            DB_CONNECTION.closeConnection();
            EXCEL_FILE_MANAGER.closeStreamsFromEstructuralExcelFile();
            EXCEL_FILE_MANAGER.closeStreamsFromDataExcelFile();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            System.out.print(" ");
        }
        AppConsoleStyle.clearConsole();

    }

    //*************************************************************************
    //* MAIN FUNCTIONALITY                                                    *
    //*************************************************************************

    private void mainInsertInDB() {
        Map<TableAndColumnNameExcelData, Object[]> dataRow;

        List<String> excelAllTablesHeaders;                                                                                          // LISTA QUE VIENE DEL SET DONDE APARECEN TODAS LAS TABLAS
        Map<TableAndColumnNameExcelData, String[]> excelHeaderForeignKey = REPOSITORY.getFOREIGN_KEY_HEADERS_FROM_EXCEL();           // MAPA DE LAS FK, CON _KEYS_ COMO LAS COLUMNAS DEL EXCEL
        Map<TableAndColumnNameExcelData, Object[]> excelHeaderInnerConnections = REPOSITORY.getINNER_DATA_HEADERS_FROM_EXCEL();      // MAPA DE LOS INNER_CONNECTIONS, CON _KEYS_ COMO LAS COLUMNA

        Map<String, Data[]> tableConfig = REPOSITORY.getDEFAULT_TABLES_DATA_FROM_CONFIG();                                           // MAPA DE LOS DATOS DE LAS TABLAS EN EL ARCHIVO DE config.json
        Map<String, String[]> updateConfig = REPOSITORY.getDEFAULT_UPDATE_FROM_CONFIG();                                             // MAPA DE LOS DATOS DE LA COLUMNA IMPORTANTE POR TABLA EN EL ARCHIVO DE config.json PARA ACTUALIZAR
        Map<String, List<Data>> dataPerTableConfig = new LinkedHashMap<>();                                                          // MAPA DE LOS DATOS QUE HAY EN ARCHIVO config.json, CON _KEYS_ CO

        String tableName = null;                                                                                                     // STRING, NOMBRE DE LA TABLA POR COLUMNA
        int tableNumber = 0;

        int loopNumber = 0;
        int maxProgress = REPOSITORY.getDATA_FROM_EXCEL().size();
        ProgressBar progressBar = new ProgressBar(maxProgress);


        while ((dataRow = REPOSITORY.getDATA_FROM_EXCEL().poll()) != null) {

            //*************************************************
            //*  INITIALIZATION OF REQUIRED COMPONENTS        *
            //*************************************************

            DATA_TO_INSERT_INTO_DB.clear();

            excelAllTablesHeaders = REPOSITORY.getALL_TABLES_EXCEL();

            for (String table : excelAllTablesHeaders) {
                DATA_TO_INSERT_INTO_DB.put(table, new LinkedHashMap<>());
                if (tableConfig.get(table) != null) {
                    dataPerTableConfig.put(table, Arrays.stream(tableConfig.get(table)).toList());
                }
            }

            //*************************************************
            //*    LOOP FOR EACH COLUMN IN THE EXCEL FILE     *
            //*************************************************

            for (TableAndColumnNameExcelData column : REPOSITORY.getCOLUMN_AND_TABLE_FROM_ESTRUCTURE_EXCEL()) {

                //*************************************************
                //*    GETTING THE NAME OF THE COLUMN'S TABLE     *
                //*************************************************

                String lastestTableName = tableName;
                int lastestTableNumber = tableNumber;
                tableName = column.getTableName();
                tableNumber = column.getColumnNumber();

                //*************************************************
                //*    INSERT OF REGISTRY WITH SAME TABLE NAME    *
                //*************************************************

                if (lastestTableName != null
                        && (!lastestTableName.equals(tableName) || tableNumber != lastestTableNumber)
                        && !DATA_TO_INSERT_INTO_DB.get(lastestTableName).isEmpty()) {

                    updateOrCreateRegistryInDB(lastestTableName, updateConfig, tableConfig, dataPerTableConfig);
                }

                Object[] infoToInsert;

                if (dataRow.get(column) == null) {                                                                          // [IF] EMPTY EXCEL CELL
                    Data columnInfoFromConfig = null;

                    if (dataPerTableConfig.get(tableName) != null && !dataPerTableConfig.get(tableName).isEmpty()) {            // WE SEARCH IT ON CONFIG FILE
                        columnInfoFromConfig = dataPerTableConfig.get(tableName).stream()
                                .filter(data -> data.getName().equals(column.getColumnName()))
                                .findFirst()
                                .orElse(null);
                    }


                    if (columnInfoFromConfig == null || columnInfoFromConfig.getData() == null)                                 // [IF] THERE ISN'T DATA IN CONFIG FILE
                        continue;


                    infoToInsert = createDataToInsert(                                                                          // [ELSE] THERE'S DATA IN CONFIG FILE (ADDS IT)
                            tableConfig,
                            excelHeaderForeignKey,
                            excelHeaderInnerConnections,
                            REPOSITORY.getCOLUMN_AND_TABLE_FROM_ESTRUCTURE_EXCEL(),
                            column,
                            new Object[]{columnInfoFromConfig.getType(), columnInfoFromConfig.getData()}
                    );

                } else {                                                                                                    // [ELSE] NOT EMPTY CELL IN EXCEL
                    infoToInsert = createDataToInsert(
                            tableConfig,
                            excelHeaderForeignKey,
                            excelHeaderInnerConnections,
                            REPOSITORY.getCOLUMN_AND_TABLE_FROM_ESTRUCTURE_EXCEL(),
                            column,
                            dataRow.get(column)
                    );
                }

                //*************************************************
                //*        INSERT INTO THE INSERTION MAP          *
                //*************************************************

                DATA_TO_INSERT_INTO_DB.get(tableName).put(column.getColumnName(), infoToInsert);

                //*********************************************************
                //* DELETE VALUE FROM CONFIG LIST TO NOT GET DOUBLED DATA *
                //*********************************************************

                try {
                    List<Data> modifiedList;
                    if (dataPerTableConfig.get(tableName) != null) {
                        modifiedList = new ArrayList<>(dataPerTableConfig.get(tableName));
                        eraseFromConfigList(modifiedList, column.getColumnName());
                        dataPerTableConfig.put(tableName, modifiedList);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(AppConsoleStyle.RED
                            + "[ERROR] No se ha borrado de la configuración datos de la tabla "
                            + tableName + AppConsoleStyle.RESET);
                }

            }

            //*********************************************************
            //*  THIS ONLY HAPPENS WHEN IS THE LAST TABLE IN EXCEL,   *
            //*  IT ADDS THE DATA FROM CONFIG TABLE THAT WASN'T       *
            //*  DELETED, TO INSERT IT INTO THE INSERTION MAP         *
            //*********************************************************

            if (!DATA_TO_INSERT_INTO_DB.get(tableName).isEmpty()) {
                updateOrCreateRegistryInDB(tableName, updateConfig, tableConfig, dataPerTableConfig);
            }

            progressBar.refresh(loopNumber++);
        }
        progressBar.refresh(loopNumber + 1);
    }

    private Map<String, List<String>> getPK() {
        Map<String, List<String>> PKs = new HashMap<>();
        for (String table : REPOSITORY.getALL_TABLES_EXCEL()) {
            PKs.put(table, DB_CONNECTION.getPrimaryKeys(table));
        }
        return PKs;
    }


    private Object[] createDataToInsert(
            Map<String, Data[]> config,
            Map<TableAndColumnNameExcelData, String[]> excelHeaderForeignKey,
            Map<TableAndColumnNameExcelData, Object[]> excelHeaderInnerConnections,
            List<TableAndColumnNameExcelData> excelHeaderTable,
            TableAndColumnNameExcelData column,
            Object[] data
    ) {
        Object[] infoToInsert;
        String[] foreignKeyInfo;
        Object[] innerConnectionInfo;
        int[] newData;

        if ((foreignKeyInfo = excelHeaderForeignKey.get(column)) != null
                && (innerConnectionInfo = excelHeaderInnerConnections.get(column)) != null) {                               // [IF] EXISTS FK AND INNER_CONNECTIONS

            TableAndColumnNameExcelData tableAndColumnNameExcelDataFromInnerConnection = excelHeaderTable.stream()
                    .filter(tableAndColumnNameExcelData ->
                            tableAndColumnNameExcelData.equals(innerConnectionInfo[0]))
                    .findFirst()
                    .orElse(null);

            if (tableAndColumnNameExcelDataFromInnerConnection == null) {
                throw new RuntimeException(AppConsoleStyle.RED
                        + "[ERROR] No se ha encontrado la tabla para la columna "
                        + column.getColumnName() + AppConsoleStyle.RESET);
            }

                Object[] dataFromExcel = DATA_TO_INSERT_INTO_DB
                        .get(tableAndColumnNameExcelDataFromInnerConnection.getTableName())
                        .get(tableAndColumnNameExcelDataFromInnerConnection.getColumnName());

                String[] innerConnectionColumnData = new String[]{
                        excelHeaderForeignKey.get(tableAndColumnNameExcelDataFromInnerConnection)[0],
                        excelHeaderForeignKey.get(tableAndColumnNameExcelDataFromInnerConnection)[2],
                        excelHeaderForeignKey.get(tableAndColumnNameExcelDataFromInnerConnection)[1],
                        dataFromExcel[0].toString(),
                        dataFromExcel[1].toString()
                };

                String[] columnData = new String[]{
                        foreignKeyInfo[0],
                        foreignKeyInfo[1],
                        (String) innerConnectionInfo[1],
                        foreignKeyInfo[2],
                        data[0].toString(),
                        data[1].toString()
                };

                newData = DB_CONNECTION.getPKFromFTWithInnerConnection(innerConnectionColumnData, columnData);                  // CHECK IF THE FK EXISTS IN THE FK TABLE

                if (newData[0] == 0) {                                                                                          // [IF] DOES NOT EXIST

                    createNewRegistryInForeignTable(                                                                                // CREATES A NEW REGISTRY IN FK TABLE
                            config,
                            innerConnectionInfo,
                            foreignKeyInfo,
                            data,
                            column,
                            true
                    );

                    newData = DB_CONNECTION.getPKFromFTWithInnerConnection(innerConnectionColumnData, columnData);
                }

                infoToInsert = new Object[]{Types.INTEGER, newData[1]};


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
                        foreignKeyInfo,
                        data,
                        column,
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
            Object[] innerConnection,
            String[] foreignKeyInfo,
            Object[] data,
            TableAndColumnNameExcelData columnName,
            boolean hasInnerConnections
    ) {
        Map<String, Object[]> FOREIGN_KEY_INSERT_NEW_REGISTRY = new LinkedHashMap<>();
        List<Data> foreignKeyDataFromConfig = null;

        if (config.get(foreignKeyInfo[0]) != null) {                                                                        // [IF] CHECK THERE'S INFO IN THE CONFIG FILE
            foreignKeyDataFromConfig = new ArrayList<>(Arrays.stream(config.get(foreignKeyInfo[0])).toList());                  // ADDS IT
        }

        if (hasInnerConnections) {                                                                                          // [IF] THERE'S INNER_CONNECTIONS
            Object[] infoToInsert;

            TableAndColumnNameExcelData columnInfoFromInnerConnection = (TableAndColumnNameExcelData) innerConnection[0];


            if ((infoToInsert = DATA_TO_INSERT_INTO_DB                                                                          // [IF] INFO FROM THE OTHER COLUMNS ADDS IT
                    .get(columnInfoFromInnerConnection.getTableName())
                    .get(columnInfoFromInnerConnection.getColumnName())) != null) {

                FOREIGN_KEY_INSERT_NEW_REGISTRY.put(
                        (String) innerConnection[1],
                        infoToInsert
                );

            } else {                                                                                                            // [ELSE] THERE'S NO DATA FROM IT, ERROR IS THROWN, BAD ORDER IN THE EXCEL FILE
                System.out.println(AppConsoleStyle.RED
                        + "[ERROR] Revise el orden del excel porque no se pudo obtener el dato de la columna "
                        + columnName + AppConsoleStyle.RESET);
                System.exit(1);
            }

            if (foreignKeyDataFromConfig != null && !foreignKeyDataFromConfig.isEmpty()) {                                      // IT ERASE IT FROM CONFIG FILE LIST DATA
                eraseFromConfigList(foreignKeyDataFromConfig, (String) innerConnection[1]);
            }
        }

        FOREIGN_KEY_INSERT_NEW_REGISTRY.put(
                foreignKeyInfo[2],                                                                                          // ADDS DATA
                new Object[]{
                        data[0],
                        data[1]
                }
        );

        try {
            if (foreignKeyDataFromConfig != null) {
                eraseFromConfigList(foreignKeyDataFromConfig, foreignKeyInfo[2]);                                           // IT TRIES TO ERASE IT FROM CONFIG FILE LIST DATA
            }
        } catch (Exception e) {
            throw new RuntimeException(AppConsoleStyle.RED
                    + "[ERROR]: No se eliminó ningún valor de la lista para la creación de un nuevo registro en tabla foránea"
                    + AppConsoleStyle.RESET);
        }

        if (foreignKeyDataFromConfig != null && !foreignKeyDataFromConfig.isEmpty()) {
            addLastInfoFromConfigList(FOREIGN_KEY_INSERT_NEW_REGISTRY, foreignKeyDataFromConfig);                           // ADDS THE DATA FROM CONFIG TABLE THAT WASN'T DELETED
        }

        createNewRegistry(foreignKeyInfo[0], FOREIGN_KEY_INSERT_NEW_REGISTRY);                                              // CREATES NEW REGISTRY
    }

    private void updateOrCreateRegistryInDB(String tableName,
                                            Map<String, String[]> updateConfig,
                                            Map<String, Data[]> tableConfig,
                                            Map<String, List<Data>> dataPerTableConfig) {

        if (dataPerTableConfig.get(tableName) != null && !dataPerTableConfig.get(tableName).isEmpty()) {
            Map<String, Object[]> dataToInsertAux = DATA_TO_INSERT_INTO_DB.get(tableName);
            addLastInfoFromConfigList(dataToInsertAux, dataPerTableConfig.get(tableName));
            DATA_TO_INSERT_INTO_DB.put(tableName, dataToInsertAux);
            dataPerTableConfig.remove(tableName);
            // RENOVATE THE DATA FROM CONFIG IN CASE THERE ARE MORE THAN ONE KIND OF TABLE IN THE EXCEL
            dataPerTableConfig.put(tableName, Arrays.stream(tableConfig.get(tableName)).toList());
        }

            Map<String, Object[]> mapOfUpdatableColumns = new LinkedHashMap<>();
            String[] updateColumns;
            if ((updateColumns = updateConfig.get(tableName)) != null) {
                for (String updateColumn : updateColumns) {
                    Object[] dataFromExcel;
                    if ((dataFromExcel = DATA_TO_INSERT_INTO_DB.get(tableName).get(updateColumn)) != null) {
                        mapOfUpdatableColumns.put(updateColumn, dataFromExcel);
                    }
                }
            }

            if (DB_CONNECTION.registryExistsInDB(tableName, mapOfUpdatableColumns))
                updateRegistry(tableName, DATA_TO_INSERT_INTO_DB.get(tableName), mapOfUpdatableColumns);
            else
                createNewRegistry(tableName, DATA_TO_INSERT_INTO_DB.get(tableName));

    }

    private void updateRegistry(String tableName,
                                Map<String, Object[]> dataForUpdateRegistry,
                                Map<String, Object[]> whereValuesForUpdateRegistry) {
        for (String column : whereValuesForUpdateRegistry.keySet()) {
            dataForUpdateRegistry.remove(column);
        }

        DB_CONNECTION.updateRegistry(tableName, dataForUpdateRegistry, whereValuesForUpdateRegistry);
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

        List<String> tableFK;
        if ((tableFK = pkPerTable.get(tableName)) != null)
            for (String column : tableFK) {
                if (!dataForNewRegistry.containsKey(column)) return;
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

    private void addLastInfoFromConfigList(Map<String, Object[]> insertionMap, List<Data> list) {
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
