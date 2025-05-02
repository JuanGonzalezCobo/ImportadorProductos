package data.model;

import lombok.AllArgsConstructor;


@lombok.Data
@AllArgsConstructor
public class TableData {
    private String name;
    private Data[] data;
}
