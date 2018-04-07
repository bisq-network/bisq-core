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

package bisq.core.network.p2p;

import bisq.common.app.Capabilities;

import java.util.ArrayList;
import java.util.Arrays;

public class CoreNetworkCapabilities {
    public static void init() {
        Capabilities.setSupportedCapabilities(new ArrayList<>(Arrays.asList(
                Capabilities.Capability.TRADE_STATISTICS.ordinal(),
                Capabilities.Capability.TRADE_STATISTICS_2.ordinal(),
                Capabilities.Capability.ACCOUNT_AGE_WITNESS.ordinal(),
                Capabilities.Capability.COMP_REQUEST.ordinal(),
                Capabilities.Capability.VOTE.ordinal()
        )));
    }
}
