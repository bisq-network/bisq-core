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

package bisq.core.dao.state.ext;

import bisq.core.dao.state.blockchain.TxOutput;

import io.bisq.generated.protobuffer.PB;

import javax.inject.Inject;

import java.util.Optional;

import lombok.Value;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@Value
public class Issuance {
    private final TxOutput txOutput;
    private final int chainHeight;
    private final long amount;
    @Nullable
    private final String inputPubKey; // sig key of first input it issuance tx
    private final long date;

    @Inject
    public Issuance(TxOutput txOutput, int chainHeight, long amount, @Nullable String inputPubKey, long date) {
        this.txOutput = txOutput;
        this.chainHeight = chainHeight;
        this.amount = amount;
        this.inputPubKey = inputPubKey;
        this.date = date;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.Issuance toProtoMessage() {
        final PB.Issuance.Builder builder = PB.Issuance.newBuilder()
                .setTxOutput(txOutput.toProtoMessage())
                .setChainHeight(chainHeight)
                .setAmount(amount)
                .setDate(date);

        Optional.ofNullable(inputPubKey).ifPresent(e -> builder.setInputPubKey(inputPubKey));

        return builder.build();
    }

    public static Issuance fromProto(PB.Issuance proto) {
        return new Issuance(TxOutput.fromProto(proto.getTxOutput()),
                proto.getChainHeight(),
                proto.getAmount(),
                proto.getInputPubKey().isEmpty() ? null : proto.getInputPubKey(),
                proto.getDate());
    }

}
