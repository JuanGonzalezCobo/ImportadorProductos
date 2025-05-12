package domain;

import app.App;
import app.AppConfig;
import app.AppState;
import data.model.Data;
import data.model.IncreaseData;
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

        List<String> excelAllTablesHeaders;

        Map<String, String> excelHeaderTable = REPOSITORY.getTABLE_HEADERS_FROM_EXCEL();
        Map<String, String[]> excelHeaderForeignKey = REPOSITORY.getFOREIGN_KEY_HEADERS_FROM_EXCEL();
        Map<String, List<String[]>> excelHeaderInnerConnections = REPOSITORY.getINNER_DATA_HEADERS_FROM_EXCEL();
        Map<String, Data[]> config = REPOSITORY.getDEFAULT_TABLES_DATA_FROM_CONFIG();

        Map<String, List<Data>> dataPerTableConfig = new LinkedHashMap<>();

        String tableName = null;

        while ((dataRow = REPOSITORY.getDATA_FROM_EXCEL().poll()) != null) {

            // INITIALIZATION OF REQUIRED COMPONENTS
            excelAllTablesHeaders = REPOSITORY.getALL_TABLES_EXCEL();

            DATA_TO_INSERT_INTO_DB.clear();
            for (String table : excelAllTablesHeaders) {
                DATA_TO_INSERT_INTO_DB.put(table, new LinkedHashMap<>());
            }

            for (String tablePerExcelColumns : excelAllTablesHeaders) {
                if (config.get(tablePerExcelColumns) != null) {
                    dataPerTableConfig.put(tablePerExcelColumns, Arrays.stream(config.get(tablePerExcelColumns)).toList());
                }
            }

            for (String columnName : REPOSITORY.getCOLUMNS_EXCEL()) {
                String lastestTableName = tableName;
                tableName = excelHeaderTable.get(columnName);

                if (lastestTableName != null && !lastestTableName.equals(tableName)) {
                    if (!DATA_TO_INSERT_INTO_DB.get(lastestTableName).isEmpty()) {
                        if (config.get(lastestTableName) != null) {
                            Map<String, Object[]> dataToInsertAux = new HashMap<>(DATA_TO_INSERT_INTO_DB.get(lastestTableName));
                            addLastInfoFromConfig(dataToInsertAux, dataPerTableConfig.get(lastestTableName));
                            DATA_TO_INSERT_INTO_DB.put(lastestTableName, dataToInsertAux);
                            dataPerTableConfig.remove(lastestTableName);
                        }
                        if (!DB_CONNECTION.checkIfAlreadyExists(lastestTableName, DATA_TO_INSERT_INTO_DB.get(lastestTableName)))
                            createNewRegistry(lastestTableName, DATA_TO_INSERT_INTO_DB.get(lastestTableName));
                    }
                }

                Object[] infoToInsert;

                if (dataRow.get(columnName) == null) {  //  ESTÁ VACÍO EL CAMPO EN EL EXCEL
                    Data columnInfoFromConfig = null;
                    // PRIMERO BUSCAMOS EN EL FILE_CONFIG
                    if (dataPerTableConfig.get(tableName) != null && !dataPerTableConfig.get(tableName).isEmpty()) {
                        columnInfoFromConfig = dataPerTableConfig.get(tableName).stream()
                                .filter(data -> data.getName().equals(columnName))
                                .findFirst()
                                .orElse(null);
                    }

                    // SI NO ENCUENTRA NADA, ENTONCES CONTINUA CON LA SIGUIENTE COLUMNA DEL EXCEL.
                    if (columnInfoFromConfig == null || columnInfoFromConfig.getData() == null) continue;

                    // SI ENCUENTRA QUE SE AÑADA
                    infoToInsert = createDataToInsert(
                            config,
                            excelHeaderForeignKey,
                            excelHeaderInnerConnections,
                            excelHeaderTable,
                            columnName,
                            new Object[]{columnInfoFromConfig.getType(), columnInfoFromConfig.getData()}
                    );

                } else { // NO ESTA VACÍO EL CAMPO EN EL EXCEL
                    infoToInsert = createDataToInsert(
                            config,
                            excelHeaderForeignKey,
                            excelHeaderInnerConnections,
                            excelHeaderTable,
                            columnName,
                            dataRow.get(columnName)
                    );
                }
                // INSERTAMOS EN EL MAPA PARA LUEGO CREAR EL NUEVO REGISTRO
                DATA_TO_INSERT_INTO_DB.get(tableName).put(columnName, infoToInsert);

                // BORRAMOS EL VALOR QUE ESTÁ DENTRO DE LA LISTA DE CONFIGURACIÓN PARA QUE NO HAYA REPETICIONES
                try {
                    List<Data> modifiedList;
                    if (dataPerTableConfig.get(tableName) != null) {
                        modifiedList = new ArrayList<>(dataPerTableConfig.get(tableName));
                        eraseFromConfigList(modifiedList, columnName);
                        dataPerTableConfig.put(tableName, modifiedList);
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage()); // CAMBIAR CODIGO
                }
            }
            /*
                AL FINAL SE COMPRUEBA EL ARCHIVO DE CONFIGURACION, QUE NO TENGA MÁS COSAS,
                SI NO ES EL CASO, QUE TERMINE POR IMPORTANDOLO PARA PONERLO EN EL INSERT DE LA DB
            */
            if (!DATA_TO_INSERT_INTO_DB.get(tableName).isEmpty()) {
                if (dataPerTableConfig.get(tableName) != null && !dataPerTableConfig.get(tableName).isEmpty()) {
                    Map<String, Object[]> dataToInsertAux = DATA_TO_INSERT_INTO_DB.get(tableName);
                    addLastInfoFromConfig(dataToInsertAux, dataPerTableConfig.get(tableName));
                    DATA_TO_INSERT_INTO_DB.put(tableName, dataToInsertAux);
                }
                // CREAMOS EL NUEVO REGISTRO EN LA BASE DE DATOS
                if (!DB_CONNECTION.checkIfAlreadyExists(tableName, DATA_TO_INSERT_INTO_DB.get(tableName)))
                    createNewRegistry(tableName, DATA_TO_INSERT_INTO_DB.get(tableName));
            }
        }
    }

    private Object[] createDataToInsert(
            Map<String, Data[]> config,
            Map<String, String[]> excelHeaderForeignKey,
            Map<String, List<String[]>> excelHeaderInnerConnections,
            Map<String, String> excelHeaderTable,
            String columnName,
            Object[] data
    ) {
        Object[] infoToInsert;
        String[] foreignKeyInfo;
        if ((foreignKeyInfo = excelHeaderForeignKey.get(columnName)) != null) { // EXISTE UNA FOREIGN KEY EN EL EXCEL

            //COMPROBAMOS QUE EXISTE EN LA TABLA REFERENTE EN LA BASE DE DATOS.
            int[] newData = DB_CONNECTION
                    .getIdentifierColumn(data[1],
                            foreignKeyInfo[0],
                            foreignKeyInfo[1],
                            foreignKeyInfo[2]
                    );

            if (newData[0] == 0) {  // NO EXISTE
                createNewRegistryInForeignTable(config,
                        excelHeaderInnerConnections,
                        excelHeaderTable,
                        foreignKeyInfo,
                        data,
                        columnName);
                newData = DB_CONNECTION.getIdentifierColumn(data[1],
                        foreignKeyInfo[0],
                        foreignKeyInfo[1],
                        foreignKeyInfo[2]
                );

            }

            infoToInsert = new Object[]{Types.INTEGER, newData[1]};
        } else { // NO EXISTE LA FOREIGN KEY EN EL EXCEL
            // SE AÑADE SIMPLEMENTE
            infoToInsert = new Object[]{
                    data[0],
                    data[1]
            };
        }

        // TODO: AÑADIR AQUI LO DE LOS INNER_CONNECTIONS

        return infoToInsert;
    }

    private void createNewRegistryInForeignTable(
            Map<String, Data[]> config,
            Map<String, List<String[]>> excelHeaderInnerConnections,
            Map<String, String> excelHeaderTable,
            String[] foreignKeyInfo,
            Object[] data,
            String columnName
    ) {
        Map<String, Object[]> FOREIGN_KEY_INSERT_NEW_REGISTRY = new LinkedHashMap<>();
        List<String[]> innerConnections;
        List<Data> foreignKeyDataFromConfig = null;
        // COMPROBAMOS QUE EXISTA INFORMACION EN EL ARCHIVO DE CONFIGURACIÓN
        if (config.get(foreignKeyInfo[0]) != null) {
            // AÑADIMOS LO NECESARIO DEL ARCHIVO DE CONFIGURACIÓN
            foreignKeyDataFromConfig = new ArrayList<>(Arrays.stream(config.get(foreignKeyInfo[0])).toList());
        }
        try {   //TODO: SACARLO A UN METODO DIFERENTE PARA PODER USARLO POR SI NO EXITE FK PERO SI INNER_CONNECTION
            //MIRAMOS QUE EXISTAN MÁS DATOS, LOS DEL INNER_CONNECTION PARA SER EXACTOS
            if ((innerConnections = excelHeaderInnerConnections.get(columnName)) != null) { // ESTO ES QUE HAYA INNER CONNECTIONS
                for (String[] innerConnection : innerConnections) {
                    Object[] infoToInsert;
                    if ((infoToInsert = DATA_TO_INSERT_INTO_DB
                            .get(excelHeaderTable.get(innerConnection[0]))
                            .get(innerConnection[0])) != null) {
                        // AÑADIMOS LOS INNER_CONNECTIONS
                        FOREIGN_KEY_INSERT_NEW_REGISTRY.put(
                                innerConnection[1],
                                infoToInsert
                        );

                    } else {
                        System.out.println("[ERROR] Revise el orden del excel porque no se pudo obtener el dato de la columna" + columnName);
                        System.exit(1);
                    }

                    if (foreignKeyDataFromConfig != null && !foreignKeyDataFromConfig.isEmpty()) {
                        eraseFromConfigList(foreignKeyDataFromConfig, innerConnection[1]);
                    }
                }
            }

            // AÑADIMOS EL DATO DEL EXCEL
            FOREIGN_KEY_INSERT_NEW_REGISTRY.put(foreignKeyInfo[2], new Object[]{
                    data[0],
                    data[1]
            });

            if (foreignKeyDataFromConfig != null) {
                eraseFromConfigList(foreignKeyDataFromConfig, foreignKeyInfo[2]);
            }
        } catch (Exception e) {
            System.out.println("[ERROR]: No se eliminó ningún valor de la lista para la creación de un nuevo registro en tabla foránea");
        }

        // AÑADIMOS LO QUE QUEDA DE LA LISTA DE CONFIGURACIÓN
        if (foreignKeyDataFromConfig != null && !foreignKeyDataFromConfig.isEmpty()) {
            addLastInfoFromConfig(FOREIGN_KEY_INSERT_NEW_REGISTRY, foreignKeyDataFromConfig);
        }

        createNewRegistry(foreignKeyInfo[0], FOREIGN_KEY_INSERT_NEW_REGISTRY);
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
            // DEJARLO PARA LUEGO
        } else {
            if (DB_CONNECTION.setIncreaseThroughInt((Integer) increaseData.getData()))                  // UPDATE (boolean)
                incrementData = DB_CONNECTION.getIncreaseCodeInDB((Integer) increaseData.getData());    // SELECT
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
