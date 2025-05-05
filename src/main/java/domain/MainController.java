package domain;

import app.App;
import app.AppConfig;
import app.AppConsoleStyle;
import app.AppState;
import data.model.Data;
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

    private final Map<String, Object[]> DATA_TO_INSERT_INTO_DB = new HashMap<>();


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
        //String defaultDataFromConfigFile = SCANNER.nextLine();


        DB_CONNECTION.closeConnection();
        App.clearConsole();
    }

    //*************************************************************************
    //* FILL REPOSITORY                                                       *
    //*************************************************************************

    private void setRepositoryData() {
        List<List<Object>> dataFromExcel = EXCEL_FILE_MANAGER.readFile();
        REPOSITORY.setCOLUMNS_EXCEL(setExcelsColumn(dataFromExcel));
        REPOSITORY.setFOREIGN_KEY_HEADERS_FROM_EXCEL(setExcelsHeaderForeignKeys(dataFromExcel));
        REPOSITORY.setINNER_DATA_HEADERS_FROM_EXCEL(setExcelHeaderInnerConnections(dataFromExcel));
        REPOSITORY.setDATA_FROM_EXCEL(setExcelsData(dataFromExcel));
    }

    private List<String> setExcelsColumn(List<List<Object>> data) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < data.get(EXCEL_FILE_MANAGER.headersRow).size(); i++) {
            columns.add(data.get(EXCEL_FILE_MANAGER.headersRow).get(i).toString());
        }
        return columns;
    }

    private Map<String, String[]> setExcelsHeaderForeignKeys(List<List<Object>> data) {
        Map<String, String[]> header = new HashMap<>();

        for (int i = 0; i < data.getFirst().size(); i++) {
            if (data.get(EXCEL_FILE_MANAGER.headersRow - 1).get(i) != null) {
                header.put((String) data.get(EXCEL_FILE_MANAGER.headersRow).get(i), new String[]{
                        (String) data.get(EXCEL_FILE_MANAGER.headersRow - 1).get(i),    // Tabla referenciada
                        (String) data.get(EXCEL_FILE_MANAGER.headersRow - 2).get(i),    // Columna de tabla referenciada
                        (String) data.get(EXCEL_FILE_MANAGER.headersRow - 3).get(i)     // Columna que iguala el valor de la columna del excel
                });
            }
        }
        return header;
    }

    private Map<String, List<String[]>> setExcelHeaderInnerConnections(List<List<Object>> data) {
        Map<String, List<String[]>> header = new HashMap<>();

        if (EXCEL_FILE_MANAGER.headersRow != 3) {
            for (int i = 0; i < data.getFirst().size(); i++) {
                List<String[]> innerConnections = new ArrayList<>();
                if (data.get(EXCEL_FILE_MANAGER.headersRow - 4).get(i) == null) continue;

                for (int j = 0; j < (EXCEL_FILE_MANAGER.headersRow - 3) / 2; j++) {
                    if (data.get(EXCEL_FILE_MANAGER.headersRow - 4 - j * 2).get(i) == null) continue;
                    innerConnections.add(new String[]{
                            (String) data.get(EXCEL_FILE_MANAGER.headersRow - 4 - j * 2).get(i), //Nombre de la columna en excel
                            (String) data.get(EXCEL_FILE_MANAGER.headersRow - 5 - j * 2).get(i)  //Nombre de la columna en la tabla referenciada
                            }
                    );
                }
                header.put((String) data.get(EXCEL_FILE_MANAGER.headersRow).get(i), innerConnections);

            }
        }
        return header;
    }

    private Queue<Map<String, Object>> setExcelsData(List<List<Object>> data) {
        Queue<Map<String, Object>> registryData = new LinkedList<>();
        if (data.size() > EXCEL_FILE_MANAGER.headersRow + 1) {
            for (int i = EXCEL_FILE_MANAGER.headersRow + 1; i < data.size(); i++) {
                Map<String, Object> dataRow = new HashMap<>();
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
        Map<String, Object> dataRow;



        while ((dataRow = REPOSITORY.getDATA_FROM_EXCEL().poll()) != null) {

            DATA_TO_INSERT_INTO_DB.clear();
            Map<String, String[]> excelHeaderForeignKey = REPOSITORY.getFOREIGN_KEY_HEADERS_FROM_EXCEL();
            Map<String, Data[]> config = REPOSITORY.getDEFAULT_TABLES_DATA_FROM_CONFIG();

            List<Data> dataPerTableConfig = new ArrayList<>(Arrays.stream(config.get(tableName)).toList());

            for (int i = 0; i < REPOSITORY.getCOLUMNS_EXCEL().size(); i++) {
                String columnName = REPOSITORY.getCOLUMNS_EXCEL().get(i);

                String[] headerPerExcelColumn = excelHeaderForeignKey.get(columnName);

                if (dataRow.get(columnName) == null) {

                    //ESTA VACÍO EL CAMPO EN EL EXCEL, BUSCAMOS EN EL CONFIG

                    Data columnInfoFromConfig = dataPerTableConfig.stream()
                            .filter(data -> data.getName().equals(columnName))
                            .findFirst()
                            .orElse(null);

                    if (columnInfoFromConfig == null) continue;

                    //Hacer aqui lo del incremento. Con un condicional
                    //Y se quita lo que tenemos abajo

                    /*************************************/
                    DATA_TO_INSERT_INTO_DB.put(columnName,
                            new Object[]{
                                    columnInfoFromConfig.getType(),
                                    columnInfoFromConfig.getData()
                            }
                    );
                    /*************************************/

                    // Aqui lo borramos para, al final, comprobar valores que tenga en la configuracion.
                    // TODO HAY QUE CAMBIARLO PARA QUE SI APARECE EN EL EXCEL SE BORRE TBN
                    dataPerTableConfig.remove(columnInfoFromConfig);

                } else {

                    if (headerPerExcelColumn[0] == null) {
                        DATA_TO_INSERT_INTO_DB.put(columnName, new Object[]{dataRow[i]});
                    } else {
                        int code;

                        code = foreignOperation(new Object[]{dataRow[i]},
                                headerPerExcelColumn[0],
                                headerPerExcelColumn[1],
                                headerPerExcelColumn[2]);

                        DATA_TO_INSERT_INTO_DB.put(columnName, new Object[]{Types.INTEGER, code});
                    }
                }
            }

            if (!dataPerTableConfig.isEmpty()) {
                for (Data dataPutInDB : dataPerTableConfig) {
                    DATA_TO_INSERT_INTO_DB.put(dataPutInDB.getName(), new Object[]{
                            dataPutInDB.getType(),
                            dataPutInDB.getData()
                    });
                }

            }
        }
    }
/*
    private int foreignOperation(Object[] data, String tableName, String pk, String columnName) {
        if (!DB_CONNECTION.dataExistsInForeignTable(tableName, columnName, data)) {

        }

        int[] newValue = DB_CONNECTION
                .getIdentifierColumn(data[0],
                        tableName, // Nombre de la tabla
                        pk,
                        columnName);
        if (newValue == null) System.exit(1);
        if (newValue[0] == 0) {
            System.out.println("0");
            System.exit(1);
        }
        return newValue[1];
    }

    private void increaseOperationThroughInt(int code) {
        int[] increaseNumber = DB_CONNECTION.increaseCodeTable(code);
    }

    private void increaseOperationThroughString() {

    }
    */


}
