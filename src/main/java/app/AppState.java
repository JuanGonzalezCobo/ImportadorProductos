package app;

import lombok.Getter;
import lombok.Setter;

public class AppState {
    @Getter
    @Setter
    boolean hasFinishedReadingData = false;

    @Getter
    @Setter
    boolean hasFinishedTransferFromEstructureExcel  = false;

}
