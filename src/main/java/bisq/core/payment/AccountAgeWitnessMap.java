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

package bisq.core.payment;

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
public class AccountAgeWitnessMap implements PersistableEnvelope {
    @Getter
    private Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map = new ConcurrentHashMap<>();

    AccountAgeWitnessMap() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AccountAgeWitnessMap(Map<P2PDataStorage.ByteArray, AccountAgeWitness> map) {
        this.map.putAll(map);
    }

    public Message toProtoMessage() {
        // Protobuffer maps don't support bytes as key so we use a hex string
        Map<String, PB.AccountAgeWitness> mapForPB = map.entrySet().stream().
                collect(Collectors.toMap(e -> e.getKey().getHex(), e -> ((AccountAgeWitness) e.getValue()).toProtoAccountAgeWitness()));
        return PB.PersistableEnvelope.newBuilder()
                .setAccountAgeWitnessMap(PB.AccountAgeWitnessMap.newBuilder()
                        .putAllItems(mapForPB))
                .build();
    }

    public static PersistableEnvelope fromProto(PB.AccountAgeWitnessMap proto) {
        Map<P2PDataStorage.ByteArray, AccountAgeWitness> mapFromPB = proto.getItemsMap().entrySet().stream()
                .collect(Collectors.toMap(e -> new P2PDataStorage.ByteArray(e.getKey()),
                        e -> AccountAgeWitness.fromProto(e.getValue())));
        return new AccountAgeWitnessMap(mapFromPB);
    }

    public boolean containsKey(P2PDataStorage.ByteArray hash) {
        return map.containsKey(hash);
    }
}
