package com.flansmod.common.teams;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.flansmod.client.gui.GuiDriveableMenu;
import com.flansmod.client.gui.GuiTeamSelect;
import com.flansmod.client.gui.teams.EnumLoadoutSlot;
import com.flansmod.client.gui.teams.GuiChooseLoadout;
import com.flansmod.client.gui.teams.GuiEditLoadout;
import com.flansmod.client.gui.teams.GuiLandingPage;
import com.flansmod.client.teams.ClientTeamsData;
import com.flansmod.common.FlansMod;
import com.flansmod.common.PlayerData;
import com.flansmod.common.PlayerHandler;
import com.flansmod.common.network.PacketLoadoutData;
import com.flansmod.common.network.PacketRankUpdate;
import com.flansmod.common.network.PacketTeamSelect;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TeamsManagerRanked extends TeamsManager
{
	public static HashMap<UUID, PlayerRankData> rankData = new HashMap<UUID, PlayerRankData>();
	
	public LoadoutPool currentPool;
	
	public static TeamsManagerRanked GetInstance()
	{
		return (TeamsManagerRanked)TeamsManager.instance;
	}
	
	public TeamsManagerRanked()
	{
		super();
	}
	
	@Override
	public void startRound()
	{
		super.startRound();

		for(EntityPlayer player : getPlayers())
		{
			sendLoadoutData((EntityPlayerMP)player);
		}
	}
	
	public void sendLoadoutData(EntityPlayerMP player)
	{
		PacketLoadoutData data = new PacketLoadoutData();
		
		//Get the available teams from the gametype
		Team[] availableTeams = currentRound.gametype.getTeamsCanSpawnAs(currentRound, player);
		//Add in the spectators as an option and "none" if the player is an op
		boolean playerIsOp = MinecraftServer.getServer().getConfigurationManager().canSendCommands(player.getGameProfile());
		Team[] allAvailableTeams = new Team[availableTeams.length + (playerIsOp ? 2 : 1)];
		System.arraycopy(availableTeams, 0, allAvailableTeams, 0, availableTeams.length);
		allAvailableTeams[availableTeams.length] = Team.spectators;
		
		data.teamsAvailable = allAvailableTeams;
		data.currentPool = currentPool;
		data.myRankData = rankData.get(player.getUniqueID());
		
		FlansMod.getPacketHandler().sendTo(data, player);
	}
	
	@Override
	public void onPlayerLogin(EntityPlayer player) 
	{
		if(!rankData.containsKey(player.getUniqueID()))
		{
			PlayerRankData data = new PlayerRankData();
			
			if(currentPool != null)
			{
				for(int i = 0; i < 5; i++)
				{
					for(int j = 0; j < EnumLoadoutSlot.values().length; j++)
					{
						if(currentPool.defaultLoadouts[i].slots[j] != null)
						{
							data.loadouts[i].slots[j] = currentPool.defaultLoadouts[i].slots[j].copy();
						}
					}
				}
			}

			rankData.put(player.getUniqueID(), data);
		}
		
		//super.onPlayerLogin(player);
		
		if(!enabled || currentRound == null)
			return;
		
		if(player instanceof EntityPlayerMP)
		{
			EntityPlayerMP playerMP = (EntityPlayerMP)player;
			//sendTeamsMenuToPlayer(playerMP);
			sendLoadoutData(playerMP);
			currentRound.gametype.playerJoined(playerMP);
		}
	}
	
	@Override
	public void onPlayerLogout(EntityPlayer player) 
	{
		super.onPlayerLogout(player);
	}
	
	@Override
	public void OnPlayerKilled(EntityPlayerMP player, DamageSource source)
	{
		super.OnPlayerKilled(player, source);
		
		if(source.getSourceOfDamage() instanceof EntityPlayerMP)
		{
			EntityPlayerMP attacker = ((EntityPlayerMP)source.getSourceOfDamage());
			AwardXP(attacker, currentPool.XPForKill);
		}
		
	}
	
	public static void AwardXP(EntityPlayerMP player, int amount)
	{
		PlayerRankData data = rankData.get(player.getUniqueID());
		if(data != null)
		{
			data.AddXP(amount);
		}
	}
	
	@Override
	protected void OnRoundEnded()
	{
		for(EntityPlayer player : getPlayers())
		{
			SendRankDataToPlayer((EntityPlayerMP)player);
		}
		
		super.OnRoundEnded();
	}
	
	private void SendRankDataToPlayer(EntityPlayerMP player)
	{
		PacketRankUpdate packet = new PacketRankUpdate();
		PlayerRankData data = rankData.get(player.getUniqueID());
		if(data != null)
		{
			packet.pendingXP = data.pendingXP;
			int resultantXP = data.pendingXP;
			int resultantLevel = data.currentLevel;
			while(resultantXP >= currentPool.XPPerLevel[resultantLevel + 1])
			{
				resultantXP -= currentPool.XPPerLevel[resultantLevel + 1];
				resultantLevel++;
			}
			
			packet.resultantXP = resultantXP;
			packet.resultantLevel = resultantLevel;
			
			FlansMod.getPacketHandler().sendTo(packet, player);
			
			data.pendingXP = 0;
			data.currentLevel = resultantLevel;
			data.currentXP = resultantXP;
		}
	}
	
	@Override
	public void showTeamsMenuToAll(boolean info)
	{
		// Do nothing. We never need this
	}
	
	@Override
	public void sendTeamsMenuToPlayer(EntityPlayerMP player, boolean info)
	{
		if(!enabled || currentRound == null || currentRound.teams == null)
			return;

		sendLoadoutData(player);
	}
	
	@Override
	public void sendClassMenuToPlayer(EntityPlayerMP player)
	{
		// Don't need this either
	}
	
	@Override
	protected void ReadFromNBT(NBTTagCompound tags, World world)
	{
		super.ReadFromNBT(tags, world);
		
		int iPoolHash = tags.getInteger("pool");
		currentPool = LoadoutPool.GetPool(iPoolHash);
		
		NBTTagList ranks = tags.getTagList("playerRanks", 10); // 10 = CompoundTag
		if(ranks != null)
		{
			for(int i = 0; i < ranks.tagCount(); i++)
			{
				NBTTagCompound playerTags = ranks.getCompoundTagAt(i);
				UUID uuid = new UUID(playerTags.getLong("uuid1"), playerTags.getLong("uuid2"));
				PlayerRankData rData = new PlayerRankData();
				rData.readFromNBT(playerTags);
				rankData.put(uuid, rData);
			}
		}
	}
	
	@Override
	protected void WriteToNBT(NBTTagCompound tags)
	{
		super.WriteToNBT(tags);
		
		tags.setInteger("pool", currentPool.shortName.hashCode());
		
		NBTTagList ranks = new NBTTagList();
		for(Map.Entry<UUID, PlayerRankData> entry : rankData.entrySet())
		{
			NBTTagCompound playerTags = new NBTTagCompound();
			playerTags.setLong("uuid1", entry.getKey().getMostSignificantBits());
			playerTags.setLong("uuid2", entry.getKey().getLeastSignificantBits());
			entry.getValue().writeToNBT(playerTags);
			
			ranks.appendTag(playerTags);
		}
		
		tags.setTag("playerRanks", ranks);
	}
	
	@Override
	public void playerSelectedClass(EntityPlayerMP player, String className)
	{
		if(!enabled || currentRound == null)
			return;
		
		//Get player class requested
		int selection = Integer.parseInt(className);
		PlayerRankData data = rankData.get(player.getUniqueID());
		//PlayerData data = PlayerHandler.getPlayerData(player);
		
		IPlayerClass playerClass = new PlayerClassCustom(selection, data.loadouts[selection]);
		
		playerSelectedClass(player, playerClass);
	}
	
	@SideOnly(Side.CLIENT)
	public static void OpenLandingPage()
	{
		FMLClientHandler.instance().getClient().displayGuiScreen(new GuiLandingPage());
	}
	
	@SideOnly(Side.CLIENT)
	public static void OpenEditLoadoutPage(int loadout)
	{
		FMLClientHandler.instance().getClient().displayGuiScreen(new GuiEditLoadout(loadout));
	}

	@SideOnly(Side.CLIENT)
	public static void OpenTeamSelectPage()
	{
		FMLClientHandler.instance().getClient().displayGuiScreen(new GuiTeamSelect());
	}
	
	@SideOnly(Side.CLIENT)
	public static void ConfirmLoadoutChanges() 
	{
		PacketLoadoutData packet = new PacketLoadoutData();
		packet.myRankData = ClientTeamsData.theRankData;
		FlansMod.getPacketHandler().sendToServer(packet);
	}

	@SideOnly(Side.CLIENT)
	public static void ChooseLoadout(int id) 
	{
		PacketTeamSelect packet = new PacketTeamSelect();
		packet.classChoicesPacket = true;
		packet.info = false;
		packet.selection = "" + id;
		packet.selectionPacket = true;
		
		FlansMod.getPacketHandler().sendToServer(packet);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void SelectTeam(Team team) 
	{
		FlansMod.getPacketHandler().sendToServer(new PacketTeamSelect(team == null ? "null" : team.shortName, false));
		FMLClientHandler.instance().getClient().displayGuiScreen(new GuiChooseLoadout());
	}
	


}