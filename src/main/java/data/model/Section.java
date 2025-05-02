package data.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Section {
    @SerializedName("name")
    private String name;

    @SerializedName("data")
    private Object sectionData;
}
