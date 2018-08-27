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

package bisq.core.dao.node.parser;

import bisq.core.dao.node.parser.exceptions.InvalidGenesisTxException;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.RawTx;
import bisq.core.dao.state.blockchain.TempTx;
import bisq.core.dao.state.blockchain.TempTxOutput;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutputKey;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.governance.Param;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies if a given transaction is a BSQ transaction.
 */
@Slf4j
public class TxParser {

    private final TxInputParser txInputParser;
    private final TxOutputParser txOutputParser;
    private final PeriodService periodService;
    private final BsqStateService bsqStateService;

    @Inject
    public TxParser(TxInputParser txInputParser,
                    TxOutputParser txOutputParser,
                    PeriodService periodService,
                    BsqStateService bsqStateService) {
        this.txInputParser = txInputParser;
        this.txOutputParser = txOutputParser;
        this.periodService = periodService;
        this.bsqStateService = bsqStateService;
    }

    // Apply state changes to tx, inputs and outputs
    // return true if any input contained BSQ
    // Any tx with BSQ input is a BSQ tx (except genesis tx but that is not handled in
    // that class).
    // There might be txs without any valid BSQ txOutput but we still keep track of it,
    // for instance to calculate the total burned BSQ.
    public Optional<Tx> findTx(RawTx rawTx, String genesisTxId, int genesisBlockHeight, Coin genesisTotalSupply) {
        // Let's see if we have a genesis tx
        Optional<TempTx> optionalGenesisTx = TxParser.findGenesisTx(
                genesisTxId,
                genesisBlockHeight,
                genesisTotalSupply,
                rawTx);
        if (optionalGenesisTx.isPresent()) {
            TempTx genesisTx = optionalGenesisTx.get();
            txOutputParser.processGenesisTxOutput(genesisTx);
            return Optional.of(Tx.fromTempTx(genesisTx));
        }

        // If it is not a genesis tx we continue to parse to see if it is a valid BSQ tx.
        int blockHeight = rawTx.getBlockHeight();
        // We could pass tx also to the sub validators but as long we have not refactored the validators to pure
        // functions lets use the parsingModel.
        TempTx tempTx = TempTx.fromRawTx(rawTx);
        ParsingModel parsingModel = new ParsingModel(tempTx);

        for (int inputIndex = 0; inputIndex < tempTx.getTxInputs().size(); inputIndex++) {
            TxInput input = tempTx.getTxInputs().get(inputIndex);
            TxOutputKey outputKey = input.getConnectedTxOutputKey();
            txInputParser.process(outputKey, blockHeight, rawTx.getId(), inputIndex, parsingModel);
        }

        final boolean leftOverBsq = parsingModel.isInputValuePositive();
        if (leftOverBsq) {
            final List<TempTxOutput> outputs = tempTx.getTempTxOutputs();
            // We start with last output as that might be an OP_RETURN output and gives us the specific tx type, so it is
            // easier and cleaner at parsing the other outputs to detect which kind of tx we deal with.
            // Setting the opReturn type here does not mean it will be a valid BSQ tx as the checks are only partial and
            // BSQ inputs are not verified yet.
            // We keep the temporary opReturn type in the parsingModel object.
            checkArgument(!outputs.isEmpty(), "outputs must not be empty");
            int lastIndex = outputs.size() - 1;
            txOutputParser.processOpReturnCandidate(outputs.get(lastIndex), parsingModel);

            // txOutputsIterator.iterate(tx, blockHeight, parsingModel);

            // We use order of output index. An output is a BSQ utxo as long there is enough input value
            // We iterate all outputs including the opReturn to do a full validation including the BSQ fee
            for (int index = 0; index < outputs.size(); index++) {
                txOutputParser.processTxOutput(tempTx, outputs.get(index), index, blockHeight, parsingModel);
            }

            long bsqFee = parsingModel.getAvailableInputValue();
            if (parsingModel.getVerifiedOpReturnType() == OpReturnType.PROPOSAL) {
                isFeeAndPhaseValid(blockHeight, tempTx, bsqFee, DaoPhase.Phase.PROPOSAL, Param.PROPOSAL_FEE);
            } else if (parsingModel.getVerifiedOpReturnType() == OpReturnType.COMPENSATION_REQUEST) {
                boolean isFeeAndPhaseValid = isFeeAndPhaseValid(blockHeight, tempTx, bsqFee, DaoPhase.Phase.PROPOSAL, Param.PROPOSAL_FEE);

                //TODO refactor issuanceCandidate
                TempTxOutput issuanceCandidate = parsingModel.getIssuanceCandidate();
                if (isFeeAndPhaseValid) {
                    if (issuanceCandidate != null) {
                        issuanceCandidate.setTxOutputType(TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
                    } else {
                        log.warn("It can be that we have a opReturn which is correct from its structure but the whole tx " +
                                "in not valid as the issuanceCandidate in not there. " +
                                "As the BSQ fee is set it must be either a buggy tx or an manually crafted invalid tx.");
                    }
                } else {
                    // If the opReturn is invalid we set the issuanceCandidate as BTC if it is available
                    if (issuanceCandidate != null) {
                        issuanceCandidate.setTxOutputType(TxOutputType.BTC_OUTPUT);
                    } else {
                        // That is a possible valid case where a random tx matches our opReturn rules but it is not a
                        // valid BSQ tx.
                    }
                }
            } else if (parsingModel.getVerifiedOpReturnType() == OpReturnType.BLIND_VOTE) {
                boolean isFeeAndPhaseValid = isFeeAndPhaseValid(blockHeight, tempTx, bsqFee, DaoPhase.Phase.BLIND_VOTE, Param.BLIND_VOTE_FEE);
                if (!isFeeAndPhaseValid) {
                    // TODO refactor getBlindVoteLockStakeOutput
                    TempTxOutput blindVoteLockStakeOutput = parsingModel.getBlindVoteLockStakeOutput();
                    if (blindVoteLockStakeOutput != null) {
                        blindVoteLockStakeOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
                    } else {
                        // That is a possible valid case where a random tx matches our opReturn rules but it is not a
                        // valid BSQ tx.
                    }
                }
            } else if (parsingModel.getVerifiedOpReturnType() == OpReturnType.VOTE_REVEAL) {
                boolean isPhaseValid = isPhaseValid(blockHeight, tempTx, DaoPhase.Phase.VOTE_REVEAL);
                if (!isPhaseValid || parsingModel.getVoteRevealInputState() != ParsingModel.VoteRevealInputState.VALID) {
                    TempTxOutput voteRevealUnlockStakeOutput = parsingModel.getVoteRevealUnlockStakeOutput();
                    if (voteRevealUnlockStakeOutput != null) {
                        voteRevealUnlockStakeOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
                    } else {
                        // That is a possible valid case where a random tx matches our opReturn rules but it is not a
                        // valid BSQ tx.
                    }
                }
            } else if (parsingModel.getVerifiedOpReturnType() == OpReturnType.LOCKUP) {
                // nothing to do
            }

            if (parsingModel.getVerifiedOpReturnType() != OpReturnType.COMPENSATION_REQUEST) {
                //TODO refactor issuanceCandidate
                // If the opReturn is invalid the issuance candidate cannot become BSQ, so we set it to BTC
                TempTxOutput issuanceCandidate = parsingModel.getIssuanceCandidate();
                if (issuanceCandidate != null)
                    issuanceCandidate.setTxOutputType(TxOutputType.BTC_OUTPUT);
            }

            if (parsingModel.getVerifiedOpReturnType() != OpReturnType.BLIND_VOTE) {
                //TODO refactor
                TempTxOutput blindVoteLockStakeOutput = parsingModel.getBlindVoteLockStakeOutput();
                if (blindVoteLockStakeOutput != null)
                    blindVoteLockStakeOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
            }

            if (parsingModel.getVerifiedOpReturnType() != OpReturnType.VOTE_REVEAL) {
                //TODO refactor
                TempTxOutput voteRevealUnlockStakeOutput = parsingModel.getVoteRevealUnlockStakeOutput();
                if (voteRevealUnlockStakeOutput != null)
                    voteRevealUnlockStakeOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
            }


            if (parsingModel.getVerifiedOpReturnType() != OpReturnType.LOCKUP) {
                //TODO refactor
                TempTxOutput lockupCandidate = parsingModel.getLockupOutput();
                if (lockupCandidate != null)
                    lockupCandidate.setTxOutputType(TxOutputType.BTC_OUTPUT);
            }


            // We don't allow multiple opReturn outputs (they are non-standard but to be safe lets check it)
            long numOpReturnOutputs = tempTx.getTempTxOutputs().stream().filter(txOutputParser::isOpReturnOutput).count();
            if (numOpReturnOutputs <= 1) {
                // If we had an issuanceCandidate and the type was not applied in the opReturnController due failed validation
                // we set it to an BTC_OUTPUT.
                TempTxOutput issuanceCandidate = parsingModel.getIssuanceCandidate();
                if (issuanceCandidate != null &&
                        issuanceCandidate.getTxOutputType() == TxOutputType.UNDEFINED) {
                    issuanceCandidate.setTxOutputType(TxOutputType.BTC_OUTPUT);
                }

                boolean isAnyTxOutputTypeUndefined = tempTx.getTempTxOutputs().stream()
                        .anyMatch(txOutput -> TxOutputType.UNDEFINED == txOutput.getTxOutputType());
                if (!isAnyTxOutputTypeUndefined) {
                    TxType txType = TxParser.getTxType(tempTx, parsingModel);
                    tempTx.setTxType(txType);
                    final long burntFee = bsqFee;
                    if (burntFee > 0)
                        tempTx.setBurntFee(burntFee);
                } else {
                    String msg = "We have undefined txOutput types which must not happen. tx=" + tempTx;
                    DevEnv.logErrorAndThrowIfDevMode(msg);
                }
            } else {
                // We don't consider a tx with multiple OpReturn outputs valid.
                tempTx.setTxType(TxType.INVALID);
                String msg = "Invalid tx. We have multiple opReturn outputs. tx=" + tempTx;
                log.warn(msg);
            }
        }

        // TODO || parsingModel.getBurntBondValue() > 0; should not be necessary
        // How should we consider the burnt BSQ from spending a LOCKUP tx with the wrong format.
        // Example: LOCKUP txOutput is 1000 satoshi but first txOutput in spending tx is 900
        // satoshi, this burns the 1000 satoshi and is currently not considered in the
        // bsqInputBalancePositive, hence the need to check for parsingModel.getBurntBondValue
        // Perhaps adding boolean parsingModel.isBSQTx and checking for that would be better?
        if (leftOverBsq || parsingModel.getBurntBondValue() > 0)
            return Optional.of(Tx.fromTempTx(tempTx));
        else
            return Optional.empty();
    }

    private boolean isFeeAndPhaseValid(int blockHeight, TempTx tempTx, long bsqFee, DaoPhase.Phase phase, Param param) {
        // The leftover BSQ balance from the inputs is the BSQ fee in case we are in an OP_RETURN output

        if (!isPhaseValid(blockHeight, tempTx, phase))
            return false;

        long paramValue = bsqStateService.getParamValue(param, blockHeight);
        boolean isFeeCorrect = bsqFee == paramValue;
        if (!isFeeCorrect) {
            log.warn("Invalid fee. used fee={}, required fee={}", bsqFee, paramValue);
            tempTx.setTxType(TxType.INVALID);
            // TODO should we return already?

            return false;
        }

        return true;
    }

    private boolean isPhaseValid(int blockHeight, TempTx tempTx, DaoPhase.Phase phase) {
        boolean isInPhase = periodService.isInPhase(blockHeight, phase);
        if (!isInPhase) {
            log.warn("Not in {} phase. blockHeight={}", phase, blockHeight);
            tempTx.setTxType(TxType.INVALID);
            // TODO should we return already?
            return false;
        }
        return true;
    }

    /*
    TODO add tests
    todo(chirhonul): would be nice to make this not need parsingModel; can the midstate checked within
    be deduced from tx, or otherwise explicitly passed in?
    */
    @SuppressWarnings("WeakerAccess")
    @VisibleForTesting
    static TxType getTxType(TempTx tx, ParsingModel parsingModel) {
        TxType txType;
        // We need to have at least one BSQ output
        Optional<OpReturnType> optionalOpReturnType = TxParser.getOptionalOpReturnType(tx, parsingModel);
        if (optionalOpReturnType.isPresent()) {
            txType = TxParser.getTxTypeForOpReturn(tx, optionalOpReturnType.get());
        } else if (parsingModel.getOpReturnTypeCandidate() == null) {
            boolean bsqFeesBurnt = parsingModel.isInputValuePositive();
            if (bsqFeesBurnt) {
                // Burned fee but no opReturn
                txType = TxType.PAY_TRADE_FEE;
            } else if (tx.getTempTxOutputs().get(0).getTxOutputType() == TxOutputType.UNLOCK) {
                txType = TxType.UNLOCK;
            } else {
                // No burned fee and no opReturn.
                txType = TxType.TRANSFER_BSQ;
            }
        } else {
            // We got some OP_RETURN type candidate but it failed at validation
            txType = TxType.INVALID;
        }

        return txType;
    }

    static private TxType getTxTypeForOpReturn(TempTx tx, OpReturnType opReturnType) {
        TxType txType;
        switch (opReturnType) {
            case COMPENSATION_REQUEST:
                checkArgument(tx.getTempTxOutputs().size() >= 3, "Compensation request tx need to have at least 3 outputs");
                TempTxOutput issuanceTxOutput = tx.getTempTxOutputs().get(1);
                checkArgument(issuanceTxOutput.getTxOutputType() == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT,
                        "Compensation request txOutput type need to be ISSUANCE_CANDIDATE_OUTPUT");
                txType = TxType.COMPENSATION_REQUEST;
                break;
            case PROPOSAL:
                txType = TxType.PROPOSAL;
                break;
            case BLIND_VOTE:
                txType = TxType.BLIND_VOTE;
                break;
            case VOTE_REVEAL:
                txType = TxType.VOTE_REVEAL;
                break;
            case LOCKUP:
                txType = TxType.LOCKUP;
                break;
            default:
                log.warn("We got a BSQ tx with fee and unknown OP_RETURN. tx={}", tx);
                txType = TxType.INVALID;
        }
        return txType;
    }

    static private Optional<OpReturnType> getOptionalOpReturnType(TempTx tx, ParsingModel parsingModel) {
        if (parsingModel.isBsqOutputFound()) {
            // We want to be sure that the initial assumption of the opReturn type was matching the result after full
            // validation.
            OpReturnType opReturnTypeCandidate = parsingModel.getOpReturnTypeCandidate();
            OpReturnType verifiedOpReturnType = parsingModel.getVerifiedOpReturnType();
            if (opReturnTypeCandidate == verifiedOpReturnType) {
                return Optional.ofNullable(verifiedOpReturnType);
            } else {
                String msg = "We got a different opReturn type after validation as we expected initially. " +
                        "opReturnTypeCandidate=" + opReturnTypeCandidate +
                        ", verifiedOpReturnType=" + parsingModel.getVerifiedOpReturnType() +
                        ", txId=" + tx.getId();
                log.error(msg);
            }
        } else {
            String msg = "We got a tx without any valid BSQ output but with burned BSQ. " +
                    "Burned fee=" + parsingModel.getAvailableInputValue() / 100D + " BSQ. tx=" + tx;
            log.warn(msg);
        }
        return Optional.empty();
    }

    public static Optional<TempTx> findGenesisTx(String genesisTxId, int genesisBlockHeight, Coin genesisTotalSupply, RawTx rawTx) {
        boolean isGenesis = rawTx.getBlockHeight() == genesisBlockHeight &&
                rawTx.getId().equals(genesisTxId);
        if (!isGenesis)
            return Optional.empty();

        TempTx tempTx = TempTx.fromRawTx(rawTx);
        tempTx.setTxType(TxType.GENESIS);
        long remainingInputValue = genesisTotalSupply.getValue();
        for (int i = 0; i < tempTx.getTempTxOutputs().size(); ++i) {
            TempTxOutput txOutput = tempTx.getTempTxOutputs().get(i);
            long value = txOutput.getValue();
            boolean isValid = value <= remainingInputValue;
            if (!isValid)
                throw new InvalidGenesisTxException("Genesis tx is invalid; using more than available inputs. " +
                        "Remaining input value is " + remainingInputValue + " sat; tx info: " + tempTx.toString());

            remainingInputValue -= value;
            txOutput.setTxOutputType(TxOutputType.GENESIS_OUTPUT);
        }
        if (remainingInputValue > 0) {
            throw new InvalidGenesisTxException("Genesis tx is invalid; not using all available inputs. " +
                    "Remaining input value is " + remainingInputValue + " sat, tx info: " + tempTx.toString());
        }
        return Optional.of(tempTx);
    }
}
