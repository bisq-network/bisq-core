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

package bisq.core.dao.vote.voteresult.issuance;

import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.WritableBsqBlockChain;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.proposal.compensation.CompensationRequestPayload;

import javax.inject.Inject;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;

//TODO case that user misses reveal phase not impl. yet

@Slf4j
public class IssuanceService {
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final WritableBsqBlockChain writableBsqBlockChain;
    private final PeriodService periodService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public IssuanceService(ReadableBsqBlockChain readableBsqBlockChain,
                           WritableBsqBlockChain writableBsqBlockChain,
                           PeriodService periodService) {
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.writableBsqBlockChain = writableBsqBlockChain;
        this.periodService = periodService;
    }


    public void issueBsq(CompensationRequestPayload compensationRequestPayload, int chainHeight) {
        final Set<TxOutput> compReqIssuanceTxOutputs = readableBsqBlockChain.getCompReqIssuanceTxOutputs();
        compReqIssuanceTxOutputs.stream()
                .filter(txOutput -> txOutput.getTxId().equals(compensationRequestPayload.getTxId()))
                .filter(txOutput -> compensationRequestPayload.getRequestedBsq().value == txOutput.getValue())
                .filter(txOutput -> {
                    final String bsqAddress = compensationRequestPayload.getBsqAddress();
                    final String rawBsqAddress = bsqAddress.substring(1);
                    return rawBsqAddress.equals(txOutput.getAddress());
                })
                .filter(txOutput -> periodService.isTxInCorrectCycle(txOutput.getTxId(), chainHeight))
                .filter(txOutput -> !txOutput.isVerified()) // our candidate is not yet verified
                .forEach(txOutput -> {
                    writableBsqBlockChain.issueBsq(txOutput);
                    StringBuilder sb = new StringBuilder();
                    sb.append("\n################################################################################\n");
                    sb.append("We issued new BSQ to txId ").append(txOutput.getTxId())
                            .append("\nfor compensationRequestPayload with UID ").append(compensationRequestPayload.getUid())
                            .append("\n################################################################################\n");
                    log.info(sb.toString());
                });
    }

    public void onAllServicesInitialized() {
    }

    public void shutDown() {
    }
}
