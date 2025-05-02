package domain;

import app.App;
import app.AppConfig;
import app.AppState;
import app.threads.WriteOnFileThread;
import data.model.Data;
import data.model.DbData;
import data.repository.Repository;
import data.service.config.ConfigFileManager;
import data.service.db.DataBaseConnection;
import data.service.db.model.Column;
import data.service.excel.ExcelFileManager;

import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

public class FilesSetterController {
    private final AppConfig CONFIG;
    private final AppState STATE;

    private final Repository REPOSITORY;
    private final ExcelFileManager EXCEL_FILE_MANAGER;
    private final ConfigFileManager CONFIG_FILE_MANAGER;
    private final DataBaseConnection DB_CONNECTION;
    private final Scanner SCANNER;


    public FilesSetterController(AppState state, AppConfig config, Scanner scanner) {
        this.SCANNER = scanner;
        this.CONFIG_FILE_MANAGER = new ConfigFileManager();
        App.clearConsole();
        System.out.println("""
        ----------------------------------------------------------------
                 CREACIÓN DE FICHEROS PARA EXCEL y config.json
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
        String tableName;
        System.out.println("\nIntroduzca el nombre de la tabla a la que le quiera crear los archivos");
        System.out.print("~> ");
        while (!DB_CONNECTION.tableExists(tableName = SCANNER.nextLine())) {
            System.out.println("La tabla que buscas no existe, pruebe con alguna de las siguientes:");
            System.out.println(Arrays.toString(DB_CONNECTION.compareTableNames(tableName)));
            System.out.print("~> ");
        }

        Column[] columnInfoFromTable = DB_CONNECTION.getAllColumns(tableName);

        String[] columnNames = Arrays.stream(columnInfoFromTable)
                .map(Column::getColumnName)
                .toArray(String[]::new);


        WriteOnFileThread[] threads = new WriteOnFileThread[]{
                new WriteOnFileThread(
                        new Object[]{
                                tableName,
                                setHeaderForExcel(
                                        columnNames,
                                        DB_CONNECTION.getAllForeignKeys(tableName))
                        },
                        (param) -> EXCEL_FILE_MANAGER.writeFile((String) param[0], (String[][]) param[1])
                ),
                new WriteOnFileThread(
                        new Object[]{
                                REPOSITORY.getSECTIONS_CONFIG().get("DbConfig"),
                                REPOSITORY.getSECTIONS_CONFIG().get("Incrementos"),
                                tableName,
                                columnInfoFromTable
                        },
                        (param) -> CONFIG_FILE_MANAGER
                                .unloadConfig((DbData[]) param[0],
                                        (Data[]) param[1],
                                        (String) param[2],
                                        (Column[]) param[3])
                ),
        };

        for (WriteOnFileThread thread : threads) {
            thread.start();
        }

        try {
            for (WriteOnFileThread thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            System.out.println("[ERROR] No se pudo interrumpir los hilos que escriben ficheros");
        }

        DB_CONNECTION.closeConnection();
        App.clearConsole();
    }

    private String[][] setHeaderForExcel(String[] columnNames, Map<String, String> foreignKeys) {
        String[][] header = new String[3][columnNames.length];
        String[] foreignKeyInfo;

        for (int i = 0; i < columnNames.length; i++) {
            header[2][i] = columnNames[i];
            if (foreignKeys.containsKey(header[2][i])) {
                foreignKeyInfo = foreignKeys.get(header[2][i]).split("\\.");
                header[0][i] = foreignKeyInfo[1];
                header[1][i] = foreignKeyInfo[0];
            }
        }
        return header;
    }


}
