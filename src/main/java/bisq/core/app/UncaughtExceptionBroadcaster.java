package bisq.core.app;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

public class UncaughtExceptionBroadcaster {
    private Set<BiConsumer<Throwable, Boolean>> handlers = new HashSet<>();

    public void addExceptionHandler(BiConsumer<Throwable, Boolean> handler) {
        this.handlers.add(handler);
    }

    public void broadcast(Throwable throwable, Boolean doShutDown) {
         handlers.forEach(handler-> handler.accept(throwable,doShutDown));
    }
}
