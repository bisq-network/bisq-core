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

package bisq.core.trade.statistics;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeStatistics2Map implements PersistableEnvelope {
    @Getter
    private Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map = new ConcurrentHashMap<>();

    TradeStatistics2Map() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeStatistics2Map(Map<P2PDataStorage.ByteArray, TradeStatistics2> map) {
        this.map.putAll(map);
    }

    public Message toProtoMessage() {
        // Protobuffer maps don't support bytes as key so we use a hex string
        Map<String, PB.TradeStatistics2> mapForPB = map.entrySet().stream().
                collect(Collectors.toMap(e -> e.getKey().getHex(), e -> ((TradeStatistics2) e.getValue()).toProtoTradeStatistics2()));
        return PB.PersistableEnvelope.newBuilder()
                .setTradeStatistics2Map(PB.TradeStatistics2Map.newBuilder()
                        .putAllItems(mapForPB))
                .build();
    }

    public static PersistableEnvelope fromProto(PB.TradeStatistics2Map proto) {
        Map<P2PDataStorage.ByteArray, TradeStatistics2> mapFromPB = proto.getItemsMap().entrySet().stream()
                .collect(Collectors.toMap(e -> new P2PDataStorage.ByteArray(e.getKey()),
                        e -> TradeStatistics2.fromProto(e.getValue())));
        return new TradeStatistics2Map(mapFromPB);
    }

    public boolean containsKey(P2PDataStorage.ByteArray hash) {
        return map.containsKey(hash);
    }
}
