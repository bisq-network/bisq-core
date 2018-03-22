/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.blockchain;

import bisq.core.dao.node.BsqNode;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Passing the BsqNode directly to the classes interested in onBsqBlockChainChanged events cause Guice dependency issues,
 * so we use that object to isolate that concern.
 * <p>
 * TODO check if refactorings has solved the dependency problems.
 */
@Slf4j
public class BsqBlockChainChangeDispatcher implements BsqNode.BsqBlockChainListener {
    private final List<BsqNode.BsqBlockChainListener> bsqBlockChainListeners = new ArrayList<>();

    public BsqBlockChainChangeDispatcher() {
    }

    @Override
    public void onBsqBlockChainChanged() {
        bsqBlockChainListeners.stream().forEach(BsqNode.BsqBlockChainListener::onBsqBlockChainChanged);
    }

    public void addBsqBlockChainListener(BsqNode.BsqBlockChainListener bsqBlockChainListener) {
        bsqBlockChainListeners.add(bsqBlockChainListener);
    }

    public void removeBsqBlockChainListener(BsqNode.BsqBlockChainListener bsqBlockChainListener) {
        bsqBlockChainListeners.remove(bsqBlockChainListener);
    }
}
