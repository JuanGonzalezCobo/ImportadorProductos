package data.repository;

import data.model.Data;
import data.model.DbData;
import data.model.TableData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@lombok.Data
public class Repository {

    //*************************************************************************
    //* ARGUMENTS                                                             *
    //*************************************************************************

    //***************
    //* CONFIG FILE *
    //***************

    private final Map<String, Object> SECTIONS_CONFIG;

    private Map<String, Object[]> DEFAULT_INCREASE_FROM_CONFIG;
    private Map<String, Data[]> DEFAULT_TABLES_DATA_FROM_CONFIG;

    //***************
    //* EXCEL FILE  *
    //***************

    private List<String> COLUMNS_EXCEL;

    private Map<String, String[]> FOREIGN_KEY_HEADERS_FROM_EXCEL;       //Estos son aquellos que tienen un foreign key
    private Map<String, List<String[]>> INNER_DATA_HEADERS_FROM_EXCEL;  //Estos son aquellos que necesitan de otra columna para funcionar
    private Queue<Map<String, Object>> DATA_FROM_EXCEL;

    //*************************************************************************
    //* CONSTRUCTOR                                                           *
    //*************************************************************************


    public Repository(Map<String, Object> sectionsConfig) {
        this.SECTIONS_CONFIG = sectionsConfig;
        this.DEFAULT_INCREASE_FROM_CONFIG = setIncreaseConfig();
        this.DEFAULT_TABLES_DATA_FROM_CONFIG = setTablesConfig();

    }

    //***************
    //* SETTERS     *
    //***************

    private Map<String, Object[]> setIncreaseConfig() {
        Map<String, Object[]> increaseConfig = new HashMap<>();
        for (Data increaseData : (Data[]) SECTIONS_CONFIG.get("Incrementos")) {
            increaseConfig.put(increaseData.getName(), new Object[]{increaseData.getType(), increaseData.getData()});
        }
        return increaseConfig;
    }

    private Map<String, Data[]> setTablesConfig() {
        Map<String, Data[]> tablesConfig = new HashMap<>();

        for (TableData increaseData : (TableData[]) SECTIONS_CONFIG.get("Tablas")) {
            tablesConfig.put(increaseData.getName(), increaseData.getData());
        }

        return tablesConfig;
    }

    //***************
    //* GETTERS     *
    //***************

    public DbData[] getDataBaseConfig() {
        return (DbData[]) SECTIONS_CONFIG.get("DbConfig");
    }

}
