package co.uk.flansmods.common.driveables;

import java.io.IOException;
import java.util.HashMap;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumMovingObjectType;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import co.uk.flansmods.api.IControllable;
import co.uk.flansmods.api.IExplodeable;
import co.uk.flansmods.common.EntityBullet;
import co.uk.flansmods.common.FlansMod;
import co.uk.flansmods.common.RotatedAxes;
import co.uk.flansmods.common.network.PacketDriveableDamage;
import co.uk.flansmods.common.vector.Vector3f;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;

public abstract class EntityDriveable extends Entity implements IControllable, IExplodeable, IEntityAdditionalSpawnData
{
	public boolean syncFromServer = true;
	/** Ticks since last server update. Use to smoothly transition to new position */
	public int serverPositionTransitionTicker;
	/** Server side position, as synced by PacketVehicleControl packets */
    public double serverPosX, serverPosY, serverPosZ;
	/** Server side rotation, as synced by PacketVehicleControl packets */
    public double serverYaw, serverPitch, serverRoll;
	
    /** The dataID, for obtaining the driveable data */
	public int dataID;
	/** The driveable data which contains the inventory, the engine and the fuel */
	public DriveableData driveableData;
	/** The shortName of the driveable type, used to obtain said type */
	public String driveableType;
	
	/** The throttle, in the range -1, 1 is multiplied by the maxThrottle (or maxNegativeThrottle) from the plane type to obtain the thrust */
	public float throttle;
	
	/** Each driveable part has a small class that holds its current status */
	public HashMap<EnumDriveablePart, DriveablePart> parts = new HashMap<EnumDriveablePart, DriveablePart>();

	public boolean fuelling;
	/** Extra prevRoation field for smoothness in all 3 rotational axes */
	public float prevRotationRoll;
	/** Angular velocity */
	public float velocityYaw, velocityPitch, velocityRoll;
	
	public RotatedAxes axes;
	
	public EntitySeat[] seats;
	
    public EntityDriveable(World world)
    {
        super(world);
		axes = new RotatedAxes();
        preventEntitySpawning = true;
        setSize(1F, 1F);
        yOffset = 6F / 16F;
		ignoreFrustumCheck = true;
		renderDistanceWeight = 200D;
    }
	
	public EntityDriveable(World world, DriveableType t, DriveableData d)
	{
		this(world);
		driveableType = t.shortName;
		driveableData = d;
	}
	
	protected void initType(DriveableType type, boolean clientSide)
	{
		seats = new EntitySeat[type.numPassengers + 1];
		for(int i = 0; i < type.numPassengers + 1; i++)
		{
			if(!clientSide)
			{
				seats[i] = new EntitySeat(worldObj, this, i);
				worldObj.spawnEntityInWorld(seats[i]);
			}
		}
		for(EnumDriveablePart part : EnumDriveablePart.values())
		{
			parts.put(part, new DriveablePart(part, type.health.get(part)));
		}
		yOffset = type.yOffset;
	}
	
	/** Make the plane / vehicle / helicopter / whatever class provide a data file that will suit its needs */
	protected abstract DriveableData getData(int dataID);
	
	@Override
    protected void writeEntityToNBT(NBTTagCompound tag)
    {
		tag.setInteger("DataID", dataID);
		driveableData.writeToNBT(tag);
		tag.setString("Type", driveableType);
		tag.setFloat("RotationYaw", axes.getYaw());
		tag.setFloat("RotationPitch", axes.getPitch());
		tag.setFloat("RotationRoll", axes.getRoll());
		for(DriveablePart part : parts.values())
		{
			part.writeToNBT(tag);
		}
    }

	@Override
    protected void readEntityFromNBT(NBTTagCompound tag)
    {
		driveableType = tag.getString("Type");
		initType(DriveableType.getDriveable(driveableType), false);
		dataID = tag.getInteger("DataID");
		driveableData = getData(dataID);
		driveableData.readFromNBT(tag);
		
		prevRotationYaw = tag.getFloat("RotationYaw");
		prevRotationPitch = tag.getFloat("RotationPitch");
		prevRotationRoll = tag.getFloat("RotationRoll");
		axes = new RotatedAxes(prevRotationYaw, prevRotationPitch, prevRotationRoll);
		for(DriveablePart part : parts.values())
		{
			part.readFromNBT(tag);
		}
    }
	
	@Override
	public void writeSpawnData(ByteArrayDataOutput data)
	{
		try
		{
			data.writeUTF(driveableType);
			data.writeInt(dataID);
			
			NBTTagCompound tag = new NBTTagCompound();
			driveableData.writeToNBT(tag);
			tag.writeNamedTag(tag, data);
			
			data.writeFloat(axes.getYaw());
			data.writeFloat(axes.getPitch());
			data.writeFloat(axes.getRoll());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}		
	}

	@Override
	public void readSpawnData(ByteArrayDataInput inputData)
	{
		try
		{
			driveableType = inputData.readUTF();
			dataID = inputData.readInt();
			driveableData = getData(dataID);
			driveableData.readFromNBT((NBTTagCompound)NBTBase.readNamedTag(inputData));
			initType(getDriveableType(), true);
			
			axes.setAngles(inputData.readFloat(), inputData.readFloat(), inputData.readFloat());
			prevRotationYaw = axes.getYaw();
			prevRotationPitch = axes.getPitch();
			prevRotationRoll = axes.getRoll();

		}
		catch(Exception e)
		{
			FlansMod.log("Failed to retreive plane type from server.");
			super.setDead();
			e.printStackTrace();
		}
	}
	
	/**
	 * Called with the movement of the mouse. Used in controlling vehicles if need be.
	 * @param deltaY 
	 * @param deltaX 
	 * @return if mouse movement was handled.
	 */
	@Override
	public abstract void onMouseMoved(int deltaX, int deltaY);

	@Override
    protected boolean canTriggerWalking()
    {
        return false;
    }

	@Override
    protected void entityInit()
    {
    }

	@Override
    public AxisAlignedBB getCollisionBox(Entity entity)
    {
        return entity.boundingBox;
    }

	@Override
    public AxisAlignedBB getBoundingBox()
    {
        return boundingBox;
    }

	@Override
    public boolean canBePushed()
    {
        return false;
    }

	@Override
    public double getMountedYOffset()
    {
        return -0.3D;
    }
	
	@Override
	/** Do nothing when attacked by standard methods. It'll take more than that to break a genuine Flan's Mod Driveable (TM) */
	public boolean attackEntityFrom(DamageSource damagesource, float i)
    {
	    if(worldObj.isRemote || isDead)
        {
            return true;
        }
		return true;
	}
	
	@Override
	public void setDead()
	{
		super.setDead();
		for(EntitySeat seat : seats)
			if(seat != null)
				seat.setDead();
	}
	
	@Override
	public void onCollideWithPlayer(EntityPlayer par1EntityPlayer) 
	{
		//Do nothing. Like a boss.
		// TODO: perhaps send the player flying??
		//Sounds good. ^ 
	}

	@Override
    public boolean canBeCollidedWith()
    {
        return !isDead;
    }
	
	@Override
    public void setPositionAndRotation2(double d, double d1, double d2, float f, float f1, int i)
    {
		if(ticksExisted > 1)
			return;
		if(riddenByEntity instanceof EntityPlayer && FlansMod.proxy.isThePlayer((EntityPlayer)riddenByEntity))
		{
		}
		else
		{				
			if(syncFromServer)
	        {
	            serverPositionTransitionTicker = i + 5;
	        }
	        else
	        {
	            double var10 = d - posX;
	            double var12 = d1 - posY;
	            double var14 = d2 - posZ;
	            double var16 = var10 * var10 + var12 * var12 + var14 * var14;
	
	            if (var16 <= 1.0D)
	            {
	                return;
	            }
	
	            serverPositionTransitionTicker = 3;
	        }
	        serverPosX = d;
	        serverPosY = d1;
	        serverPosZ = d2;
	        serverYaw = (double)f;
	        serverPitch = (double)f1;
		}
    }
	
	public void setPositionRotationAndMotion(double x, double y, double z, float yaw, float pitch, float roll, double motX, double motY, double motZ, float velYaw, float velPitch, float velRoll, float throt)
	{
		if(worldObj.isRemote)
		{
	        serverPosX = x;
	        serverPosY = y;
	        serverPosZ = z;
	        serverYaw = yaw;
	        serverPitch = pitch;
	        serverRoll = roll;
	        serverPositionTransitionTicker = 5;
		}
		else
		{
			setPosition(x, y, z);
			prevRotationYaw = yaw;
			prevRotationPitch = pitch;
			prevRotationRoll = roll;
			setRotation(yaw, pitch, roll);
		}
		//Set the motions regardless of side.
        motionX = motX;
        motionY = motY;
        motionZ = motZ;
        velocityYaw = velYaw;
        velocityPitch = velPitch;
        velocityRoll = velRoll;
        throttle = throt;
	}
	

	@Override
    public void setVelocity(double d, double d1, double d2)
    {
        motionX = d;
        motionY = d1;
        motionZ = d2;
    }
	
	public abstract boolean pressKey(int key, EntityPlayer player);

	@Override
    public void onUpdate()
    {
        super.onUpdate();
        if(!worldObj.isRemote)
        {
        	for(int i = 0; i < getDriveableType().numPassengers; i++)
        	{
        		if(seats[i] == null || !seats[i].addedToChunk)
        		{
        			seats[i] = new EntitySeat(worldObj, this, i);
    				worldObj.spawnEntityInWorld(seats[i]);
        		}
        	}
        }
        
        for(DriveablePart part : parts.values())
        {
        	if(part.box != null)
        	{
        		
	        	part.update(this);
	        	//Client side particles
	        	if(worldObj.isRemote)
	        	{
	           		if(part.onFire)
	        		{
	        			//Pick a random position within the bounding box and spawn a flame there
		        		Vector3f pos = axes.findLocalVectorGlobally(new Vector3f((float)part.box.x / 16F + rand.nextFloat() * (float)part.box.w / 16F, (float)part.box.y / 16F + rand.nextFloat() * (float)part.box.h / 16F, (float)part.box.z / 16F + rand.nextFloat() * (float)part.box.d / 16F));
		        		worldObj.spawnParticle("flame", posX + pos.x, posY + pos.y, posZ + pos.z, 0, 0, 0);
	        		}
	           		if(part.health > 0 && part.health < part.maxHealth / 2)
	        		{
	        			//Pick a random position within the bounding box and spawn a flame there
		        		Vector3f pos = axes.findLocalVectorGlobally(new Vector3f((float)part.box.x / 16F + rand.nextFloat() * (float)part.box.w / 16F, (float)part.box.y / 16F + rand.nextFloat() * (float)part.box.h / 16F, (float)part.box.z / 16F + rand.nextFloat() * (float)part.box.d / 16F));
		        		worldObj.spawnParticle(part.health < part.maxHealth / 4 ? "largesmoke" : "smoke", posX + pos.x, posY + pos.y, posZ + pos.z, 0, 0, 0);
	        		}
	        	}
	        	//Server side fire handling
	        	if(part.onFire)
	        	{
	        		//Rain can put out fire
	        		if(worldObj.isRaining() && rand.nextInt(40) == 0)
	        			part.onFire = false;
	        		//Also water blocks
	        		//Get the centre point of the part
	        		Vector3f pos = axes.findLocalVectorGlobally(new Vector3f((float)part.box.x / 16F + (float)part.box.w / 32F, (float)part.box.y / 16F + (float)part.box.h / 32F, (float)part.box.z / 16F + (float)part.box.d / 32F));
	        		if(worldObj.getBlockMaterial(MathHelper.floor_double(posX + pos.x), MathHelper.floor_double(posY + pos.y), MathHelper.floor_double(posZ + pos.z)) == Material.water)
	        		{
	        			part.onFire = false;
	        		}
	        	}
	        	else
	        	{
	        		Vector3f pos = axes.findLocalVectorGlobally(new Vector3f((float)part.box.x / 16F + (float)part.box.w / 32F, (float)part.box.y / 16F + (float)part.box.h / 32F, (float)part.box.z / 16F + (float)part.box.d / 32F));
	        		if(worldObj.getBlockMaterial(MathHelper.floor_double(posX + pos.x), MathHelper.floor_double(posY + pos.y), MathHelper.floor_double(posZ + pos.z)) == Material.lava)
	        		{
	        			part.onFire = true;
	        		}
	        	}
        	}
        }
        
        checkParts();
        
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
		prevRotationYaw = axes.getYaw();
		prevRotationPitch = axes.getPitch();
		prevRotationRoll = axes.getRoll();		
		
        if(riddenByEntity != null && riddenByEntity.isDead)
        {
            riddenByEntity = null;
        }
		if(riddenByEntity != null && isDead)
		{
			riddenByEntity.mountEntity(null);
		}
		if(riddenByEntity != null)
			riddenByEntity.fallDistance = 0F;
		
		//If the player jumps out or dies, smoothly return the throttle to 0 so the plane might actually come down again */
		if(!worldObj.isRemote && seats[0].riddenByEntity == null)
		{
			throttle *= 0.9F;
		}
    }
	
	/** Takes a vector (such as the origin of a seat / gun) and translates it from local coordinates to global coordinates */
	public Vector3f rotate(Vector3f inVec)
	{
		return axes.findLocalVectorGlobally(inVec);
	}
		
	/** Takes a vector (such as the origin of a seat / gun) and translates it from local coordinates to global coordinates */
	public Vector3f rotate(Vec3 inVec)
	{
		return rotate(inVec.xCoord, inVec.yCoord, inVec.zCoord);
	}

	/** Takes a vector (such as the origin of a seat / gun) and translates it from local coordinates to global coordinates */
	public Vector3f rotate(double x, double y, double z)
	{	
		return rotate(new Vector3f((float)x, (float)y, (float)z));
	}
	
	//Rotate the plane locally by some angle about the yaw axis
	public void rotateYaw(float rotateBy)
	{
		if(Math.abs(rotateBy) < 0.01F)
			return;
		axes.rotateLocalYaw(rotateBy);
		updatePrevAngles();
	}
	
	//Rotate the plane locally by some angle about the pitch axis
	public void rotatePitch(float rotateBy)
	{
		if(Math.abs(rotateBy) < 0.01F)
			return;
		axes.rotateLocalPitch(rotateBy);
		updatePrevAngles();
	}
	
	//Rotate the plane locally by some angle about the roll axis
	public void rotateRoll(float rotateBy)
	{
		if(Math.abs(rotateBy) < 0.01F)
			return;
		axes.rotateLocalRoll(rotateBy);
		updatePrevAngles();
	}
		
	public void updatePrevAngles()
	{		
		//Correct angles that crossed the +/- 180 line, so that rendering doesnt make them swing 360 degrees in one tick.
		double dYaw = axes.getYaw() - prevRotationYaw;
		if(dYaw > 180)
			prevRotationYaw += 360F;
		if(dYaw < -180)
			prevRotationYaw -= 360F;
		
		double dPitch = axes.getPitch() - prevRotationPitch;
		if(dPitch > 180)
			prevRotationPitch += 360F;
		if(dPitch < -180)
			prevRotationPitch -= 360F;
		
		double dRoll = axes.getRoll() - prevRotationRoll;
		if(dRoll > 180)
			prevRotationRoll += 360F;
		if(dRoll < -180)
			prevRotationRoll -= 360F;
	}
			
	public void setRotation(float rotYaw, float rotPitch, float rotRoll)
	{
		axes.setAngles(rotYaw, rotPitch, rotRoll);
	}
	
	//Used to stop self collision
	public boolean isPartOfThis(Entity ent)
	{
		for(EntitySeat seat : seats)
		{
			if(seat == null)
				continue;
			if(ent == seat)
				return true;
			if(seat.riddenByEntity != null && seat.riddenByEntity == ent)
				return true;
		}
		return ent == this;	
	}

	@Override
    public float getShadowSize()
    {
        return 0.0F;
    }
    
    public DriveableType getDriveableType()
    {
    	return DriveableType.getDriveable(driveableType);
    }
    
    public DriveableData getDriveableData()
    {
    	return driveableData;
    }
	
	@Override
	public boolean isDead()
	{
		return isDead;
	}
	
	@Override
	public Entity getControllingEntity()
	{
		return seats[0].getControllingEntity();
	}
	
	@Override
    public ItemStack getPickedResult(MovingObjectPosition target)
    {
		return new ItemStack(getDriveableType().itemID, 1, dataID);
    }
	
    
    public boolean hasFuel()
    {
    	if(seats[0].riddenByEntity instanceof EntityPlayer && ((EntityPlayer)seats[0].riddenByEntity).capabilities.isCreativeMode)
    		return true;
    	return driveableData.fuelInTank > 0;
    }
	
	//Physics time! Oooh yeah
	
	public double getSpeedXYZ()
	{
		return Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
	}
	
	public double getSpeedXZ()
	{
		return Math.sqrt(motionX * motionX + motionZ * motionZ);
	}
	
	/** Returns the kinetic energy of the driveable */
	public double getKineticEnergy()
	{
		return 0.5D * getDriveableType().mass * getSpeedXYZ();
	}
	
	/** Attempts to move the driveable by this vector, and checks for collision in the process */
	public void move(Vec3 move)
	{
		DriveableType type = getDriveableType();
		//Create an array to store the hits of the collision points
		MovingObjectPosition[] hits = new MovingObjectPosition[type.points.size()];
		int i = 0;
		for(CollisionPoint point : type.points)
		{
			i++;
			//Find the point in global coordinates
			Vec3 pointVec = rotate(point.posX / 16D, point.posY / 16D, point.posZ / 16D).toVec3();
			//Ray trace
			hits[i] = worldObj.clip(pointVec, pointVec.addVector(move.xCoord, move.yCoord, move.zCoord));
			
			if(hits[i].typeOfHit == EnumMovingObjectType.ENTITY)
			{
				Entity entity = hits[i].entityHit;
				//If its a living Entity, deal it some damage and deal some back to the driveable
				if(entity instanceof EntityLivingBase)
				{
					//Attack the collision point based on Entity health and plane energy
					attackPoint(point, EntityDamageSource.causeMobDamage((EntityLivingBase)entity), ((EntityLivingBase)entity).func_110143_aJ() * (float)getKineticEnergy());
					//Attack the entity based on the point strength and the plane energy
					entity.attackEntityFrom(new EntityDamageSourceCollision(this), point.strength * (float)getKineticEnergy());
				}
			}
			if(hits[i].typeOfHit == EnumMovingObjectType.TILE)
			{
				//Now things get complicated. The idea is that when a collision point hits a block, the plane stops moving in the direction it was going in
				//and instead pivots about the hit point, at least until the block is broken, if block breaking is enabled
				
			}
		}
	}
	
	/** Applies both a translational and rotational force at forceOrigin along forceVector with magnitude that of forceVector for the duration of the tick in which it is called */
	public void applyForce(Vector3f forceOrigin, Vector3f forceVector)
	{
		//Apply the translational force
		applyTranslationalForce(forceOrigin, forceVector);
		//And then the rotational force
		applyRotationalForce(forceOrigin, forceVector);
	}
	
	/** Applies a rotational force at forceOrigin along forceVector with magnitude that of forceVector for the duration of the tick in which it is called */
	public void applyRotationalForce(Vector3f forceOrigin, Vector3f forceVector)
	{
		
	}
	
	/** Applies a rotational force of forceVector.x in the yaw axis, forceVector.y in the pitch axis and forceVector.z in the roll axis */
	public void applyTorque(Vector3f forceVector)
	{
		velocityYaw += forceVector.x;
		velocityPitch += forceVector.y;
		velocityRoll += forceVector.z;
	}
	
	/** Applies a translational force at forceOrigin along forceVector with magnitude that of forceVector for the duration of the tick in which it is called */
	public void applyTranslationalForce(Vector3f forceOrigin, Vector3f forceVector)
	{
		float deltaTime = 1F / 20F;
		Vector3f accelerationVector = (Vector3f)forceVector.scale(1F / getDriveableType().mass);
		//Apply v=u+at
		motionX += accelerationVector.x * deltaTime;
		motionY += accelerationVector.y * deltaTime;
		motionZ += accelerationVector.z * deltaTime;
	}
	
	/** Whether or not the plane is on the ground 
	 * TODO : Replace with proper check based on wheels
	 * */
	public boolean onGround()
	{
		return onGround;
	}
	
	public boolean attackPoint(CollisionPoint point, DamageSource damagesource, float i)
	{
		return false;
	}
	
	/** Attack method called by bullets hitting the plane. Does advanced raytracing to detect which part of the plane is hit */
	public boolean attackFromBullet(EntityBullet bullet, Vector3f origin, Vector3f motion)
	{
		//Get the position of the bullet origin, relative to the centre of the plane, and then rotate the vectors onto local co-ordinates
		Vector3f relativePosVector = Vector3f.sub(origin, new Vector3f((float)posX, (float)posY, (float)posZ), null);
		Vector3f rotatedPosVector = axes.findGlobalVectorLocally(relativePosVector);
		Vector3f rotatedMotVector = axes.findGlobalVectorLocally(motion);
		//Check each part
		for(DriveablePart part : parts.values())
		{
			//Ray trace the bullet
			if(part.rayTrace(bullet, rotatedPosVector, rotatedMotVector))
			{
				//This is server side bsns
				if(worldObj.isRemote)
					return true;
				checkParts();

				//If it hit, send a damage update packet
				PacketDispatcher.sendPacketToAllAround(posX, posY, posZ, 100, dimension, PacketDriveableDamage.buildUpdatePacket(this));
				return true;
			}
		}
		return false;
	}
	
	/** Internal method for checking that all parts are ok, destroying broken ones, dropping items and making sure that child parts are destroyed when their parents are */
	private void checkParts()
	{
		for(DriveablePart part : parts.values())
		{
			if(!part.dead && part.health <= 0 && part.maxHealth > 0)
			{
				killPart(part);
			}
		}
		//If the core was destroyed, kill the driveable
		if(parts.get(EnumDriveablePart.core).dead)			
			setDead();
	}
	
	/** Internal method for killing driveable parts */
	private void killPart(DriveablePart part)
	{
		if(part.dead)
			return;
		part.health = 0;
		part.dead = true;
		
		//Drop items
		DriveableType type = getDriveableType();
		if(!worldObj.isRemote)
		{
			Vector3f pos = new Vector3f(0, 0, 0);
					
			//Get the midpoint of the part
			if(part.box != null)
	    		pos = axes.findLocalVectorGlobally(new Vector3f((float)part.box.x / 16F + (float)part.box.w / 32F, (float)part.box.y / 16F + (float)part.box.h / 32F, (float)part.box.z / 16F + (float)part.box.d / 32F));
	    		
    		ItemStack[] drops = type.recipe.get(part.type);
    		if(drops != null)
			{
				//Drop each itemstack 
        		for(ItemStack stack : drops)
				{
					worldObj.spawnEntityInWorld(new EntityItem(worldObj, posX + pos.x, posY + pos.y, posZ + pos.z, stack.copy()));
				}
			}
			dropItemsOnPartDeath(pos, part);
			
			//Inventory is in the core, so drop it if the core is broken
			if(part.type == EnumDriveablePart.core)
			{
				for(int i = 0; i < getDriveableData().getSizeInventory(); i++)
				{
					ItemStack stack = getDriveableData().getStackInSlot(i);
					if(stack != null)
					{
						worldObj.spawnEntityInWorld(new EntityItem(worldObj, posX + rand.nextGaussian(), posY + rand.nextGaussian(), posZ + rand.nextGaussian(), stack));
					}
				}
			}
		}
		
		//Kill all child parts to stop things floating unconnected
		for(EnumDriveablePart child : part.type.getChildren())
		{
			killPart(parts.get(child));
		}
	}
	
	/** Method for planes, vehicles and whatnot to drop their own specific items if they wish */
	protected abstract void dropItemsOnPartDeath(Vector3f midpoint, DriveablePart part);
	
	@Override
	public float getPlayerRoll() 
	{
		return axes.getRoll();
	}

	@Override
	public void explode() 
	{
		
	}
	
	@Override
	public float getCameraDistance()
	{
		return getDriveableType().cameraDistance;
	}
	
	public boolean isPartIntact(EnumDriveablePart part)
	{
		return parts.get(part).maxHealth == 0 || parts.get(part).health > 0; 
	}
}
