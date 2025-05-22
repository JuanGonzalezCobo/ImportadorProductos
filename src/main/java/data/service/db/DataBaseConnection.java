package data.service.db;

import app.AppConsoleStyle;
import data.service.db.model.Column;
import lombok.Getter;

import java.sql.*;
import java.util.*;

@Getter
public class DataBaseConnection {

    private final Connection connection;

    private final Properties properties = new Properties();

    public DataBaseConnection(String[] data) {
        connection = getConnection(data[0], data[1], data[2], data[3], data[4]);
    }

    //*************************************************************************
    //* CONNECTION HANDLER                                                    *
    //*************************************************************************

    private Connection getConnection(String url, String database, String user, String password, String charset) {
        Connection con = null;
        try {
            properties.setProperty("user", user);
            properties.setProperty("password", password);
            properties.setProperty("lc_ctype", charset);

            con = DriverManager
                    .getConnection(url.concat(database), properties);
            System.out.println(AppConsoleStyle.GREEN
                    + "[OK] Conexión exitosa a la base de datos."
                    + AppConsoleStyle.RESET);
        } catch (SQLException e) {
            System.out.println(AppConsoleStyle.RED
                    + "[ERROR] No se conectó con la base de datos."
                    + "\n(Detalle): " + e.getMessage() + AppConsoleStyle.RESET);
            try {
                Thread.sleep(4000);
            } catch (InterruptedException q) {
                System.out.print(" ");
            }
            System.exit(1);
        }
        return con;
    }

    public void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            System.out.println(AppConsoleStyle.RED
                    + "[ERROR] No se desconectó con la base de datos."
                    + "\n(Detalle): " + e.getMessage() + AppConsoleStyle.RESET);
            try {
                Thread.sleep(4000);
            } catch (InterruptedException q) {
                System.out.print(" ");
            }
            System.exit(1);
        }
    }

    //*************************************************************************
    //* DATABASE METHODS                                                      *
    //*************************************************************************


    public Column[] getAllColumns(String tableName) {
        ArrayList<Column> columns = new ArrayList<>();
        try {
            DatabaseMetaData dbmd = connection.getMetaData();
            ResultSet r = dbmd
                    .getColumns("public", "%", tableName.toUpperCase(), null);

            while (r.next()) {
                columns.add(
                        new Column(r.getString(4),   //Column name
                                r.getString(5))      //Column type
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException(AppConsoleStyle.RED
                    + "[ERROR] No se pudo obtener el nombre de las columnas en la tabla"
                    + tableName + "\n(Detalle): " + e.getMessage() + AppConsoleStyle.RESET);
        }

        return columns.toArray(new Column[0]);
    }

    public List<String> getPrimaryKeys(String tableName) {
        List<String> primaryKeys = new ArrayList<>();

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet keys = metaData.getPrimaryKeys(connection.getCatalog(), null, tableName.toUpperCase());

            while (keys.next()) {
                primaryKeys.add(keys.getString("COLUMN_NAME"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(AppConsoleStyle.RED
                    + "[ERROR] No se pudo obtener la PK de la tabla " + tableName + ".\n(Detalle): "
                    + e.getMessage() + AppConsoleStyle.RESET);
        }

        return primaryKeys;
    }

    public int[] getPKFromFTWithoutInnerConnection(Object data, String tableName, String pk, String columnName) {
        final StringBuilder SQL = new StringBuilder("SELECT " + pk + " FROM " + tableName + " WHERE " + columnName + " = ");

        Column[] columns = getAllColumns(tableName);
        Column column = Arrays.stream(columns)
                .filter(oneColumn -> Objects.equals(oneColumn.getColumnName(), columnName)).findFirst()
                .orElse(null);

        try (Statement stmt = connection.createStatement()) {

            switch (Integer.parseInt(Objects.requireNonNull(column).getType())) {
                case Types.FLOAT,
                     Types.DOUBLE,
                     Types.DECIMAL,
                     Types.INTEGER,
                     Types.BIGINT,
                     Types.SMALLINT,
                     Types.TINYINT,
                     Types.BOOLEAN -> SQL.append(data);
                default -> SQL.append("'").append(data.toString()).append("'");
            }

            int[] returnValue = new int[]{0, -1};
            ResultSet rs = stmt.executeQuery(SQL.toString());
            if (rs.next()) {
                returnValue = new int[]{1, rs.getInt(1)};
            }
            return returnValue;

        } catch (SQLException e) {
            throw new RuntimeException(AppConsoleStyle.RED
                    + "[ERROR] No se pudo obtener la PK para la inserción de la FK.\n(Detalle): "
                    + e.getMessage() + AppConsoleStyle.RESET);
        }
    }

    public int[] getPKFromFTWithInnerConnection(String[] innerConnectionData, String[] columnData) {
        final StringBuilder SQL = new StringBuilder("SELECT tabla1." + columnData[1] +
                " FROM " + columnData[0] + " tabla1" +
                " JOIN " + innerConnectionData[0] + " tabla2 ON tabla2." + innerConnectionData[2] + " = tabla1." + columnData[2] +
                " WHERE tabla1." + columnData[3] + " = ");

        addInStringBuilder(SQL, new Object[]{
                columnData[4],
                columnData[5]
        });

        SQL.append(" AND tabla2." + innerConnectionData[2] + " = ");

        addInStringBuilder(SQL, new Object[]{
                innerConnectionData[3],
                innerConnectionData[4]
        });

        try (Statement statement = connection.createStatement()) {
            int[] returnValue = new int[]{0, -1};
            ResultSet rs = statement.executeQuery(SQL.toString());
            if (rs.next()) {
                returnValue = new int[]{1, rs.getInt(1)};
            }
            return returnValue;

        } catch (SQLException e) {
            throw new RuntimeException(AppConsoleStyle.RED
                    + "[ERROR] No se pudo obtener la PK para la inserción de la FK.\n(Detalle): "
                    + e.getMessage() + AppConsoleStyle.RESET);
        }
    }

    public boolean setIncreaseThroughInt(int codeToIncrease) {
        final String SQL = "UPDATE CODIGOS SET VALOR = (VALOR + 1) WHERE CODIGOS.CODIGO = ";

        try (Statement stmt = connection.createStatement()) {

            return (stmt.executeUpdate(SQL + codeToIncrease)) != 0;
        } catch (SQLException e) {
            throw new RuntimeException(AppConsoleStyle.RED
                    + "[ERROR] No se pudo actualizar el registro en la tabla CODIGOS.\n(Detalle): "
                    + e + AppConsoleStyle.RESET);
        }
    }

    public int[] setIncreaseThroughGenerator(String generator) {
        int[] returnValue = new int[]{0, -1};
        return new int[]{0, -1};
    }

    public int[] getIncreaseCodeInDB(int codeToIncrease) {
        final String SQL = "SELECT VALOR FROM CODIGOS WHERE CODIGOS.CODIGO = ";

        try (Statement stmt = connection.createStatement()) {

            int[] returnValue = new int[]{0, -1};
            ResultSet rs = stmt.executeQuery(SQL + codeToIncrease);
            if (rs.next()) {
                returnValue = new int[]{1, rs.getInt(1)};
            }
            return returnValue;

        } catch (SQLException e) {
            throw new RuntimeException(AppConsoleStyle.RED
                    + "[ERROR] No se pudo obtener el incremento de la tabla CODIGOS.\n(Detalle): "
                    + e.getMessage() + AppConsoleStyle.RESET);
        }
    }

    public void insertNewRegistry(String tableName, Map<String, Object[]> values) {
        if (values.isEmpty()) return;

        final StringBuilder SQL = new StringBuilder("INSERT INTO " + tableName + " ( ");
        for (String key : values.keySet()) {
            SQL.append(key);
            if (!Objects.equals(key, values.keySet().stream().toList().getLast())) {
                SQL.append(", ");
            }

        }
        SQL.append(") VALUES ( ");

        for (Object[] value : values.values()) {

            addInStringBuilder(SQL, value);

            if (value != values.values().stream().toList().getLast()) {
                SQL.append(", ");
            }
        }
        SQL.append(" ) ");

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(SQL.toString());
        } catch (SQLException e) {
            throw new RuntimeException(AppConsoleStyle.RED
                    + "[ERROR] No se pudo insertar el nuevo registro en la tabla"
                    + tableName + "\n(Detalle): " + e.getMessage() + AppConsoleStyle.RESET);
        }

    }

    public void updateRegistry(String tableName, Map<String, Object[]> values, Map<String, Object[]> whereValues) {
        if (values.isEmpty()) return;

        final StringBuilder SQL = new StringBuilder("UPDATE " + tableName + " SET ");

        for (String key : values.keySet()) {
            SQL.append(key).append(" = ");
            addInStringBuilder(SQL, values.get(key));
            if (!Objects.equals(key, values.keySet().stream().toList().getLast())) {
                SQL.append(", ");
            }
        }

        SQL.append(" WHERE ");

        for (String key : whereValues.keySet()) {
            SQL.append(key).append(" = ");
            addInStringBuilder(SQL, whereValues.get(key));
            if (!Objects.equals(key, whereValues.keySet().stream().toList().getLast())) {
                SQL.append(" AND ");
            }
        }

        try (Statement statement = connection.createStatement()){
            statement.executeUpdate(SQL.toString());
        } catch (SQLException e) {
            throw new RuntimeException(AppConsoleStyle.RED
                    + "[ERROR] No se pudo actualizar el registro en la tabla"
                    + tableName + "\n(Detalle): " + e.getMessage() + AppConsoleStyle.RESET);
        }


    }


    public boolean registryExistsInDB(String tableName, Map<String, Object[]> values) {
        if (values.isEmpty()) {
            System.out.println("No existen valores en el mapa para comprobar si ya existe en la tabla " + tableName);
            System.exit(1);
            return false;
        }
        boolean isFirstIteration = true;
        final StringBuilder SQL = new StringBuilder("SELECT COUNT(*) FROM " + tableName + " WHERE ");

        for (String keys : values.keySet()) {
            Object[] value = values.get(keys);
            if (keys.contains("FECHA")) continue;
            if (isFirstIteration) {
                isFirstIteration = false;
            } else {
                SQL.append(" AND ");
            }
            SQL.append(keys).append(" = ");
            addInStringBuilder(SQL, value);
        }

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SQL.toString())) {
            if (resultSet.next()) {
                return resultSet.getInt("COUNT") == 1;
            } else {
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException(AppConsoleStyle.RED
                    + "[ERROR] No se pudo ver si el registro ya existe en la tabla"
                    + tableName + "\n(Detalle): " + e.getMessage() + AppConsoleStyle.RESET);
        }
    }


    private void addInStringBuilder(StringBuilder sql, Object[] value) {
        switch (Integer.parseInt(value[0].toString())) {
            case Types.FLOAT,
                 Types.DOUBLE,
                 Types.DECIMAL,
                 Types.INTEGER,
                 Types.BIGINT,
                 Types.SMALLINT,
                 Types.TINYINT,
                 Types.BOOLEAN -> sql.append(value[1]);
            default -> sql.append("'").append(value[1].toString()).append("'");
        }
    }
}
