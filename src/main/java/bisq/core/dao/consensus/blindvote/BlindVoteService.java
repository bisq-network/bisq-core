/*
 * This file is part of bisq.
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

package bisq.core.dao.consensus.blindvote;

import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.state.StateService;

import bisq.common.storage.Storage;

import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * We listen to blind votes arriving from the p2p network as well as on new txBlocks.
 * In case we are in the last block of the break after the blind vote period we pass over our collected
 * blind votes to be stored in the state and remove those items from our openBlindVoteList.
 * <p>
 * All methods in that class are executed on the parser thread.
 */
@Slf4j
public class BlindVoteService  /*StateChangeEventsProvider*/ {
    private final PeriodService periodService;
    private final StateService stateService;
    private final BlindVoteValidator blindVoteValidator;
    private final Storage<BlindVoteList> storage;

    @Getter
    private final BlindVoteList blindVoteList = new BlindVoteList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteService(PeriodService periodService,
                            StateService stateService,
                            BlindVoteValidator blindVoteValidator,
                            Storage<BlindVoteList> storage) {
        this.periodService = periodService;
        this.stateService = stateService;
        this.blindVoteValidator = blindVoteValidator;
        this.storage = storage;

        //  stateService.registerStateChangeEventsProvider(this);
    }



    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateChangeEventsProvider
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* @Override
    public Set<StateChangeEvent> provideStateChangeEvents(TxBlock txBlock) {
        Set<StateChangeEvent> stateChangeEvents = new HashSet<>();
        Set<BlindVotePayload> toRemove = new HashSet<>();

        blindVoteList.stream()
                .map(blindVote -> {
                    final Optional<StateChangeEvent> optional = getAddBlindVoteEvent(blindVote, txBlock.getHeight());

                    // If we are in the correct block and we add a BlindVoteEvent to the state we remove
                    // the blindVote from our list after we have completed iteration.
                    //TODO remove after we added to state
                      *//*  if (optional.isPresent())
                            toRemove.add(blindVote);*//*

                    return optional;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(stateChangeEvents::add);


        // We remove those blindVotes we have just added as stateChangeEvent.
        toRemove.forEach(blindVote -> {
            if (blindVoteList.remove(blindVote))
                persist();
            else
                log.warn("We called removeBlindVoteFromList at a blindVote which was not in our list");
        });

        return stateChangeEvents;
    }
*/

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

  /*  // We add a BlindVoteEvent if the tx is already available and blindVotePayload and tx are valid.
    // We only add it after the blindVotePayload phase.
    // We use the last block in the BREAK2 phase to set all blindVotePayload for that cycle.
    // If a blindVotePayload would arrive later it will be ignored.
    private Optional<StateChangeEvent> getAddBlindVoteEvent(BlindVote blindVote, int height) {
        return stateService.getTx(blindVote.getTxId())
                .filter(tx -> isLastToleratedBlock(height))
                .filter(tx -> periodService.isTxInCorrectCycle(tx.getBlockHeight(), height))
                .filter(tx -> periodService.isInPhase(tx.getBlockHeight(), Phase.BLIND_VOTE))
                .filter(tx -> blindVoteValidator.isValid(blindVote))
                .map(tx -> new BlindVoteEvent(blindVote, height));
    }
*/
 /*   private boolean isLastToleratedBlock(int height) {
        return height == periodService.getLastBlockOfPhase(height, Phase.BREAK2);
    }

    private boolean isInToleratedBlockRange(int height) {
        return height < periodService.getLastBlockOfPhase(height, Phase.BREAK2);
    }
*/
}
