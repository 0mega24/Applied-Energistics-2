/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.features.registries;

import appeng.api.features.IPlayerRegistry;
import appeng.core.AppEng;
import appeng.core.worlddata.WorldData;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;

import javax.annotation.Nullable;
import java.util.UUID;

public class PlayerRegistry implements IPlayerRegistry {

    @Override
    public int getID(final GameProfile username) {
        if (username == null || !username.isComplete()) {
            return -1;
        }

        return WorldData.instance().playerData().getMePlayerId(username);
    }

    @Override
    public int getID(final PlayerEntity player) {
        return this.getID(player.getGameProfile());
    }

    @Nullable
    @Override
    public PlayerEntity findPlayer(final int playerID) {
        UUID profileId = WorldData.instance().playerData().getProfileId(playerID);
        if (profileId == null) {
            return null;
        }

        return AppEng.instance().getPlayers()
                .filter(p -> p.getGameProfile().getId().equals(profileId))
                .findFirst()
                .orElse(null);
    }
}
