package bisq.core.payment.validation;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AssetProviderRegistry {

    private Map<String, AssetProvider> providers;

    private static AssetProviderRegistry instance;

    public static AssetProviderRegistry getInstance() {
        if (null == instance) {
            instance = new AssetProviderRegistry();
        }
        return instance;
    }

    private AssetProviderRegistry() {
    }

    @Nullable
    public AssetProvider getProviderByCurrencyCode(String code) {
        return getProvidersMap().get(code);
    }

    @Nonnull
    public Collection<AssetProvider> getProviders() {
        return Collections.unmodifiableCollection(getProvidersMap().values());
    }

    @Nonnull
    private Map<String, AssetProvider> getProvidersMap() {
        if (null == providers) {
            initProviders();
        }
        return providers;
    }

    private void initProviders() {
        providers = new HashMap<>();
        for (AssetProvider provider : ServiceLoader.load(AssetProvider.class)) {
            String currencyCode = provider.getCurrencyCode();
            AssetProvider existingProvider = providers.get(currencyCode);
            if (null != existingProvider) {
                final String providerClassName = provider.getClass().getCanonicalName();
                final String existingProviderClassName = existingProvider.getClass().getCanonicalName();
                String message = String.format("AssetProvider %s wants to register itself for asset %s which is already registered with %s", providerClassName, currencyCode, existingProviderClassName);
                throw new RuntimeException(message);
            }
            providers.put(currencyCode, provider);
        }
    }
}
