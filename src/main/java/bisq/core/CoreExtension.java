package bisq.core;

import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;
import bisq.core.app.BisqExecutable;
import bisq.core.app.CoreAppSetup;
import bisq.core.btc.BtcOptionKeys;
import bisq.core.btc.RegTestHost;
import bisq.core.dao.DaoOptionKeys;
import bisq.core.setup.CoreSetup;
import bisq.core.util.joptsimple.EnumValueConverter;

import bisq.network.NetworkOptionKeys;
import bisq.network.p2p.P2PService;

import bisq.common.CommonOptionKeys;

import org.springframework.util.StringUtils;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.lang.String.format;
import static java.lang.String.join;



import bisq.spi.LoadableExtension;

public class CoreExtension implements LoadableExtension {
    @Override
    public void decorateOptionParser(OptionParser parser) {
        //CommonOptionKeys
        parser.accepts(CommonOptionKeys.LOG_LEVEL_KEY,
                description("Log level [OFF, ALL, ERROR, WARN, INFO, DEBUG, TRACE]", BisqEnvironment.LOG_LEVEL_DEFAULT))
                .withRequiredArg();

        //NetworkOptionKeys
        parser.accepts(NetworkOptionKeys.SEED_NODES_KEY,
                description("Override hard coded seed nodes as comma separated list: E.g. rxdkppp3vicnbgqt.onion:8002, mfla72c4igh5ta2t.onion:8002", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.MY_ADDRESS,
                description("My own onion address (used for botstrap nodes to exclude itself)", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.BAN_LIST,
                description("Nodes to exclude from network connections.", ""))
                .withRequiredArg();
        // use a fixed port as arbitrator use that for his ID
        parser.accepts(NetworkOptionKeys.PORT_KEY,
                description("Port to listen on", 9999))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P,
                description("Use localhost P2P network for development", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(NetworkOptionKeys.MAX_CONNECTIONS,
                description("Max. connections a peer will try to keep", P2PService.MAX_CONNECTIONS_DEFAULT))
                .withRequiredArg()
                .ofType(int.class);
        parser.accepts(NetworkOptionKeys.SOCKS_5_PROXY_BTC_ADDRESS,
                description("A proxy address to be used for Bitcoin network. [host:port]", ""))
                .withRequiredArg();
        parser.accepts(NetworkOptionKeys.SOCKS_5_PROXY_HTTP_ADDRESS,
                description("A proxy address to be used for Http requests (should be non-Tor). [host:port]", ""))
                .withRequiredArg();

        //AppOptionKeys
        parser.accepts(AppOptionKeys.USER_DATA_DIR_KEY,
                description("User data directory", BisqEnvironment.DEFAULT_USER_DATA_DIR))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.APP_NAME_KEY,
                description("Application name", BisqEnvironment.DEFAULT_APP_NAME))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.MAX_MEMORY,
                description("Max. permitted memory (used only at headless versions)", 600))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.APP_DATA_DIR_KEY,
                description("Application data directory", BisqEnvironment.DEFAULT_APP_DATA_DIR))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.IGNORE_DEV_MSG_KEY,
                description("If set to true all signed network_messages from bisq developers are ignored " +
                        "(Global alert, Version update alert, Filters for offers, nodes or trading account data)", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS,
                description("If that is true all the privileged features which requires a private key to enable it are overridden by a dev key pair " +
                        "(This is for developers only!)", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(CommonOptionKeys.USE_DEV_MODE,
                description("Enables dev mode which is used for convenience for developer testing", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(AppOptionKeys.DUMP_STATISTICS,
                description("If set to true the trade statistics are stored as json file in the data dir.", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(AppOptionKeys.PROVIDERS,
                description("Custom providers (comma separated)", false))
                .withRequiredArg();

        //BtcOptionKeys
        parser.accepts(BtcOptionKeys.BASE_CURRENCY_NETWORK,
                description("Base currency network", BisqEnvironment.getDefaultBaseCurrencyNetwork().name()))
                .withRequiredArg()
                .ofType(String.class);
        //.withValuesConvertedBy(new EnumValueConverter(String.class));
        parser.accepts(BtcOptionKeys.REG_TEST_HOST,
                description("", RegTestHost.DEFAULT))
                .withRequiredArg()
                .ofType(RegTestHost.class)
                .withValuesConvertedBy(new EnumValueConverter(RegTestHost.class));
        parser.accepts(BtcOptionKeys.BTC_NODES,
                description("Custom nodes used for BitcoinJ as comma separated IP addresses.", ""))
                .withRequiredArg();
        parser.accepts(BtcOptionKeys.USE_TOR_FOR_BTC,
                description("If set to true BitcoinJ is routed over tor (socks 5 proxy).", ""))
                .withRequiredArg();
        parser.accepts(BtcOptionKeys.SOCKS5_DISCOVER_MODE,
                description("Specify discovery mode for Bitcoin nodes. One or more of: [ADDR, DNS, ONION, ALL]" +
                        " (comma separated, they get OR'd together). Default value is ALL", "ALL"))
                .withRequiredArg();
        parser.accepts(BtcOptionKeys.USE_ALL_PROVIDED_NODES,
                description("Set to true if connection of bitcoin nodes should include clear net nodes", ""))
                .withRequiredArg();
        parser.accepts(BtcOptionKeys.USER_AGENT,
                description("User agent at btc node connections", ""))
                .withRequiredArg();
        parser.accepts(BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC,
                description("Number of connections to the Bitcoin network", "9"))
                .withRequiredArg();


        //RpcOptionKeys
        parser.accepts(DaoOptionKeys.RPC_USER,
                description("Bitcoind rpc username", ""))
                .withRequiredArg();
        parser.accepts(DaoOptionKeys.RPC_PASSWORD,
                description("Bitcoind rpc password", ""))
                .withRequiredArg();
        parser.accepts(DaoOptionKeys.RPC_PORT,
                description("Bitcoind rpc port", ""))
                .withRequiredArg();
        parser.accepts(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT,
                description("Bitcoind rpc port for block notifications", ""))
                .withRequiredArg();
        parser.accepts(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA,
                description("If set to true the blockchain data from RPC requests to Bitcoin Core are stored " +
                        "as json file in the data dir.", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(DaoOptionKeys.FULL_DAO_NODE,
                description("If set to true the node requests the blockchain data via RPC requests from Bitcoin Core and " +
                        "provide the validated BSQ txs to the network. It requires that the other RPC properties are " +
                        "set as well.", false))
                .withRequiredArg()
                .ofType(boolean.class);
        parser.accepts(DaoOptionKeys.GENESIS_TX_ID,
                description("Genesis transaction ID when not using the hard coded one", ""))
                .withRequiredArg();
        parser.accepts(DaoOptionKeys.GENESIS_BLOCK_HEIGHT,
                description("Genesis transaction block height when not using the hard coded one", ""))
                .withRequiredArg();
        parser.accepts("gui", "Enable GUI").withOptionalArg().ofType(boolean.class).defaultsTo(true);
    }

    @Override
    public AbstractModule configure(OptionSet options) {
        final BisqEnvironment environment = new BisqEnvironment(options);
//        TODO depending on response from cbeams this line should either stay here or go to setup method
        BisqExecutable.initAppDir(environment.getProperty(AppOptionKeys.APP_DATA_DIR_KEY));
        return new CoreModule(new BisqEnvironment(options));
    }

    public CompletableFuture<Void> setup(Injector injector) {
        CoreSetup.setup(injector.getInstance(BisqEnvironment.class));
        final CoreAppSetup desktopAppSetup = injector.getInstance(CoreAppSetup.class);
        try {
            desktopAppSetup.initPersistedDataHosts().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> preStart(Injector injector) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void start(Injector injector) {
        final Thread thread = new Thread(() -> {
            injector.getInstance(CoreAppSetup.class).initBasicServices();
        });
        thread.start();
    }

    private static String description(String descText, Object defaultValue) {
        String description = "";
        if (StringUtils.hasText(descText))
            description = description.concat(descText);
        if (defaultValue != null)
            description = join(" ", description, format("(default: %s)", defaultValue));
        return description;
    }
}
