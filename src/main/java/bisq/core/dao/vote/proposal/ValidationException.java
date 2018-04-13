package bisq.core.dao.vote.proposal;

import bisq.core.dao.state.blockchain.Tx;

import org.bitcoinj.core.Coin;

import lombok.Getter;

import javax.annotation.Nullable;

@Getter
public class ValidationException extends Exception {
    @Nullable
    private Coin requestedBsq;
    @Nullable
    private Coin minRequestAmount;
    @Nullable
    private Tx tx;

    public ValidationException(String message, Coin requestedBsq, Coin minRequestAmount) {
        super(message);
        this.requestedBsq = requestedBsq;
        this.minRequestAmount = minRequestAmount;
    }

    public ValidationException(Throwable cause) {
        super(cause);
    }

    public ValidationException(String message, Tx tx) {
        super(message);
        this.tx = tx;
    }

    public ValidationException(Throwable cause, Tx tx) {
        super(cause);
        this.tx = tx;
    }

    @Override
    public String toString() {
        return "ValidationException{" +
                "\n     message=" + getMessage() +
                "\n     cause.message=" + getCause().getMessage() +
                "\n     requestedBsq=" + (requestedBsq == null ? "null" : requestedBsq) +
                ",\n     minRequestAmount=" + (minRequestAmount == null ? "null" : minRequestAmount) +
                ",\n     txId=" + (tx == null ? "null" : tx.getId()) +
                "\n} " + super.toString();
    }
}
