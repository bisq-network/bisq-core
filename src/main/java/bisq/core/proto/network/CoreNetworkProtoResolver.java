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

package bisq.core.proto.network;

import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.core.alert.Alert;
import bisq.core.alert.PrivateNotificationMessage;
import bisq.core.arbitration.Arbitrator;
import bisq.core.arbitration.Mediator;
import bisq.core.arbitration.messages.*;
import bisq.core.dao.node.messages.GetBsqBlocksRequest;
import bisq.core.dao.node.messages.GetBsqBlocksResponse;
import bisq.core.dao.node.messages.NewBsqBlockBroadcastMessage;
import bisq.core.dao.request.compensation.CompensationRequestPayload;
import bisq.core.filter.Filter;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.messages.OfferAvailabilityRequest;
import bisq.core.offer.messages.OfferAvailabilityResponse;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.messages.*;
import bisq.core.trade.statistics.TradeStatistics;
import bisq.network.p2p.CloseConnectionMessage;
import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.peers.keepalive.messages.Pong;
import bisq.network.p2p.peers.peerexchange.messages.GetPeersRequest;
import bisq.network.p2p.peers.peerexchange.messages.GetPeersResponse;
import bisq.network.p2p.storage.messages.*;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public class CoreNetworkProtoResolver extends CoreProtoResolver implements NetworkProtoResolver {

    @Inject
    public CoreNetworkProtoResolver() {
    }

    @Override
    public NetworkEnvelope fromProto(PB.NetworkEnvelope proto) {
        if (proto != null) {
            final int messageVersion = proto.getMessageVersion();
            switch (proto.getMessageCase()) {
                case PRELIMINARY_GET_DATA_REQUEST:
                    return PreliminaryGetDataRequest.fromProto(proto.getPreliminaryGetDataRequest(), messageVersion);
                case GET_DATA_RESPONSE:
                    return GetDataResponse.fromProto(proto.getGetDataResponse(), this, messageVersion);
                case GET_UPDATED_DATA_REQUEST:
                    return GetUpdatedDataRequest.fromProto(proto.getGetUpdatedDataRequest(), messageVersion);

                case GET_PEERS_REQUEST:
                    return GetPeersRequest.fromProto(proto.getGetPeersRequest(), messageVersion);
                case GET_PEERS_RESPONSE:
                    return GetPeersResponse.fromProto(proto.getGetPeersResponse(), messageVersion);
                case PING:
                    return Ping.fromProto(proto.getPing(), messageVersion);
                case PONG:
                    return Pong.fromProto(proto.getPong(), messageVersion);

                case OFFER_AVAILABILITY_REQUEST:
                    return OfferAvailabilityRequest.fromProto(proto.getOfferAvailabilityRequest(), messageVersion);
                case OFFER_AVAILABILITY_RESPONSE:
                    return OfferAvailabilityResponse.fromProto(proto.getOfferAvailabilityResponse(), messageVersion);
                case REFRESH_OFFER_MESSAGE:
                    return RefreshOfferMessage.fromProto(proto.getRefreshOfferMessage(), messageVersion);

                case ADD_DATA_MESSAGE:
                    return AddDataMessage.fromProto(proto.getAddDataMessage(), this, messageVersion);
                case REMOVE_DATA_MESSAGE:
                    return RemoveDataMessage.fromProto(proto.getRemoveDataMessage(), this, messageVersion);
                case REMOVE_MAILBOX_DATA_MESSAGE:
                    return RemoveMailboxDataMessage.fromProto(proto.getRemoveMailboxDataMessage(), this, messageVersion);

                case CLOSE_CONNECTION_MESSAGE:
                    return CloseConnectionMessage.fromProto(proto.getCloseConnectionMessage(), messageVersion);
                case PREFIXED_SEALED_AND_SIGNED_MESSAGE:
                    return PrefixedSealedAndSignedMessage.fromProto(proto.getPrefixedSealedAndSignedMessage(), messageVersion);

                case PAY_DEPOSIT_REQUEST:
                    return PayDepositRequest.fromProto(proto.getPayDepositRequest(), this, messageVersion);
                case DEPOSIT_TX_PUBLISHED_MESSAGE:
                    return DepositTxPublishedMessage.fromProto(proto.getDepositTxPublishedMessage(), messageVersion);
                case PUBLISH_DEPOSIT_TX_REQUEST:
                    return PublishDepositTxRequest.fromProto(proto.getPublishDepositTxRequest(), this, messageVersion);
                case COUNTER_CURRENCY_TRANSFER_STARTED_MESSAGE:
                    return CounterCurrencyTransferStartedMessage.fromProto(proto.getCounterCurrencyTransferStartedMessage(), messageVersion);
                case PAYOUT_TX_PUBLISHED_MESSAGE:
                    return PayoutTxPublishedMessage.fromProto(proto.getPayoutTxPublishedMessage(), messageVersion);

                case OPEN_NEW_DISPUTE_MESSAGE:
                    return OpenNewDisputeMessage.fromProto(proto.getOpenNewDisputeMessage(), this, messageVersion);
                case PEER_OPENED_DISPUTE_MESSAGE:
                    return PeerOpenedDisputeMessage.fromProto(proto.getPeerOpenedDisputeMessage(), this, messageVersion);
                case DISPUTE_COMMUNICATION_MESSAGE:
                    return DisputeCommunicationMessage.fromProto(proto.getDisputeCommunicationMessage(), messageVersion);
                case DISPUTE_RESULT_MESSAGE:
                    return DisputeResultMessage.fromProto(proto.getDisputeResultMessage(), messageVersion);
                case PEER_PUBLISHED_DISPUTE_PAYOUT_TX_MESSAGE:
                    return PeerPublishedDisputePayoutTxMessage.fromProto(proto.getPeerPublishedDisputePayoutTxMessage(), messageVersion);

                case PRIVATE_NOTIFICATION_MESSAGE:
                    return PrivateNotificationMessage.fromProto(proto.getPrivateNotificationMessage(), messageVersion);

                case GET_BSQ_BLOCKS_REQUEST:
                    return GetBsqBlocksRequest.fromProto(proto.getGetBsqBlocksRequest(), messageVersion);
                case GET_BSQ_BLOCKS_RESPONSE:
                    return GetBsqBlocksResponse.fromProto(proto.getGetBsqBlocksResponse(), messageVersion);
                case NEW_BSQ_BLOCK_BROADCAST_MESSAGE:
                    return NewBsqBlockBroadcastMessage.fromProto(proto.getNewBsqBlockBroadcastMessage(), messageVersion);

                case ADD_PERSISTABLE_NETWORK_PAYLOAD_MESSAGE:
                    return AddPersistableNetworkPayloadMessage.fromProto(proto.getAddPersistableNetworkPayloadMessage(), this, messageVersion);
                default:
                    throw new ProtobufferException("Unknown proto message case (PB.NetworkEnvelope). messageCase=" + proto.getMessageCase());
            }
        } else {
            log.error("PersistableEnvelope.fromProto: PB.NetworkEnvelope is null");
            throw new ProtobufferException("PB.NetworkEnvelope is null");
        }
    }

    public NetworkPayload fromProto(PB.StorageEntryWrapper proto) {
        if (proto != null) {
            switch (proto.getMessageCase()) {
                case PROTECTED_MAILBOX_STORAGE_ENTRY:
                    return ProtectedMailboxStorageEntry.fromProto(proto.getProtectedMailboxStorageEntry(), this);
                case PROTECTED_STORAGE_ENTRY:
                    return ProtectedStorageEntry.fromProto(proto.getProtectedStorageEntry(), this);
                default:
                    throw new ProtobufferException("Unknown proto message case(PB.StorageEntryWrapper). messageCase=" + proto.getMessageCase());
            }
        } else {
            log.error("PersistableEnvelope.fromProto: PB.StorageEntryWrapper is null");
            throw new ProtobufferException("PB.StorageEntryWrapper is null");
        }
    }

    public NetworkPayload fromProto(PB.StoragePayload proto) {
        if (proto != null) {
            switch (proto.getMessageCase()) {
                case ALERT:
                    return Alert.fromProto(proto.getAlert());
                case ARBITRATOR:
                    return Arbitrator.fromProto(proto.getArbitrator());
                case MEDIATOR:
                    return Mediator.fromProto(proto.getMediator());
                case FILTER:
                    return Filter.fromProto(proto.getFilter());
                case COMPENSATION_REQUEST_PAYLOAD:
                    return CompensationRequestPayload.fromProto(proto.getCompensationRequestPayload());
                case TRADE_STATISTICS:
                    // Still used to convert TradeStatistics data from pre v0.6 versions
                    return TradeStatistics.fromProto(proto.getTradeStatistics());
                case MAILBOX_STORAGE_PAYLOAD:
                    return MailboxStoragePayload.fromProto(proto.getMailboxStoragePayload());
                case OFFER_PAYLOAD:
                    return OfferPayload.fromProto(proto.getOfferPayload());
                default:
                    throw new ProtobufferException("Unknown proto message case (PB.StoragePayload). messageCase=" + proto.getMessageCase());
            }
        } else {
            log.error("PersistableEnvelope.fromProto: PB.StoragePayload is null");
            throw new ProtobufferException("PB.StoragePayload is null");
        }
    }
}