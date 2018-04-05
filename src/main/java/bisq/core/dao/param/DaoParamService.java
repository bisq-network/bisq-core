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

package bisq.core.dao.param;

import bisq.core.app.BisqEnvironment;

import bisq.common.app.DevEnv;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains a list of Dao parameter change events which gets created in case a Parameter change proposal gets accepted
 * in the voting process. The events contain the blockHeight when they become valid. When obtaining the value of an
 * parameter we look up the latest change in case we have any changeEvents, otherwise we use the default value from the
 * DaoParam.
 * We do not need to sync that data structure with the BsqBlockChain or have handling for snapshots because changes by
 * voting are safe against blockchain re-orgs as we use sufficient breaks between the phases. So even in case the
 * BsqBlockchain gets changed due a re-org we will not suffer from a stale state.
 */
@Slf4j
public class DaoParamService implements PersistedDataHost {
    private final Storage<ParamChangeEventList> storage;
    @Getter
    private final ParamChangeEventList paramChangeEventList = new ParamChangeEventList();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    public DaoParamService(Storage<ParamChangeEventList> storage) {
        this.storage = storage;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            ParamChangeEventList persisted = storage.initAndGetPersisted(paramChangeEventList, 20);
            if (persisted != null) {
                this.paramChangeEventList.clear();
                this.paramChangeEventList.addAll(persisted.getList());
            }
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        persist();
    }

    public void shutDown() {
    }

    public long getDaoParamValue(DaoParam daoParam, int blockHeight) {
        final List<ParamChangeEvent> sortedFilteredList = getParamChangeEventListForParam(daoParam).stream()
                .filter(e -> e.getBlockHeight() <= blockHeight)
                .sorted(Comparator.comparing(ParamChangeEvent::getBlockHeight))
                .collect(Collectors.toList());

        if (sortedFilteredList.isEmpty()) {
            return daoParam.getDefaultValue();
        } else {
            final ParamChangeEvent mostRecentEvent = sortedFilteredList.get(sortedFilteredList.size() - 1);
            return mostRecentEvent.getValue();
        }
    }

    public void addChangeEvent(ParamChangeEvent event) {
        if (!paramChangeEventList.contains(event)) {
            if (!hasConflictingValue(getParamChangeEventListForParam(event.getDaoParam()), event)) {
                paramChangeEventList.add(event);
            } else {
                String msg = "We have already an ParamChangeEvent with the same blockHeight but a different value. " +
                        "That must not happen.";
                DevEnv.logErrorAndThrowIfDevMode(msg);
            }
        } else {
            log.warn("We have that ParamChangeEvent already in our list. ParamChangeEvent={}", event);
        }
        persist();
    }

    private List<ParamChangeEvent> getParamChangeEventListForParam(DaoParam daoParam) {
        return paramChangeEventList.getList().stream()
                .filter(e -> e.getDaoParam() == daoParam)
                .collect(Collectors.toList());
    }

    private boolean hasConflictingValue(List<ParamChangeEvent> list, ParamChangeEvent event) {
        return list.stream()
                .filter(e -> e.getBlockHeight() == event.getBlockHeight())
                .anyMatch(e -> e.getValue() != event.getValue());
    }

    private void persist() {
        storage.queueUpForSave();
    }
}
