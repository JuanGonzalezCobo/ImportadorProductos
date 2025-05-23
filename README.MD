# IMPORTADOR DE EXCEL

## Índice

1. [Estructura del programa](#estructura)
    + [Main](#main)
    + app
        - [App.java](#appjava)
        - [AppConfig.java](#appconfigjava)
        - [AppConsoleStyle.java](#appconsolestylejava)
        - [AppState.java](#appstatejava)
        - threads
            * [ProgressBar.java](#progressbarjava)
    + data
        - model
            * [TableAndColumnNameExcelData.java](#tableandcolumnnameexceldatajava)
            * [DbData.java](#dbdatajava)
            * [IncreaseData.java](#increasedatajava)
            * [TableData.java](#tabledatajava)
            * [UpdateData.java](#updatedatajava)
            * [Data.java](#datajava)
        - repository
            * [Repository.java](#repositoryjava)
        - service
            * config
                + [ClassType.java](#classtypejava)
                + [ConfigFileManager.java](#configfilemanagerjava)
            * db
                + [DataBaseConnection.java](#databaseconnectionjava)
                + model
                    + [Column.java](#columnjava)
            * excel
                + [ExcelFileManager.java](#excelfilemanagerjava)
    + domain
        - [MainController.java](#maincontrollerjava)

2. [Información de interés](#información-de-interés)

## Estructura

```
./
│    Main.java
│    
├─── app
│     │   App.java
│     │   AppConfig.java
│     │   AppConsoleStyle.java
│     │   AppState.java
│     │   
│     └─── threads
│              ProgressBar.java
│              WriteOnFileThread.java
│            
├─── data
│      ├─── model
│      │        TableAndColumnNameExcelData.java
│      │        DbData.java
│      │        IncreaseData.java
│      │        TableData.java
│      │        UpdateData.java
│      │        Data.java
│      │ 
│      ├─── repository
│      │        Repository.java
│      │ 
│      └─── service
│              ├─── config
│              │        ClassType.java
│              │        ConfigFileManager.java
│              │ 
│              ├─── db
│              │    │   DataBaseConnection.java
│              │    │
│              │    └─── model
│              │             Column.java
│              │ 
│              └─── excel
│                       ExcelFileManager.java
│ 
└─── domain
        MainController.java
```

### Main

Se trata del main del programa, el iniciador de la aplicación

```
public class Main {
    public static void main(String[] args) {
        AppConfig appConfig = new AppConfig();
        AppState appState = new AppState();

        new App(appConfig, appState);
    }
}
```

### App.java

Clase usada para la elección de la ejecución de la aplicación


El constructor de la clase es el que nos permite la elección e inicia la instancia 
del [MainController.java](#maincontrollerjava) 
```
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
```

Luego tenemos una clase privada que es el filtro para lo que el usuario escribe por teclado
```
private int appPath(String response) throws NumberFormatException {
    int intResponse;
    intResponse = Integer.parseInt(response.trim());

    if (Arrays.stream(CHOOSABLE_OPTIONS)
            .allMatch(value -> intResponse != value))
        throw new NumberFormatException();

    return intResponse;
}
```

### AppConfig.java

En esta clase tenemos la configuración de la Base de Datos

```
public String URL;
public String DATABASE_NAME;
public String USER;
public String PASSWORD = "*******";
public String CHARSET;

public void setDatabaseConfig(DbData[] data) {
    URL = data[0].getData();
    DATABASE_NAME = data[1].getData();
    USER = data[2].getData();
    String password = data[3].getData();
    if (!password.isEmpty()) PASSWORD = password;
    String charset = data[4].getData();
    if (!charset.isEmpty()) CHARSET = charset;
}
```

### AppConsoleStyle.java

Aquí tenemos los colores que podemos poner en el cmd, además los menús que aparecen en el cmd

### AppState.java

No hay nada funcional realmente, hace pegote, pero a futuro se puede usar

### ProgressBar.java

Es la clase que le da vida a la barra de progreso cuando se están insertando o actualizando registros

Imprime por pantalla la barra de progreso.
```
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
```

Actualiza la barra y la enseña por pantalla
```
public void refresh(int newProgress) {
    this.PROGRESS = Math.min(newProgress, MAX);
    showBar();
}
```

### TableAndColumnNameExcelData.java

Se trata de una DataClass* usado para los headers de los Excels (la fila con otro color), que recoge la información
para parsearla y convertirla en objeto, con formato -> _TABLA.COLUMNA.NÚMERO_

```
public class TableAndColumnNameExcelData {
    private String columnName, tableName;
    private int columnNumber;
}
```

### DbData.java

Se trata de una DataClass* usada para recoger la información sobre la Base de Datos del archivo
de configuración -> config.json
```
public class DbData {
    private String name, data;
}
```

### IncreaseData.java

Se trata de una DataClass* usada para recoger la información de la Tabla INCREMENTOS de la Base de Datos del 
archivo de configuración -> config.json

### TableData.java

### UpdateData.java

### Data.java

### Repository.java

### ClassType.java

### ConfigFileManager.java

### DataBaseConnection.java

### Column.java

### ExcelFileManager.java

### MainController.java

## Información de interés
* **DataClass**: es una clase que sirve como modelo para recoger información y convertirla en un objeto para una facilidad de uso
* **Lombok**: se trata de una librería para Java que mediante comentarios @, se puede omitir código, como constructores,
getters y setters... Muy útil para proyectos grandes.