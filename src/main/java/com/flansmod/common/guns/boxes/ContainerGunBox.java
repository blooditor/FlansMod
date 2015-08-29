package com.flansmod.common.guns.boxes;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class ContainerGunBox extends Container
{
	public InventoryPlayer inventory;
	
	public ContainerGunBox(InventoryPlayer inventoryplayer)
	{
		inventory = inventoryplayer;
		
		//Main inventory slots
		for(int row = 0; row < 3; row++)
		{
			for(int col = 0; col < 9; col++)
			{
				addSlotToContainer(new Slot(inventoryplayer, col + row * 9 + 9, 80 + col * 18, 79 + row * 18));
			}
		}
		
		//Quickbar slots
		for(int col = 0; col < 9; col++)
		{
			addSlotToContainer(new Slot(inventoryplayer, col, 8 + col * 18, 137));
		}
	}

	@Override
	public ItemStack transferStackInSlot(EntityPlayer player, int slotID)
	{
		ItemStack stack = null;
		Slot currentSlot = (Slot)inventorySlots.get(slotID);

		if(currentSlot != null && currentSlot.getHasStack())
		{
			ItemStack slotStack = currentSlot.getStack();
			stack = slotStack.copy();

			if(slotID != 0)
			{
				if(!mergeItemStack(slotStack, 0, 1, false))
				{
					return null;
				}
			}
			else {
				if(!mergeItemStack(slotStack, 1, inventorySlots.size(), true))
				{
					return null;
				}
			}

			if (slotStack.stackSize == 0)
			{
				currentSlot.putStack(null);
			}
			else
			{
				currentSlot.onSlotChanged();
			}

			if (slotStack.stackSize == stack.stackSize)
			{
				return null;
			}

			currentSlot.onPickupFromSlot(player, slotStack);
		}

		return stack;
	}

	@Override
	public boolean canInteractWith(EntityPlayer entityplayer)
	{
		return true;
	}

}