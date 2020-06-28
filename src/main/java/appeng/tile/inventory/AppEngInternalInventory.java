/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.tile.inventory;

import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.item.FixedItemInvView;
import alexiil.mc.lib.attributes.item.filter.ConstantItemFilter;
import alexiil.mc.lib.attributes.item.filter.ItemFilter;
import alexiil.mc.lib.attributes.item.impl.FullFixedItemInv;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.inv.filter.IAEItemFilter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

// FIXME: the filtering is not correctly implemented and need to be reworked
public class AppEngInternalInventory extends FullFixedItemInv implements Iterable<ItemStack> {
    private boolean enableClientEvents = false;
    private IAEAppEngInventory te;
    private final int[] maxStack;
    private IAEItemFilter filter;
    private boolean dirtyFlag = false;

    public AppEngInternalInventory(final IAEAppEngInventory inventory, final int size, final int maxStack,
            IAEItemFilter filter) {
        super(size);
        this.setTileEntity(inventory);
        this.setFilter(filter);
        this.maxStack = new int[size];
        Arrays.fill(this.maxStack, maxStack);

        setOwnerListener(this::onContentsChanged);
    }

    public AppEngInternalInventory(final IAEAppEngInventory inventory, final int size, final int maxStack) {
        this(inventory, size, maxStack, null);
    }

    public AppEngInternalInventory(final IAEAppEngInventory inventory, final int size) {
        this(inventory, size, 64);
    }

    public void setFilter(IAEItemFilter filter) {
        this.filter = filter;
    }

    @Override
    public int getMaxAmount(int slot, ItemStack stack) {
        return Math.min(maxStack[slot], super.getMaxAmount(slot, stack));
    }

    protected void onContentsChanged(FixedItemInvView inv, int slot, ItemStack previous, ItemStack current) {
        if (this.getBlockEntity() != null && this.eventsEnabled() && !this.dirtyFlag) {
            this.dirtyFlag = true;
            ItemStack newStack = current.copy();
            ItemStack oldStack = previous;
            InvOperation op = InvOperation.SET;

            if (newStack.isEmpty() || oldStack.isEmpty() || ItemStack.areItemsEqual(newStack, oldStack)) {
                if (newStack.getCount() > oldStack.getCount()) {
                    newStack.decrement(oldStack.getCount());
                    oldStack = ItemStack.EMPTY;
                    op = InvOperation.INSERT;
                } else {
                    oldStack.decrement(newStack.getCount());
                    newStack = ItemStack.EMPTY;
                    op = InvOperation.EXTRACT;
                }
            }

            this.getBlockEntity().onChangeInventory(this, slot, op, oldStack, newStack);
            this.getBlockEntity().saveChanges();
            this.dirtyFlag = false;
        }
    }

    protected boolean eventsEnabled() {
        return Platform.isServer() || this.isEnableClientEvents();
    }

    public void setMaxStackSize(final int slot, final int size) {
        this.maxStack[slot] = size;
    }

    @Override
    public ItemFilter getFilterForSlot(int slot) {
        if (this.maxStack[slot] == 0) {
            return ConstantItemFilter.NOTHING;
        }
        if (this.filter != null) {
            return stack -> {
                // FIXME: This is not correct...
                if (stack == ItemStack.EMPTY) {
                    return filter.allowExtract(this, slot, this.getSlot(slot).get().getCount());
                } else {
                    return filter.allowExtract(this, slot, this.getSlot(slot).get().getCount());
                }
            };
        }
        return ConstantItemFilter.ANYTHING;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (this.maxStack[slot] == 0) {
            return false;
        }
        if (this.filter != null) {
            return this.filter.allowInsert(this, slot, stack);
        }
        return true;
    }

    public void writeToNBT(final CompoundTag data, final String name) {
        data.put(name, this.toTag());
    }

    public void readFromNBT(final CompoundTag data, final String name) {
        final CompoundTag c = data.getCompound(name);
        if (c != null) {
            this.readFromNBT(c);
        }
    }

    public void readFromNBT(final CompoundTag data) {
        this.fromTag(data);
    }

    @Override
    public Iterator<ItemStack> iterator() {
        return stackIterable().iterator();
    }

    private boolean isEnableClientEvents() {
        return this.enableClientEvents;
    }

    public void setEnableClientEvents(final boolean enableClientEvents) {
        this.enableClientEvents = enableClientEvents;
    }

    private IAEAppEngInventory getBlockEntity() {
        return this.te;
    }

    public void setTileEntity(final IAEAppEngInventory te) {
        this.te = te;
    }
}
