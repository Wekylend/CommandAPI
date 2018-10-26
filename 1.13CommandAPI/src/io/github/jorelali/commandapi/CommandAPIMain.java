package io.github.jorelali.commandapi;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.jorelali.commandapi.api.CommandAPI;
import io.github.jorelali.commandapi.api.FunctionWrapper;
import io.github.jorelali.commandapi.api.arguments.AdvancementArgument;
import io.github.jorelali.commandapi.api.arguments.Argument;
import io.github.jorelali.commandapi.api.arguments.ChatComponentArgument;
import io.github.jorelali.commandapi.api.arguments.EntitySelectorArgument;
import io.github.jorelali.commandapi.api.arguments.EntitySelectorArgument.EntitySelector;
import io.github.jorelali.commandapi.api.arguments.FunctionArgument;
import io.github.jorelali.commandapi.api.arguments.IntegerArgument;
import io.github.jorelali.commandapi.api.arguments.LiteralArgument;
import io.github.jorelali.commandapi.api.arguments.RecipeArgument;
import io.github.jorelali.commandapi.api.arguments.SuggestedStringArgument;
import io.github.jorelali.commandapi.api.arguments.TextArgument;
import net.md_5.bungee.api.chat.BaseComponent;

public class CommandAPIMain extends JavaPlugin {
	
	private static Logger logger;
	
	public static Logger getLog() {
		return logger;
	}
	
	/** 
	 * Configuration wrapper class.
	 * The config.yml file used by the CommandAPI is only ever read from,
	 * nothing is ever written to it. That's why there's only getter methods.
	 */
	public class Config {
		
		//Output registering and unregistering of commands
		private final boolean verboseOutput;
		
		//Create a command_registration.json file
		private final boolean createDispatcherFile;
		
		//Run test code in the onEnable() method
		private final boolean runTestCode;
		
		public Config(FileConfiguration fileConfig) {
			verboseOutput = fileConfig.getBoolean("verbose-outputs");
			createDispatcherFile = fileConfig.getBoolean("create-dispatcher-json");
			runTestCode = fileConfig.getBoolean("test-code");
		}
		
		public boolean hasVerboseOutput() {
			return verboseOutput;
		}
		
		public boolean willCreateDispatcherFile() {
			return createDispatcherFile;
		}
		
		public boolean runTestCode() {
			return runTestCode;
		}
		
	}

	private static Config config;

	//Gets the instance of Config
	public static Config getConfiguration() {
		return config;
	}
	
	
	@Override
	public void onLoad() {
		saveDefaultConfig();
		CommandAPIMain.config = new Config(getConfig());
		logger = getLogger();
	}
	
	@Override
	public void onEnable() {
		if(config.runTestCode()) {

			//Test command unregistration
			CommandAPI.getInstance().unregister("gamemode");

			//Test ChatComponentArgument
			LinkedHashMap<String, Argument> arguments = new LinkedHashMap<>();
			arguments.put("rawText", new ChatComponentArgument());
			CommandAPI.getInstance().register("raw", arguments, (sender, args) -> {
				sender.sendMessage("a");
//				if (sender instanceof Player) {
//					Player player = (Player) sender;
//					BaseComponent[] arr = (BaseComponent[]) args[0];
//					player.spigot().sendMessage(arr);
//				}
			});
			
			//Tests ChatComponentArgument compatibility with books
			arguments.clear();
			arguments.put("contents", new ChatComponentArgument());
			CommandAPI.getInstance().register("tobook", arguments, (sender, args) -> {
				if (sender instanceof Player) {
					Player player = (Player) sender;
					BaseComponent[] arr = (BaseComponent[]) args[0];
					
					ItemStack is = new ItemStack(Material.WRITTEN_BOOK);
					BookMeta meta = (BookMeta) is.getItemMeta(); 
					meta.spigot().addPage(arr);
					is.setItemMeta(meta);
					
					player.getInventory().addItem(is);
				}
			});

			//Test gamemode command literals
			HashMap<String, GameMode> gamemodes = new HashMap<>();
			gamemodes.put("adventure", GameMode.ADVENTURE);
			gamemodes.put("creative", GameMode.CREATIVE);
			gamemodes.put("spectator", GameMode.SPECTATOR);
			gamemodes.put("survival", GameMode.SURVIVAL);

			for (String key : gamemodes.keySet()) {
				LinkedHashMap<String, Argument> myArgs = new LinkedHashMap<>();
				myArgs.put(key, new LiteralArgument(key));
				CommandAPI.getInstance().register("gamemode", new String[] {"gm"}, myArgs, (sender, args) -> {
					if (sender instanceof Player) {
						Player player = (Player) sender;
						player.setGameMode(gamemodes.get(key));
					}
				});
			}
			
			arguments.clear();
			arguments.put("id", new IntegerArgument(0, 3));
			CommandAPI.getInstance().register("gamemode", new String[] {"gm"}, arguments, (sender, args) -> {
				if (sender instanceof Player) {
					Player player = (Player) sender;
					GameMode targetGM = null;
					switch((int) args[0]) {
						default:
						case 0:
							targetGM = GameMode.SURVIVAL;
							break;
						case 1:
							targetGM = GameMode.CREATIVE;
							break;
						case 2:
							targetGM = GameMode.ADVENTURE;
							break;
						case 3:
							targetGM = GameMode.SPECTATOR;
							break;
					}
					player.setGameMode(targetGM);
				}
			});
			
			//Tests SuggestedStringArguments
			arguments.clear();
			List<String> strList = Arrays.stream(Material.values()).map(element -> element.name()).collect(Collectors.toList());
			arguments.put("test", new SuggestedStringArgument(strList));
			CommandAPI.getInstance().register("suggest", arguments, (sender, args) -> {
				if (sender instanceof Player) {
					Player player = (Player) sender;
					player.sendMessage((String) args[0]);
				}
			});
			
			//Tests target entities
			arguments.clear();
			arguments.put("target", new EntitySelectorArgument(EntitySelector.MANY_ENTITIES));
			CommandAPI.getInstance().register("aaa", arguments, (sender, args) -> {
				System.out.println(args[0]);
			});

			//Tests target entities
			arguments.clear();
			arguments.put("target", new EntitySelectorArgument(EntitySelector.MANY_PLAYERS));
			arguments.put("b", new TextArgument());
			CommandAPI.getInstance().register("aaa", arguments, (sender, args) -> {
				System.out.println(args[0]);
			});
			
			arguments.clear();
			arguments.put("yes", new FunctionArgument());
			CommandAPI.getInstance().register("run", arguments, (sender, args) -> {
				FunctionWrapper func = (FunctionWrapper) args[0];
				func.run();
			});
			arguments.put("target", new EntitySelectorArgument(EntitySelector.ONE_ENTITY));
			CommandAPI.getInstance().register("run", arguments, (sender, args) -> {
				FunctionWrapper func = ((FunctionWrapper[]) args[0])[0];
				func.runAs((Entity) args[1]);
			});

			//TODO: Test tags (groups of functions)
			//Test goes here
			
			
			//TODO: Test AdvancementArguments
			arguments.clear();
			arguments.put("advancement", new AdvancementArgument());
			CommandAPI.getInstance().register("adv", arguments, (sender, args) -> {
				Advancement advancement = (Advancement) args[0];
				System.out.println("Criteria for " + advancement.getKey().toString());
				advancement.getCriteria().forEach(System.out::println);
			});
			
			//TODO: Test RecipeArguments
			arguments.clear();
			arguments.put("recipe", new RecipeArgument());
			CommandAPI.getInstance().register("rec", arguments, (sender, args) -> {
				Recipe recipe = (Recipe) args[0];
				System.out.println("Recipe creates " + recipe.getResult().getType().name());
			});
		}
	}
	
}