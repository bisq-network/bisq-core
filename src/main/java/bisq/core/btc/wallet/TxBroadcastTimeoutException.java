/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.btc.wallet;

import org.bitcoinj.core.Transaction;

import lombok.Getter;

import javax.annotation.Nullable;


public class TxBroadcastTimeoutException extends TxBroadcastException {
    @Getter
    @Nullable
    private final Transaction localTx;
    @Getter
    private int delay;

    public TxBroadcastTimeoutException(Transaction localTx, int delay) {
        super("The transaction was not broadcasted in " + delay +
                "seconds. txId=" + localTx.getHashAsString());
        this.localTx = localTx;
        this.delay = delay;
    }

    @Override
    public String toString() {
        return "TxBroadcastTimeoutException{" +
                "\n     localTx=" + localTx +
                ",\n     delay=" + delay +
                "\n} " + super.toString();
    }
}
