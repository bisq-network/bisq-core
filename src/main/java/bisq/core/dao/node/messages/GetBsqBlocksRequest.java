package bisq.core.dao.node.messages;

import bisq.common.app.Capabilities;
import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Getter
@ToString
public final class GetBsqBlocksRequest extends NetworkEnvelope implements DirectMessage, CapabilityRequiringPayload {
    private final int fromBlockHeight;
    private final int nonce;

    public GetBsqBlocksRequest(int fromBlockHeight, int nonce) {
        this(fromBlockHeight, nonce, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBsqBlocksRequest(int fromBlockHeight, int nonce, int messageVersion) {
        super(messageVersion);
        this.fromBlockHeight = fromBlockHeight;
        this.nonce = nonce;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetBsqBlocksRequest(PB.GetBsqBlocksRequest.newBuilder()
                        .setFromBlockHeight(fromBlockHeight)
                        .setNonce(nonce))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBsqBlocksRequest proto, int messageVersion) {
        return new GetBsqBlocksRequest(proto.getFromBlockHeight(), proto.getNonce(), messageVersion);
    }

    @Override
    public List<Integer> getRequiredCapabilities() {
        return new ArrayList<>(Collections.singletonList(
                Capabilities.Capability.DAO_FULL_NODE.ordinal()
        ));
    }
}
