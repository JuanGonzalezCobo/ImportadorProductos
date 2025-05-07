package data.repository;

import data.model.*;
import data.service.config.ClassType;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static java.time.LocalTime.now;

@lombok.Data
public class Repository {

    //*************************************************************************
    //* ARGUMENTS                                                             *
    //*************************************************************************

    //***************
    //* CONFIG FILE *
    //***************

    private final Map<String, Object> SECTIONS_CONFIG;

    private Map<String, IncreaseData> DEFAULT_INCREASE_FROM_CONFIG;
    private Map<String, Data[]> DEFAULT_TABLES_DATA_FROM_CONFIG;

    //***************
    //* EXCEL FILE  *
    //***************

    private List<String> COLUMNS_EXCEL;

    private Map<String, String[]> FOREIGN_KEY_HEADERS_FROM_EXCEL;       //Estos son aquellos que tienen un foreign key
    private Map<String, List<String[]>> INNER_DATA_HEADERS_FROM_EXCEL;  //Estos son aquellos que necesitan de otra columna para funcionar
    private Queue<Map<String, Object[]>> DATA_FROM_EXCEL;

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

    private Map<String, IncreaseData> setIncreaseConfig() {
        Map<String, IncreaseData> increaseConfig = new HashMap<>();
        for (IncreaseData increaseData : (IncreaseData[]) SECTIONS_CONFIG.get("Incrementos")) {
            Double auxData = (Double) increaseData.getData();
            increaseData.setData(auxData.intValue());
            increaseConfig.put(increaseData.getName(), increaseData);
        }
        return increaseConfig;
    }

    private Map<String, Data[]> setTablesConfig() {
        Map<String, Data[]> tablesConfig = new HashMap<>();

        for (TableData eachTableData : (TableData[]) SECTIONS_CONFIG.get("Tablas")) {
            for (Data eachColumn : eachTableData.getData()) {
                String type = (String) eachColumn.getType();
                int classType = ClassType.getClassType(type);
                // AÑADIR LA HORA DE AHORA
                if ((classType == 93) && eachColumn.getData() != null && eachColumn.getData().equals("AHORA()")) {
                    eachColumn.setData(LocalDateTime.now());
                }
                eachColumn.setType(ClassType.getClassType(type));
            }
            tablesConfig.put(eachTableData.getName(), eachTableData.getData());
        }
        System.out.println(LocalDateTime.now());
        return tablesConfig;
    }

    //***************
    //* GETTERS     *
    //***************

    public DbData[] getDataBaseConfig() {
        return (DbData[]) SECTIONS_CONFIG.get("DbConfig");
    }

}
