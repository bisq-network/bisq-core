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

package bisq.core.dao;

import bisq.core.dao.ballot.BallotListService;
import bisq.core.dao.ballot.FilteredBallotListService;
import bisq.core.dao.ballot.MyBallotListService;
import bisq.core.dao.ballot.compensation.CompensationBallotService;
import bisq.core.dao.ballot.generic.GenericBallotFactory;
import bisq.core.dao.blindvote.BlindVoteListService;
import bisq.core.dao.blindvote.BlindVoteService;
import bisq.core.dao.blindvote.BlindVoteValidator;
import bisq.core.dao.myvote.MyVoteListService;
import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.node.blockchain.json.JsonBlockChainExporter;
import bisq.core.dao.node.consensus.BsqTxController;
import bisq.core.dao.node.consensus.GenesisTxController;
import bisq.core.dao.node.consensus.GenesisTxOutputController;
import bisq.core.dao.node.consensus.OpReturnBlindVoteController;
import bisq.core.dao.node.consensus.OpReturnCompReqController;
import bisq.core.dao.node.consensus.OpReturnController;
import bisq.core.dao.node.consensus.OpReturnProposalController;
import bisq.core.dao.node.consensus.OpReturnVoteRevealController;
import bisq.core.dao.node.consensus.TxInputController;
import bisq.core.dao.node.consensus.TxInputsController;
import bisq.core.dao.node.consensus.TxOutputController;
import bisq.core.dao.node.consensus.TxOutputsController;
import bisq.core.dao.node.full.FullNode;
import bisq.core.dao.node.full.FullNodeParser;
import bisq.core.dao.node.full.FullNodeParserFacade;
import bisq.core.dao.node.full.RpcService;
import bisq.core.dao.node.full.network.FullNodeNetworkService;
import bisq.core.dao.node.lite.LiteNode;
import bisq.core.dao.node.lite.LiteNodeParser;
import bisq.core.dao.node.lite.LiteNodeParserFacade;
import bisq.core.dao.node.lite.network.LiteNodeNetworkService;
import bisq.core.dao.period.PeriodService;
import bisq.core.dao.period.PeriodState;
import bisq.core.dao.period.PeriodStateUpdater;
import bisq.core.dao.proposal.ProposalService;
import bisq.core.dao.proposal.ProposalValidator;
import bisq.core.dao.proposal.compensation.CompensationValidator;
import bisq.core.dao.proposal.param.ChangeParamService;
import bisq.core.dao.state.SnapshotManager;
import bisq.core.dao.state.State;
import bisq.core.dao.state.StateService;
import bisq.core.dao.voteresult.VoteResultService;
import bisq.core.dao.voteresult.issuance.IssuanceService;
import bisq.core.dao.votereveal.VoteRevealService;

import bisq.common.app.AppModule;

import org.springframework.core.env.Environment;

import com.google.inject.Singleton;
import com.google.inject.name.Names;

import static com.google.inject.name.Names.named;

public class DaoModule extends AppModule {

    public DaoModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(DaoSetup.class).in(Singleton.class);
        bind(DaoFacade.class).in(Singleton.class);

        // Node, parser
        bind(BsqNodeProvider.class).in(Singleton.class);
        bind(RpcService.class).in(Singleton.class);
        bind(FullNode.class).in(Singleton.class);
        bind(FullNodeParserFacade.class).in(Singleton.class);
        bind(FullNodeNetworkService.class).in(Singleton.class);
        bind(FullNodeParser.class).in(Singleton.class);
        bind(LiteNode.class).in(Singleton.class);
        bind(LiteNodeNetworkService.class).in(Singleton.class);
        bind(LiteNodeParserFacade.class).in(Singleton.class);
        bind(LiteNodeParser.class).in(Singleton.class);

        // State
        bind(State.class).in(Singleton.class);
        bind(StateService.class).in(Singleton.class);
        bind(SnapshotManager.class).in(Singleton.class);
        bind(JsonBlockChainExporter.class).in(Singleton.class);

        // Period
        bind(PeriodState.class).in(Singleton.class);
        bind(PeriodStateUpdater.class).in(Singleton.class);
        bind(PeriodService.class).in(Singleton.class);

        // blockchain parser
        bind(GenesisTxController.class).in(Singleton.class);
        bind(GenesisTxOutputController.class).in(Singleton.class);
        bind(BsqTxController.class).in(Singleton.class);
        bind(TxInputsController.class).in(Singleton.class);
        bind(TxInputController.class).in(Singleton.class);
        bind(TxOutputsController.class).in(Singleton.class);
        bind(TxOutputController.class).in(Singleton.class);
        bind(OpReturnController.class).in(Singleton.class);
        bind(OpReturnProposalController.class).in(Singleton.class);
        bind(OpReturnCompReqController.class).in(Singleton.class);
        bind(OpReturnBlindVoteController.class).in(Singleton.class);
        bind(OpReturnVoteRevealController.class).in(Singleton.class);

        // Proposal
        bind(ProposalService.class).in(Singleton.class);
        bind(ProposalValidator.class).in(Singleton.class);
        bind(CompensationValidator.class).in(Singleton.class);
        bind(ChangeParamService.class).in(Singleton.class);

        // Ballot
        bind(GenericBallotFactory.class).in(Singleton.class);
        bind(BallotListService.class).in(Singleton.class);
        bind(MyBallotListService.class).in(Singleton.class);
        bind(FilteredBallotListService.class).in(Singleton.class);
        bind(CompensationBallotService.class).in(Singleton.class);

        // MyVote
        bind(MyVoteListService.class).in(Singleton.class);

        // BlindVote
        bind(BlindVoteService.class).in(Singleton.class);
        bind(BlindVoteListService.class).in(Singleton.class);
        bind(BlindVoteValidator.class).in(Singleton.class);

        // VoteReveal
        bind(VoteRevealService.class).in(Singleton.class);

        // VoteResult
        bind(VoteResultService.class).in(Singleton.class);
        bind(IssuanceService.class).in(Singleton.class);

        // Genesis
        String genesisTxId = environment.getProperty(DaoOptionKeys.GENESIS_TX_ID, String.class, State.DEFAULT_GENESIS_TX_ID);
        bind(String.class).annotatedWith(Names.named(DaoOptionKeys.GENESIS_TX_ID)).toInstance(genesisTxId);

        Integer genesisBlockHeight = environment.getProperty(DaoOptionKeys.GENESIS_BLOCK_HEIGHT, Integer.class, State.DEFAULT_GENESIS_BLOCK_HEIGHT);
        bind(Integer.class).annotatedWith(Names.named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT)).toInstance(genesisBlockHeight);

        // Options
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_USER)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_USER));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_PASSWORD)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_PASSWORD));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_PORT)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_PORT));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT))
                .to(environment.getRequiredProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT));
        bindConstant().annotatedWith(named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA))
                .to(environment.getRequiredProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA));
        bindConstant().annotatedWith(named(DaoOptionKeys.FULL_DAO_NODE))
                .to(environment.getRequiredProperty(DaoOptionKeys.FULL_DAO_NODE));
        Boolean daoActivated = environment.getProperty(DaoOptionKeys.DAO_ACTIVATED, Boolean.class, false);
        bind(Boolean.class).annotatedWith(Names.named(DaoOptionKeys.DAO_ACTIVATED)).toInstance(daoActivated);
    }
}

