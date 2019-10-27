/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.items;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.api.ApiUtils;
import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.shader.CapabilityShader;
import blusunrize.immersiveengineering.api.shader.CapabilityShader.ShaderWrapper;
import blusunrize.immersiveengineering.api.shader.CapabilityShader.ShaderWrapper_Item;
import blusunrize.immersiveengineering.api.shader.ShaderCase;
import blusunrize.immersiveengineering.api.shader.ShaderRegistry;
import blusunrize.immersiveengineering.api.shader.ShaderRegistry.ShaderRegistryEntry;
import blusunrize.immersiveengineering.api.tool.BulletHandler;
import blusunrize.immersiveengineering.api.tool.BulletHandler.IBullet;
import blusunrize.immersiveengineering.api.tool.ITool;
import blusunrize.immersiveengineering.client.ClientUtils;
import blusunrize.immersiveengineering.client.models.IOBJModelCallback;
import blusunrize.immersiveengineering.common.CommonProxy;
import blusunrize.immersiveengineering.common.entities.EntityRevolvershot;
import blusunrize.immersiveengineering.common.gui.ContainerRevolver;
import blusunrize.immersiveengineering.common.gui.IESlot;
import blusunrize.immersiveengineering.common.items.IEItemInterfaces.IBulletContainer;
import blusunrize.immersiveengineering.common.items.IEItemInterfaces.IGuiItem;
import blusunrize.immersiveengineering.common.util.IESounds;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import blusunrize.immersiveengineering.common.util.ListUtils;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.common.util.chickenbones.Matrix4;
import blusunrize.immersiveengineering.common.util.inventory.IEItemStackHandler;
import blusunrize.immersiveengineering.common.util.network.MessageSpeedloaderSync;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mojang.realmsclient.gui.ChatFormatting;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.EntityEquipmentSlot.Type;
import net.minecraft.inventory.Slot;
import net.minecraft.item.EnumAction;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.apache.commons.lang3.tuple.Triple;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoublePredicate;
import java.util.function.Function;

public class ItemRevolver extends ItemUpgradeableTool implements IOBJModelCallback<ItemStack>, ITool, IGuiItem, IBulletContainer
{
	public ItemRevolver()
	{
		super("revolver", 1, "REVOLVER");
	}

	public static UUID speedModUUID = Utils.generateNewUUID();
	public static UUID luckModUUID = Utils.generateNewUUID();
	public HashMap<String, TextureAtlasSprite> revolverIcons = new HashMap<>();
	public TextureAtlasSprite revolverDefaultTexture;

	public void stichRevolverTextures(TextureMap map)
	{
		revolverDefaultTexture = ApiUtils.getRegisterSprite(map, "immersiveengineering:revolvers/revolver");
		for(String key : specialRevolversByTag.keySet())
			if(!key.isEmpty()&&!specialRevolversByTag.get(key).tag.isEmpty())
			{
				int split = key.lastIndexOf("_");
				if(split < 0)
					split = key.length();
				revolverIcons.put(key, ApiUtils.getRegisterSprite(map, "immersiveengineering:revolvers/revolver_"+key.substring(0, split).toLowerCase()));
			}
	}

	/* ------------- CORE ITEM METHODS ------------- */

	@Override
	public int getGuiID(ItemStack stack)
	{
		return Lib.GUIID_Revolver;
	}

	@Override
	public boolean isTool(ItemStack item)
	{
		return true;
	}

	@Nullable
	@Override
	public NBTTagCompound getNBTShareTag(ItemStack stack)
	{
		NBTTagCompound ret = super.getNBTShareTag(stack);
		if(ret==null)
			ret = new NBTTagCompound();
		else
			ret = ret.copy();
		IItemHandler handler = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
		if(handler!=null)
		{
			NonNullList<ItemStack> bullets = NonNullList.withSize(getBulletCount(stack), ItemStack.EMPTY);
			for(int i = 0; i < getBulletCount(stack); i++)
				bullets.set(i, handler.getStackInSlot(i));
			ret.setTag("bullets", Utils.writeInventory(bullets));
		}
		return ret;
	}

	@Override
	public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt)
	{
		if(!stack.isEmpty())
			return new IEItemStackHandler(stack)
			{
				final ShaderWrapper_Item shaders = new ShaderWrapper_Item("immersiveengineering:revolver", stack);

				@Override
				public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing facing)
				{
					return capability==CapabilityShader.SHADER_CAPABILITY||
							super.hasCapability(capability, facing);
				}

				@Override
				public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing facing)
				{
					if(capability==CapabilityShader.SHADER_CAPABILITY)
						return (T)shaders;
					return super.getCapability(capability, facing);
				}
			};
		return null;
	}

	/* ------------- INTERNAL INVENTORY ------------- */

	@Override
	public int getSlotCount(ItemStack stack)
	{
		return 18+2+1;
	}

	@Override
	public Slot[] getWorkbenchSlots(Container container, ItemStack stack)
	{
		IItemHandler inv = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
		return new Slot[]
				{
						new IESlot.Upgrades(container, inv, 18+0, 80, 32, "REVOLVER", stack, true),
						new IESlot.Upgrades(container, inv, 18+1, 100, 32, "REVOLVER", stack, true)
				};
	}

	@Override
	public boolean canModify(ItemStack stack)
	{
		return stack.getMetadata()!=1;
	}

	@Override
	public void removeFromWorkbench(EntityPlayer player, ItemStack stack)
	{
		IItemHandler inv = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
		if(inv!=null&&!inv.getStackInSlot(18).isEmpty()&&!inv.getStackInSlot(19).isEmpty())
			Utils.unlockIEAdvancement(player, "main/upgrade_revolver");
	}

	/* ------------- NAME, TOOLTIP, SUB-ITEMS ------------- */

	@Override
	public String getTranslationKey(ItemStack stack)
	{
		if(stack.getItemDamage()!=1)
		{
			String tag = getRevolverDisplayTag(stack);
			if(!tag.isEmpty())
				return this.getTranslationKey()+"."+tag;
		}
		return super.getTranslationKey(stack);
	}

	@Override
	public void addInformation(ItemStack stack, @Nullable World world, List<String> list, ITooltipFlag flag)
	{
		String tag = getRevolverDisplayTag(stack);
		if(!tag.isEmpty())
			list.add(I18n.format(Lib.DESC_FLAVOUR+"revolver."+tag));
		else if(ItemNBTHelper.hasKey(stack, "flavour"))
			list.add(I18n.format(Lib.DESC_FLAVOUR+"revolver."+ItemNBTHelper.getString(stack, "flavour")));
		else if(stack.getMetadata()==0)
			list.add(I18n.format(Lib.DESC_FLAVOUR+"revolver"));

//			ItemStack shader = getShaderItem(stack);
//			if(shader!=null)
//			{
//				list.add(TextFormatting.DARK_GRAY+shader.getDisplayName());
//				ShaderCase sCase = ((IShaderItem)shader.getItem()).getShaderCase(shader, shader, getShaderType());
//			}

		NBTTagCompound perks = getPerks(stack);
		for(String key : perks.getKeySet())
		{
			RevolverPerk perk = RevolverPerk.get(key);
			if(perk!=null)
				list.add(" "+perk.getDisplayString(perks.getDouble(key)));
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> list)
	{
		if(this.isInCreativeTab(tab))
			list.add(new ItemStack(this));
//		for(Map.Entry<String, SpecialRevolver> e : specialRevolversByTag.entrySet())
//		{
//			ItemStack stack = new ItemStack(this, 1, 0);
//			applySpecialCrafting(stack, e.getValue());
//			this.recalculateUpgrades(stack);
//			list.add(stack);
//		}
	}

	/* ------------- ATTRIBUTES, UPDATE, RIGHTCLICK ------------- */

	@Override
	public Multimap getAttributeModifiers(@Nonnull EntityEquipmentSlot slot, ItemStack stack)
	{
		Multimap multimap = super.getAttributeModifiers(slot, stack);
		if(slot==EntityEquipmentSlot.MAINHAND)
		{
			if(getUpgrades(stack).getBoolean("fancyAnimation"))
				multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", -2, 0));
			double melee = getUpgradeValue_d(stack, "melee");
			if(melee!=0)
			{
				multimap.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", melee, 0));
				multimap.put(SharedMonsterAttributes.ATTACK_SPEED.getName(), new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", -2.4000000953674316D, 0));
			}
		}
		if(slot.getSlotType()==Type.HAND)
		{
			double speed = getUpgradeValue_d(stack, "speed");
			if(speed!=0)
				multimap.put(SharedMonsterAttributes.MOVEMENT_SPEED.getName(), new AttributeModifier(speedModUUID, "Weapon modifier", speed, 1));

			double luck = getUpgradeValue_d(stack, RevolverPerk.LUCK.getNBTKey());
			if(luck!=0)
				multimap.put(SharedMonsterAttributes.LUCK.getName(), new AttributeModifier(luckModUUID, "Weapon modifier", luck, 1));
		}
		return multimap;
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity ent, int slot, boolean inHand)
	{
		super.onUpdate(stack, world, ent, slot, inHand);
		{
			if(ItemNBTHelper.hasKey(stack, "reload"))
			{
				int reload = ItemNBTHelper.getInt(stack, "reload")-1;
				if(reload <= 0)
					ItemNBTHelper.remove(stack, "reload");
				else
					ItemNBTHelper.setInt(stack, "reload", reload);
			}
			if(ItemNBTHelper.hasKey(stack, "cooldown"))
			{
				int cooldown = ItemNBTHelper.getInt(stack, "cooldown")-1;
				if(cooldown <= 0)
					ItemNBTHelper.remove(stack, "cooldown");
				else
					ItemNBTHelper.setInt(stack, "cooldown", cooldown);
			}
		}
	}

	@Override
	public EnumAction getItemUseAction(ItemStack p_77661_1_)
	{
		return EnumAction.BOW;
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand)
	{
		ItemStack revolver = player.getHeldItem(hand);
		if(!world.isRemote)
		{
			if(player.isSneaking())
			{
				CommonProxy.openGuiForItem(player, hand==EnumHand.MAIN_HAND?EntityEquipmentSlot.MAINHAND: EntityEquipmentSlot.OFFHAND);
				return new ActionResult(EnumActionResult.SUCCESS, revolver);
			}
			else if(player.getCooledAttackStrength(1) >= 1)
			{
				if(this.getUpgrades(revolver).getBoolean("nerf"))
					world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1f, 0.6f);
				else
				{
					if(getShootCooldown(revolver) > 0||ItemNBTHelper.hasKey(revolver, "reload"))
						return new ActionResult(EnumActionResult.PASS, revolver);

					NonNullList<ItemStack> bullets = getBullets(revolver);

					if(isEmpty(revolver, false))
						for(int i = 0; i < player.inventory.getSizeInventory(); i++)
						{
							ItemStack stack = player.inventory.getStackInSlot(i);
							if(stack.getItem() instanceof ItemSpeedloader&&!((ItemSpeedloader)stack.getItem()).isEmpty(stack))
							{
								for(ItemStack b : bullets)
									if(!b.isEmpty())
										world.spawnEntity(new EntityItem(world, player.posX, player.posY, player.posZ, b));
								setBullets(revolver, ((ItemSpeedloader)stack.getItem()).getContainedItems(stack), true);
								((ItemSpeedloader)stack.getItem()).setContainedItems(stack, NonNullList.withSize(8, ItemStack.EMPTY));
								player.inventory.markDirty();
								if(player instanceof EntityPlayerMP)
									ImmersiveEngineering.packetHandler.sendTo(new MessageSpeedloaderSync(i, hand), (EntityPlayerMP)player);

								ItemNBTHelper.setInt(revolver, "reload", 60);
								return new ActionResult(EnumActionResult.SUCCESS, revolver);
							}
						}

					if(!ItemNBTHelper.hasKey(revolver, "reload"))
					{
						if(!bullets.get(0).isEmpty()&&bullets.get(0).getItem() instanceof ItemBullet&&ItemNBTHelper.hasKey(bullets.get(0), "bullet"))
						{
							String key = ItemNBTHelper.getString(bullets.get(0), "bullet");
							IBullet bullet = BulletHandler.getBullet(key);
							if(bullet!=null)
							{
								Vec3d vec = player.getLookVec();
								boolean electro = getUpgrades(revolver).getBoolean("electro");
								int count = bullet.getProjectileCount(player);
								if(count==1)
								{
									Entity entBullet = getBullet(player, vec, vec, key, bullets.get(0), electro);
									player.world.spawnEntity(bullet.getProjectile(player, bullets.get(0), entBullet, electro));
								}
								else
									for(int i = 0; i < count; i++)
									{
										Vec3d vecDir = vec.add(player.getRNG().nextGaussian()*.1, player.getRNG().nextGaussian()*.1, player.getRNG().nextGaussian()*.1);
										Entity entBullet = getBullet(player, vec, vecDir, key, bullets.get(0), electro);
										player.world.spawnEntity(bullet.getProjectile(player, bullets.get(0), entBullet, electro));
									}
								bullets.set(0, bullet.getCasing(bullets.get(0)).copy());

								float noise = 0.5f;
								if(hasUpgradeValue(revolver, RevolverPerk.NOISE.getNBTKey()))
									noise *= (float)getUpgradeValue_d(revolver, RevolverPerk.NOISE.getNBTKey());
								Utils.attractEnemies(player, 64*noise);
								SoundEvent sound = bullet.getSound();
								if(sound==null)
									sound = IESounds.revolverFire;
								world.playSound(null, player.posX, player.posY, player.posZ, sound, SoundCategory.PLAYERS, noise, 1f);
							}
							else
								world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.BLOCK_NOTE_HAT, SoundCategory.PLAYERS, 1f, 1f);
						}
						else
							world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.BLOCK_NOTE_HAT, SoundCategory.PLAYERS, 1f, 1f);

						rotateCylinder(revolver, player, true, bullets);
						ItemNBTHelper.setInt(revolver, "cooldown", getMaxShootCooldown(revolver));
						return new ActionResult(EnumActionResult.SUCCESS, revolver);
					}
				}
			}
		}
		else if(!player.isSneaking()&&revolver.getItemDamage()==0)
		{
			if(getShootCooldown(revolver) > 0||ItemNBTHelper.hasKey(revolver, "reload"))
				return new ActionResult(EnumActionResult.PASS, revolver);
			NonNullList<ItemStack> bullets = getBullets(revolver);
			if(!bullets.get(0).isEmpty()&&bullets.get(0).getItem() instanceof ItemBullet&&ItemNBTHelper.hasKey(bullets.get(0), "bullet"))
			{
				Triple<ItemStack, ShaderRegistryEntry, ShaderCase> shader = ShaderRegistry.getStoredShaderAndCase(revolver);
				if(shader!=null)
				{
					Vec3d pos = Utils.getLivingFrontPos(player, .75, player.height*.75, hand==EnumHand.MAIN_HAND?player.getPrimaryHand(): player.getPrimaryHand().opposite(), false, 1);
					shader.getMiddle().getEffectFunction().execute(world, shader.getLeft(), revolver, shader.getRight().getShaderType(), pos, player.getForward(), .125f);
				}
			}
			return new ActionResult(EnumActionResult.SUCCESS, revolver);
		}
		return new ActionResult(EnumActionResult.SUCCESS, revolver);
	}

	public int getShootCooldown(ItemStack stack)
	{
		return ItemNBTHelper.getInt(stack, "cooldown");
	}

	public int getMaxShootCooldown(ItemStack stack)
	{
		if(hasUpgradeValue(stack, RevolverPerk.COOLDOWN.getNBTKey()))
			return (int)Math.ceil(15*getUpgradeValue_d(stack, RevolverPerk.COOLDOWN.getNBTKey()));
		return 15;
	}

	/* ------------- IBulletContainer ------------- */

	@Override
	public int getBulletCount(ItemStack revolver)
	{
		return 8+this.getUpgrades(revolver).getInteger("bullets");
	}

	@Override
	public NonNullList<ItemStack> getBullets(ItemStack revolver, boolean remote)
	{
		if(!remote&&isEmpty(revolver, true))
			remote = true;
		else if(remote&&!ItemNBTHelper.hasKey(revolver, "bullets"))
			remote = false;
		if(!remote)
			return ListUtils.fromItems(this.getContainedItems(revolver).subList(0, getBulletCount(revolver)));
		else
			return Utils.readInventory(ItemNBTHelper.getTag(revolver).getTagList("bullets", 10), getBulletCount(revolver));
	}

	/* ------------- BULLET UTILITY ------------- */

	EntityRevolvershot getBullet(EntityPlayer player, Vec3d vecSpawn, Vec3d vecDir, String type, ItemStack stack, boolean electro)
	{
		EntityRevolvershot bullet = new EntityRevolvershot(player.world, player, vecDir.x*1.5, vecDir.y*1.5, vecDir.z*1.5, type, stack);
		bullet.motionX = vecDir.x*2;
		bullet.motionY = vecDir.y*2;
		bullet.motionZ = vecDir.z*2;
		bullet.bulletElectro = electro;
		return bullet;
	}

	public void setBullets(ItemStack revolver, NonNullList<ItemStack> bullets, boolean ignoreExtendedMag)
	{
		IItemHandlerModifiable inv = (IItemHandlerModifiable)revolver.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
		assert inv!=null;
		for(int i = 0; i < 18; i++)
			inv.setStackInSlot(i, ItemStack.EMPTY);
		if(ignoreExtendedMag&&getUpgrades(revolver).getInteger("bullets") > 0)
			for(int i = 0; i < bullets.size(); i++)
				inv.setStackInSlot(i < 2?i: i+getUpgrades(revolver).getInteger("bullets"), bullets.get(i));
		else
			for(int i = 0; i < bullets.size(); i++)
				inv.setStackInSlot(i, bullets.get(i));
	}

	public void rotateCylinder(ItemStack revolver, EntityPlayer player, boolean forward, NonNullList<ItemStack> bullets)
	{
		NonNullList<ItemStack> cycled = NonNullList.withSize(getBulletCount(revolver), ItemStack.EMPTY);
		int offset = forward?-1: 1;
		for(int i = 0; i < cycled.size(); i++)
			cycled.set((i+offset+cycled.size())%cycled.size(), bullets.get(i));
		setBullets(revolver, cycled, false);
		player.inventory.markDirty();
	}

	public void rotateCylinder(ItemStack revolver, EntityPlayer player, boolean forward)
	{
		NonNullList<ItemStack> bullets = getBullets(revolver);
		rotateCylinder(revolver, player, forward, bullets);
	}

	public boolean isEmpty(ItemStack stack, boolean allowCasing)
	{
		IItemHandler inv = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
		if(inv!=null)
			for(int i = 0; i < inv.getSlots(); i++)
			{
				ItemStack b = inv.getStackInSlot(i);
				if(!b.isEmpty()&&b.getItem() instanceof ItemBullet&&(allowCasing||ItemNBTHelper.hasKey(b, "bullet")))
					return false;
			}
		return true;
	}

	/* ------------- UPGRADES & PERKS ------------- */

	@Override
	public NBTTagCompound getUpgradeBase(ItemStack stack)
	{
		return ItemNBTHelper.getTagCompound(stack, "baseUpgrades");
	}

	public String getRevolverDisplayTag(ItemStack revolver)
	{
		String tag = ItemNBTHelper.getString(revolver, "elite");
		if(!tag.isEmpty())
		{
			int split = tag.lastIndexOf("_");
			if(split < 0)
				split = tag.length();
			return tag.substring(0, split);
		}
		return "";
	}

	public NBTTagCompound getPerks(ItemStack stack)
	{
		return ItemNBTHelper.getTagCompound(stack, "perks");
	}

	public boolean hasUpgradeValue(ItemStack stack, String key)
	{
		return getUpgrades(stack).hasKey(key)||getPerks(stack).hasKey(key);
	}

	public double getUpgradeValue_d(ItemStack stack, String key)
	{
		return getUpgrades(stack).getDouble(key)+getPerks(stack).getDouble(key);
	}


	/* ------------- CRAFTING ------------- */

	@Override
	public void onCreated(ItemStack stack, World world, EntityPlayer player)
	{
		if(stack.isEmpty()||player==null)
			return;

		if(stack.getItemDamage()==1)
			return;
		String uuid = player.getUniqueID().toString();
		if(specialRevolvers.containsKey(uuid))
		{
			ArrayList<SpecialRevolver> list = new ArrayList(specialRevolvers.get(uuid));
			if(!list.isEmpty())
			{
				list.add(null);
				String existingTag = ItemNBTHelper.getString(stack, "elite");
				if(existingTag.isEmpty())
					applySpecialCrafting(stack, list.get(0));
				else
				{
					int i = 0;
					for(; i < list.size(); i++)
						if(list.get(i)!=null&&existingTag.equals(list.get(i).tag))
							break;
					int next = (i+1)%list.size();
					applySpecialCrafting(stack, list.get(next));
				}
			}
		}
		this.recalculateUpgrades(stack);
	}

	public void applySpecialCrafting(ItemStack stack, SpecialRevolver r)
	{
		if(r==null)
		{
			ItemNBTHelper.remove(stack, "elite");
			ItemNBTHelper.remove(stack, "flavour");
			ItemNBTHelper.remove(stack, "baseUpgrades");
			return;
		}
		if(r.tag!=null&&!r.tag.isEmpty())
			ItemNBTHelper.setString(stack, "elite", r.tag);
		if(r.flavour!=null&&!r.flavour.isEmpty())
			ItemNBTHelper.setString(stack, "flavour", r.flavour);
		NBTTagCompound baseUpgrades = new NBTTagCompound();
		for(Map.Entry<String, Object> e : r.baseUpgrades.entrySet())
		{
			if(e.getValue() instanceof Boolean)
				baseUpgrades.setBoolean(e.getKey(), (Boolean)e.getValue());
			else if(e.getValue() instanceof Integer)
				baseUpgrades.setInteger(e.getKey(), (Integer)e.getValue());
			else if(e.getValue() instanceof Float)
				baseUpgrades.setDouble(e.getKey(), (Float)e.getValue());
			else if(e.getValue() instanceof Double)
				baseUpgrades.setDouble(e.getKey(), (Double)e.getValue());
			else if(e.getValue() instanceof String)
				baseUpgrades.setString(e.getKey(), (String)e.getValue());
		}
		ItemNBTHelper.setTagCompound(stack, "baseUpgrades", baseUpgrades);
	}

	/* ------------- RENDERING ------------- */

	@Override
	public boolean isFull3D()
	{
		return true;
	}

	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged)
	{
		if(slotChanged)
			return true;
		if(super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged))
			return true;

		if(oldStack.hasCapability(CapabilityShader.SHADER_CAPABILITY, null)&&newStack.hasCapability(CapabilityShader.SHADER_CAPABILITY, null))
		{
			ShaderWrapper wrapperOld = oldStack.getCapability(CapabilityShader.SHADER_CAPABILITY, null);
			ShaderWrapper wrapperNew = newStack.getCapability(CapabilityShader.SHADER_CAPABILITY, null);
			if(!ItemStack.areItemStacksEqual(wrapperOld.getShaderItem(), wrapperNew.getShaderItem()))
				return true;
		}
		if(ItemNBTHelper.hasKey(oldStack, "elite")||ItemNBTHelper.hasKey(newStack, "elite"))
			return !ItemNBTHelper.getString(oldStack, "elite").equals(ItemNBTHelper.getString(newStack, "elite"));

		return false;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public TextureAtlasSprite getTextureReplacement(ItemStack stack, String material)
	{
		String tag = ItemNBTHelper.getString(stack, "elite");
		if(!tag.isEmpty())
			return this.revolverIcons.get(tag);
		else
			return this.revolverDefaultTexture;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public boolean shouldRenderGroup(ItemStack stack, String group)
	{
		if(group.equals("frame")||group.equals("cylinder")||group.equals("barrel")||group.equals("cosmetic_compensator"))
			return true;

		HashSet<String> render = new HashSet<String>();
		String tag = ItemNBTHelper.getString(stack, "elite");
		String flavour = ItemNBTHelper.getString(stack, "flavour");
		if(tag!=null&&!tag.isEmpty()&&specialRevolversByTag.containsKey(tag))
		{
			SpecialRevolver r = specialRevolversByTag.get(tag);
			if(r!=null&&r.renderAdditions!=null)
				for(String ss : r.renderAdditions)
					render.add(ss);
		}
		else if(flavour!=null&&!flavour.isEmpty()&&specialRevolversByTag.containsKey(flavour))
		{
			SpecialRevolver r = specialRevolversByTag.get(flavour);
			if(r!=null&&r.renderAdditions!=null)
				for(String ss : r.renderAdditions)
					render.add(ss);
		}
		NBTTagCompound upgrades = this.getUpgrades(stack);
		if(upgrades.getInteger("bullets") > 0&&!render.contains("dev_mag"))
			render.add("player_mag");
		if(upgrades.getDouble("melee") > 0&&!render.contains("dev_bayonet"))
		{
			render.add("bayonet_attachment");
			render.add("player_bayonet");
		}
		if(upgrades.getBoolean("electro"))
		{
			render.add("player_electro_0");
			render.add("player_electro_1");
		}
		return render.contains(group);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public Matrix4 handlePerspective(ItemStack stack, TransformType cameraTransformType, Matrix4 perspective, @Nullable EntityLivingBase entity)
	{
		if(entity instanceof EntityPlayer&&(cameraTransformType==TransformType.FIRST_PERSON_RIGHT_HAND||cameraTransformType==TransformType.FIRST_PERSON_LEFT_HAND||cameraTransformType==TransformType.THIRD_PERSON_RIGHT_HAND||cameraTransformType==TransformType.THIRD_PERSON_LEFT_HAND))
		{
			boolean main = (cameraTransformType==TransformType.FIRST_PERSON_RIGHT_HAND||cameraTransformType==TransformType.THIRD_PERSON_RIGHT_HAND)==(entity.getPrimaryHand()==EnumHandSide.RIGHT);
			boolean left = cameraTransformType==TransformType.FIRST_PERSON_LEFT_HAND||cameraTransformType==TransformType.THIRD_PERSON_LEFT_HAND;
			if(getUpgrades(stack).getBoolean("fancyAnimation")&&main)
			{
				float f = ((EntityPlayer)entity).getCooledAttackStrength(ClientUtils.mc().timer.renderPartialTicks);
				if(f < 1)
				{
					float angle = 3.14159f+f*-9.42477f;
					if(left)
						angle *= -1;
					if(cameraTransformType==TransformType.FIRST_PERSON_RIGHT_HAND||cameraTransformType==TransformType.FIRST_PERSON_LEFT_HAND)
						perspective.translate(0, 2-f, 0);
					perspective.rotate(angle, 0, 0, 1);
				}
			}

			//Re-grab stack because the other one doesn't do reloads properly
			stack = main?entity.getHeldItemMainhand(): entity.getHeldItemOffhand();
			if(ItemNBTHelper.hasKey(stack, "reload"))
			{
				float f = 3-ItemNBTHelper.getInt(stack, "reload")/20f; //Reload time in seconds, for coordinating with audio
				if(f > .35&&f < 1.95)
					if(f < .5)
						perspective.translate((.35-f)*2, 0, 0).rotate(2.64*(f-.35), 0, 0, left?-1: 1);
					else if(f < .6)
						perspective.translate((f-.5)*6, (.5-f)*1, 0).rotate(.87266, 0, 0, left?-1: 1);
					else if(f < 1.7)
						perspective.translate(0, -.6, 0).rotate(.87266, 0, 0, left?-1: 1);
					else if(f < 1.8)
						perspective.translate((1.8-f)*6, (f-1.8)*1, 0).rotate(.87266, 0, 0, left?-1: 1);
					else
						perspective.translate((f-1.95f)*2, 0, 0).rotate(2.64*(1.95-f), 0, 0, left?-1: 1);
			}
			else if(((EntityPlayer)entity).openContainer instanceof ContainerRevolver)
				perspective.translate(left?.4: -.4, .4, 0).rotate(.87266, 0, 0, left?-1: 1);
		}
		return perspective;
	}

	private static final String[][] groups = {{"frame"}, {"cylinder"}};

	@SideOnly(Side.CLIENT)
	@Override
	public String[][] getSpecialGroups(ItemStack stack, TransformType transform, EntityLivingBase entity)
	{
		return groups;
	}

	private static final Matrix4 matOpen = new Matrix4().translate(-.625, .25, 0).rotate(-.87266, 0, 0, 1);
	private static final Matrix4 matClose = new Matrix4().translate(-.625, .25, 0);
	private static final Matrix4 matCylinder = new Matrix4().translate(0, .6875, 0);

	@Nonnull
	@Override
	public Matrix4 getTransformForGroups(ItemStack stack, String[] groups, TransformType transform, EntityLivingBase entity,
										 Matrix4 mat, float partialTicks)
	{
		if(entity instanceof EntityPlayer&&(transform==TransformType.FIRST_PERSON_RIGHT_HAND||transform==TransformType.FIRST_PERSON_LEFT_HAND||transform==TransformType.THIRD_PERSON_RIGHT_HAND||transform==TransformType.THIRD_PERSON_LEFT_HAND))
		{
			boolean main = (transform==TransformType.FIRST_PERSON_RIGHT_HAND||transform==TransformType.THIRD_PERSON_RIGHT_HAND)==(entity.getPrimaryHand()==EnumHandSide.RIGHT);
			boolean left = transform==TransformType.FIRST_PERSON_LEFT_HAND||transform==TransformType.THIRD_PERSON_LEFT_HAND;
			//Re-grab stack because the other one doesn't do reloads properly
			stack = main?entity.getHeldItemMainhand(): entity.getHeldItemOffhand();
			if(ItemNBTHelper.hasKey(stack, "reload"))
			{
				float f = 3-ItemNBTHelper.getInt(stack, "reload")/20f; //Reload time in seconds, for coordinating with audio
				if("frame".equals(groups[0]))
				{
					if(f < .35||f > 1.95)
						return matClose;
					else if(f < .5)
						return mat.setIdentity().translate(-.625, .25, 0).rotate(-2.64*(f-.35), 0, 0, 1);
					else if(f < 1.8)
						return matOpen;
					else
						return mat.setIdentity().translate(-.625, .25, 0).rotate(-2.64*(1.95-f), 0, 0, 1);
				}
				else if(f > 2.5&&f < 2.9)
					return mat.setIdentity().translate(0, .6875, 0).rotate(-15.70795*(f-2.5), left?-1: 1, 0, 0);
			}
			else if("frame".equals(groups[0])&&((EntityPlayer)entity).openContainer instanceof ContainerRevolver)
				return matOpen;
		}
		return "frame".equals(groups[0])?matClose: matCylinder;
	}

	/* ------------- INNER CLASSES ------------- */

	public static final ArrayListMultimap<String, SpecialRevolver> specialRevolvers = ArrayListMultimap.create();
	public static final Map<String, SpecialRevolver> specialRevolversByTag = new HashMap<String, SpecialRevolver>();

	public static class SpecialRevolver
	{
		public final String[] uuid;
		public final String tag;
		public final String flavour;
		public final HashMap<String, Object> baseUpgrades;
		public final String[] renderAdditions;

		public SpecialRevolver(String[] uuid, String tag, String flavour, HashMap<String, Object> baseUpgrades, String[] renderAdditions)
		{
			this.uuid = uuid;
			this.tag = tag;
			this.flavour = flavour;
			this.baseUpgrades = baseUpgrades;
			this.renderAdditions = renderAdditions;
		}
	}

	@ParametersAreNonnullByDefault
	public enum RevolverPerk
	{
		COOLDOWN(f -> f > 1,
				f -> Utils.NUMBERFORMAT_PREFIXED.format((1-f)*100),
				(l, r) -> l*r,
				1, -0.75, -0.05),
		NOISE(f -> f > 1,
				f -> Utils.NUMBERFORMAT_PREFIXED.format((f-1)*100),
				(l, r) -> l*r,
				1, -.9, -0.1),
		LUCK(f -> f < 0,
				f -> Utils.NUMBERFORMAT_PREFIXED.format(f*100),
				(l, r) -> l+r,
				0, 3, 0.5);

		private final DoublePredicate isBadValue;
		private final Function<Double, String> valueFormatter;
		private final DoubleBinaryOperator valueConcat;
		private final double generate_median;
		private final double generate_deviation;
		private final double generate_luckScale;

		RevolverPerk(DoublePredicate isBadValue, Function<Double, String> valueFormatter, DoubleBinaryOperator valueConcat, double generate_median, double generate_deviation, double generate_luckScale)
		{
			this.isBadValue = isBadValue;
			this.valueFormatter = valueFormatter;
			this.valueConcat = valueConcat;
			this.generate_median = generate_median;
			this.generate_deviation = generate_deviation;
			this.generate_luckScale = generate_luckScale;
		}

		public String getNBTKey()
		{
			return name().toLowerCase();
		}

		@SideOnly(Side.CLIENT)
		public String getDisplayString(double value)
		{
			String key = Lib.DESC_INFO+"revolver.perk."+this.toString();
			return (isBadValue.test(value)?ChatFormatting.RED: ChatFormatting.BLUE)+I18n.format(key, valueFormatter.apply(value));
		}

		public static String getFormattedName(String name, NBTTagCompound perksTag)
		{
			double averageTier = 0;
			for(String key : perksTag.getKeySet())
			{
				ItemRevolver.RevolverPerk perk = ItemRevolver.RevolverPerk.get(key);
				double value = perksTag.getDouble(key);
				double dTier = (value-perk.generate_median)/perk.generate_deviation*3;
				averageTier += dTier;
				int iTier = (int)MathHelper.clamp((dTier < 0?Math.floor(dTier): Math.ceil(dTier)), -3, 3);
				String translate = Lib.DESC_INFO+"revolver.perk."+perk.name().toLowerCase()+".tier"+iTier;
				name = net.minecraft.util.text.translation.I18n.translateToLocalFormatted(translate, name);
			}

			int rarityTier = (int)Math.ceil(MathHelper.clamp(averageTier+3, 0, 6)/6*5);
			EnumRarity rarity = rarityTier==5?Lib.RARITY_Masterwork: rarityTier==4?EnumRarity.EPIC: rarityTier==3?EnumRarity.RARE: rarityTier==2?EnumRarity.UNCOMMON: EnumRarity.COMMON;
			return rarity.color+name;
		}

		public static int calculateTier(NBTTagCompound perksTag)
		{
			double averageTier = 0;
			for(String key : perksTag.getKeySet())
			{
				ItemRevolver.RevolverPerk perk = ItemRevolver.RevolverPerk.get(key);
				double value = perksTag.getDouble(key);
				double dTier = (value-perk.generate_median)/perk.generate_deviation*3;
				averageTier += dTier;
			}
			return (int)Math.ceil(MathHelper.clamp(averageTier+3, 0, 6)/6*5);
		}

		public double concat(double left, double right)
		{
			return this.valueConcat.applyAsDouble(left, right);
		}

		public double generateValue(Random rand, boolean isBad, float luck)
		{
			double d = Utils.generateLuckInfluencedDouble(generate_median, generate_deviation, luck, rand, isBad, generate_luckScale);
			int i = (int)(d*100);
			d = i/100d;
			return d;
		}

		@Override
		public String toString()
		{
			return this.name().toLowerCase();
		}

		public static RevolverPerk get(String name)
		{
			try
			{
				return valueOf(name.toUpperCase());
			} catch(Exception e)
			{
				return null;
			}
		}

		public static RevolverPerk getRandom(Random rand)
		{
			int i = rand.nextInt(values().length);
			return values()[i];
		}

		public static NBTTagCompound generatePerkSet(Random rand, float luck)
		{
			RevolverPerk goodPerk = RevolverPerk.getRandom(rand);
			RevolverPerk badPerk = RevolverPerk.LUCK;
			//RevolverPerk.getRandom(rand);
			double val = goodPerk.generateValue(rand, false, luck);

			NBTTagCompound perkCompound = new NBTTagCompound();
			if(goodPerk==badPerk)
				val = (val+badPerk.generateValue(rand, true, luck))/2;
			else
				perkCompound.setDouble(badPerk.getNBTKey(), badPerk.generateValue(rand, true, luck));
			perkCompound.setDouble(goodPerk.getNBTKey(), val);

			return perkCompound;
		}
	}
}