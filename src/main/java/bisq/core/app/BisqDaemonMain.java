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

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.setup.CommonSetup;
import bisq.common.setup.UncaughtExceptionHandler;

import joptsimple.OptionSet;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Application for headless version of Bisq with all features enabled.
 * Can run standalone but does not has any interface without any of the application wrappers
 * like Desktop, Http-API and gRPC.
 */
@Slf4j
public class BisqDaemonMain extends BisqExecutable implements UncaughtExceptionHandler {

    private BisqDaemon bisqDaemon;

    public static void main(String[] args) throws Exception {
        if (BisqExecutable.setupInitialOptionParser(args)) {
            // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
            // In order to work around a bug in JavaFX 8u25 and below, you must include the following code as the first line of your realMain method:
            Thread.currentThread().setContextClassLoader(BisqDaemonMain.class.getClassLoader());

            new BisqDaemonMain().execute(args);
        }
    }

    @Override
    protected void doExecute(OptionSet options) {
        super.doExecute(options);

        CommonSetup.setup(this);

        keepRunning();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // First synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void configUserThread() {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(this.getClass().getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));
    }

    @Override
    protected void launchApplication() {
        UserThread.execute(() -> {
            try {
                bisqDaemon = new BisqDaemon();
                UserThread.execute(this::onApplicationLaunched);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onApplicationLaunched() {
        super.onApplicationLaunched();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected AppModule getModule() {
        return new BisqDaemonModule(bisqEnvironment);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        bisqDaemon.setInjector(injector);
    }

    @Override
    protected void startApplication() {
        bisqDaemon.startApplication();

    }

    @Override
    public void handleUncaughtException(Throwable throwable, boolean doShutDown) {
        log.error(throwable.toString());

        if (doShutDown)
            gracefulShutDown(() -> log.info("gracefulShutDown complete"));
    }

    private void keepRunning() {
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }
}

