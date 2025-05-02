package app;

import domain.FilesSetterController;
import domain.MainController;

import java.util.Arrays;
import java.util.Scanner;

public class App {
    private final AppConfig CONFIG;
    private final AppState STATE;
    private final Scanner SCANNER;

    private final int[] choosableOptions = new int[] { 1, 2, 3 };

    private final String MENU_PROMPT = """
            ----------------------------------------------------------------
                                          Menú
            ----------------------------------------------------------------
            1 > Crear el archivo config.json y el Excel correspondiente
            2 > Insertar Excel en la base de datos
            3 > Salir

            ~>""";

    private final String MAIN_PROMPT = """
            ****************************************************************
            *                 IMPORTADOR DE BASES DE DATOS                 *
            ****************************************************************
            %s""";

    private final String ERROR_PROMPT = """
            [ERROR] No ha introducido una opción válida prueba [1,2,3]
            ~>""";

    public App(AppConfig config, AppState state) {
        this.CONFIG = config;
        this.STATE = state;
        this.SCANNER = new Scanner(System.in);
        System.out.printf(MAIN_PROMPT, MENU_PROMPT);
        int option = 0;

        while (option != 3) {
            if (option == 1 || option == 2) System.out.print(MENU_PROMPT);
            try {
                switch (option = appPath(SCANNER.nextLine())) {
                    case 1 -> new FilesSetterController(STATE, CONFIG, SCANNER);
                    case 2 -> new MainController(STATE, CONFIG, SCANNER);
                    default -> System.out.println("-- SALIENDO DEL PROGRAMA --");
                }

            } catch (NumberFormatException e) {
                System.out.print(ERROR_PROMPT);
            }
        }

        SCANNER.close();
    }

    private int appPath(String response) throws NumberFormatException {
        int intResponse;
        intResponse = Integer.parseInt(response.trim());

        if (Arrays.stream(choosableOptions)
                .allMatch(value -> intResponse != value))
            throw new NumberFormatException();

        return intResponse;
    }

    public static void clearConsole() {
        try {
            final String os = System.getProperty("os.name");

            if (os.contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("bash", "-c", "clear").inheritIO().start().waitFor();
            }
        } catch (Exception e) {
            for (int i = 0; i < 50; i++) {
                System.out.println();
            }
        }
    }
}
