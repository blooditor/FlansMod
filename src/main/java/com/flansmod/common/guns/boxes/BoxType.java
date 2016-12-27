package com.flansmod.common.guns.boxes;

import com.flansmod.common.FlansMod;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.types.TypeFile;

import net.minecraft.client.model.ModelBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class BoxType extends InfoType 
{
	public String topTexturePath;
	public String sideTexturePath;
	public String bottomTexturePath;
	
	public BoxType(TypeFile file) 
	{
		super(file);
	}

	@Override
	protected void read(String[] split, TypeFile file)
	{
		super.read(split, file);
		try
		{		
			topTexturePath = Read(split, "TopTexture", topTexturePath);
			bottomTexturePath = Read(split, "BottomTexture", bottomTexturePath);
			sideTexturePath = Read(split, "SideTexture", sideTexturePath);
		} 
		catch (Exception e)
		{
			FlansMod.log("Reading box file failed : " + shortName);
			e.printStackTrace();
		}
	}

	@Override
	protected void preRead(TypeFile file)
	{
	}

	@Override
	protected void postRead(TypeFile file)
	{
	}

	@Override
	@SideOnly(Side.CLIENT)
	public ModelBase GetModel()
	{
		return null;
	}
}
