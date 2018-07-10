package bisq.core.notifications;

import bisq.common.util.JsonExclude;

import java.util.Date;

import lombok.Value;

@Value
public class MobileMessage {
    private long date;
    private String txId;
    private String title;
    private String message;
    @JsonExclude
    private MobileMessageType msgType;
    private String type;
    private String actionRequired;
    private int version;

    public MobileMessage(String title, String message, MobileMessageType msgType) {
        this(title, message, "", msgType);
    }

    public MobileMessage(String title, String message, String txId, MobileMessageType msgType) {
        this.title = title;
        this.message = message;
        this.txId = txId;
        this.msgType = msgType;

        type = msgType.name();
        actionRequired = "";
        date = new Date().getTime();
        version = 1;
    }
}
