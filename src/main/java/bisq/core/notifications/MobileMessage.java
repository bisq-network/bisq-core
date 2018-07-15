package bisq.core.notifications;

import bisq.common.util.JsonExclude;

import lombok.Value;

@Value
public class MobileMessage {
    private long sentDate;
    private String txId;
    private String title;
    private String message;
    @JsonExclude
    transient private MobileMessageType mobileMessageType;
    private String type;
    private String actionRequired;
    private int version;

    public MobileMessage(String title, String message, MobileMessageType mobileMessageType) {
        this(title, message, "", mobileMessageType);
    }

    public MobileMessage(String title, String message, String txId, MobileMessageType mobileMessageType) {
        this.title = title;
        this.message = message;
        this.txId = txId;
        this.mobileMessageType = mobileMessageType;

        this.type = mobileMessageType.name();
        actionRequired = "";
        sentDate = 0;//new Date().getTime();
        version = 1;
    }
}
