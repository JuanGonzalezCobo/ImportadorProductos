package domain;

import app.App;
import app.AppConfig;
import app.AppConsoleStyle;
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
    private final Scanner SCANNER;

    private final Map<String, Object[]> DATA_TO_INSERT_INTO_DB = new LinkedHashMap<>();


    public MainController(AppState state, AppConfig config, Scanner scanner) {
        this.SCANNER = scanner;
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
        setRepositoryData();
        //String defaultTable = SCANNER.nextLine();

        mainInsertInDB("ARTICULO");
        DB_CONNECTION.closeConnection();
        App.clearConsole();
    }

    //*************************************************************************
    //* FILL REPOSITORY                                                       *
    //*************************************************************************

    private void setRepositoryData() {
        List<List<Object[]>> dataFromExcel = EXCEL_FILE_MANAGER.readFile();
        REPOSITORY.setCOLUMNS_EXCEL(setExcelsColumn(dataFromExcel));
        REPOSITORY.setFOREIGN_KEY_HEADERS_FROM_EXCEL(setExcelsHeaderForeignKeys(dataFromExcel));
        REPOSITORY.setINNER_DATA_HEADERS_FROM_EXCEL(setExcelHeaderInnerConnections(dataFromExcel));
        REPOSITORY.setDATA_FROM_EXCEL(setExcelsData(dataFromExcel));
    }

    private List<String> setExcelsColumn(List<List<Object[]>> data) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < data.get(EXCEL_FILE_MANAGER.headersRow).size(); i++) {
            columns.add((String) data.get(EXCEL_FILE_MANAGER.headersRow).get(i)[1]);
        }
        return columns;
        //
    }

    private Map<String, String[]> setExcelsHeaderForeignKeys(List<List<Object[]>> data) {
        Map<String, String[]> header = new HashMap<>();

        for (int i = 0; i < data.getFirst().size(); i++) {
            if (data.get(EXCEL_FILE_MANAGER.headersRow - 1).get(i) != null) {
                header.put((String) data.get(EXCEL_FILE_MANAGER.headersRow).get(i)[1], new String[]{
                        (String) data.get(EXCEL_FILE_MANAGER.headersRow - 1).get(i)[1],    // Tabla referenciada
                        (String) data.get(EXCEL_FILE_MANAGER.headersRow - 2).get(i)[1],    // Columna de tabla referenciada
                        (String) data.get(EXCEL_FILE_MANAGER.headersRow - 3).get(i)[1]     // Columna que iguala el valor de la columna del excel
                });
            }
        }
        return header;
    }

    private Map<String, List<String[]>> setExcelHeaderInnerConnections(List<List<Object[]>> data) {
        Map<String, List<String[]>> header = new HashMap<>();

        if (EXCEL_FILE_MANAGER.headersRow != 3) {
            for (int i = 0; i < data.getFirst().size(); i++) {
                List<String[]> innerConnections = new ArrayList<>();
                if (data.get(EXCEL_FILE_MANAGER.headersRow - 4).get(i) == null) continue;

                for (int j = 0; j < (EXCEL_FILE_MANAGER.headersRow - 3) / 2; j++) {
                    if (data.get(EXCEL_FILE_MANAGER.headersRow - 4 - j * 2).get(i) == null) continue;
                    innerConnections.add(new String[]{
                                    (String) data.get(EXCEL_FILE_MANAGER.headersRow - 4 - j * 2).get(i)[1], //Nombre de la columna en excel
                                    (String) data.get(EXCEL_FILE_MANAGER.headersRow - 5 - j * 2).get(i)[1]  //Nombre de la columna en la tabla referenciada
                            }
                    );
                }
                header.put((String) data.get(EXCEL_FILE_MANAGER.headersRow).get(i)[1], innerConnections);
            }
        }
        return header;
    }

    private Queue<Map<String, Object[]>> setExcelsData(List<List<Object[]>> data) {
        Queue<Map<String, Object[]>> registryData = new LinkedList<>();
        if (data.size() > EXCEL_FILE_MANAGER.headersRow + 1) {
            for (int i = EXCEL_FILE_MANAGER.headersRow + 1; i < data.size(); i++) {
                Map<String, Object[]> dataRow = new HashMap<>();
                for (int j = 0; j < data.get(i).size(); j++) {
                    dataRow.put(REPOSITORY.getCOLUMNS_EXCEL().get(j), data.get(i).get(j));
                }
                registryData.add(dataRow);
            }
        } else {
            System.out.println(AppConsoleStyle.RED + "[ERROR] No hay datos en el excel" + AppConsoleStyle.RESET);
            System.exit(1);
        }
        return registryData;
    }

    //*************************************************************************
    //* MAIN FUNCTIONALITY                                                    *
    //*************************************************************************

    private void mainInsertInDB(String tableName) {
        Map<String, Object[]> dataRow;

        Map<String, String[]> excelHeaderForeignKey = REPOSITORY.getFOREIGN_KEY_HEADERS_FROM_EXCEL();
        Map<String, List<String[]>> excelHeaderInnerConnections = REPOSITORY.getINNER_DATA_HEADERS_FROM_EXCEL();
        Map<String, Data[]> config = REPOSITORY.getDEFAULT_TABLES_DATA_FROM_CONFIG();

        List<Data> dataPerTableConfig;
        // TODO CREAR UNA CLASE QUE ME PERMITA HACER LO DE ANTES

        while ((dataRow = REPOSITORY.getDATA_FROM_EXCEL().poll()) != null) {

            DATA_TO_INSERT_INTO_DB.clear();
            dataPerTableConfig = new ArrayList<>(Arrays.stream(config.get(tableName)).toList());

            for (String columnName : REPOSITORY.getCOLUMNS_EXCEL()) {
                Object[] infoToInsert;

                if (dataRow.get(columnName) == null) {  //  ESTÁ VACÍO EL CAMPO EN EL EXCEL

                    // PRIMERO BUSCAMOS EN EL FILE_CONFIG
                    Data columnInfoFromConfig = dataPerTableConfig.stream()
                            .filter(data -> data.getName().equals(columnName))
                            .findFirst()
                            .orElse(null);

                    // SI NO ENCUENTRA NADA, ENTONCES CONTINUA CON LA SIGUIENTE COLUMNA DEL EXCEL.
                    if (columnInfoFromConfig == null || columnInfoFromConfig.getData() == null) continue;

                    // SI ENCUENTRA QUE SE AÑADA
                    infoToInsert = createDataToInsert(
                            config,
                            excelHeaderForeignKey,
                            excelHeaderInnerConnections,
                            columnName,
                            new Object[]{columnInfoFromConfig.getType(), columnInfoFromConfig.getData()}
                    );

                } else { // NO ESTA VACÍO EL CAMPO EN EL EXCEL
                    infoToInsert = createDataToInsert(
                            config,
                            excelHeaderForeignKey,
                            excelHeaderInnerConnections,
                            columnName,
                            dataRow.get(columnName)
                    );
                }
                // INSERTAMOS EN EL MAPA PARA LUEGO CREAR EL NUEVO REGISTRO
                DATA_TO_INSERT_INTO_DB.put(columnName, infoToInsert);

                // BORRAMOS EL VALOR QUE ESTÁ DENTRO DE LA LISTA DE CONFIGURACIÓN PARA QUE NO HAYA REPETICIONES
                try {
                    eraseFromConfigList(dataPerTableConfig, columnName);
                } catch (Exception e) {
                    System.out.println("[STATUS]: No se eliminó ningún valor en la tabla principal");
                }
            }
            /*
                AL FINAL SE COMPRUEBA EL ARCHIVO DE CONFIGURACION, QUE NO TENGA MÁS COSAS,
                SI NO ES EL CASO, QUE TERMINE POR IMPORTANDOLO PARA PONERLO EN EL INSERT DE LA DB
            */
            if (!dataPerTableConfig.isEmpty()) {
                addLastInfoFromConfig(DATA_TO_INSERT_INTO_DB, dataPerTableConfig);
            }

            // CREAMOS EL NUEVO REGISTRO EN LA BASE DE DATOS
            createNewRegistry(tableName, DATA_TO_INSERT_INTO_DB);
        }
    }

    private Object[] createDataToInsert(
            Map<String, Data[]> config,
            Map<String, String[]> excelHeaderForeignKey,
            Map<String, List<String[]>> excelHeaderInnerConnections,
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
                createNewRegistryInForeignTable(config, excelHeaderInnerConnections, foreignKeyInfo, data, columnName);
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

        return infoToInsert;
    }

    private void createNewRegistryInForeignTable(
            Map<String, Data[]> config,
            Map<String, List<String[]>> excelHeaderInnerConnections,
            String[] foreignKeyInfo,
            Object[] data,
            String columnName
    ) {
        Map<String, Object[]> FOREIGN_KEY_INSERT_NEW_REGISTRY = new LinkedHashMap<>();
        List<String[]> innerConnections;
        List<Data> foreignKeyDataFromConfig = null;
        // COMPROBAMOS QUE EXISTA INFORMACION EN EL ARCHIVO DE CONFIGURACIÓN
        if (config.get(foreignKeyInfo[0]) != null) {
            // AÑADIMOS LO NECESARIO DEL ARCHIVO DE CONFIGURACION
            foreignKeyDataFromConfig = new ArrayList<>(Arrays.stream(config.get(foreignKeyInfo[0])).toList());
        }
        try {
            //MIRAMOS QUE EXISTAN MÁS DATOS, LOS DEL INNER_CONNECTION PARA SER EXACTOS
            if ((innerConnections = excelHeaderInnerConnections.get(columnName)) != null) { // ESTO ES QUE HAYA INNER CONNECTIONS
                for (String[] innerConnection : innerConnections) {
                    Object[] infoToInsert;
                    if ((infoToInsert = DATA_TO_INSERT_INTO_DB.get(innerConnection[0])) != null) {
                        // AÑADIMOS LOS INNER_CONNECTIONS
                        FOREIGN_KEY_INSERT_NEW_REGISTRY.put(
                                innerConnection[1],
                                infoToInsert
                        );  // ESTO PUEDE DAR ERROR!!!!!

                    } else {
                        System.out.println("[ERROR] Revise el orden del excel porque no se pudo obtener el dato de la columna" + columnName);
                        System.exit(1);
                    }

                    if (foreignKeyDataFromConfig != null) {
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
            System.out.println("[STATUS]: No se eliminó ningún valor de la lista para la creación de un nuevo registro en tabla foránea");
        }

        // AÑADIMOS LO QUE QUEDA DE LA LISTA DE CONFIGURACIÓN
        if (foreignKeyDataFromConfig != null && !foreignKeyDataFromConfig.isEmpty()) {
            addLastInfoFromConfig(FOREIGN_KEY_INSERT_NEW_REGISTRY, foreignKeyDataFromConfig);
        }

        createNewRegistry(foreignKeyInfo[0], FOREIGN_KEY_INSERT_NEW_REGISTRY);
    }

    //ESTO FUNCIONA
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
            DB_CONNECTION.setIncreaseThroughInt((Integer) increaseData.getData());                  // UPDATE
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
