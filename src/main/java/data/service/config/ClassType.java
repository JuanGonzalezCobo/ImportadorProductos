package data.service.config;

import java.sql.Types;
import java.util.Date;

public class ClassType {

    public static String setClassType(int type) {
        return switch (type) {
            case Types.FLOAT, Types.DECIMAL -> "fl";
            case Types.NUMERIC -> "numeric";
            case Types.DOUBLE -> "double";
            case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> "int";
            case Types.TIMESTAMP -> "dateTime";     //AHORA
            case Types.DATE -> "date";              //HOY
            case Types.BOOLEAN -> "bool";
            default -> "str";
        };
    }

    public static int getClassType(String type) {
        return switch (type) {
            case "fl" -> Types.FLOAT;
            case "double" -> Types.DOUBLE;
            case "numeric" -> Types.NUMERIC;
            case "int" -> Types.INTEGER;
            case "date" -> Types.DATE;
            case "dateTime" -> Types.TIMESTAMP;
            case "bool" -> Types.BOOLEAN;
            default -> Types.VARCHAR;
        };
    }
}
