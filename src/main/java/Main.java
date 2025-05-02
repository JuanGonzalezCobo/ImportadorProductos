import app.App;
import app.AppConfig;
import app.AppState;

public class Main {
    public static void main(String[] args) {
        AppConfig appConfig = new AppConfig();
        AppState appState = new AppState();

        new App(appConfig, appState);
    }
}
