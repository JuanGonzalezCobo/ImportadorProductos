package data.service.db;

import data.service.db.model.Column;
import lombok.Getter;

import java.sql.*;
import java.util.*;

@Getter
public class DataBaseConnection {

    private final Connection connection;

    public DataBaseConnection(String[] data) {
        connection = getConnection(data[0], data[1], data[2], data[3]);
    }

    //*************************************************************************
    //* CONNECTION HANDLER                                                    *
    //*************************************************************************

    private Connection getConnection(String url, String database, String user, String password) {
        Connection con = null;
        try {
            con = DriverManager
                    .getConnection(url.concat(database), user, password);
            System.out.println("[STATUS] Conexión exitosa a la base de datos.");
        } catch (SQLException e) {
            System.out.println("[ERROR] No se conectó con la base de datos: " + e.getMessage());
            System.exit(1);
        }
        return con;
    }

    public void closeConnection() {
        try {
            connection.close();
        } catch (SQLException e) {
            System.out.println("[ERROR] No se desconectó bien la base de datos");
        }
    }

    //*************************************************************************
    //* DATABASE METHODS                                                      *
    //*************************************************************************

    //Quitar el nombre de la base de datos para poder jugar con la parte de firebird

    public boolean tableExists(String tableName) {
        try {
            DatabaseMetaData dbmd = connection.getMetaData();
            ResultSet r = dbmd
                    .getTables("public",
                            "%",
                            tableName.trim().toUpperCase(),
                            null);
            return r.next();
        } catch (SQLException e) {
            System.out.println("[ERROR] No se pudo comprobar si la tabla existe: " + e.getMessage());
            return false;
        }
    }


    public String[] compareTableNames(String tableNameCompared) {
        ArrayList<String> tables = new ArrayList<>();
        try {
            DatabaseMetaData dbmd = connection.getMetaData();
            ResultSet r = dbmd
                    .getTables("public",
                            "%",
                            "%" + tableNameCompared.trim().toUpperCase() + "%",
                            null);
            while (r.next()) {
                String tableName = r.getString(3);
                tables.add(tableName);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return tables.toArray(new String[0]);
    }

    public Column[] getAllColumns(String tableName) {
        ArrayList<Column> columns = new ArrayList<>();
        try {
            DatabaseMetaData dbmd = connection.getMetaData();
            ResultSet r = dbmd
                    .getColumns("public", "%", tableName.toUpperCase(), null);

            while (r.next()) {
                columns.add(
                        new Column(r.getString(4),   //Column name
                                r.getString(5))     //Column type
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return columns.toArray(new Column[0]);
    }

    public Map<String, String> getAllForeignKeys(String tableName) {
        Map<String, String> foreignKeys = new HashMap<>();

        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet keys = metaData.getImportedKeys(connection.getCatalog(), null, tableName.toUpperCase());

            while (keys.next()) {
                String foreignKeyColumn = keys.getString("FKCOLUMN_NAME");
                String primaryKeyColumn = keys.getString("PKCOLUMN_NAME");
                String primaryTable = keys.getString("PKTABLE_NAME");
                foreignKeys.put(foreignKeyColumn, primaryTable + "." + primaryKeyColumn);
            }

        } catch (SQLException e) {
            System.err.println("Error al obtener claves foráneas: " + e.getMessage());
        }
        return foreignKeys;
    }

    public boolean dataExistsInForeignTable(String tableName, String columnName, Object[] value) {
        final String SQL = "SELECT COUNT(*) FROM ? WHERE ? = ?";

        try (PreparedStatement stmt = connection.prepareStatement(SQL)) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            switch (Integer.parseInt(value[0].toString())) {
                case Types.FLOAT, Types.DOUBLE, Types.DECIMAL -> stmt.setFloat(3, (Float) value[1]);
                case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> stmt.setInt(3, (Integer) value[1]);
                case Types.TIMESTAMP, Types.DATE -> stmt.setTimestamp(3, (Timestamp) value[1]);
                case Types.BOOLEAN -> stmt.setBoolean(3, (Boolean) value[1]);
                default -> stmt.setString(3, value[1].toString());
            }

            boolean returnValue = false;
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                returnValue = true;
            }
            return returnValue;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public int[] getIdentifierColumn(Object data, String tableName, String pk, String columnName) {
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
            System.out.println(e.getMessage());
            return null;
        }
    }

    public boolean setIncreaseThroughInt(int codeToIncrease) {
        final String SQL = "UPDATE CODIGOS SET VALOR = (VALOR + 1) WHERE CODIGOS.CODIGO = ";

        try (Statement stmt = connection.createStatement()) {

            return (stmt.executeUpdate(SQL + codeToIncrease)) != 0;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
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
            System.out.println(e.getMessage());
            return null;
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
            switch ((Integer) value[0]) {
                case Types.FLOAT,
                     Types.DOUBLE,
                     Types.DECIMAL,
                     Types.INTEGER,
                     Types.BIGINT,
                     Types.SMALLINT,
                     Types.TINYINT,
                     Types.BOOLEAN -> SQL.append(value[1]);
                default -> SQL.append("'").append(value[1].toString()).append("'");
            }

            if (value != values.values().stream().toList().getLast()) {
                SQL.append(", ");
            }
        }
        SQL.append(" ) ");

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(SQL.toString());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }
}
