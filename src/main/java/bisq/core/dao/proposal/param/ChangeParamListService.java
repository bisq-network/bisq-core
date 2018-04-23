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

package bisq.core.dao.proposal.param;

import bisq.core.dao.state.events.ParamChangeEvent;

import bisq.common.app.DevEnv;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Maintains a list of Dao parameter change events which gets created in case a Parameter change proposal gets accepted
 * in the voting process. The events contain the blockHeight when they become valid. When obtaining the value of an
 * parameter we look up the latest change in case we have any changeEvents, otherwise we use the default value from the
 * Param.
 * We do not need to sync that data structure with the StateService or have handling for snapshots because changes by
 * voting are safe against blockchain re-orgs as we use sufficient breaks between the phases. So even in case the
 * BsqBlockchain gets changed due a re-org we will not suffer from a stale state.
 */
//TODO WIP
@Slf4j
public class ChangeParamListService implements PersistedDataHost {
    private final Storage<ChangeParamEventList> storage;
    private final ChangeParamEventList changeParamEventList = new ChangeParamEventList();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    public ChangeParamListService(Storage<ChangeParamEventList> storage) {
        this.storage = storage;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        //TODO
       /* if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            ChangeParamEventList persisted = storage.initAndGetPersisted(changeParamEventList, 20);
            if (persisted != null) {
                this.changeParamEventList.clear();
                this.changeParamEventList.addAll(persisted.getList());
            }
        }*/
    }
    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        persist();
    }


    public long getDaoParamValue(Param param, int blockHeight) {
        final List<ParamChangeEvent> sortedFilteredList = getParamChangeEventListForParam(param).stream()
                .filter(e -> e.getHeight() <= blockHeight)
                .sorted(Comparator.comparing(ParamChangeEvent::getHeight))
                .collect(Collectors.toList());

        if (sortedFilteredList.isEmpty()) {
            return param.getDefaultValue();
        } else {
            final ParamChangeEvent mostRecentEvent = sortedFilteredList.get(sortedFilteredList.size() - 1);
            return mostRecentEvent.getValue();
        }
    }

    public void addChangeEvent(ParamChangeEvent event) {
        if (!changeParamEventList.contains(event)) {
            if (!hasConflictingValue(getParamChangeEventListForParam(event.getDaoParam()), event)) {
                changeParamEventList.add(event);
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

    private List<ParamChangeEvent> getParamChangeEventListForParam(Param param) {
        return changeParamEventList.getList().stream()
                .filter(e -> e.getDaoParam() == param)
                .collect(Collectors.toList());
    }

    private boolean hasConflictingValue(List<ParamChangeEvent> list, ParamChangeEvent event) {
        return list.stream()
                .filter(e -> e.getHeight() == event.getHeight())
                .anyMatch(e -> e.getValue() != event.getValue());
    }

    private void persist() {
        storage.queueUpForSave();
    }
}
