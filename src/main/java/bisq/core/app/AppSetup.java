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

package bisq.core.app;

import bisq.core.setup.CorePersistedDataHost;

import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.P2PService;

import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.proto.persistable.PersistedDataHost;

import com.google.inject.Injector;

import javax.inject.Inject;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AppSetup {
    private final EncryptionService encryptionService;
    private final Injector injector;
    protected final KeyRing keyRing;
    private final P2PService p2PService;
    private CompletableFuture<Void> checkCryptoSetupResult;
    private CompletableFuture<Void> initPersistedDataHostsResult;
    private CompletableFuture<Void> initBasicServicesResult;
    private CompletableFuture<Void> readFromResourcesResult;

    @Inject
    public AppSetup(EncryptionService encryptionService,
                    Injector injector,
                    KeyRing keyRing,
                    P2PService p2PService) {
        // we need to reference it so the seed node stores tradeStatistics
        this.encryptionService = encryptionService;
        this.injector = injector;
        this.keyRing = keyRing;
        this.p2PService = p2PService;

        Version.setBaseCryptoNetworkId(BisqEnvironment.getBaseCurrencyNetwork().ordinal());
        Version.printVersion();
    }

    private CompletableFuture<Void> checkCryptoSetup() {
        if (null != checkCryptoSetupResult)
            return checkCryptoSetupResult;

        checkCryptoSetupResult = new CompletableFuture<>();

        SetupUtils.checkCryptoSetup(keyRing, encryptionService, () -> checkCryptoSetupResult.complete(null), throwable -> {
            log.error(throwable.getMessage());
            throwable.printStackTrace();
            checkCryptoSetupResult.completeExceptionally(throwable);
            System.exit(1);
        });

        return checkCryptoSetupResult;
    }

    public CompletableFuture<Void> initPersistedDataHosts() {
        if (null != initPersistedDataHostsResult)
            return initPersistedDataHostsResult;
        initPersistedDataHostsResult = checkCryptoSetup()
                .thenRun(this::doInitPersistedDataHosts);
        return initPersistedDataHostsResult;
    }

    protected CompletableFuture<Void> readFromResources() {
        if (null != readFromResourcesResult)
            return readFromResourcesResult;
        readFromResourcesResult = new CompletableFuture<>();
        SetupUtils.readFromResources(p2PService.getP2PDataStorage()).addListener((observable, oldValue, newValue) -> {
            if (!newValue) return;
            readFromResourcesResult.complete(null);
        });
        return readFromResourcesResult;
    }

    private void doInitPersistedDataHosts() {
        log.debug("doInitPersistedDataHosts");
        PersistedDataHost.apply(CorePersistedDataHost.getPersistedDataHosts(injector));
    }

    public CompletableFuture<Void> initBasicServices() {
        if (null == initBasicServicesResult)
            initBasicServicesResult = initPersistedDataHosts()
                    .thenCompose(r -> this.doInitBasicServices())
                    .thenRun(this::onBasicServicesInitialized);
        return initBasicServicesResult;
    }

    protected abstract CompletableFuture<Void> doInitBasicServices();

    protected abstract void onBasicServicesInitialized();

}
