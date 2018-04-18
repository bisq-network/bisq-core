package bisq.spi;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;

import java.util.concurrent.CompletableFuture;

public interface LoadableExtension {

    void decorateOptionParser(OptionParser parser);

    AbstractModule configure(OptionSet options);

    CompletableFuture<Void> preStart(Injector injector);

    CompletableFuture<Void> setup(Injector injector);

    void start(Injector injector);

}
