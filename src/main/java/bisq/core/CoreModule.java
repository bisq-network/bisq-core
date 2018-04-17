package bisq.core;

import bisq.core.alert.AlertModule;
import bisq.core.app.AppOptionKeys;
import bisq.core.app.BisqEnvironment;
import bisq.core.app.CoreAppSetup;
import bisq.core.arbitration.ArbitratorModule;
import bisq.core.btc.BitcoinModule;
import bisq.core.dao.DaoModule;
import bisq.core.filter.FilterModule;
import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.network.p2p.seed.SeedNodeAddressLookup;
import bisq.core.offer.OfferModule;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.trade.TradeModule;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.crypto.EncryptionServiceModule;
import bisq.network.p2p.P2PModule;
import bisq.network.p2p.network.BridgeAddressProvider;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.Clock;
import bisq.common.CommonOptionKeys;
import bisq.common.app.AppModule;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.KeyStorage;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.storage.Storage;

import com.google.inject.Singleton;
import com.google.inject.name.Names;

import java.io.File;

import static com.google.inject.name.Names.named;

public class CoreModule extends AppModule {

    public CoreModule(BisqEnvironment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
//        TODO BisqEnvironment should be renamed to CoreEnvironment
        bind(BisqEnvironment.class).toInstance((BisqEnvironment) environment);

        bind(CoreAppSetup.class).in(Singleton.class);
        bind(KeyStorage.class).in(Singleton.class);
        bind(KeyRing.class).in(Singleton.class);
        bind(User.class).in(Singleton.class);
        bind(Clock.class).in(Singleton.class);
        bind(Preferences.class).in(Singleton.class);
        bind(BridgeAddressProvider.class).to(Preferences.class).in(Singleton.class);

        bind(SeedNodeAddressLookup.class).in(Singleton.class);
        bind(SeedNodeRepository.class).to(DefaultSeedNodeRepository.class).in(Singleton.class);

        File storageDir = new File(environment.getRequiredProperty(Storage.STORAGE_DIR));
        bind(File.class).annotatedWith(named(Storage.STORAGE_DIR)).toInstance(storageDir);

        File keyStorageDir = new File(environment.getRequiredProperty(KeyStorage.KEY_STORAGE_DIR));
        bind(File.class).annotatedWith(named(KeyStorage.KEY_STORAGE_DIR)).toInstance(keyStorageDir);

        bind(NetworkProtoResolver.class).to(CoreNetworkProtoResolver.class).in(Singleton.class);
        bind(PersistenceProtoResolver.class).to(CorePersistenceProtoResolver.class).in(Singleton.class);

        Boolean useDevPrivilegeKeys = environment.getProperty(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS, Boolean.class, false);
        bind(boolean.class).annotatedWith(Names.named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS)).toInstance(useDevPrivilegeKeys);

        Boolean useDevMode = environment.getProperty(CommonOptionKeys.USE_DEV_MODE, Boolean.class, false);
        bind(boolean.class).annotatedWith(Names.named(CommonOptionKeys.USE_DEV_MODE)).toInstance(useDevMode);

        // ordering is used for shut down sequence
        install(tradeModule());
        install(encryptionServiceModule());
        install(arbitratorModule());
        install(offerModule());
        install(p2pModule());
        install(bitcoinModule());
        install(daoModule());
        install(alertModule());
        install(filterModule());
    }

    private TradeModule tradeModule() {
        return new TradeModule(environment);
    }

    private EncryptionServiceModule encryptionServiceModule() {
        return new EncryptionServiceModule(environment);
    }

    private ArbitratorModule arbitratorModule() {
        return new ArbitratorModule(environment);
    }

    private AlertModule alertModule() {
        return new AlertModule(environment);
    }

    private FilterModule filterModule() {
        return new FilterModule(environment);
    }

    private OfferModule offerModule() {
        return new OfferModule(environment);
    }

    private P2PModule p2pModule() {
        return new P2PModule(environment);
    }

    private BitcoinModule bitcoinModule() {
        return new BitcoinModule(environment);
    }

    private DaoModule daoModule() {
        return new DaoModule(environment);
    }
}
