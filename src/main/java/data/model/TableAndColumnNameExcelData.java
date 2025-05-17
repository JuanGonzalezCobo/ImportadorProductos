package data.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TableAndColumnNameExcelData {
    private String columnName, tableName;
    private int columnNumber;
}
