package data.service.config;

import app.AppConsoleStyle;
import com.google.gson.*;
import data.model.DbData;
import data.model.IncreaseData;
import data.model.TableData;
import data.model.UpdateData;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigFileManager {

    private final String CONFIG_FILE_NAME = "configuracion\\config.json";

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
                case "Incrementos" -> configData.put(sectionName, gson.fromJson(dataElement, IncreaseData[].class));
                case "Tablas" -> configData.put(sectionName, gson.fromJson(dataElement, TableData[].class));
                case "Actualización" -> configData.put(sectionName, gson.fromJson(dataElement, UpdateData[].class));
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
            System.out.println(AppConsoleStyle.RED
                    + "[ERROR] No se encontró el archivo " + CONFIG_FILE_NAME
                    + ".\n(Detalle): " + e.getMessage() + AppConsoleStyle.RESET);
            try {
                Thread.sleep(4000);
            } catch (InterruptedException q) {
                System.out.print(" ");
            }
            System.exit(2);
        } catch (IOException e) {
            System.out.println(AppConsoleStyle.RED
                    + "[ERROR] No se pudo leer el archivo " + CONFIG_FILE_NAME
                    + ".\n(Detalle): " + e.getMessage() + AppConsoleStyle.RESET);
            try {
                Thread.sleep(4000);
            } catch (InterruptedException q) {
                System.out.print(" ");
            }
            System.exit(2);
        }

        return json.toString();
    }
}
