package app;

import domain.MainController;

import java.util.Arrays;
import java.util.Scanner;

public class App {
    private final AppConfig CONFIG;
    private final AppState STATE;
    private final Scanner SCANNER;

    private final int[] CHOOSABLE_OPTIONS = new int[] { 1, 2 };

    public App(AppConfig config, AppState state) {
        this.CONFIG = config;
        this.STATE = state;
        this.SCANNER = new Scanner(System.in);
        System.out.printf(AppConsoleStyle.MAIN_PROMPT, AppConsoleStyle.MENU_PROMPT);
        int option = 0;

        while (option != 2) {
            if (option == 1) System.out.print(AppConsoleStyle.MENU_PROMPT);
            try {
                if ((option = appPath(SCANNER.nextLine())) == 1) {
                    new MainController(STATE, CONFIG);
                } else {
                    System.out.println("== SALIENDO DEL PROGRAMA ==");
                }

            } catch (NumberFormatException e) {
                System.out.print(AppConsoleStyle.ERROR_PROMPT);
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
}
