package dev.jorel.commandapi.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.craftbukkit.v1_18_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_18_R1.inventory.CraftItemFactory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpTopic;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.mockito.Mockito;

import com.google.common.collect.Streams;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.enchantments.EnchantmentMock;
import be.seeseemelk.mockbukkit.potion.MockPotionEffectType;
import dev.jorel.commandapi.CommandAPIBukkit;
import dev.jorel.commandapi.commandsenders.AbstractCommandSender;
import dev.jorel.commandapi.commandsenders.BukkitCommandSender;
import dev.jorel.commandapi.nms.NMS;
import dev.jorel.commandapi.wrappers.ParticleData;
import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.text.Component;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.commands.synchronization.ArgumentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

public class MockNMS extends ArgumentNMS {

	public MockNMS(NMS<?> baseNMS) {
		super(baseNMS);
		
		CommandAPIBukkit nms = Mockito.spy((CommandAPIBukkit) BASE_NMS);
		// Stub in our getMinecraftServer implementation
		Mockito.when(nms.getMinecraftServer()).thenAnswer(i -> getMinecraftServer());
		BASE_NMS = nms;

		// Initialize WorldVersion (game version)
		SharedConstants.tryDetectVersion();

		// MockBukkit is very helpful and registers all of the potion
		// effects and enchantments for us. We need to not do this (because
		// we call Bootstrap.bootStrap() below which does the same thing)
		unregisterAllEnchantments();
		unregisterAllPotionEffects();

		// Invoke Minecraft's registry. This also initializes all argument types.
		// How convenient!
		Bootstrap.bootStrap();
		
		// Sometimes, and I have no idea why, Bootstrap.bootStrap() only works
		// on the very first test in the test suite. After that, everything else
		// doesn't work. At this point, we'll use the ServerMock#createPotionEffectTypes
		// method (which unfortunately is private and pure, so instead of using reflection
		// we'll just implement it right here instead)
		@SuppressWarnings("unchecked")
		Map<NamespacedKey, PotionEffectType> byKey = (Map<NamespacedKey, PotionEffectType>) getField(PotionEffectType.class, "byKey", null);
		if(byKey.isEmpty()) {
			createPotionEffectTypes();
		}
		// Don't use EnchantmentMock.registerDefaultEnchantments because we want
		// to specify what enchantments to mock (i.e. only 1.18 ones, and not any
		// 1.19 ones!)
		registerDefaultEnchantments();
	}
	
	private static void registerPotionEffectType(int id, @NotNull String name, boolean instant, int rgb) {
		NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT));
		PotionEffectType type = new MockPotionEffectType(key, id, name, instant, Color.fromRGB(rgb));
		PotionEffectType.registerPotionEffectType(type);
	}
	
	// ItemStackArgument requirements
	public static ItemFactory getItemFactory() {
		return CraftItemFactory.instance();
	}

	/**
	 * @return A list of all item names, sorted in alphabetical order. Each item
	 * is prefixed with {@code minecraft:}
	 */
	public static List<String> getAllItemNames() {
		// Registry.ITEM
		return StreamSupport.stream(Registry.ITEM.spliterator(), false)
			.map(Object::toString)
			.map(s -> "minecraft:" + s)
			.sorted()
			.toList();
	}

	public static void registerDefaultEnchantments() {
		for(Enchantment enchantment : getEnchantments()) {
			if (Enchantment.getByKey(enchantment.getKey()) == null) {
				Enchantment.registerEnchantment(new EnchantmentMock(enchantment.getKey(), enchantment.getKey().getKey()));
			}
		}
	}
	
	/**
	 * This registers Minecraft's default {@link PotionEffectType PotionEffectTypes}. It also prevents any new effects to
	 * be created afterwards.
	 */
	public static void createPotionEffectTypes() {
		for (PotionEffectType type : PotionEffectType.values()) {
			// We probably already registered all Potion Effects
			// otherwise this would be null
			if (type != null) {
				// This is not perfect, but it works.
				return;
			}
		}

		registerPotionEffectType(1, "SPEED", false, 8171462);
		registerPotionEffectType(2, "SLOWNESS", false, 5926017);
		registerPotionEffectType(3, "HASTE", false, 14270531);
		registerPotionEffectType(4, "MINING_FATIGUE", false, 4866583);
		registerPotionEffectType(5, "STRENGTH", false, 9643043);
		registerPotionEffectType(6, "INSTANT_HEALTH", true, 16262179);
		registerPotionEffectType(7, "INSTANT_DAMAGE", true, 4393481);
		registerPotionEffectType(8, "JUMP_BOOST", false, 2293580);
		registerPotionEffectType(9, "NAUSEA", false, 5578058);
		registerPotionEffectType(10, "REGENERATION", false, 13458603);
		registerPotionEffectType(11, "RESISTANCE", false, 10044730);
		registerPotionEffectType(12, "FIRE_RESISTANCE", false, 14981690);
		registerPotionEffectType(13, "WATER_BREATHING", false, 3035801);
		registerPotionEffectType(14, "INVISIBILITY", false, 8356754);
		registerPotionEffectType(15, "BLINDNESS", false, 2039587);
		registerPotionEffectType(16, "NIGHT_VISION", false, 2039713);
		registerPotionEffectType(17, "HUNGER", false, 5797459);
		registerPotionEffectType(18, "WEAKNESS", false, 4738376);
		registerPotionEffectType(19, "POISON", false, 5149489);
		registerPotionEffectType(20, "WITHER", false, 3484199);
		registerPotionEffectType(21, "HEALTH_BOOST", false, 16284963);
		registerPotionEffectType(22, "ABSORPTION", false, 2445989);
		registerPotionEffectType(23, "SATURATION", true, 16262179);
		registerPotionEffectType(24, "GLOWING", false, 9740385);
		registerPotionEffectType(25, "LEVITATION", false, 13565951);
		registerPotionEffectType(26, "LUCK", false, 3381504);
		registerPotionEffectType(27, "UNLUCK", false, 12624973);
		registerPotionEffectType(28, "SLOW_FALLING", false, 16773073);
		registerPotionEffectType(29, "CONDUIT_POWER", false, 1950417);
		registerPotionEffectType(30, "DOLPHINS_GRACE", false, 8954814);
		registerPotionEffectType(31, "BAD_OMEN", false, 745784);
		registerPotionEffectType(32, "HERO_OF_THE_VILLAGE", false, 4521796);
		PotionEffectType.stopAcceptingRegistrations();
	}

	@SuppressWarnings("unchecked")
	public static void unregisterAllPotionEffects() {
		PotionEffectType[] byId = (PotionEffectType[]) getField(PotionEffectType.class, "byId", null);
		for (int i = 0; i < byId.length; i++) {
			byId[i] = null;
		}

		Map<String, PotionEffectType> byName = (Map<String, PotionEffectType>) getField(PotionEffectType.class, "byName", null);
		byName.clear();

		Map<NamespacedKey, PotionEffectType> byKey = (Map<NamespacedKey, PotionEffectType>) getField(PotionEffectType.class, "byKey", null);
		byKey.clear();

		setField(PotionEffectType.class, "acceptingNew", null, true);
	}

	@SuppressWarnings("unchecked")
	private void unregisterAllEnchantments() {

		Map<String, Enchantment> byName = (Map<String, Enchantment>) getField(Enchantment.class, "byName", null);
		byName.clear();

		Map<NamespacedKey, Enchantment> byKey = (Map<NamespacedKey, Enchantment>) getField(Enchantment.class, "byKey", null);
		byKey.clear();

		setField(Enchantment.class, "acceptingNew", null, true);
	}

	@Override
	public String[] compatibleVersions() {
		return new String[] { "1.19.2" };
	}

	CommandDispatcher<CommandSourceStack> dispatcher;

	@Override
	public CommandDispatcher<CommandSourceStack> getBrigadierDispatcher() {
		if (this.dispatcher == null) {
			this.dispatcher = new CommandDispatcher<>();
		}
		return this.dispatcher;
	}

	@Override
	public SimpleCommandMap getSimpleCommandMap() {
		return ((ServerMock) Bukkit.getServer()).getCommandMap();
	}

	List<ServerPlayer> players = new ArrayList<>();
	PlayerList playerListMock;
	
	@SuppressWarnings({ "deprecation", "unchecked" })
	@Override
	public CommandSourceStack getBrigadierSourceFromCommandSender(AbstractCommandSender<? extends CommandSender> senderWrapper) {
		CommandSender sender = senderWrapper.getSource();
		CommandSourceStack css = Mockito.mock(CommandSourceStack.class);
		Mockito.when(css.getBukkitSender()).thenReturn(sender);

		if (sender instanceof Player player) {
			// Location argument
			Location loc = player.getLocation();
			Mockito.when(css.getPosition()).thenReturn(new Vec3(loc.getX(), loc.getY(), loc.getZ()));
			
			ServerLevel worldServerMock = Mockito.mock(ServerLevel.class);
			Mockito.when(css.getLevel()).thenReturn(worldServerMock);
			Mockito.when(css.getLevel().hasChunkAt(any(BlockPos.class))).thenReturn(true);
			Mockito.when(css.getLevel().isInWorldBounds(any(BlockPos.class))).thenReturn(true);
			Mockito.when(css.getAnchor()).thenReturn(Anchor.EYES);

			// Advancement argument
			MinecraftServer minecraftServerMock = Mockito.mock(MinecraftServer.class);
			Mockito.when(minecraftServerMock.getAdvancements()).thenReturn(advancementDataWorld);
			Mockito.when(css.getServer()).thenReturn(minecraftServerMock);

			// Entity selector argument
			for(Player onlinePlayer : Bukkit.getOnlinePlayers()) {
				ServerPlayer entityPlayerMock = Mockito.mock(ServerPlayer.class);
				CraftPlayer craftPlayerMock = Mockito.mock(CraftPlayer.class);
				Mockito.when(craftPlayerMock.getName()).thenReturn(onlinePlayer.getName());
				Mockito.when(craftPlayerMock.getUniqueId()).thenReturn(onlinePlayer.getUniqueId());
				Mockito.when(entityPlayerMock.getBukkitEntity()).thenReturn(craftPlayerMock);
				players.add(entityPlayerMock);
			}
			
			if(playerListMock == null) {
				playerListMock = Mockito.mock(PlayerList.class);
				Mockito.when(playerListMock.getPlayerByName(anyString())).thenAnswer(invocation -> {
					String playerName = invocation.getArgument(0);
					for(ServerPlayer onlinePlayer : players) {
						if(onlinePlayer.getBukkitEntity().getName().equals(playerName)) {
							return onlinePlayer;
						}
					}
					return null;
				});
			}
			
			Mockito.when(minecraftServerMock.getPlayerList()).thenReturn(playerListMock);
			Mockito.when(minecraftServerMock.getPlayerList().getPlayers()).thenReturn(players);
			
			// Player argument
			GameProfileCache userCacheMock = Mockito.mock(GameProfileCache.class);
			Mockito.when(userCacheMock.get(anyString())).thenAnswer(invocation -> {
				String playerName = invocation.getArgument(0);
				for(ServerPlayer onlinePlayer : players) {
					if(onlinePlayer.getBukkitEntity().getName().equals(playerName)) {
						return Optional.of(new GameProfile(onlinePlayer.getBukkitEntity().getUniqueId(), playerName));
					}
				}
				return Optional.empty();
			});
			Mockito.when(minecraftServerMock.getProfileCache()).thenReturn(userCacheMock);
			
			// World (Dimension) argument
			Mockito.when(minecraftServerMock.getLevel(any(ResourceKey.class))).thenAnswer(invocation -> {
				// Get the ResourceKey<World> and extract the world name from it
				ResourceKey<Level> resourceKey = invocation.getArgument(0);
				String worldName = resourceKey.location().getPath();
				
				// Get the world via Bukkit (returns a WorldMock) and create a
				// CraftWorld clone of it for WorldServer.getWorld()
				World world = Bukkit.getServer().getWorld(worldName);
				if(world == null) {
					return null;
				} else {
					CraftWorld craftWorldMock = Mockito.mock(CraftWorld.class);
					Mockito.when(craftWorldMock.getName()).thenReturn(world.getName());
					Mockito.when(craftWorldMock.getUID()).thenReturn(world.getUID());
					
					// Create our return WorldServer object
					ServerLevel bukkitWorldServerMock = Mockito.mock(ServerLevel.class);
					Mockito.when(bukkitWorldServerMock.getWorld()).thenReturn(craftWorldMock);
					return bukkitWorldServerMock;
				}
			});
			
			Mockito.when(css.levels()).thenAnswer(invocation -> {
				Set<ResourceKey<Level>> set = new HashSet<>();
				// We only need to implement resourceKey.a()
				
				for(World world : Bukkit.getWorlds()) {
					ResourceKey<Level> key = Mockito.mock(ResourceKey.class);
					Mockito.when(key.location()).thenReturn(new ResourceLocation(world.getName()));
					set.add(key);
				}
				
				return set;
			});
			
			// Rotation argument
			Mockito.when(css.getRotation()).thenReturn(new Vec2(loc.getYaw(), loc.getPitch()));
			
			// Team argument
			ServerScoreboard scoreboardServerMock = Mockito.mock(ServerScoreboard.class);
			Mockito.when(scoreboardServerMock.getPlayerTeam(anyString())).thenAnswer(invocation -> { // Scoreboard#getPlayerTeam
				String teamName = invocation.getArgument(0);
				Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
				if (team == null) {
					return null;
				} else {
					return new PlayerTeam(scoreboardServerMock, teamName);
				}
			});
			Mockito.when(minecraftServerMock.getScoreboard()).thenReturn(scoreboardServerMock); // MinecraftServer#getScoreboard
			
			Mockito.when(css.getAllTeams()).thenAnswer(invocation -> { // CommandSourceStack#getAllTeams
				return Bukkit.getScoreboardManager().getMainScoreboard().getTeams().stream().map(Team::getName).toList();
			});
		}
		return css;
	}

	public static Object getField(Class<?> className, String fieldName, Object instance) {
		try {
			Field field = className.getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(instance);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	public static void setField(Class<?> className, String fieldName, Object instance, Object value) {
		try {
			Field field = className.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(instance, value);
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T getFieldAs(Class<?> className, String fieldName, Object instance, Class<T> asType) {
		try {
			Field field = className.getDeclaredField(fieldName);
			field.setAccessible(true);
			return (T) field.get(instance);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	@Override
	public BukkitCommandSender<? extends CommandSender> getCommandSenderFromCommandSource(CommandSourceStack clw) {
		try {
			return wrapCommandSender(clw.getBukkitSender());
		} catch (UnsupportedOperationException e) {
			return null;
		}
	}

	@Override
	public BukkitCommandSender<? extends CommandSender> getSenderForCommand(CommandContext<CommandSourceStack> cmdCtx, boolean forceNative) {
		return getCommandSenderFromCommandSource(cmdCtx.getSource());
	}

	@Override
	public void createDispatcherFile(File file, CommandDispatcher<CommandSourceStack> dispatcher)
			throws IOException {
		Files
			.asCharSink(file, StandardCharsets.UTF_8)
			.write(new GsonBuilder()
				.setPrettyPrinting()
				.create()
				.toJson(ArgumentTypes.serializeNodeToJson(dispatcher, dispatcher.getRoot())));
	}

	@Override
	public World getWorldForCSS(CommandSourceStack clw) {
		return new WorldMock();
	}
	
	@Override
	public void resendPackets(Player player) {
		// There's nothing to do here, we can't "send packets to players"
		return;
	}

	@Override
	public void addToHelpMap(Map<String, HelpTopic> helpTopicsToAdd) {
		throw new Error("unimplemented");
	}

	@Override
	public String convert(ItemStack is) {
		throw new Error("unimplemented");
	}

	@Override
	public String convert(ParticleData<?> particle) {
		throw new Error("unimplemented");
	}

	@Override
	public String convert(PotionEffectType potion) {
		throw new Error("unimplemented");
	}

	@Override
	public String convert(Sound sound) {
		throw new Error("unimplemented");
	}

	@Override
	public HelpTopic generateHelpTopic(String commandName, String shortDescription, String fullDescription, String permission) {
		throw new Error("unimplemented");
	}

	@Override
	public boolean isVanillaCommandWrapper(Command command) {
		throw new Error("unimplemented");
	}

	@Override
	public void reloadDataPacks() {
		throw new Error("unimplemented");
	}

	public static PotionEffectType[] getPotionEffects() {
		return new PotionEffectType[] {
			PotionEffectType.SPEED,
			PotionEffectType.SLOW,
			PotionEffectType.FAST_DIGGING,
			PotionEffectType.SLOW_DIGGING,
			PotionEffectType.INCREASE_DAMAGE,
			PotionEffectType.HEAL,
			PotionEffectType.HARM,
			PotionEffectType.JUMP,
			PotionEffectType.CONFUSION,
			PotionEffectType.REGENERATION,
			PotionEffectType.DAMAGE_RESISTANCE,
			PotionEffectType.FIRE_RESISTANCE,
			PotionEffectType.WATER_BREATHING,
			PotionEffectType.INVISIBILITY,
			PotionEffectType.BLINDNESS,
			PotionEffectType.NIGHT_VISION,
			PotionEffectType.HUNGER,
			PotionEffectType.WEAKNESS,
			PotionEffectType.POISON,
			PotionEffectType.WITHER,
			PotionEffectType.HEALTH_BOOST,
			PotionEffectType.ABSORPTION,
			PotionEffectType.SATURATION,
			PotionEffectType.GLOWING,
			PotionEffectType.LEVITATION,
			PotionEffectType.LUCK,
			PotionEffectType.UNLUCK,
			PotionEffectType.SLOW_FALLING,
			PotionEffectType.CONDUIT_POWER,
			PotionEffectType.DOLPHINS_GRACE,
			PotionEffectType.BAD_OMEN,
			PotionEffectType.HERO_OF_THE_VILLAGE
		};
	}

	public static EntityType[] getEntityTypes() {
		return new EntityType[] {
			EntityType.DROPPED_ITEM,
			EntityType.EXPERIENCE_ORB,
			EntityType.AREA_EFFECT_CLOUD,
			EntityType.ELDER_GUARDIAN,
			EntityType.WITHER_SKELETON,
			EntityType.STRAY,
			EntityType.EGG,
			EntityType.LEASH_HITCH,
			EntityType.PAINTING,
			EntityType.ARROW,
			EntityType.SNOWBALL,
			EntityType.FIREBALL,
			EntityType.SMALL_FIREBALL,
			EntityType.ENDER_PEARL,
			EntityType.ENDER_SIGNAL,
			EntityType.SPLASH_POTION,
			EntityType.THROWN_EXP_BOTTLE,
			EntityType.ITEM_FRAME,
			EntityType.WITHER_SKULL,
			EntityType.PRIMED_TNT,
			EntityType.FALLING_BLOCK,
			EntityType.FIREWORK,
			EntityType.HUSK,
			EntityType.SPECTRAL_ARROW,
			EntityType.SHULKER_BULLET,
			EntityType.DRAGON_FIREBALL,
			EntityType.ZOMBIE_VILLAGER,
			EntityType.SKELETON_HORSE,
			EntityType.ZOMBIE_HORSE,
			EntityType.ARMOR_STAND,
			EntityType.DONKEY,
			EntityType.MULE,
			EntityType.EVOKER_FANGS,
			EntityType.EVOKER,
			EntityType.VEX,
			EntityType.VINDICATOR,
			EntityType.ILLUSIONER,
			EntityType.MINECART_COMMAND,
			EntityType.BOAT,
			EntityType.MINECART,
			EntityType.MINECART_CHEST,
			EntityType.MINECART_FURNACE,
			EntityType.MINECART_TNT,
			EntityType.MINECART_HOPPER,
			EntityType.MINECART_MOB_SPAWNER,
			EntityType.CREEPER,
			EntityType.SKELETON,
			EntityType.SPIDER,
			EntityType.GIANT,
			EntityType.ZOMBIE,
			EntityType.SLIME,
			EntityType.GHAST,
			EntityType.ZOMBIFIED_PIGLIN,
			EntityType.ENDERMAN,
			EntityType.CAVE_SPIDER,
			EntityType.SILVERFISH,
			EntityType.BLAZE,
			EntityType.MAGMA_CUBE,
			EntityType.ENDER_DRAGON,
			EntityType.WITHER,
			EntityType.BAT,
			EntityType.WITCH,
			EntityType.ENDERMITE,
			EntityType.GUARDIAN,
			EntityType.SHULKER,
			EntityType.PIG,
			EntityType.SHEEP,
			EntityType.COW,
			EntityType.CHICKEN,
			EntityType.SQUID,
			EntityType.WOLF,
			EntityType.MUSHROOM_COW,
			EntityType.SNOWMAN,
			EntityType.OCELOT,
			EntityType.IRON_GOLEM,
			EntityType.HORSE,
			EntityType.RABBIT,
			EntityType.POLAR_BEAR,
			EntityType.LLAMA,
			EntityType.LLAMA_SPIT,
			EntityType.PARROT,
			EntityType.VILLAGER,
			EntityType.ENDER_CRYSTAL,
			EntityType.TURTLE,
			EntityType.PHANTOM,
			EntityType.TRIDENT,
			EntityType.COD,
			EntityType.SALMON,
			EntityType.PUFFERFISH,
			EntityType.TROPICAL_FISH,
			EntityType.DROWNED,
			EntityType.DOLPHIN,
			EntityType.CAT,
			EntityType.PANDA,
			EntityType.PILLAGER,
			EntityType.RAVAGER,
			EntityType.TRADER_LLAMA,
			EntityType.WANDERING_TRADER,
			EntityType.FOX,
			EntityType.BEE,
			EntityType.HOGLIN,
			EntityType.PIGLIN,
			EntityType.STRIDER,
			EntityType.ZOGLIN,
			EntityType.PIGLIN_BRUTE,
			EntityType.AXOLOTL,
			EntityType.GLOW_ITEM_FRAME,
			EntityType.GLOW_SQUID,
			EntityType.GOAT,
			EntityType.MARKER,
			EntityType.FISHING_HOOK,
			EntityType.LIGHTNING,
			EntityType.PLAYER,
			EntityType.UNKNOWN
		};
	}

	public static Enchantment[] getEnchantments() {
		return new Enchantment[] {
			Enchantment.PROTECTION_ENVIRONMENTAL,
			Enchantment.PROTECTION_FIRE,
			Enchantment.PROTECTION_FALL,
			Enchantment.PROTECTION_EXPLOSIONS,
			Enchantment.PROTECTION_PROJECTILE,
			Enchantment.OXYGEN,
			Enchantment.WATER_WORKER,
			Enchantment.THORNS,
			Enchantment.DEPTH_STRIDER,
			Enchantment.FROST_WALKER,
			Enchantment.BINDING_CURSE,
			Enchantment.DAMAGE_ALL,
			Enchantment.DAMAGE_UNDEAD,
			Enchantment.DAMAGE_ARTHROPODS,
			Enchantment.KNOCKBACK,
			Enchantment.FIRE_ASPECT,
			Enchantment.LOOT_BONUS_MOBS,
			Enchantment.SWEEPING_EDGE,
			Enchantment.DIG_SPEED,
			Enchantment.SILK_TOUCH,
			Enchantment.DURABILITY,
			Enchantment.LOOT_BONUS_BLOCKS,
			Enchantment.ARROW_DAMAGE,
			Enchantment.ARROW_KNOCKBACK,
			Enchantment.ARROW_FIRE,
			Enchantment.ARROW_INFINITE,
			Enchantment.LUCK,
			Enchantment.LURE,
			Enchantment.LOYALTY,
			Enchantment.IMPALING,
			Enchantment.RIPTIDE,
			Enchantment.CHANNELING,
			Enchantment.MULTISHOT,
			Enchantment.QUICK_CHARGE,
			Enchantment.PIERCING,
			Enchantment.MENDING,
			Enchantment.VANISHING_CURSE,
			Enchantment.SOUL_SPEED
		};
	}
	
	public static org.bukkit.loot.LootTables[] getLootTables() {
		return new org.bukkit.loot.LootTables[] {
			// org.bukkit.loot.LootTables.EMPTY,
			org.bukkit.loot.LootTables.ABANDONED_MINESHAFT,
			org.bukkit.loot.LootTables.BURIED_TREASURE,
			org.bukkit.loot.LootTables.DESERT_PYRAMID,
			org.bukkit.loot.LootTables.END_CITY_TREASURE,
			org.bukkit.loot.LootTables.IGLOO_CHEST,
			org.bukkit.loot.LootTables.JUNGLE_TEMPLE,
			org.bukkit.loot.LootTables.JUNGLE_TEMPLE_DISPENSER,
			org.bukkit.loot.LootTables.NETHER_BRIDGE,
			org.bukkit.loot.LootTables.PILLAGER_OUTPOST,
			org.bukkit.loot.LootTables.BASTION_TREASURE,
			org.bukkit.loot.LootTables.BASTION_OTHER,
			org.bukkit.loot.LootTables.BASTION_BRIDGE,
			org.bukkit.loot.LootTables.BASTION_HOGLIN_STABLE,
			org.bukkit.loot.LootTables.RUINED_PORTAL,
			org.bukkit.loot.LootTables.SHIPWRECK_MAP,
			org.bukkit.loot.LootTables.SHIPWRECK_SUPPLY,
			org.bukkit.loot.LootTables.SHIPWRECK_TREASURE,
			org.bukkit.loot.LootTables.SIMPLE_DUNGEON,
			org.bukkit.loot.LootTables.SPAWN_BONUS_CHEST,
			org.bukkit.loot.LootTables.STRONGHOLD_CORRIDOR,
			org.bukkit.loot.LootTables.STRONGHOLD_CROSSING,
			org.bukkit.loot.LootTables.STRONGHOLD_LIBRARY,
			org.bukkit.loot.LootTables.UNDERWATER_RUIN_BIG,
			org.bukkit.loot.LootTables.UNDERWATER_RUIN_SMALL,
			org.bukkit.loot.LootTables.VILLAGE_ARMORER,
			org.bukkit.loot.LootTables.VILLAGE_BUTCHER,
			org.bukkit.loot.LootTables.VILLAGE_CARTOGRAPHER,
			org.bukkit.loot.LootTables.VILLAGE_DESERT_HOUSE,
			org.bukkit.loot.LootTables.VILLAGE_FISHER,
			org.bukkit.loot.LootTables.VILLAGE_FLETCHER,
			org.bukkit.loot.LootTables.VILLAGE_MASON,
			org.bukkit.loot.LootTables.VILLAGE_PLAINS_HOUSE,
			org.bukkit.loot.LootTables.VILLAGE_SAVANNA_HOUSE,
			org.bukkit.loot.LootTables.VILLAGE_SHEPHERD,
			org.bukkit.loot.LootTables.VILLAGE_SNOWY_HOUSE,
			org.bukkit.loot.LootTables.VILLAGE_TAIGA_HOUSE,
			org.bukkit.loot.LootTables.VILLAGE_TANNERY,
			org.bukkit.loot.LootTables.VILLAGE_TEMPLE,
			org.bukkit.loot.LootTables.VILLAGE_TOOLSMITH,
			org.bukkit.loot.LootTables.VILLAGE_WEAPONSMITH,
			org.bukkit.loot.LootTables.WOODLAND_MANSION,
			org.bukkit.loot.LootTables.ARMOR_STAND,
			org.bukkit.loot.LootTables.AXOLOTL,
			org.bukkit.loot.LootTables.BAT,
			org.bukkit.loot.LootTables.BEE,
			org.bukkit.loot.LootTables.BLAZE,
			org.bukkit.loot.LootTables.CAT,
			org.bukkit.loot.LootTables.CAVE_SPIDER,
			org.bukkit.loot.LootTables.CHICKEN,
			org.bukkit.loot.LootTables.COD,
			org.bukkit.loot.LootTables.COW,
			org.bukkit.loot.LootTables.CREEPER,
			org.bukkit.loot.LootTables.DOLPHIN,
			org.bukkit.loot.LootTables.DONKEY,
			org.bukkit.loot.LootTables.DROWNED,
			org.bukkit.loot.LootTables.ELDER_GUARDIAN,
			org.bukkit.loot.LootTables.ENDER_DRAGON,
			org.bukkit.loot.LootTables.ENDERMAN,
			org.bukkit.loot.LootTables.ENDERMITE,
			org.bukkit.loot.LootTables.EVOKER,
			org.bukkit.loot.LootTables.FOX,
			org.bukkit.loot.LootTables.GHAST,
			org.bukkit.loot.LootTables.GIANT,
			org.bukkit.loot.LootTables.GLOW_SQUID,
			org.bukkit.loot.LootTables.GOAT,
			org.bukkit.loot.LootTables.GUARDIAN,
			org.bukkit.loot.LootTables.HOGLIN,
			org.bukkit.loot.LootTables.HORSE,
			org.bukkit.loot.LootTables.HUSK,
			org.bukkit.loot.LootTables.ILLUSIONER,
			org.bukkit.loot.LootTables.IRON_GOLEM,
			org.bukkit.loot.LootTables.LLAMA,
			org.bukkit.loot.LootTables.MAGMA_CUBE,
			org.bukkit.loot.LootTables.MOOSHROOM,
			org.bukkit.loot.LootTables.MULE,
			org.bukkit.loot.LootTables.OCELOT,
			org.bukkit.loot.LootTables.PANDA,
			org.bukkit.loot.LootTables.PARROT,
			org.bukkit.loot.LootTables.PHANTOM,
			org.bukkit.loot.LootTables.PIG,
			org.bukkit.loot.LootTables.PIGLIN,
			org.bukkit.loot.LootTables.PIGLIN_BRUTE,
			org.bukkit.loot.LootTables.PILLAGER,
			org.bukkit.loot.LootTables.PLAYER,
			org.bukkit.loot.LootTables.POLAR_BEAR,
			org.bukkit.loot.LootTables.PUFFERFISH,
			org.bukkit.loot.LootTables.RABBIT,
			org.bukkit.loot.LootTables.RAVAGER,
			org.bukkit.loot.LootTables.SALMON,
			org.bukkit.loot.LootTables.SHULKER,
			org.bukkit.loot.LootTables.SILVERFISH,
			org.bukkit.loot.LootTables.SKELETON,
			org.bukkit.loot.LootTables.SKELETON_HORSE,
			org.bukkit.loot.LootTables.SLIME,
			org.bukkit.loot.LootTables.SNOW_GOLEM,
			org.bukkit.loot.LootTables.SPIDER,
			org.bukkit.loot.LootTables.SQUID,
			org.bukkit.loot.LootTables.STRAY,
			org.bukkit.loot.LootTables.STRIDER,
			org.bukkit.loot.LootTables.TRADER_LLAMA,
			org.bukkit.loot.LootTables.TROPICAL_FISH,
			org.bukkit.loot.LootTables.TURTLE,
			org.bukkit.loot.LootTables.VEX,
			org.bukkit.loot.LootTables.VILLAGER,
			org.bukkit.loot.LootTables.VINDICATOR,
			org.bukkit.loot.LootTables.WANDERING_TRADER,
			org.bukkit.loot.LootTables.WITCH,
			org.bukkit.loot.LootTables.WITHER,
			org.bukkit.loot.LootTables.WITHER_SKELETON,
			org.bukkit.loot.LootTables.WOLF,
			org.bukkit.loot.LootTables.ZOGLIN,
			org.bukkit.loot.LootTables.ZOMBIE,
			org.bukkit.loot.LootTables.ZOMBIE_HORSE,
			org.bukkit.loot.LootTables.ZOMBIE_VILLAGER,
			org.bukkit.loot.LootTables.ZOMBIFIED_PIGLIN,
			org.bukkit.loot.LootTables.ARMORER_GIFT,
			org.bukkit.loot.LootTables.BUTCHER_GIFT,
			org.bukkit.loot.LootTables.CARTOGRAPHER_GIFT,
			org.bukkit.loot.LootTables.CAT_MORNING_GIFT,
			org.bukkit.loot.LootTables.CLERIC_GIFT,
			org.bukkit.loot.LootTables.FARMER_GIFT,
			org.bukkit.loot.LootTables.FISHERMAN_GIFT,
			org.bukkit.loot.LootTables.FISHING,
			org.bukkit.loot.LootTables.FISHING_FISH,
			org.bukkit.loot.LootTables.FISHING_JUNK,
			org.bukkit.loot.LootTables.FISHING_TREASURE,
			org.bukkit.loot.LootTables.FLETCHER_GIFT,
			org.bukkit.loot.LootTables.LEATHERWORKER_GIFT,
			org.bukkit.loot.LootTables.LIBRARIAN_GIFT,
			org.bukkit.loot.LootTables.MASON_GIFT,
			org.bukkit.loot.LootTables.SHEPHERD_GIFT,
			org.bukkit.loot.LootTables.TOOLSMITH_GIFT,
			org.bukkit.loot.LootTables.WEAPONSMITH_GIFT,
			org.bukkit.loot.LootTables.PIGLIN_BARTERING,
			org.bukkit.loot.LootTables.SHEEP,
			org.bukkit.loot.LootTables.SHEEP_BLACK,
			org.bukkit.loot.LootTables.SHEEP_BLUE,
			org.bukkit.loot.LootTables.SHEEP_BROWN,
			org.bukkit.loot.LootTables.SHEEP_CYAN,
			org.bukkit.loot.LootTables.SHEEP_GRAY,
			org.bukkit.loot.LootTables.SHEEP_GREEN,
			org.bukkit.loot.LootTables.SHEEP_LIGHT_BLUE,
			org.bukkit.loot.LootTables.SHEEP_LIGHT_GRAY,
			org.bukkit.loot.LootTables.SHEEP_LIME,
			org.bukkit.loot.LootTables.SHEEP_MAGENTA,
			org.bukkit.loot.LootTables.SHEEP_ORANGE,
			org.bukkit.loot.LootTables.SHEEP_PINK,
			org.bukkit.loot.LootTables.SHEEP_PURPLE,
			org.bukkit.loot.LootTables.SHEEP_RED,
			org.bukkit.loot.LootTables.SHEEP_WHITE,
			org.bukkit.loot.LootTables.SHEEP_YELLOW
		};
	}
	
	public static String getNMSPotionEffectName_1_16_5(PotionEffectType potionEffectType) {
		throw new Error("Can't get legacy NMS PotionEffectName in this version: 1.18");
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getMinecraftServer() {
		MinecraftServer minecraftServerMock = Mockito.mock(MinecraftServer.class);

		// LootTableArgument
		Mockito.when(minecraftServerMock.getLootTables()).thenAnswer(invocation -> {
			LootTables lootTables = Mockito.mock(LootTables.class);
			Mockito.when(lootTables.get(any(ResourceLocation.class))).thenAnswer(i -> {
				if(BuiltInLootTables.all().contains(i.getArgument(0))) {
					return net.minecraft.world.level.storage.loot.LootTable.EMPTY;
				} else {
					return null;
				}
			});
			Mockito.when(lootTables.getIds()).thenAnswer(i -> {
				return Streams
				.concat(
					Arrays.stream(getEntityTypes())
						.filter(e -> !e.equals(EntityType.UNKNOWN))
						.filter(e -> e.isAlive())
						.map(EntityType::getKey)
						.map(k -> new ResourceLocation("minecraft", "entities/" + k.getKey())),
					BuiltInLootTables.all().stream()
				)
				.collect(Collectors.toSet());
			});
			return lootTables;
		});
		
		// Advancement argument
		Mockito.when(minecraftServerMock.getAdvancements()).thenReturn(advancementDataWorld);
		return (T) minecraftServerMock;
	}
	
	static ServerAdvancementManager advancementDataWorld = new ServerAdvancementManager(null);

	public static org.bukkit.advancement.Advancement addAdvancement(NamespacedKey key) {
		advancementDataWorld.advancements.advancements.put(new ResourceLocation(key.toString()), new Advancement(new ResourceLocation(key.toString()), null, null, null, new HashMap<>(), null));
		return new org.bukkit.advancement.Advancement() {
			
			@Override
			public NamespacedKey getKey() {
				return key;
			}
			
			@Override
			public Collection<String> getCriteria() {
				return List.of();
			}

			@Override
			public @Nullable AdvancementDisplay getDisplay() {
				return null;
			}

			@Override
			public @NotNull Component displayName() {
				return null;
			}

			@Override
			public org.bukkit.advancement.@Nullable Advancement getParent() {
				return null;
			}

			@Override
			public @NotNull @Unmodifiable Collection<org.bukkit.advancement.Advancement> getChildren() {
				return null;
			}

			@Override
			public org.bukkit.advancement.@NotNull Advancement getRoot() {
				return null;
			}
		};
	}


}
