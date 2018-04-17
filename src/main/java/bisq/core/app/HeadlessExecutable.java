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
import bisq.common.setup.ShutDownHandler;
import bisq.common.util.Profiler;
import bisq.common.util.RestartUtil;

import org.bitcoinj.store.BlockStoreException;

import joptsimple.OptionSet;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HeadlessExecutable extends BisqExecutable {
    private static final long MAX_MEMORY_MB_DEFAULT = 500;
    private static final long CHECK_MEMORY_PERIOD_SEC = 2 * 60;
    private volatile boolean stopped;
    private static long maxMemory = MAX_MEMORY_MB_DEFAULT;

    @Override
    protected void doExecute(OptionSet options) {
        super.doExecute(options);

        //TODO to be removed later
        Thread.UncaughtExceptionHandler handler = (thread, throwable) -> {
            if (throwable.getCause() != null && throwable.getCause().getCause() != null &&
                    throwable.getCause().getCause() instanceof BlockStoreException) {
                log.error(throwable.getMessage());
            } else {
                log.error("Uncaught Exception from thread " + Thread.currentThread().getName());
                log.error("throwableMessage= " + throwable.getMessage());
                log.error("throwableClass= " + throwable.getClass());
                log.error("Stack trace:\n" + ExceptionUtils.getStackTrace(throwable));
                throwable.printStackTrace();
                log.error("We shut down the app because an unhandled error occurred");
                // We don't use the restart as in case of OutOfMemory errors the restart might fail as well
                // The run loop will restart the node anyway...
                System.exit(EXIT_FAILURE);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);
    }

    protected void configUserThread() {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(this.getClass().getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));
    }

    @SuppressWarnings("InfiniteLoopStatement")
    protected void keepRunning() {
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }

    protected void checkMemory(BisqEnvironment environment, ShutDownHandler shutDownHandler) {
        String maxMemoryOption = environment.getProperty(AppOptionKeys.MAX_MEMORY);
        if (maxMemoryOption != null && !maxMemoryOption.isEmpty()) {
            try {
                maxMemory = Integer.parseInt(maxMemoryOption);
            } catch (Throwable t) {
                log.error(t.getMessage());
            }
        }

        UserThread.runPeriodically(() -> {
            Profiler.printSystemLoad(log);
            if (!stopped) {
                long usedMemoryInMB = Profiler.getUsedMemoryInMB();
                if (usedMemoryInMB > (maxMemory * 0.8)) {
                    log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                    "We are over our memory warn limit and call the GC. usedMemoryInMB: {}" +
                                    "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                            usedMemoryInMB);
                    System.gc();
                    Profiler.printSystemLoad(log);
                }

                UserThread.runAfter(() -> {
                    if (Profiler.getUsedMemoryInMB() > maxMemory)
                        restart(environment, shutDownHandler);
                }, 5);
            }
        }, CHECK_MEMORY_PERIOD_SEC);
    }

    protected void restart(BisqEnvironment bisqEnvironment, ShutDownHandler shutDownHandler) {
        stopped = true;
        shutDownHandler.gracefulShutDown(() -> {
            //noinspection finally
            try {
                final String[] tokens = bisqEnvironment.getAppDataDir().split("_");
                String logPath = "error_" + (tokens.length > 1 ? tokens[tokens.length - 2] : "") + ".log";
                RestartUtil.restartApplication(logPath);
            } catch (IOException e) {
                log.error(e.toString());
                e.printStackTrace();
            } finally {
                log.warn("Shutdown complete");
                System.exit(0);
            }
        });
    }

}
