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

import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.node.full.FullNode;
import bisq.core.dao.node.full.FullNodeParser;
import bisq.core.dao.node.full.RpcService;
import bisq.core.dao.node.full.network.FullNodeNetworkService;
import bisq.core.dao.node.json.JsonBlockChainExporter;
import bisq.core.dao.node.lite.LiteNode;
import bisq.core.dao.node.lite.LiteNodeParser;
import bisq.core.dao.node.lite.network.LiteNodeNetworkService;
import bisq.core.dao.node.validation.GenesisTxOutputIterator;
import bisq.core.dao.node.validation.GenesisTxOutputValidator;
import bisq.core.dao.node.validation.GenesisTxValidator;
import bisq.core.dao.node.validation.OpReturnBlindVoteValidator;
import bisq.core.dao.node.validation.OpReturnCompReqValidator;
import bisq.core.dao.node.validation.OpReturnProcessor;
import bisq.core.dao.node.validation.OpReturnProposalValidator;
import bisq.core.dao.node.validation.OpReturnVoteRevealValidator;
import bisq.core.dao.node.validation.TxInputProcessor;
import bisq.core.dao.node.validation.TxInputsIterator;
import bisq.core.dao.node.validation.TxOutputValidator;
import bisq.core.dao.node.validation.TxOutputsIterator;
import bisq.core.dao.node.validation.TxValidator;
import bisq.core.dao.state.SnapshotManager;
import bisq.core.dao.state.State;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.CycleService;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ballot.BallotListService;
import bisq.core.dao.voting.ballot.FilteredBallotListService;
import bisq.core.dao.voting.blindvote.BlindVoteListService;
import bisq.core.dao.voting.blindvote.BlindVoteService;
import bisq.core.dao.voting.blindvote.BlindVoteValidator;
import bisq.core.dao.voting.blindvote.storage.appendonly.BlindVoteAppendOnlyStorageService;
import bisq.core.dao.voting.blindvote.storage.appendonly.BlindVoteAppendOnlyStore;
import bisq.core.dao.voting.blindvote.storage.protectedstorage.BlindVoteStorageService;
import bisq.core.dao.voting.blindvote.storage.protectedstorage.BlindVoteStore;
import bisq.core.dao.voting.myvote.MyVoteListService;
import bisq.core.dao.voting.proposal.FilteredProposalListService;
import bisq.core.dao.voting.proposal.MyProposalListService;
import bisq.core.dao.voting.proposal.ProposalService;
import bisq.core.dao.voting.proposal.ProposalValidator;
import bisq.core.dao.voting.proposal.compensation.CompensationProposalService;
import bisq.core.dao.voting.proposal.compensation.CompensationValidator;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalAppendOnlyStorageService;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalAppendOnlyStore;
import bisq.core.dao.voting.proposal.storage.protectedstorage.ProposalStorageService;
import bisq.core.dao.voting.proposal.storage.protectedstorage.ProposalStore;
import bisq.core.dao.voting.voteresult.VoteResultService;
import bisq.core.dao.voting.voteresult.issuance.IssuanceService;
import bisq.core.dao.voting.votereveal.VoteRevealService;

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
        bind(FullNodeNetworkService.class).in(Singleton.class);
        bind(FullNodeParser.class).in(Singleton.class);
        bind(LiteNode.class).in(Singleton.class);
        bind(LiteNodeNetworkService.class).in(Singleton.class);
        bind(LiteNodeParser.class).in(Singleton.class);

        // State
        bind(State.class).in(Singleton.class);
        bind(StateService.class).in(Singleton.class);
        bind(SnapshotManager.class).in(Singleton.class);
        bind(JsonBlockChainExporter.class).in(Singleton.class);

        // Period
        bind(CycleService.class).in(Singleton.class);
        bind(PeriodService.class).in(Singleton.class);

        // blockchain parser
        bind(GenesisTxValidator.class).in(Singleton.class);
        bind(GenesisTxOutputIterator.class).in(Singleton.class);
        bind(GenesisTxOutputValidator.class).in(Singleton.class);
        bind(TxValidator.class).in(Singleton.class);
        bind(TxInputsIterator.class).in(Singleton.class);
        bind(TxInputProcessor.class).in(Singleton.class);
        bind(TxOutputsIterator.class).in(Singleton.class);
        bind(TxOutputValidator.class).in(Singleton.class);
        bind(OpReturnProcessor.class).in(Singleton.class);
        bind(OpReturnProposalValidator.class).in(Singleton.class);
        bind(OpReturnCompReqValidator.class).in(Singleton.class);
        bind(OpReturnBlindVoteValidator.class).in(Singleton.class);
        bind(OpReturnVoteRevealValidator.class).in(Singleton.class);

        // Proposal
        bind(ProposalService.class).in(Singleton.class);
        bind(MyProposalListService.class).in(Singleton.class);
        bind(FilteredProposalListService.class).in(Singleton.class);

        bind(ProposalAppendOnlyStore.class).in(Singleton.class);
        bind(ProposalAppendOnlyStorageService.class).in(Singleton.class);
        bind(ProposalStore.class).in(Singleton.class);
        bind(ProposalStorageService.class).in(Singleton.class);
        bind(ProposalValidator.class).in(Singleton.class);

        bind(CompensationValidator.class).in(Singleton.class);
        bind(CompensationProposalService.class).in(Singleton.class);

        // Ballot
        bind(BallotListService.class).in(Singleton.class);
        bind(FilteredBallotListService.class).in(Singleton.class);

        // MyVote
        bind(MyVoteListService.class).in(Singleton.class);

        // BlindVote
        bind(BlindVoteService.class).in(Singleton.class);
        bind(BlindVoteAppendOnlyStore.class).in(Singleton.class);
        bind(BlindVoteAppendOnlyStorageService.class).in(Singleton.class);
        bind(BlindVoteStore.class).in(Singleton.class);
        bind(BlindVoteStorageService.class).in(Singleton.class);
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

