package data.service.config;

import com.google.gson.*;
import data.model.Data;
import data.model.DbData;
import data.model.Section;
import data.model.TableData;
import data.service.db.model.Column;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ConfigFileManager {

    private final String CONFIG_FILE_NAME = "config.json";

    public Map<String, Object> loadConfig() {
        Map<String, Object> configData = new HashMap<>();

        Gson gson = new Gson();
        JsonArray jsonArray = JsonParser.parseString(getJsonFromFile()).getAsJsonArray();

        for (JsonElement jsonElement : jsonArray) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String sectionName = jsonObject.get("name").getAsString();
            JsonElement dataElement = jsonObject.get("data");

            switch (sectionName) {
                case "DbConfig" -> configData.put(sectionName, gson.fromJson(dataElement, DbData[].class));
                case "Incrementos" -> configData.put(sectionName, gson.fromJson(dataElement, Data[].class));
                case "Tablas" -> configData.put(sectionName, gson.fromJson(dataElement, TableData[].class));
            }
        }
        return configData;
    }

    private String getJsonFromFile() {
        StringBuilder json = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(CONFIG_FILE_NAME))) {
            String line;
            while ((line = br.readLine()) != null) {
                json.append(line);
            }
        } catch (FileNotFoundException e) {
            System.out.println("[ERROR] No se pudo encontrar el archivo: " + CONFIG_FILE_NAME);
        } catch (IOException e) {
            System.out.println("[ERROR] No se pudo leer el archivo: " + CONFIG_FILE_NAME);
        }

        return json.toString();
    }

    public Void unloadConfig(DbData[] dataBaseConfig, Data[] increaseConfig, String tableName, Column[] columnData) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONFIG_FILE_NAME))) {
            writer.write(createNewSection(dataBaseConfig, increaseConfig, tableName, columnData));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private String createNewSection(DbData[] dataBaseConfig, Data[] increaseConfig, String tableName, Column[] columnData) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        List<Section> sectionList = new ArrayList<>();

        //Añado lo necesario para crear el config, solamente la configuración de la base de datos y los incrementos

        sectionList.add(new Section("DbConfig", dataBaseConfig));
        sectionList.add(new Section("Incrementos", increaseConfig));

        List<Data> newData = new ArrayList<>();

        for (Column column : columnData) {
            newData.add(
                    new Data(column.getColumnName(),
                            ClassType.setClassType(Integer.parseInt(column.getType())),
                            null
                    )
            );
        }

        sectionList
                .add(new Section(
                        "Tablas",
                        new Data[]{
                                new Data(tableName,
                                        null,
                                        newData.toArray(Data[]::new))
                        })
                );

        return gson.toJson(sectionList.toArray(Section[]::new));
    }
}
