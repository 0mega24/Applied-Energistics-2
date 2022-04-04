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

package appeng.crafting;


import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import com.google.common.collect.ImmutableCollection;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;


public class CraftingTreeProcess
{
	final ICraftingPatternDetails details;
	private final CraftingTreeNode parent;
	private final CraftingJob job;
	private final Object2LongArrayMap<CraftingTreeNode> nodes = new Object2LongArrayMap<>();
	boolean possible = true;
	private long crafts = 0;
	private long bytes = 0;
	private boolean hasContainerItem = false;

	public CraftingTreeProcess( final ICraftingGrid cc, final CraftingJob job, final ICraftingPatternDetails details, int times, final CraftingTreeNode craftingTreeNode )
	{
		this.parent = craftingTreeNode;
		this.details = details;
		this.job = job;
		World world = job.getWorld();

		final IAEItemStack[] list = details.getInputs();

		// this is minor different then below, this slot uses the pattern, but kinda fudges it.
		for( IAEItemStack part : details.getCondensedInputs() )
		{
			if( part == null )
			{
				continue;
			}
			for( int x = 0; x < list.length; x++ )
			{
				final IAEItemStack comparePart = list[x];
				if( part.equals( comparePart ) )
				{
					boolean isPartContainer = false;
					if( part.getItem().hasContainerItem( part.getDefinition() ) )
					{
						part = list[x];
						isPartContainer = true;
						this.hasContainerItem = true;
					}

					long wantedSize = isPartContainer ? part.getStackSize() : part.getStackSize() * times;

					if( details.canSubstitute() && cc.getCraftingFor( part, details, x, world ).isEmpty() )
					{
						for( IAEItemStack subs : details.getSubstituteInputs( x ) )
						{
							if( subs.fuzzyComparison( part, FuzzyMode.IGNORE_ALL ) )
							{
								this.nodes.put( new CraftingTreeNode( cc, job, subs.copy(), wantedSize, this, x ), part.getStackSize() / times );
								wantedSize = 0;
								break;
							}
						}
						//try to order the crafting of a substitute
						ICraftingPatternDetails prioritizedPattern = null;
						IAEItemStack prioritizedIAE = null;
						for( IAEItemStack subs : details.getSubstituteInputs( x ) )
						{
							if( subs.equals( part ) )
							{
								continue;
							}
							ImmutableCollection<ICraftingPatternDetails> detailCollection = cc.getCraftingFor( subs, details, x, world );

							for( ICraftingPatternDetails sp : detailCollection )
							{
								if( prioritizedPattern == null )
								{
									prioritizedPattern = sp;
									prioritizedIAE = subs;
								}
								else
								{
									if( sp.getPriority() > prioritizedPattern.getPriority() )
									{
										prioritizedPattern = sp;
									}
								}
							}
							if( prioritizedIAE != null )
							{
								this.nodes.put( new CraftingTreeNode( cc, job, prioritizedIAE.copy(), wantedSize, this, x ), part.getStackSize() / times );
								wantedSize = 0;
								break;
							}
						}
					}
					if( wantedSize > 0 )
					{
						part = part.copy().setStackSize( wantedSize );
						// use the first slot...
						this.nodes.put( new CraftingTreeNode( cc, job, part.copy(), wantedSize, this, x ), part.getStackSize() / times );
						wantedSize = 0;
					}
					if( !isPartContainer && wantedSize == 0 )
					{
						break;
					}
				}
			}
		}
	}

	CraftingTreeNode notRecursive( CraftingTreeNode node )
	{
		if( parent == null )
		{
			return null;
		}
		return this.parent.recursiveNode( node );
	}

	long getTimes( final long remaining, final long stackSize )
	{
		if( hasContainerItem )
		{
			return 1;
		}
		return ( remaining / stackSize ) + ( remaining % stackSize != 0 ? 1 : 0 );
	}

	void request( final MECraftingInventory inv, final long amountOfTimes, final IActionSource src ) throws CraftBranchFailure, InterruptedException
	{
		this.job.handlePausing();
		List<IAEItemStack> containerItems = null;

		// request and remove inputs...
		for( final Entry<CraftingTreeNode, Long> entry : this.nodes.object2LongEntrySet() )
		{
			final IAEItemStack stack = entry.getKey().request( inv, details.isCraftable() && hasContainerItem ? 1 : entry.getValue() * amountOfTimes, src );

			if( stack.equals( job.getOutput() ) )
			{
				job.getNeededForLoop().add( stack.copy() );
			}

			if( details.isCraftable() && stack.getItem().hasContainerItem( stack.getDefinition() ) )
			{
				final ItemStack is = Platform.getContainerItem( stack.createItemStack() );
				final IAEItemStack o = AEItemStack.fromItemStack( is );
				if( o != null )
				{
					if( containerItems == null )
					{
						containerItems = new ArrayList<>();
					}
					this.bytes++;
					o.setCachedItemStack( is );
					containerItems.add( o );
				}
				if( containerItems != null )
				{
					for( IAEItemStack i : containerItems )
					{
						inv.injectItems( i, Actionable.MODULATE, src );
					}
				}
			}
		}


		// assume its possible.

		// add crafting results..
		for( final IAEItemStack out : this.details.getCondensedOutputs() )
		{
			final IAEItemStack o = out.copy();
			o.setStackSize( o.getStackSize() * amountOfTimes );
			inv.injectItems( o, Actionable.MODULATE, src );
		}
		this.crafts += amountOfTimes;
	}

	void dive( final CraftingJob job )
	{
		job.addTask( this.getAmountCrafted( this.parent.getStack( 1 ) ), this.crafts );
		for( final Entry<CraftingTreeNode, Long> entry : this.nodes.object2LongEntrySet() )
		{
			entry.getKey().dive( job );
		}

		job.addBytes( this.crafts * 8 + this.bytes );
	}

	IAEItemStack getAmountCrafted( IAEItemStack what2 )
	{
		for( final IAEItemStack is : this.details.getCondensedOutputs() )
		{
			if( is.isSameType( what2 ) )
			{
				what2 = what2.copy();
				what2.setStackSize( is.getStackSize() );
				return what2;
			}
		}

		// more fuzzy!
		for( final IAEItemStack is : this.details.getCondensedOutputs() )
		{
			if( is.getItem() == what2.getItem() && ( is.getItem().isDamageable() || is.getItemDamage() == what2.getItemDamage() ) )
			{
				what2 = is.copy();
				what2.setStackSize( is.getStackSize() );
				return what2;
			}
		}

		throw new IllegalStateException( "Crafting Tree construction failed." );
	}

	void setSimulate()
	{
		this.crafts = 0;
		this.bytes = 0;

		for( final Entry<CraftingTreeNode, Long> entry : this.nodes.object2LongEntrySet() )
		{
			entry.getKey().setSimulate();
		}
	}

	void setJob( final MECraftingInventory storage, final CraftingCPUCluster craftingCPUCluster, final IActionSource src ) throws CraftBranchFailure
	{
		craftingCPUCluster.addCrafting( this.details, this.crafts );

		for( final Entry<CraftingTreeNode, Long> entry : this.nodes.object2LongEntrySet() )
		{
			entry.getKey().setJob( storage, craftingCPUCluster, src );
		}
	}

	void getPlan( final IItemList<IAEItemStack> plan )
	{
		for( IAEItemStack i : this.details.getOutputs() )
		{
			i = i.copy();
			i.setCountRequestable( i.getStackSize() * this.crafts );
			plan.addRequestable( i );
		}

		for( final Entry<CraftingTreeNode, Long> entry : this.nodes.object2LongEntrySet() )
		{
			entry.getKey().getPlan( plan );
		}
	}

	public void reserveForNode()
	{
		parent.reserveForNode();
	}
}
