package com.flansmod.common.tools;

import io.netty.buffer.ByteBuf;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

public class EntityParachute extends Entity implements IEntityAdditionalSpawnData
{
	public ToolType type;
	
	public EntityParachute(World w) 
	{
		super(w);
		ignoreFrustumCheck = true;
		System.out.println(w.isRemote ? "Client paraspawn" : "Server paraspawn");
		
		//increase render distance
		renderDistanceWeight = 250D;
	}
	
	public EntityParachute(World w, ToolType t, EntityPlayer player)
	{
		this(w);
		type = t;
		setPosition(player.posX, player.posY, player.posZ);
		
		//Give the parachute some horizontal speed on opening, depending on his y speed
		float speedMultiplier = 0.050F;
		double moveForwards = player.moveForward - 10*player.motionY;
		double moveStrafing = player.moveStrafing;
		double sinYaw = -Math.sin((player.rotationYaw * (float)Math.PI / 180.0F));
		double cosYaw = Math.cos((player.rotationYaw * (float)Math.PI / 180.0F));
		motionX += (moveForwards * sinYaw + moveStrafing * cosYaw) * speedMultiplier;
		motionZ += (moveForwards * cosYaw - moveStrafing * sinYaw) * speedMultiplier;
		motionY = player.motionY;
	}

	@Override
	public void onUpdate()
	{
		super.onUpdate();
		
		if(!worldObj.isRemote && (riddenByEntity == null || riddenByEntity.ridingEntity != this))
		{
			setDead();
		}
		
		if(riddenByEntity != null)
			riddenByEntity.fallDistance = 0F;
		
		//smoothly slowing down after opening
		if(motionY < -0.1D)
			motionY *= 0.85d;
		
		if(motionY > -0.1D)
			motionY = -0.1D;
		
		if(riddenByEntity != null && riddenByEntity instanceof EntityLivingBase)
		{
			float speedMultiplier = 0.050F; //a bit more speed
			double moveForwards = ((EntityLivingBase)this.riddenByEntity).moveForward;
			double moveStrafing = ((EntityLivingBase)this.riddenByEntity).moveStrafing;
			double sinYaw = -Math.sin((riddenByEntity.rotationYaw * (float)Math.PI / 180.0F));
			double cosYaw = Math.cos((this.riddenByEntity.rotationYaw * (float)Math.PI / 180.0F));
			motionX += (moveForwards * sinYaw + moveStrafing * cosYaw) * speedMultiplier;
			motionZ += (moveForwards * cosYaw - moveStrafing * sinYaw) * speedMultiplier;
			
			prevRotationYaw = rotationYaw;
			rotationYaw = riddenByEntity.rotationYaw;
		}		
		
		//slowly loosing horizontal speed
		motionX *= 0.99F;
		motionZ *= 0.99F;
			
		//max horizontal speed
		if(motionX > 0.55)
			motionX = 0.55f;
		if(motionZ > 0.55)
			motionZ = 0.55f;
		
		if(motionX < -0.55)
			motionX = -0.55f;
		if(motionZ < -0.55)
			motionZ = -0.55f;
		
		//horizontally faster -> going down faster
		motionY = motionY-(Math.max(-motionX, motionX)/8)-(Math.max(-motionZ, motionZ)/8);
		
		
		moveEntity(motionX, motionY, motionZ);
		
		if(onGround || worldObj.getBlockState(new BlockPos(MathHelper.floor_double(posX), MathHelper.floor_double(posY), MathHelper.floor_double(posZ))).getBlock().getMaterial() == Material.water)
		{
			setDead();
		}
	}
	
	@Override
	public void fall(float par1, float k)
    {
		//Hurting player on rough landings
		if(this.motionY < -0.3 && this.riddenByEntity != null)
			this.riddenByEntity.attackEntityFrom(DamageSource.fall, -(float) (this.motionY*10));
	}
	
	@Override
	public boolean attackEntityFrom(DamageSource source, float f)
	{
		setDead();
		return true;
	}
	
	@Override
	protected void entityInit() 
	{
	}
	
	@Override
	protected void readEntityFromNBT(NBTTagCompound tags) 
	{
		type = ToolType.getType(tags.getString("Type"));
	}

	@Override
	protected void writeEntityToNBT(NBTTagCompound tags) 
	{
		tags.setString("Type", type.shortName);
	}

	@Override
	public ItemStack getPickedResult(MovingObjectPosition target)
	{
		ItemStack stack = new ItemStack(type.item, 1, 0);
		return stack;
	}

	@Override
	public void writeSpawnData(ByteBuf buffer) 
	{
		ByteBufUtils.writeUTF8String(buffer, type.shortName);
	}

	@Override
	public void readSpawnData(ByteBuf additionalData) 
	{
		type = ToolType.getType(ByteBufUtils.readUTF8String(additionalData));
	}
}
