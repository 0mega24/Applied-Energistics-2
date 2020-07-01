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

package appeng.items.parts;

import java.util.function.Function;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.items.AEBaseItem;

public class PartItem<T extends IPart> extends AEBaseItem implements IPartItem<T> {

    private final Function<ItemStack, T> factory;

    public PartItem(Settings properties, Function<ItemStack, T> factory) {
        super(properties);
        this.factory = factory;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        ItemStack held = player.getStackInHand(context.getHand());
        if (held.getItem() != this) {
            return ActionResult.PASS;
        }

        return AEApi.instance().partHelper().placeBus(held, context.getBlockPos(), context.getSide(), player,
                context.getHand(), context.getWorld());
    }

    @Override
    public T createPart(ItemStack is) {
        return factory.apply(is);
    }

}
