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

package appeng.bootstrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import appeng.api.features.AEFeature;
import appeng.bootstrap.components.IInitComponent;
import appeng.core.AEItemGroup;
import appeng.core.AppEng;
import appeng.core.CreativeTab;
import appeng.core.features.ItemDefinition;
import appeng.util.Platform;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

class ItemDefinitionBuilder implements IItemBuilder {

    private final FeatureFactory factory;

    private final Identifier id;

    private final Function<Item.Settings, Item> itemFactory;

    private final EnumSet<AEFeature> features = EnumSet.noneOf(AEFeature.class);

    private final List<Function<Item, IBootstrapComponent>> boostrapComponents = new ArrayList<>();

    private final Item.Settings props = new Item.Settings();

    private Supplier<DispenserBehavior> dispenserBehaviorSupplier;

    @Environment(EnvType.CLIENT)
    private ItemRendering itemRendering;

    private ItemGroup itemGroup = CreativeTab.INSTANCE;

    ItemDefinitionBuilder(FeatureFactory factory, String id, Function<Item.Settings, Item> itemFactory) {
        this.factory = factory;
        this.id = AppEng.makeId(id);
        this.itemFactory = itemFactory;
        if (Platform.hasClientClasses()) {
            this.itemRendering = new ItemRendering();
        }
    }

    @Override
    public IItemBuilder bootstrap(Function<Item, IBootstrapComponent> component) {
        this.boostrapComponents.add(component);
        return this;
    }

    @Override
    public IItemBuilder features(AEFeature... features) {
        this.features.clear();
        this.addFeatures(features);
        return this;
    }

    @Override
    public IItemBuilder addFeatures(AEFeature... features) {
        Collections.addAll(this.features, features);
        return this;
    }

    @Override
    public IItemBuilder itemGroup(ItemGroup itemGroup) {
        this.itemGroup = itemGroup;
        return this;
    }

    @Override
    public IItemBuilder props(Consumer<Item.Settings> consumer) {
        consumer.accept(props);
        return this;
    }

    @Override
    public IItemBuilder rendering(ItemRenderingCustomizer callback) {
        if (Platform.hasClientClasses()) {
            this.customizeForClient(callback);
        }

        return this;
    }

    @Override
    public IItemBuilder dispenserBehavior(Supplier<DispenserBehavior> behavior) {
        this.dispenserBehaviorSupplier = behavior;
        return this;
    }

    @Environment(EnvType.CLIENT)
    private void customizeForClient(ItemRenderingCustomizer callback) {
        callback.customize(this.itemRendering);
    }

    @Override
    public ItemDefinition build() {
        props.group(itemGroup);

        Item item = this.itemFactory.apply(props);

        ItemDefinition definition = new ItemDefinition(id.getPath(), item, features);

        // Register all extra handlers
        this.boostrapComponents.forEach(component -> this.factory.addBootstrapComponent(component.apply(item)));

        // Register custom dispenser behavior if requested
        if (this.dispenserBehaviorSupplier != null) {
            this.factory.addBootstrapComponent((IInitComponent) () -> {
                DispenserBehavior behavior = this.dispenserBehaviorSupplier.get();
                DispenserBlock.registerBehavior(item, behavior);
            });
        }

        Registry.register(Registry.ITEM, id, item);

        if (Platform.hasClientClasses()) {
            this.itemRendering.apply(this.factory, item);
        }

        if (itemGroup instanceof AEItemGroup) {
            ((AEItemGroup) itemGroup).add(definition);
        }

        return definition;
    }

}
