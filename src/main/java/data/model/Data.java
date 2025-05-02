package data.model;

import com.google.gson.annotations.SerializedName;

@lombok.Data
@lombok.AllArgsConstructor
public class Data {

    @SerializedName("name")
    private String name;

    @SerializedName("type")
    private String type;

    @SerializedName("data")
    private Object data;

}
