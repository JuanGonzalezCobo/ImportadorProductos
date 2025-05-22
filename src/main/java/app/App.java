package app;

import domain.MainController;

import java.util.Arrays;
import java.util.Scanner;

public class App {
    private final AppConfig CONFIG;
    private final AppState STATE;
    private final Scanner SCANNER;

    private final int[] CHOOSABLE_OPTIONS = new int[] { 1, 2 };

    private final String MENU_PROMPT = """
            ----------------------------------------------------------------
                                          Menú
            ----------------------------------------------------------------
            1 > Insertar Excel en la Base de Datos
            2 > Salir

            ~>""";

    private final String MAIN_PROMPT = """
            ****************************************************************
            *                 IMPORTADOR DE BASES DE DATOS                 *
            ****************************************************************
            %s""";

    private final String ERROR_PROMPT = """
            [ERROR] No ha introducido una opción válida prueba [1,2]
            ~>""";

    public App(AppConfig config, AppState state) {
        this.CONFIG = config;
        this.STATE = state;
        this.SCANNER = new Scanner(System.in);
        System.out.printf(MAIN_PROMPT, MENU_PROMPT);
        int option = 0;

        while (option != 2) {
            if (option == 1) System.out.print(MENU_PROMPT);
            try {
                if ((option = appPath(SCANNER.nextLine())) == 1) {
                    new MainController(STATE, CONFIG);
                } else {
                    System.out.println("== SALIENDO DEL PROGRAMA ==");
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

        if (Arrays.stream(CHOOSABLE_OPTIONS)
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
