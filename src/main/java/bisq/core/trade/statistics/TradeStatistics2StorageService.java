/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.statistics;

import bisq.network.p2p.storage.BaseMapStorageService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.storage.Storage;

import com.google.inject.name.Named;

import javax.inject.Inject;

import java.io.File;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeStatistics2StorageService extends BaseMapStorageService<TradeStatistics2Map, PersistableNetworkPayload> {
    public static final String FILE_NAME = "TradeStatistics2Map";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeStatistics2StorageService(@Named(Storage.STORAGE_DIR) File storageDir,
                                          Storage<TradeStatistics2Map> persistableNetworkPayloadMapStorage) {
        super(storageDir, persistableNetworkPayloadMapStorage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    @Override
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> getMap() {
        return envelope.getMap();
    }

    @Override
    public boolean isMyPayload(PersistablePayload payload) {
        return payload instanceof TradeStatistics2;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected TradeStatistics2Map createEnvelope() {
        return new TradeStatistics2Map();
    }
}
