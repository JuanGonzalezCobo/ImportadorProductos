package data.service.db.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Types;

@Data
@AllArgsConstructor
public class Column {
    private String columnName, type;
}
