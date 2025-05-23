package app.threads;

import app.AppConsoleStyle;

public class ProgressBar {
    private int PROGRESS;
    final private int MAX;
    final private int WIDTH;

    public ProgressBar(int max) {
        this.MAX = max;
        this.WIDTH = 100;
        this.PROGRESS = 0;
    }

    public void showBar() {
        double percentage = (double) PROGRESS / MAX * 100;
        int fullCharacters = (int) (WIDTH * PROGRESS / MAX);

        System.out.print("\r[");

        for (int i = 0; i < fullCharacters; i++) {
            System.out.print(AppConsoleStyle.YELLOW + "█");
        }

        for (int i = fullCharacters; i < WIDTH; i++) {
            System.out.print(AppConsoleStyle.RESET + "░");
        }

        System.out.printf(AppConsoleStyle.RESET + "] %.2f%% (%d/%d)", percentage, PROGRESS, MAX);

        if (PROGRESS >= MAX) {
            System.out.println(" ¡Completado!");
        }
    }

    public void refresh(int newProgress) {
        this.PROGRESS = Math.min(newProgress, MAX);
        showBar();
    }

}
