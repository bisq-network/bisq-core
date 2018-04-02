package bisq.core.dao.vote.proposal;

import org.bitcoinj.core.Coin;

import lombok.Getter;

import javax.annotation.Nullable;

@Getter
public class ValidationException extends Exception {
    @Nullable
    private Coin requestedBsq;
    @Nullable
    private Coin minRequestAmount;

    public ValidationException(String message, Coin requestedBsq, Coin minRequestAmount) {
        super(message);
        this.requestedBsq = requestedBsq;
        this.minRequestAmount = minRequestAmount;
    }

    public ValidationException(Throwable cause) {
        super(cause);
    }
}
