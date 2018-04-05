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

package bisq.core.dao.blockchain.vo;

import bisq.core.dao.blockchain.btcd.PubKeyScript;
import bisq.core.dao.blockchain.vo.util.TxIdIndexTuple;

import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Getter
@EqualsAndHashCode
@Slf4j
public class TxOutput implements PersistablePayload {

    public static TxOutput clone(TxOutput txOutput, boolean reset) {
        //noinspection SimplifiableConditionalExpression
        return new TxOutput(txOutput.getIndex(),
                txOutput.getValue(),
                txOutput.getTxId(),
                txOutput.getPubKeyScript(),
                txOutput.getAddress(),
                txOutput.getOpReturnData(),
                txOutput.getBlockHeight(),
                reset ? false : txOutput.isUnspent(),
                reset ? false : txOutput.isVerified(),
                reset ? TxOutputType.UNDEFINED : txOutput.getTxOutputType(),
                reset ? null : txOutput.getSpentInfo());
    }

    private final int index;
    private final long value;
    private final String txId;

    // Only set if dumpBlockchainData is true
    @Nullable
    private final PubKeyScript pubKeyScript;
    @Nullable
    private final String address;
    @Nullable
    @JsonExclude
    private final byte[] opReturnData;
    private final int blockHeight;

    // Mutable data
    @Setter
    private boolean isUnspent;
    @Setter
    private boolean isVerified;
    // We use a manual setter as we want to prevent that already set values get changed
    private TxOutputType txOutputType;
    // We use a manual setter as we want to prevent that already set values get changed
    @Nullable
    private SpentInfo spentInfo;

    public TxOutput(int index,
                    long value,
                    String txId,
                    @Nullable PubKeyScript pubKeyScript,
                    @Nullable String address,
                    @Nullable byte[] opReturnData,
                    int blockHeight) {
        this(index,
                value,
                txId,
                pubKeyScript,
                address,
                opReturnData,
                blockHeight,
                false,
                false,
                TxOutputType.UNDEFINED,
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TxOutput(int index,
                     long value,
                     String txId,
                     @Nullable PubKeyScript pubKeyScript,
                     @Nullable String address,
                     @Nullable byte[] opReturnData,
                     int blockHeight,
                     boolean isUnspent,
                     boolean isVerified,
                     TxOutputType txOutputType,
                     @Nullable SpentInfo spentInfo) {
        this.index = index;
        this.value = value;
        this.txId = txId;
        this.pubKeyScript = pubKeyScript;
        this.address = address;
        this.opReturnData = opReturnData;
        this.blockHeight = blockHeight;
        this.isUnspent = isUnspent;
        this.isVerified = isVerified;
        this.txOutputType = txOutputType;
        this.spentInfo = spentInfo;
    }

    public PB.TxOutput toProtoMessage() {
        final PB.TxOutput.Builder builder = PB.TxOutput.newBuilder()
                .setIndex(index)
                .setValue(value)
                .setTxId(txId)
                .setBlockHeight(blockHeight)
                .setIsUnspent(isUnspent)
                .setIsVerified(isVerified)
                .setTxOutputType(txOutputType.toProtoMessage());

        Optional.ofNullable(pubKeyScript).ifPresent(e -> builder.setPubKeyScript(pubKeyScript.toProtoMessage()));
        Optional.ofNullable(address).ifPresent(e -> builder.setAddress(address));
        Optional.ofNullable(opReturnData).ifPresent(e -> builder.setOpReturnData(ByteString.copyFrom(opReturnData)));
        Optional.ofNullable(spentInfo).ifPresent(e -> builder.setSpentInfo(e.toProtoMessage()));

        return builder.build();
    }

    public static TxOutput fromProto(PB.TxOutput proto) {
        return new TxOutput(proto.getIndex(),
                proto.getValue(),
                proto.getTxId(),
                proto.hasPubKeyScript() ? PubKeyScript.fromProto(proto.getPubKeyScript()) : null,
                proto.getAddress().isEmpty() ? null : proto.getAddress(),
                proto.getOpReturnData().isEmpty() ? null : proto.getOpReturnData().toByteArray(),
                proto.getBlockHeight(),
                proto.getIsUnspent(),
                proto.getIsVerified(),
                TxOutputType.fromProto(proto.getTxOutputType()),
                proto.hasSpentInfo() ? SpentInfo.fromProto(proto.getSpentInfo()) : null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isCompensationRequestBtcOutput() {
        return txOutputType == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT;
    }

    public String getId() {
        return txId + ":" + index;
    }

    public TxIdIndexTuple getTxIdIndexTuple() {
        return new TxIdIndexTuple(txId, index);
    }

    public void setTxOutputType(TxOutputType txOutputType) {
        if (this.txOutputType == TxOutputType.UNDEFINED)
            this.txOutputType = txOutputType;
        else
            throw new IllegalStateException("Already set txOutputType must not be changed.");
    }

    public void setSpentInfo(SpentInfo spentInfo) {
        if (this.spentInfo == null)
            this.spentInfo = spentInfo;
        else
            throw new IllegalStateException("Already set spentInfo must not be changed.");
    }


    @Override
    public String toString() {
        return "TxOutput{" +
                "\n     index=" + index +
                ",\n     value=" + value +
                ",\n     txId='" + txId + '\'' +
                ",\n     pubKeyScript=" + pubKeyScript +
                ",\n     address='" + address + '\'' +
                ",\n     opReturnData=" + Utilities.bytesAsHexString(opReturnData) +
                ",\n     blockHeight=" + blockHeight +
                ",\n     isUnspent=" + isUnspent +
                ",\n     isVerified=" + isVerified +
                ",\n     txOutputType=" + txOutputType +
                ",\n     spentInfo=" + spentInfo +
                "\n}";
    }
}
