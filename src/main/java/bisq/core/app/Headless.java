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

import bisq.core.setup.CoreSetup;

import bisq.common.UserThread;
import bisq.common.setup.CommonSetup;

import com.google.inject.Guice;
import com.google.inject.Injector;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Headless {

    private static BisqEnvironment bisqEnvironment;

    public static void setEnvironment(BisqEnvironment bisqEnvironment) {
        Headless.bisqEnvironment = bisqEnvironment;
    }

    private final Injector injector;
    private final HeadlessModule module;
    private final AppSetup appSetup;

    public Headless() {

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("HeadlessMain")
                .setDaemon(true)
                .build();

        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        CommonSetup.setup((throwable, doShutDown) -> {
            log.error(throwable.toString());
        });
        CoreSetup.setup(bisqEnvironment);

        module = new HeadlessModule(bisqEnvironment);
        injector = Guice.createInjector(module);

        appSetup = injector.getInstance(AppSetupWithP2P.class);
        appSetup.start();
    }
}
