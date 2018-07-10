package bisq.core.notifications;

import java.util.Date;

import lombok.Value;

@Value
public class MobileMessage {
    private long date;
    private String txId;
    private String title;
    private String message;
    private String type;
    private String actionRequired;
    private int version;

    public MobileMessage(String title, String message, MobileMessageType type) {
        this.title = title;
        this.message = message;
        this.type = type.name();

        txId = "";
        actionRequired = "";
        date = new Date().getTime();
        version = 1;
    }

    public MobileMessage(String title, String message, String txId, MobileMessageType type) {
        this.title = title;
        this.message = message;
        this.txId = txId;
        this.type = type.name();

        actionRequired = "";
        date = new Date().getTime();
        version = 1;
    }
}
