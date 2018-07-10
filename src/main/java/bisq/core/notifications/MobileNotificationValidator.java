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

package bisq.core.notifications;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MobileNotificationValidator {
    @Inject
    public MobileNotificationValidator() {
    }

    public boolean isValid(String keyAndToken) {
        if (keyAndToken == null)
            return false;

        String[] tokens = keyAndToken.split(MobileModel.PHONE_SEPARATOR_ESCAPED);
        if (tokens.length != 3) {
            log.error("invalid Bisq MobileModel ID format: not three sections separated by " + MobileModel.PHONE_SEPARATOR_WRITING);
            return false;
        }
        if (tokens[1].length() != 32) {
            log.error("invalid Bisq MobileModel ID format: key not 32 bytes");
            return false;
        }
        String token0 = tokens[0];
        String token2 = tokens[2];
        if (token0.equals(MobileModel.OS.IOS.getMagicString()) ||
                token0.equals(MobileModel.OS.IOS_DEV.getMagicString())) {
            if (token2.length() != 64) {
                log.error("invalid Bisq MobileModel ID format: iOS token not 64 bytes");
                return false;
            }
        } else if (token0.equals(MobileModel.OS.ANDROID.getMagicString())) {
            if (token2.length() < 32) {
                log.error("invalid Bisq MobileModel ID format: Android token too short (<32 bytes)");
                return false;
            }
        } else {
            log.error("invalid Bisq MobileModel ID format");
            return false;
        }

        return true;
    }
}
