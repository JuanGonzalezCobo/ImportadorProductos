package data.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class IncreaseData {
    private String name, column;
    private Object type, data;
}
