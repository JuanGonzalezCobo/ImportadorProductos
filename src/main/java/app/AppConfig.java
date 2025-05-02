package app;

import data.model.DbData;

public class AppConfig {
    //*************************************************************************
    //* DATABASE                                                              *
    //*************************************************************************
    public String URL;
    public String DATABASE_NAME;
    public String USER;
    public String PASSWORD = "is2003";

    public void setDatabaseConfig(DbData[] data) {
        URL = data[0].getData();
        DATABASE_NAME = data[1].getData();
        USER = data[2].getData();
        String password = data[3].getData();
        if (!password.isEmpty()) PASSWORD = password;
    }
}
