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

package bisq.core.dao.node.messages;

import bisq.core.dao.blockchain.vo.BsqBlock;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.ExtendedDataSizePermission;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import io.bisq.generated.protobuffer.PB;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class GetBsqBlocksResponse extends NetworkEnvelope implements DirectMessage, ExtendedDataSizePermission {
    private final List<BsqBlock> bsqBlocks;
    private final int requestNonce;

    public GetBsqBlocksResponse(List<BsqBlock> bsqBlocks, int requestNonce) {
        this(bsqBlocks, requestNonce, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBsqBlocksResponse(List<BsqBlock> bsqBlocks, int requestNonce, int messageVersion) {
        super(messageVersion);
        this.bsqBlocks = bsqBlocks;
        this.requestNonce = requestNonce;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetBsqBlocksResponse(PB.GetBsqBlocksResponse.newBuilder()
                        .addAllBsqBlocks(bsqBlocks.stream()
                                .map(BsqBlock::toProtoMessage)
                                .collect(Collectors.toList()))
                        .setRequestNonce(requestNonce))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBsqBlocksResponse proto, int messageVersion) {
        return new GetBsqBlocksResponse(proto.getBsqBlocksList().isEmpty() ?
                new ArrayList<>() :
                proto.getBsqBlocksList().stream()
                        .map(BsqBlock::fromProto)
                        .collect(Collectors.toList()),
                proto.getRequestNonce(),
                messageVersion);
    }
}
