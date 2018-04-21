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

import bisq.core.dao.consensus.ConsensusServicesSetup;
import bisq.core.dao.consensus.blindvote.BlindVoteService;
import bisq.core.dao.consensus.blindvote.BlindVoteValidator;
import bisq.core.dao.consensus.myvote.MyBlindVoteService;
import bisq.core.dao.consensus.node.BsqNodeProvider;
import bisq.core.dao.consensus.node.NodeExecutor;
import bisq.core.dao.consensus.node.blockchain.json.JsonBlockChainExporter;
import bisq.core.dao.consensus.node.consensus.BsqTxController;
import bisq.core.dao.consensus.node.consensus.GenesisTxController;
import bisq.core.dao.consensus.node.consensus.GenesisTxOutputController;
import bisq.core.dao.consensus.node.consensus.OpReturnBlindVoteController;
import bisq.core.dao.consensus.node.consensus.OpReturnCompReqController;
import bisq.core.dao.consensus.node.consensus.OpReturnController;
import bisq.core.dao.consensus.node.consensus.OpReturnProposalController;
import bisq.core.dao.consensus.node.consensus.OpReturnVoteRevealController;
import bisq.core.dao.consensus.node.consensus.TxInputController;
import bisq.core.dao.consensus.node.consensus.TxInputsController;
import bisq.core.dao.consensus.node.consensus.TxOutputController;
import bisq.core.dao.consensus.node.consensus.TxOutputsController;
import bisq.core.dao.consensus.node.full.FullNode;
import bisq.core.dao.consensus.node.full.FullNodeExecutor;
import bisq.core.dao.consensus.node.full.FullNodeParser;
import bisq.core.dao.consensus.node.full.network.FullNodeNetworkService;
import bisq.core.dao.consensus.node.full.rpc.RpcService;
import bisq.core.dao.consensus.node.lite.LiteNode;
import bisq.core.dao.consensus.node.lite.LiteNodeExecutor;
import bisq.core.dao.consensus.node.lite.LiteNodeParser;
import bisq.core.dao.consensus.node.lite.network.LiteNodeNetworkService;
import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.PeriodState;
import bisq.core.dao.consensus.period.PeriodStateMutator;
import bisq.core.dao.consensus.proposal.ProposalValidator;
import bisq.core.dao.consensus.proposal.compensation.CompensationValidator;
import bisq.core.dao.consensus.proposal.param.ChangeParamService;
import bisq.core.dao.consensus.state.SnapshotManager;
import bisq.core.dao.consensus.state.State;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.voteresult.VoteResultService;
import bisq.core.dao.consensus.voteresult.issuance.IssuanceService;
import bisq.core.dao.consensus.votereveal.VoteRevealService;
import bisq.core.dao.presentation.PresentationServicesSetup;
import bisq.core.dao.presentation.ballot.CompensationBallotFactory;
import bisq.core.dao.presentation.ballot.GenericBallotFactory;
import bisq.core.dao.presentation.blindvote.BlindVoteServiceFacade;
import bisq.core.dao.presentation.myvote.MyBlindVoteServiceFacade;
import bisq.core.dao.presentation.period.PeriodServiceFacade;
import bisq.core.dao.presentation.proposal.BallotListService;
import bisq.core.dao.presentation.proposal.FilteredBallotListService;
import bisq.core.dao.presentation.proposal.MyBallotListService;
import bisq.core.dao.presentation.state.StateServiceFacade;

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
        bind(ConsensusServicesSetup.class).in(Singleton.class);
        bind(PresentationServicesSetup.class).in(Singleton.class);

        // node
        bind(BsqNodeProvider.class).in(Singleton.class);
        bind(NodeExecutor.class).in(Singleton.class);
        bind(RpcService.class).in(Singleton.class);
        bind(FullNode.class).in(Singleton.class);
        bind(FullNodeExecutor.class).in(Singleton.class);
        bind(FullNodeNetworkService.class).in(Singleton.class);
        bind(FullNodeParser.class).in(Singleton.class);
        bind(LiteNode.class).in(Singleton.class);
        bind(LiteNodeNetworkService.class).in(Singleton.class);
        bind(LiteNodeExecutor.class).in(Singleton.class);
        bind(LiteNodeParser.class).in(Singleton.class);

        // chain state
        bind(State.class).in(Singleton.class);
        bind(StateService.class).in(Singleton.class);
        bind(StateServiceFacade.class).in(Singleton.class);
        bind(SnapshotManager.class).in(Singleton.class);
        bind(ChangeParamService.class).in(Singleton.class);
        bind(JsonBlockChainExporter.class).in(Singleton.class);

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

        bind(PeriodState.class).in(Singleton.class);
        bind(PeriodStateMutator.class).in(Singleton.class);
        bind(PeriodService.class).in(Singleton.class);
        bind(PeriodServiceFacade.class).in(Singleton.class);

        // proposals
        bind(BallotListService.class).in(Singleton.class);
        bind(FilteredBallotListService.class).in(Singleton.class);
        bind(MyBallotListService.class).in(Singleton.class);
        bind(ProposalValidator.class).in(Singleton.class);
        bind(CompensationBallotFactory.class).in(Singleton.class);
        bind(CompensationValidator.class).in(Singleton.class);
        bind(GenericBallotFactory.class).in(Singleton.class);

        // vote
        bind(MyBlindVoteServiceFacade.class).in(Singleton.class);
        bind(MyBlindVoteService.class).in(Singleton.class);
        bind(BlindVoteServiceFacade.class).in(Singleton.class);
        bind(BlindVoteService.class).in(Singleton.class);
        bind(BlindVoteValidator.class).in(Singleton.class);
        bind(VoteRevealService.class).in(Singleton.class);
        bind(VoteResultService.class).in(Singleton.class);
        bind(IssuanceService.class).in(Singleton.class);

        // constants
        String genesisTxId = environment.getProperty(DaoOptionKeys.GENESIS_TX_ID, String.class, State.DEFAULT_GENESIS_TX_ID);
        bind(String.class).annotatedWith(Names.named(DaoOptionKeys.GENESIS_TX_ID)).toInstance(genesisTxId);

        Integer genesisBlockHeight = environment.getProperty(DaoOptionKeys.GENESIS_BLOCK_HEIGHT, Integer.class, State.DEFAULT_GENESIS_BLOCK_HEIGHT);
        bind(Integer.class).annotatedWith(Names.named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT)).toInstance(genesisBlockHeight);

        Boolean daoActivated = environment.getProperty(DaoOptionKeys.DAO_ACTIVATED, Boolean.class, false);
        bind(Boolean.class).annotatedWith(Names.named(DaoOptionKeys.DAO_ACTIVATED)).toInstance(daoActivated);

        // options
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_USER)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_USER));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_PASSWORD)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_PASSWORD));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_PORT)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_PORT));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT))
                .to(environment.getRequiredProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT));
        bindConstant().annotatedWith(named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA))
                .to(environment.getRequiredProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA));
        bindConstant().annotatedWith(named(DaoOptionKeys.FULL_DAO_NODE))
                .to(environment.getRequiredProperty(DaoOptionKeys.FULL_DAO_NODE));
    }
}

