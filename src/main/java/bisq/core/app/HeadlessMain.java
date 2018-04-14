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
import bisq.common.util.Utilities;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.app.BisqEnvironment.DEFAULT_APP_NAME;
import static bisq.core.app.BisqEnvironment.DEFAULT_USER_DATA_DIR;

@Slf4j
public class HeadlessMain extends BisqExecutable {
    private Headless headless;

    static {
        Utilities.removeCryptographyRestrictions();
    }

    public static void main(String[] args) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("HeadlessMain")
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        // We don't want to do the full argument parsing here as that might easily change in update versions
        // So we only handle the absolute minimum which is APP_NAME, APP_DATA_DIR_KEY and USER_DATA_DIR
        BisqEnvironment.setDefaultAppName("bisq_headless");
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.accepts(AppOptionKeys.USER_DATA_DIR_KEY, description("User data directory", DEFAULT_USER_DATA_DIR))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.APP_NAME_KEY, description("Application name", DEFAULT_APP_NAME))
                .withRequiredArg();

        try {
            OptionSet options = parser.parse(args);
            BisqEnvironment bisqEnvironment = getBisqEnvironment(options);
            BisqExecutable.initAppDir(bisqEnvironment.getProperty(AppOptionKeys.APP_DATA_DIR_KEY));

            new HeadlessMain().execute(args);
        } catch (Throwable t) {
            System.out.println(t.toString());
            System.exit(EXIT_FAILURE);
        }
    }

    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    protected void doExecute(OptionSet options) {
        final BisqEnvironment bisqEnvironment = getBisqEnvironment(options);
        Headless.setEnvironment(bisqEnvironment);

        UserThread.execute(() -> {
            try {
                headless = new Headless();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }
}
