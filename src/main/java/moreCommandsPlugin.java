import static mindustry.Vars.content;
import static mindustry.Vars.maps;
import static mindustry.Vars.netServer;
import static mindustry.Vars.state;
import static mindustry.Vars.world;

import arc.Core;
import arc.math.Mathf;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;

import mindustry.content.Blocks;
import mindustry.core.NetClient;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.Packets.KickReason;

import util.*;
import util.filter.*;
import util.filter.FilterType.Reponses;
import data.*;


public class moreCommandsPlugin extends mindustry.mod.Plugin {
    //Called after all plugins have been created and commands have been registered.
    public void init() {
    	//check if a new update is available 
    	Core.net.httpGet(mindustry.Vars.ghApi+"/repos/ZetaMap/moreCommands/releases/latest", s -> {
    		if (Strings.parseFloat(arc.util.serialization.Jval.read(s.getResultAsString()).get("tag_name").asString().substring(1)) > 
    			Strings.parseFloat(mindustry.Vars.mods.getMod("morecommands").meta.version))
    				Log.info("A new version of moreCommands is available! See 'github.com/ZetaMap/moreCommands/releases' to download it!");
    	}, f -> Log.err(f)); 
    	
    	ContentRegister.initFilters(); //init chat and actions filters
    	CM.init(); //init the commands manager
    	
    	//pause the game if no one is connected
    	if (Groups.player.size() < 1 && PVars.autoPause) {
			state.serverPaused = true;
			Log.info("auto-pause: " + Groups.player.size() + " player connected -> Game paused...");
		}
    } 
    
	public moreCommandsPlugin() {
		loadAll(); //init other classes and load settings
		ContentRegister.initEvents(); //init events
    }
    

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){
    	ContentRegister.CommandsRegister commands = ContentRegister.setHandler(handler);
    	
    	commands.add("unban-all", "[y|n]", "Unban all IP and ID", arg -> {
    		if (arg.length == 1 && !PVars.unbanConfirm) {
    			Log.err("Use first: 'unban-all', before confirming the command.");
    			return;
    		} else if (!PVars.unbanConfirm) {
    			Log.warn("Are you sure to unban all all IP and ID ? (unban-all [y|n])");
    			PVars.unbanConfirm = true;
    			return;
    		} else if (arg.length == 0 && PVars.unbanConfirm) {
    			Log.warn("Are you sure to unban all all IP and ID ? (unban-all [y|n])");
    			PVars.unbanConfirm = true;
    			return;
    		}

    		switch (arg[0]) {
    			case "y": case "yes":
    				netServer.admins.getBanned().each(unban -> netServer.admins.unbanPlayerID(unban.id));
    				netServer.admins.getBannedIPs().each(ip -> netServer.admins.unbanPlayerIP(ip));
    				Log.info("All all IP and ID have been unbanned!");
    				PVars.unbanConfirm = false;
    				break;
    			default: 
    				Log.err("Confirmation canceled ...");
    				PVars.unbanConfirm = false;
    		}
    	});
    	
    	commands.add("auto-pause", "Pause the game if there is no one connected", arg -> {
    		if (PVars.autoPause) {
    			PVars.autoPause = false;
    			saveAllSettings();
    			Log.info("Auto pause is disabled.");
    				
    	        state.serverPaused = false;
    	        Log.info("auto-pause: " + Groups.player.size() + " player(s) connected -> Game unpaused...");
    		} else {
    			PVars.autoPause = true;
    			saveAllSettings();
    			Log.info("Auto pause is enabled.");
    				
    			if (Groups.player.size() < 1 && PVars.autoPause) {
    				state.serverPaused = true;
    				Log.info("auto-pause: " + Groups.player.size() + " player connected -> Game paused...");
    			}
    		}
    	});
    	
        commands.add("chat", "[on|off]", "Enabled/disabled the chat", arg -> {
        	if (arg.length == 1) {
        		if (Strings.choiseOn(arg[0])) {
        			if (PVars.tchat) {
        				Log.err("Disabled first!");
        				return;
        			}
        			PVars.tchat = true;
        		
        		} else if (Strings.choiseOff(arg[0])) {
        			if (!PVars.tchat) {
        				Log.err("Enabled first!");
        				return;
        			}
        			PVars.tchat = false;
        		
        		} else {
        			Log.err("Invalid arguments. \n - The chat is currently @.", PVars.tchat ? "enabled" : "disabled");
        			return;
        		}
        		
        		saveAllSettings();
    			Log.info("Chat @ ...", PVars.tchat ? "enabled" : "disabled");
    			Call.sendMessage("\n[gold]-------------------- \n[scarlet]/!\\[orange] Chat " + (PVars.tchat ? "enabled" : "disabled") 
    				+ " by [scarlet][[Server][]! \n[gold]--------------------\n");
        		
        	} else Log.info("The chat is currently @.", PVars.tchat ? "enabled" : "disabled");
        });
        
        commands.add("nice-welcome", "Nice welcome for me", arg -> {
        	PVars.niceWelcome = !PVars.niceWelcome;
        	Log.info(PVars.niceWelcome ? "Enabled..." : "Disabled...");
        });
        
        commands.add("commands", "<list|commandName|reset> [on|off]", "Enable/Disable a command. /!\\Requires server restart to apply changes.", arg -> {
        	if (arg[0].equals("list")) {
        		StringBuilder builder = new StringBuilder();
        		Seq<CM.Commands> client = new Seq<CM.Commands>().addAll(CM.copy().filter(c -> c.name.startsWith("/")));
        		Seq<CM.Commands> server = new Seq<CM.Commands>().addAll(CM.copy().filter(c -> !c.name.startsWith("/")));
        		int best1 = Strings.bestLength(client.map(c -> c.name));
        		int best2 = Strings.bestLength(server.map(c -> c.name));
        		
        		Log.info("List of all commands: ");
        		Log.info(Strings.lJust("| Server commands: Total:" + server.size, 28+best2) + "Client commands: Total:" + client.size);
        		for (int i=0; i<Math.max(client.size, server.size); i++) {
        			try { builder.append(Strings.mJust("| | Name: " + server.get(i).name, " - Enabled: " + (server.get(i).isActivate ? "true " : "false"), 27+best2)); } 
        			catch (IndexOutOfBoundsException e) { builder.append("|" + Strings.createSpaces(best1+20)); }
        			try { builder.append(Strings.lJust(" | Name: " + client.get(i).name, 9+best1) + " - Enabled: " + client.get(i).isActivate); } 
        			catch (IndexOutOfBoundsException e) {}
        			
        			Log.info(builder.toString());
        			builder = new StringBuilder();
        		}
        		
        	} else if (arg[0].equals("reset")) {
        		CM.copy().each(c -> CM.get(c.name).set(true));
        		CM.save();
        		CM.update(handler);
				Log.info("All command statuses have been reset.");
        		
        		
        	} else {
        		CM.Commands command = CM.get(arg[0]);
        		
        		if (command == null) Log.err("This command doesn't exist!");
        		else if (arg.length == 2) {
        			if (Strings.choiseOn(arg[1])) command.set(true);
        			else if (Strings.choiseOff(arg[1])) command.set(false);
        			else {
        				Log.err("Invalid value");
						return;
        			}
        			
        			Log.info("@ ..", command.isActivate ? "Enabled" : "Disabled");
        			CM.save();
    				CM.update(handler);

        		} else Log.info("The command '" + command.name + "' is currently " + (command.isActivate ? "enabled" : "disabled"));
        	}
        });
        
        commands.add("clear-map", "[y|n]", "Kill all units and destroy all blocks except cores, on the current map.", arg -> {
        	if(!state.is(mindustry.core.GameState.State.playing)) Log.err("Not playing. Host first.");
            else {
            	if (arg.length == 1 && !PVars.clearConfirm) {
        			Log.err("Use first: 'clear-map', before confirming the command.");
        			return;
        		} else if (!PVars.clearConfirm) {
        			Log.warn("This command can crash the server! Are you sure you want it executed? (clear-map [y|n])");
        			PVars.clearConfirm = true;
        			return;
        		} else if (arg.length == 0 && PVars.clearConfirm) {
        			Log.warn("This command can crash the server! Are you sure you want it executed? (clear-map [y|n])");
        			PVars.clearConfirm = true;
        			return;
        		}

        		switch (arg[0]) {
        			case "y": case "yes":
        				Log.info("Begining ...");
        				Call.infoMessage("[scarlet]The map will be reset in [orange]10[] seconds! \n[]All units, players, and buildings (except core) will be destroyed.");
        				try { Thread.sleep(10000); } 
        				catch (InterruptedException e) {}
        				
        				mindustry.gen.Building block;
        				int unitCounter = 0, blockCounter = 0;
        				
        				unitCounter += Groups.unit.size();
        				Groups.unit.each(u -> u.kill());
        				for (int x=0; x<world.width(); x++) {
        					for (int y=0; y<world.height(); y++) {
        						block = world.build(x, y);
        						
        						if (block != null && (block.block != Blocks.coreShard && block.block != Blocks.coreNucleus && block.block != Blocks.coreFoundation)) {
        							blockCounter++;
        							block.kill();
        						}
        					}
        				}
                		Groups.fire.clear();
                		Groups.weather.clear();
                		unitCounter += Groups.unit.size();
                		Groups.unit.each(u -> u.kill());
                		
                		Log.info(Strings.format("Map cleaned! (Killed @ units and destroy @ blocks)", unitCounter, blockCounter));
                		Call.infoMessage(Strings.format("[green]Map cleaned! [lightgray](Killed [scarlet]@[] units and destroy [scarlet]@[] blocks)", unitCounter, blockCounter));
                		PVars.clearConfirm = false;
        				break;
        			
        			default: 
        				Log.err("Confirmation canceled ...");
        				PVars.clearConfirm = false;
        		}
            }
        });
        
        commands.add("gamemode", "[name]", "Change the gamemode of the current map", arg -> {
        	if(!state.is(mindustry.core.GameState.State.playing)) Log.err("Not playing. Host first.");
            else {
            	if (arg.length == 1) {
            		try { 
            			state.rules = state.map.applyRules(Gamemode.valueOf(arg[0]));
            			Groups.player.each(p -> {
            				Call.worldDataBegin(p.con);
                            netServer.sendWorldData(p);
            			});
            			Log.info("Gamemode set to '@'", arg[0]);
            		} catch (Exception e) { Log.err("No gamemode '@' found.", arg[0]); }
            	} else Log.info("The gamemode is curently '@'", state.rules.mode().name());
            }
        });
        
        commands.add("blacklist", "<list|add|remove|clear> <name|ip> [value...]", 
        		"Players using a nickname or ip in the blacklist cannot connect to the server (spaces on the sides and colors are cut off when checking out)", arg -> {
        	BM.blacklistCommand(arg);
        });
        
        commands.add("anti-vpn", "[on|off|limit] [number]", "Anti VPN service", arg -> {
        	if (arg.length == 0) {
        		Log.info("Anti VPN is currently @.", AntiVpn.isEnabled ? "enabled" : "disabled");
        		return;
        	}
        	
        	if (arg[0].equals("limit")) {
        		if (arg.length == 2) {
    				if(Strings.canParseInt(arg[1])){
    	               int number = Strings.parseInt(arg[1]);
    	               
    	               if (number < 999 && number > 1) {
    	            	   AntiVpn.timesLimit = number;
    	            	   Log.info("Set to @ ...", number);
    	            	   AntiVpn.saveSettings();
    	            	   
    	               } else Log.err("'number' must be less than 999 and greater than 1");
    	            } else Log.err("Please type a number");
    			} else Log.info("The unsuccessful search limit is currently at @ tests.", AntiVpn.timesLimit);
        		return;
        	
        	} else if (Strings.choiseOn(arg[0])) {
        		if (AntiVpn.isEnabled) {
    				Log.err("Disabled first!");
    				return;
    			}
    			AntiVpn.isEnabled = true;
    			AntiVpn.timesLeft = AntiVpn.timesLimit;
    			if (!AntiVpn.fullLoaded) AntiVpn.init();
        	
        	} else if (Strings.choiseOff(arg[0])) {
        		if (!AntiVpn.isEnabled) {
    				Log.err("Enabled first!");
    				return;
    			}
    			AntiVpn.isEnabled = false;
        	
        	} else {
        		Log.err("Invalid arguments. \n - Anti VPN is currently @.", AntiVpn.isEnabled ? "enabled" : "disabled");
        		return;
        	}
        	
        	Log.info("Anti VPN @ ...", AntiVpn.isEnabled ? "enabled" : "disabled");
        	AntiVpn.saveSettings();
        });
        
        commands.add("filters", "<help|on|off>", "Enabled/disabled filters", arg -> {
        	if (arg[0].equals("help")) {
        		Log.info("Filters are currently " + (ArgsFilter.enabled ? "enabled." : "disabled."));
    			Log.info("Help for all filters: ");
    			for (FilterType type : FilterType.values()) Log.info(" - " + type.getValue() + ": this filter targets " + type.getDesc() + ".");
    			return;
        	
        	} else if (Strings.choiseOn(arg[0])) {
        		if (ArgsFilter.enabled) {
        			Log.err("Disabled first!");
        			return;
        		}
        		ArgsFilter.enabled = true;
        	
        	} else if (Strings.choiseOff(arg[0])) {
        		if (!ArgsFilter.enabled) {
        			Log.err("Enabled first!");
        			return;
        		}
        		ArgsFilter.enabled = false;
        	
        	} else {
        		Log.err("Invalid arguments.");
        		return;
        	}
        	
        	Log.info("Filters @ ...", ArgsFilter.enabled ? "enabled" : "disabled");
        	ArgsFilter.saveSettings();
        });
        
        commands.add("effect", "<default|list|id|name> [on|off]", "Enabled/disabled a particles effect (default: set to default values, not reset)", arg -> {
        	Effects effect;
        	
        	if (arg[0].equals("default")) {
        		Effects.setToDefault();
        		Effects.saveSettings();
        		Log.info("Effects set to default values");
        		
        	} else if (arg[0].equals("list")) {
        		Log.info("List of all effects: Total: " + Effects.size());
        		int name = Strings.bestLength(Effects.effects.map(e -> e.name))+7, id = Strings.bestLength(Effects.effects.map(e -> e.id+""))+12;
        		Effects.effects.each(e -> Log.info("| Name: " + Strings.mJust(e.name, " - ID: ", name) + Strings.mJust(e.id+"", " - Enabled: ", id) + !e.disabled));
        		
        	} else if (Strings.canParseInt(arg[0])) {
        		effect = Effects.getByID(Strings.parseInt(arg[0])-1);
				
				if (effect != null) {
					if (arg.length == 2) {
						if (Strings.choiseOn(arg[1])) effect.disabled = false;
						else if (Strings.choiseOff(arg[1])) effect.disabled = true;
						else {
							Log.err("Invalid arguments.");
							return;
						}

						Effects.saveSettings();
						Log.info("effect '@' set to @", effect.name, !effect.disabled);	
						
					} else Log.info("effect '@' is curently @", effect.name, effect.disabled ? "disabled" : "enabled");
				} else Log.err("no effect with id '@'", arg[0]);
				
        	} else {
        		effect = Effects.getByName(arg[0]);
				
				if (effect != null) {
					if (arg.length == 2) {
						if (Strings.choiseOn(arg[1])) effect.disabled = false;
						else if (Strings.choiseOff(arg[1])) effect.disabled = true;
						else {
							Log.err("Invalid arguments.");
							return;
						}

						Effects.saveSettings();
						Log.info("effect '@' set to @", effect.name, !effect.disabled);	
						
					} else Log.info("effect '@' is curently @", effect.name, effect.disabled ? "disabled" : "enabled");
				} else Log.err("no effect with name '@'", arg[0]);
        	}
        });
        
        commands.add("switch", "<help|list|add|remove> [name] [ip] [onlyAdmin]", "Configure the list of servers in the switch.", arg -> {
        	switch (arg[0]) {
        		case "help":
        			Log.info("Switch help:");
        			Log.info(" - To set the lobby server for /lobby, just give the name of 'lobby'.");
        			Log.info(" - The character '_' will be automatically replaced by a space, in the name of the server.");
        			Log.info(" - Colors and emojis are purely decorative and will therefore be cut off when researching.");
        			Log.info(" - If the 'onlyAdmin' parameter is specified and is true, only admins will be able to see and connect to the server. "
        					+ "But if a player knows the IP of the server, he can connect to it without going through the command. "
        					+ "So please think about security if you want to make the server only accessible to admins.");
        			break;
        	
        		case "list":
        			Log.info("Lobby server: " + (Switcher.lobby == null ? "not defined" 
        				: "IP: " + Switcher.lobby.ip + " - Port: " + Switcher.lobby.port + " - forAdmin: " + Switcher.lobby.forAdmin));
        			
        			if (Switcher.isEmpty()) Log.info("Switch servers list is empty.");
        			else {
        				int name = Strings.bestLength(Switcher.names(true))+7, ip = Strings.bestLength(Switcher.ips())+9, port = Strings.bestLength(Switcher.ports().map(i -> i+""));
        				
        				Log.info("Switch servers list: Total:" + Switcher.size());
        				Switcher.each(i -> Log.info("| Name: " + Strings.mJust(i.name, " - IP: ", name) + 
        					Strings.mJust(i.ip, " - Port: ", ip) + Strings.mJust(i.port+"", " - forAdmin: ", port) + i.forAdmin));
        			}
        			break;
        			
        		case "add":
        			if (arg.length >= 3) {
        				Switcher server;
        				
        				if (!arg[1].isBlank()) {
        					if (arg.length == 4) {
        						if (Strings.choiseOn(arg[3])) server = Switcher.put(arg[1], arg[2], true);
        						else if (Strings.choiseOff(arg[3])) server = Switcher.put(arg[1], arg[2], false);
        						else {
        							Log.info("Invalid value");
        							return;
        						}
        						
        					} else server = Switcher.put(arg[1], arg[2], false);
		        			
		        			if (server != null) {
		        				Log.info(server.changed ? server.name + " set to " + server.address() + ", for admins: " + server.forAdmin + " ..." : "Added ...");
		        				Switcher.saveSettings();
		        				
		        			} else Log.err("Bad IP format");	
        				} else Log.err("Empty server name (without emoji)");
	        		} else Log.err("3 arguments are expected ");
        			
        			break;
        			
        		case "remove":
        			Log.err(Switcher.remove(arg[1]) == null ? "This server name isn't in the list" : "Removed ...");
        			Switcher.saveSettings();
        			break;
        			
        		default: Log.err("Invalid arguments.");
        	}
        });
    }
    
    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler){
    	ContentRegister.CommandsRegister commands = ContentRegister.setHandler(handler);
    	
    	handler.removeCommand("help");
    	commands.add("help", "[page|filter]", "Lists all commands", false, (arg, player) -> {
    		StringBuilder result = new StringBuilder();
    		FilterSearchReponse filter = ArgsFilter.hasFilter(player, arg);
    		
    		if (arg.length == 1) {
    			if (player.admin) {
    				if (arg[0].equals("filter")) {
    					result.append("Help for all filters: ");
    	    			for (FilterType type : FilterType.values()) result.append("\n - [gold]" + type.getValue() + "[]: this filter targets [sky]" + type.getDesc() + "[].");
    	    			player.sendMessage(result.toString());
    	    			return;
    				
    				} else if (filter.reponse == Reponses.found) {
    					player.sendMessage("Help for filter [gold]" + filter.type.getValue() + "[]: \nThe filter targets [sky]" + filter.type.getDesc() + "[].");
    					return;
    				
    				} else if (filter.reponse == Reponses.notFound) {
    					if (!Strings.canParseInt(arg[0])) {
    						player.sendMessage("[scarlet]'page' must be a number.");
    		                return;
    					}
    				
    				} else {
    					filter.sendIfError();
    					return;
    				}
    				
    			} else if (!Strings.canParseInt(arg[0])) {
    				player.sendMessage("[scarlet]'page' must be a number.");
	                return;
    			}
    		}

    		
        	Seq<CommandHandler.Command> cList = player.admin ? handler.getCommandList() : handler.getCommandList().select(c -> !PVars.adminCommands.contains(c.text));
        	CommandHandler.Command c;
        	int lines = 8,
        		page = arg.length == 1 ? Strings.parseInt(arg[0]) : 1,
        		pages = Mathf.ceil(cList.size / lines);
        	if (cList.size % lines != 0) pages++;
        	
            if(page > pages || page < 1){
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[].");
                return;
            }

            result.append(Strings.format("[orange]-- Commands Page[lightgray] @[gray]/[lightgray]@[orange] --\n", page, pages));
            for(int i=(page-1)*lines; i<lines*page; i++){
            	try { 
            		c = cList.get(i);
            		result.append("\n[orange] " + handler.getPrefix() + c.text + "[white] " + c.paramText + "[lightgray] - " + c.description); 
            	} catch (IndexOutOfBoundsException e) { break; }
            }
            
            player.sendMessage(result.toString());
        });
         
        commands.add("ut","The name of the unit", false, (args, player) -> {
        	try { player.sendMessage("You're a [sky]" + player.unit().type().name + "[]."); }
        	catch (NullPointerException e) { player.sendMessage("You're [sky]invisible ..."); }
        });
        
        commands.add("msg", "<username|ID> <message...>","Send a private message to a player", false, (arg, player) -> {
        	Players result = Players.findByNameOrID(arg);
        	
        	if (result.found) {
        		String message = String.join(" ", result.rest);
        		
        		if (!Strings.stripColors(message).isBlank()) {
        			result.data.msgData.setTarget(player);
            		Call.sendMessage(player.con, message, "[sky]me [gold]--> " + NetClient.colorizeName(result.player.id, result.player.name), player);
            		Call.sendMessage(result.player.con, message, NetClient.colorizeName(player.id, player.name) + " [gold]--> [sky]me", player);
        		
        		} else Players.err(player, "Please don't send an empty message.");
        	} else Players.errNotOnline(player);
         });
        
        commands.add("r", "<message...>","Reply to the last private message received", false, (arg, player) -> {
        	TempData target = TempData.get(player);

        	if (target.msgData.target != null) {
        		if (target.msgData.targetOnline) {
        			if (!Strings.stripColors(arg[0]).isBlank()) {
                		Call.sendMessage(player.con, arg[0], "[sky]me [gold]--> " + NetClient.colorizeName(target.msgData.target.id, target.msgData.target.name), player);
                		Call.sendMessage(target.msgData.target.con, arg[0], NetClient.colorizeName(player.id, player.name) + " [gold]--> [sky]me", player);
        			
        			} else Players.err(player, "Please don't send an empty message.");
        		} else Players.err(player, "This player is disconnected");
        	} else Players.err(player, "No one has sent you a private message");
        });

        commands.add("maps", "[page]", "List all maps on server", false, (arg, player) -> {
        	if(arg.length == 1 && !Strings.canParseInt(arg[0])){
                player.sendMessage("[scarlet]'page' must be a number.");
                return;
            }
        	
        	StringBuilder builder = new StringBuilder();
        	Seq<Map> list = mindustry.Vars.maps.all();
        	Map map;
        	int page = arg.length == 1 ? Strings.parseInt(arg[0]) : 1,
        			lines = 8, 
        			pages = Mathf.ceil(list.size / lines);
        	if (list.size % lines != 0) pages++;
            
            if (page > pages || page < 1) {
            	player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and [orange]" + pages + "[].");
            	return;
            }
            
            builder.append("\n[orange]---- [gold]Maps list [lightgray]" + page + "[gray]/[lightgray]" + pages + "[orange] ----");
            for (int i=(page-1)*lines; i<lines*page;i++) {
            	try { 
            		map = list.get(i);
            		builder.append("\n[orange]  - [white]" +  map.name() + 
	            		"[orange] | [white]" + map.width + "x" + map.height + 
	            		"[orange] | [green]" + (map.custom ? "Custom" : "Builtin") +
	            		"[orange] | By: [sky]" + map.author());
            	} catch (IndexOutOfBoundsException e) { break; }
            }
            builder.append("\n[orange]-----------------------");
            player.sendMessage(builder.toString());
        });
        
        commands.add("vnw", "[number]", "Vote for sending a New Wave", false, (arg, player) -> {
        	if (!PVars.canVote) return;
        	TempData target = TempData.get(player);
        	if (target.votedVNW) {
                player.sendMessage("You have Voted already.");
                return;
        	}
        	
        	if (arg.length == 1) {
        		if (player.admin) {
        			if(Strings.canParseInt(arg[0])) PVars.waveVoted = (byte) Strings.parseInt(arg[0]);
        			else {
                    	Players.err(player, "Please type a number");
                        return;
                    }
        			
        		} else {
        			Players.errPermDenied(player);
        			return;
        		}
        	} else PVars.waveVoted = 1;
        	
            target.votedVNW = true;
            int cur = TempData.filter(p -> p.votedVNW).size;
            int req = Mathf.ceil((float) 0.6f * Groups.player.size());
            Call.sendMessage(NetClient.colorizeName(player.id, player.name) + 
            	"[orange] has voted to "+ (PVars.waveVoted == 1 ? "send a new wave" : "skip [green]" + PVars.waveVoted + " waves") + ". [lightgray](" + (req-cur) + " votes missing)");
            
            if (cur < req) return;

            TempData.setField(p -> p.votedVNW = false);
            Call.sendMessage("[green]Vote for "+ (PVars.waveVoted == 1 ? "Sending a new wave" : "Skiping [scarlet]" + PVars.waveVoted + "[] waves") + " is Passed. New Wave will be Spawned.");

            state.wave += PVars.waveVoted;
            
            if (PVars.waveVoted > 0) {
            	while (PVars.waveVoted-- > 0) {
            		try {
            			state.wavetime = 0f;
	            		Thread.sleep(50); 
            		} catch (Exception e) { break; }
            	}
            
            } else if (state.wave < 0) state.wave = 0;
            state.wavetime = 0f;
		});
        
        commands.add("rtv", "[mapName...]", "Rock the vote to change map", false, (arg, player) -> {
        	if (!PVars.canVote) return;
        	if(Groups.player.size() < 3 && !player.admin){
                player.sendMessage("[scarlet]At least 3 players are needed to start a vote.");
                return;
            }
        	
        	int RTVsize = TempData.filter(p -> p.votedRTV).size;
        	TempData target = TempData.get(player);
        	
        	if (arg.length == 1) {
        		if (RTVsize == 0) {
        			PVars.selectedMap = maps.all().find(map -> Strings.stripColors(map.name()).replace(' ', '_').equalsIgnoreCase(Strings.stripColors(arg[0]).replace(' ', '_')));
        			
        			if (PVars.selectedMap == null) {
        				Players.err(player, "No map with name '@' found.", arg[0]);
        				return;
        			} else maps.queueNewPreview(PVars.selectedMap);
        			
        		} else {
        			Players.err(player, "A vote to change the map is already in progress! [lightgray](selected map:[white] " + PVars.selectedMap.name() + "[lightgray])");
        			return;
        		}
        	} else if (RTVsize == 0) PVars.selectedMap = maps.getNextMap(Gamemode.valueOf(Core.settings.getString("lastServerMode")), state.map);
        	if (target.votedRTV) {
                player.sendMessage("You have Voted already.");
                return;
        	}
        	
        	target.votedRTV = true;
        	RTVsize++;
            int req2 = Mathf.ceil((float) 0.6f * Groups.player.size());
            Call.sendMessage("[scarlet]RTV: [accent]" + NetClient.colorizeName(player.id, player.name) + " [white]wants to change the map, [green]" + RTVsize 
            	+ "[white] votes, [green]" + req2 + "[white] required. [lightgray](selected map: [white]" + PVars.selectedMap.name() + "[lightgray])");
            
            if (RTVsize < req2) return;
            
            TempData.setField(p -> p.votedRTV = false);
            Call.sendMessage("[scarlet]RTV: [green]Vote passed, changing map ... [lightgray](selected map: [white]" + PVars.selectedMap.name() + "[lightgray])");
            new RTV(PVars.selectedMap, Team.crux);
        });
        
        commands.add("lobby", "Switch to lobby server", false, (arg, player) -> {
        	if (Switcher.lobby == null) Players.err(player, "Lobby server not defined");
        	else {
        		Switcher.ConnectReponse connect = Switcher.lobby.connect(player);
        		Call.infoMessage(player.con, (connect.failed ? "[scarlet]Error connecting to server: \n[]" : "") + connect.message);
        	}	
        });
        
        commands.add("switch", "<list|name...>", "Switch to another server", false, (arg, player) -> {
        	if (arg[0].equals("list")) {
        		if (Switcher.isEmpty()) Players.err(player, "No server in the list");
        		else player.sendMessage("Available servers: [white]\n - [orange]" + Switcher.names(player.admin).toString("[white]\n - [orange]"));
        		
        	} else {
        		Switcher server = Switcher.getByName(arg[0]);
        		
        		if (server == null) Players.err(player, "no server with name '@'", arg[0]);
            	else {
            		Switcher.ConnectReponse connect = server.connect(player);
            		Call.infoMessage(player.con, (connect.failed ? "[scarlet]Error connecting to server: \n[]" : "") + connect.message);
            	}
        	}
        });

        commands.add("info-all", "[ID|username...]", "Get all player informations", false, (arg, player) -> {
        	StringBuilder builder = new StringBuilder();
        	ObjectSet<PlayerInfo> infos = ObjectSet.with(player.getInfo());
        	Players test;
			int i = 1;
        	boolean mode = true;
        	
        	if (arg.length == 1) {
        		test = Players.findByName(arg);
    			
    			if (!test.found) {
					if (player.admin) {
						test = Players.findByID(arg);
						
						if (!test.found) {
							infos = netServer.admins.searchNames(arg[0]);
							if (infos.size == 0) infos = ObjectSet.with(netServer.admins.getInfoOptional(arg[0]));
			    			if (infos.size == 0) {
			    				Players.err(player, "No player nickname containing [orange]'@'[].", arg[0]);
			    				return;
			    			}	
							
						} else infos = ObjectSet.with(test.player.getInfo());

		    		} else {
						if (Players.findByID(arg).found) Players.err(player, "You don't have permission to search a player by their ID!");
						else Players.errNotOnline(player);
						return;
		    		}	
    			
    			} else infos = ObjectSet.with(test.player.getInfo());
        		mode = false;
        	} 
        	
        	if (player.admin && !mode) player.sendMessage("[gold]----------------------------------------\n[scarlet]-----" + "\n[white]Players found: [gold]" + infos.size + "\n[scarlet]-----");
        	for (PlayerInfo pI : infos) {
        		if (player.admin && !mode) player.sendMessage("[gold][" + i++ + "] [white]Trace info for player [accent]'" + pI.lastName.replaceAll("\\[", "[[") 
        			+ "[accent]'[white] / ID [accent]'" + pI.id + "' ");
        		else builder.append("[white]Player name [accent]'" + pI.lastName.replaceAll("\\[", "[[") + "[accent]'"+ (mode ? "[white] / ID [accent]'" + pI.id + "'" : "")
        			+ "\n[gold]----------------------------------------[]\n");
        		
        		test = Players.findByID(pI.id + " ");
        		
        		builder.append("[white] - All names used: [accent]" + pI.names
        			+ (test.found ? "\n[white] - [green]Online" 
        				+ "\n[white] - Country: [accent]" + test.player.locale : "")
        			+ (TempData.creatorID.equals(pI.id) ? "\n[white] - [sky]Creator of moreCommands [lightgray](the plugin used by this server)" : "")
        			+ (player.admin ? "\n[white] - IP: [accent]" + pI.lastIP 
        				+ "\n[white] - All IPs used: [accent]" + pI.ips : "")
        			+ "\n[white] - Times joined: [green]" + pI.timesJoined
        			+ "\n[white] - Times kicked: [scarlet]" + pI.timesKicked
        			+ (player.admin ? "\n[white] - Is baned: [accent]" + pI.banned : "")
        			+ "\n[white] - Is admin: [accent]" + pI.admin
        			+ "\n[gold]----------------------------------------");
                	
                if (mode) Call.infoMessage(player.con, builder.toString());
                else {
                	player.sendMessage(builder.toString());
                	builder = new StringBuilder();
                }
        	}
        });
        
        commands.add("rainbow", "[ID|username...]", "[#ff0000]R[#ff7f00]A[#ffff00]I[#00ff00]N[#0000ff]B[#2e2b5f]O[#8B00ff]W[#ff0000]![#ff7f00]!", false, (arg, player) -> {
        	TempData target;
        	
        	if (arg.length == 0) target = TempData.get(player);
        	else {
        		if (player.admin) {
        			target = Players.findByNameOrID(arg).data;
        			
        			if(target == null) {
        				Players.errNotOnline(player);
        				return;
        			}
        		} else {
        			Players.errPermDenied(player);
        			return;
        		}
        	}
        	
        	if(target.rainbowed) {
    			player.sendMessage("[sky]Rainbow effect toggled off" + (arg.length == 1 ? " for the player [accent]" + target.player.name : "") + "[].");
    			target.rainbowed = false;
    			target.player.name = target.realName;
    		} else {
    			player.sendMessage("[sky]Rainbow effect toggled on" + (arg.length == 1 ? " for the player [accent]" + target.player.name : "") + "[].");
    			target.rainbowed = true;
    			target.hasEffect = false;
    		}
        	
        	if (target.rainbowLoop.getState() == Thread.State.TERMINATED || target.rainbowLoop.getState() == Thread.State.NEW) {
        		target.setRainbowLoop();
				target.rainbowLoop.start();
        	}
        });
        
        commands.add("effect", "[list|name|id] [page|ID|username...]","Gives you a particles effect", false, (arg, player) -> {
        	Effects ef;
        	StringBuilder builder = new StringBuilder();
        	TempData target = TempData.get(player);
        	
        	if (arg.length >= 1 && arg[0].equals("list")) {
        		if(arg.length == 2 && !Strings.canParseInt(arg[1])){
                    player.sendMessage("[scarlet]'page' must be a number.");
                    return;
                }
        		
        		int page = arg.length == 2 ? Strings.parseInt(arg[1]) : 1,
        			lines = 12,
        			pages = Mathf.ceil(Effects.size() / lines);
                if (Effects.size() % lines != 0) pages++;
                Effects e;

                if(page > pages || page < 0){
                    player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                    return;
                }

                player.sendMessage("\n[orange]---- [gold]Effects list [lightgray]" + page + "[gray]/[lightgray]" + pages + "[orange] ----");
                for(int i=(page-1)*lines; i<lines*page;i++){
                	try {
                		e = Effects.getByID(i);
                		builder.append("  [orange]- [lightgray]ID:[white] " + e.id + "[orange] | [lightgray]Name:[white] " + e.name + (e.disabled ? " - [scarlet]Disabled" : "") + "\n");
                	} catch (Exception err) { break; }
                }
                player.sendMessage(builder.toString());
                return;
        	
        	} else if (arg.length == 0) {
        		if (target.hasEffect) {
        			target.hasEffect = false;
        			player.sendMessage("[green]Removed particles effect.");
        			return;
        		
        		} else if (target.rainbowed) {
        			Players.err(player, "Please disable rainbow first");
        			return;
        			
        		} else {
        			target.hasEffect = true;
        			
        			do { ef = Effects.getByID(new java.util.Random().nextInt(Effects.size())); } 
        			while (ef.disabled);
        			
        			target.effect = ef;
        			player.sendMessage("Randomised effect ...");
        			player.sendMessage("[green]Start particles effect [accent]" + ef.id + "[scarlet] - []" + ef.name);
        			
        		}
        	
        	} else if (arg.length == 2) {
        		if (player.admin) {
        			TempData target2 = Players.findByNameOrID(arg).data;
        			
        			if(target2 == null) Players.errNotOnline(player);
        			else {
        				if (target2.hasEffect) {
        					target2.hasEffect = false;
        					player.sendMessage("[green]Removed particles effect for [accent]" + target2.player.name);
        				} else Players.err(player, "This player don't have particles effect");
        			}
        		} else Players.errPermDenied(player);
        		return;
        		
        	} else if (target.rainbowed) {
    			Players.err(player, "Please disable rainbow first");
    			return;
    			
        	} else {
        		if(Strings.canParseInt(arg[0])) ef = Effects.getByID(Strings.parseInt(arg[0])-1);
        		else ef = Effects.getByName(arg[0]);
        		
        		if (ef == null) {
        			Players.err(player, "Particle effect don't exist");
        			return;
        			
        		} else if (ef.disabled) {
        			Players.err(player, "This particle effect is disabled");
        			return;
        			
        		} else {
        			target.hasEffect = true;
        			target.effect = ef;
        			player.sendMessage("[green]Start particles effect [accent]" + ef.id + "[scarlet] - []" + ef.name);
        		}
        	}
        	
        	
        	if (target.effectLoop.getState() == Thread.State.TERMINATED || target.effectLoop.getState() == Thread.State.NEW) {
        		target.setEffectLoop();
	        	target.effectLoop.start();
        	}
        });
        
        commands.add("team", "[~|teamname|list|vanish] [filter|username...]","Change team", true, (args, player) ->{
            StringBuilder builder = new StringBuilder();
            Team ret = null;
            FilterSearchReponse filter = null;
            TempData target;
            
            if (args.length == 2) {
            	filter = ArgsFilter.hasFilter(player, args[1]);
            	
            	if (filter.reponse == Reponses.notFound) {
            		target = Players.findByName(args[1]).data;
	            	
	            	if (target == null) {
	            		Players.errNotOnline(player);
	            		return;
	            	}
            	
            	} else if (filter.sendIfError()) return;

            	else target = TempData.get(player);
            } else target = TempData.get(player);
            
            if (filter != null && filter.reponse == Reponses.found) 
            	filter.execute(ctx -> {
            		if (ctx.player != null) {
	            		TempData t = TempData.get(ctx.player);
	        			
	        			if (t.spectate()) {
	            			t.player.sendMessage(">[orange] transferring back to last team");
		                    t.player.team(t.spectate);
		                    Call.setPlayerTeamEditor(t.player, t.spectate);
		                    t.spectate = null;
		                    t.player.name = t.realName;
	        			}
            		}
        		});

            else if (target.spectate()) {
            	target.player.sendMessage(">[orange] transferring back to last team");
                target.player.team(target.spectate);
                Call.setPlayerTeamEditor(target.player, target.spectate);
                target.spectate = null;
                target.player.name = target.realName;
                return;
            }

            if(args.length >= 1){
                Team retTeam;
                switch (args[0]) {
                	case "~":
                		retTeam = player.team();
                		break;
                	case "sharded":
                        retTeam = Team.sharded;
                        break;
                    case "blue":
                        retTeam = Team.blue;
                        break;
                    case "crux":
                        retTeam = Team.crux;
                        break;
                    case "derelict":
                        retTeam = Team.derelict;
                        break;
                    case "green":
                        retTeam = Team.green;
                        break;
                    case "purple":
                        retTeam = Team.purple;
                        break;
                    
                    case "vanish":
                    	if (filter != null && filter.reponse == Reponses.found) {
                    		if (Players.errFilterAction("Vanish team", filter, true)) return;
                    		
                			filter.execute(ctx -> {
                				TempData t = TempData.get(ctx.player);
                				t.spectate = t.player.unit().team;
    	                    	t.rainbowed = false;
    	                    	t.hasEffect = false;
    	                    	
    	                        t.player.team(Team.all[8]);
    	                        Call.setPlayerTeamEditor(t.player, Team.all[8]);
    	                        t.player.unit().kill();
    	                        t.player.sendMessage("[green]VANISH MODE[] \nuse /team to go back to player mode.");
    	                        t.player.name = "";
                			});
                    		
                    	} else {
	                    	target.spectate = target.player.unit().team;
	                    	target.rainbowed = false;
	                    	target.hasEffect = false;
	                    	
	                        target.player.team(Team.all[8]);
	                        Call.setPlayerTeamEditor(target.player, Team.all[8]);
	                        target.player.unit().kill();
	                        target.player.sendMessage("[green]VANISH MODE[] \nuse /team to go back to player mode.");
	                        target.player.name = "";
                    	}
                    	return;
                    	
                    default: Players.err(player, "This team don't exist!");
                    case "list":
                    	builder.append("available teams:\n");
                    	builder.append(" - [accent]vanish[]\n");
                        for (Team team : Team.baseTeams) {
                        	builder.append(" - [accent]" + team.name + "[]");
                        	if (!team.cores().isEmpty()) builder.append(" | [green]" + team.cores().size + "[] core(s) found");
                        	builder.append("\n");
                        }
                        player.sendMessage(builder.toString());
                        return;   
                    
                }
                
                if(retTeam.cores().isEmpty()) {
                	Players.warn(player,"This team has no core!");
                	if (filter != null && filter.reponse == Reponses.found) 
                		filter.execute(ctx -> {
                			if (ctx.player != null) {
                				ctx.player.team(retTeam);
        	                	ctx.unit.controlling.each(u -> u.team(retTeam));
        	                	player.sendMessage("> You changed [accent]" + (args.length == 2 ? ctx.player.name + " " : "") + "[white]to team [sky]" + retTeam);
                			
                			} else ctx.unit.team(retTeam);
                		});
                		
                	else {
	                	target.player.team(retTeam);
	                	target.player.unit().controlling.each(u -> u.team(retTeam));
	                	player.sendMessage("> You changed [accent]" + (args.length == 2 ? target.player.name + " " : "") + "[white]to team [sky]" + retTeam);
                	}

                	return;
                }
                
                ret = retTeam;
            } else ret = getPosTeamLoc(target.player);

            //move team mechanic
            if(ret != null) {
            	if (filter != null && filter.reponse == Reponses.found) {
            		Team retF = ret;
            		filter.execute(ctx -> {
            			if (ctx.player != null) {
            				Call.setPlayerTeamEditor(ctx.player, retF);
            				ctx.player.team(retF);
            				ctx.unit.controlling.each(u -> u.team(ctx.player.team()));
        	                player.sendMessage("> You changed [accent]" + (args.length == 2 ? ctx.player.name : "") + "[white] to team [sky]" + retF.name);
            			
            			} else ctx.unit.team(retF);
            		});
            	
            	} else {
            		Call.setPlayerTeamEditor(target.player, ret);
	            	target.player.team(ret);
	                target.player.unit().controlling.each(u -> u.team(target.player.team()));
	                player.sendMessage("> You changed [accent]" + (args.length == 2 ? target.player.name : "") + "[white] to team [sky]" + ret.name);
            	}
            	
            } else Players.err(player, "Other team has no core, can't change!");
        });

        commands.add("am", "<message...>", "Send a message as admin", true, (arg, player) -> 
        	Call.sendMessage(arg[0], "[scarlet]<Admin>[]" + NetClient.colorizeName(player.id, player.name), player)
        );
        
        commands.add("players", "<all|online|ban> [page]", "Display the list of players", true, (arg, player) -> {
        	String message;
        	Seq<String> list = new Seq<>();
        	StringBuilder builder = new StringBuilder();
        	
            switch (arg[0]) {
            	case "ban":
            		if (netServer.admins.getBanned().isEmpty()) {
            			player.sendMessage("[green]No player banned");
            			return;
            		}
            		message = "\nTotal banned players : [green]"+ netServer.admins.getBanned().size + ". \n[gold]-------------------------------- \n[accent]Banned Players:";
            		netServer.admins.getBanned().each(p -> {
            			list.add("\n[white]======================================================================\n" +
            				"[lightgray]" + p.id +"[white] / Name: [lightgray]" + p.lastName.replaceAll("\\[", "[[") + "[white]\n" +
            				" / IP: [lightgray]" + p.lastIP + "[white] / # kick: [lightgray]" + p.timesKicked);
            		});
            		break;
            
            	case "online":
            		message = "\nTotal online players: [green]" + Groups.player.size() + "[].\n[gold]--------------------------------[]\n[accent]List of players:";
            		Groups.player.each(p -> 
            			list.add(" - [lightgray]" + p.name.replaceAll("\\[", "[[") + "[] : [accent]'" + p.uuid() + "'[]" + (p.admin ? "[white] | [scarlet]Admin[]" : "") + "\n[accent]")
            		);
            			
            		break;
            	
            	case "all":
            		message = "\nTotal players: [green]" + netServer.admins.getWhitelisted().size + "[].\n[gold]--------------------------------[]\n[accent]List of players:";
            		netServer.admins.getWhitelisted().each(p -> 
            			list.add("[white] - [lightgray]Names: [accent]" + p.names + "[white] - [lightgray]ID: [accent]'" + p.id + "'" + (p.admin ? "[white] | [scarlet]Admin" : "")
            				+ (p.banned ? "[white] | [orange]Banned" : "") + (Players.findByID(p.id).found ? "[white] | [green]Online" : "") + "\n")
            		);
            		break;
            	
            	default: 
            		Players.err(player, "Invalid arguments.");
            		return;
            }
            
            int lines = 12,
        		page = arg.length == 2 ? Strings.parseInt(arg[1]) : 1,
        		pages = Mathf.ceil(list.size / lines);
        	if (list.size % lines != 0) pages++;
        	
            if(page > pages || page < 1){
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[].");
                return;
            }

            player.sendMessage(message + "[orange] Page [lightgray]" + page + "[gray]/[lightgray]" + pages + "[accent]:");
            for(int i=(page-1)*lines; i<lines*page; i++){
            	try { builder.append(list.get(i)); } 
            	catch (IndexOutOfBoundsException e) { break; }
            }
            
            player.sendMessage(builder.toString());
        });

        commands.add("kill", "[filter|username...]", "Kill a player or a unit", true, (arg, player) -> {
        	if (arg.length == 0) player.unit().kill();
        	else {
        		FilterSearchReponse reponse = ArgsFilter.hasFilter(player, arg);
        		
        		if (reponse.reponse != Reponses.notFound && reponse.sendIfError()) return;
            	else if (reponse.reponse == Reponses.found) {
        			if (reponse.type == FilterType.random) 
        				reponse.execute(ctx -> {
        					ctx.unit.kill();
        					player.sendMessage("[green]Killed [white]" + ctx.player.name);
        				});
        			
        			else if (reponse.type == FilterType.randomUnit) 
        				reponse.execute(ctx -> {
        					ctx.unit.kill();
        					player.sendMessage("[green]Killed a [white]" + ctx.unit.type.name);
        				});
        			
        			else if (reponse.type == FilterType.trigger) player.unit().kill();
        				
        			else {
	        			int counter = reponse.execute(ctx -> ctx.unit.kill());
	        			
	        			player.sendMessage("[green]Killed " + counter + (reponse.type == FilterType.players ? " players" 
	        				: reponse.type == FilterType.units || reponse.type == FilterType.withoutPlayers ? " units"
	        				: reponse.type == FilterType.team || reponse.type == FilterType.withoutPlayersInTeam ? " units in team [accent]" + player.team().name
	        				: " players in team [accent]" + player.team().name)
	        			);
        			}
        		}
        			
        		else {
        			Player other = Players.findByName(arg).player;
    				if (other != null) {
    					other.unit().kill();
    					player.sendMessage("[green]Killed [accent]" + other.name);
    				
    				} else Players.errNotOnline(player);
        			
        		}
        	}
        });

        commands.add("core", "[small|medium|big]", "Build a core at your location", true, (arg, player) -> {
        	if(TempData.get(player).spectate()) {
        		Players.err(player, "You can't build a core in vanish mode!");
        		return;
        	}
        	
        	mindustry.world.Block core;
        	if (arg.length == 1) {
	        	switch (arg[0]) {
	        		case "small": 
	        			core = Blocks.coreShard;
	        			break;
	        		case "medium": 
	        			core = Blocks.coreFoundation;
	        			break;
	        		case "big": 
	        			core = Blocks.coreNucleus;
	        			break;
	        		default: 
	        			Players.err(player, "no core with name '@'", arg[0]);
	        			return;
	        	}
        	
        	} else core = Blocks.coreShard;
        	
        	
        	Call.constructFinish(player.tileOn(), core, player.unit(), (byte)0, player.team(), false);
        	player.sendMessage(player.tileOn().block() == core ? "[green]Core build." : "[scarlet]Error: Core not build.");
        });
        
        commands.add("tp", "<filter|name|x,y> [~|to_name|x,y...]", "Teleport to a location or player", true, (arg, player) -> {
        	int[] co = {player.tileX(), player.tileY()};
            Player target = player;
            Search result = null;
            FilterSearchReponse filter = ArgsFilter.hasFilter(player, arg);
            Seq<String> newArg = new Seq<String>().addAll(arg);
            
            if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
        	else if (filter.reponse == Reponses.found) newArg.remove(0);
        	else {
        		result = new Search(arg, player);
        		newArg = Seq.with(result.rest);
        		
	    		if (result.error) return;
	            else {
	            	if (result.XY == null) co = new int[]{result.player.tileX(), result.player.tileY()};
	            	else co = result.XY;
	            }	
    		}

            if (newArg.isEmpty() && filter.reponse == Reponses.found) {
            	Players.err(player, "2 arguments are required to use filters");
            	return;
            	
            } else if (!newArg.isEmpty()) {
            	String rest = String.join(" ", newArg);
            	
            	
            	if (rest.equals("~")) {
            		if (result != null && result.XY != null) {
	            		player.sendMessage("[scarlet]Can't teleport a coordinate to a coordinate or to a player! [lightgray]It's not logic XD.");
	            		return;	
            		}
            		
            	} else {
	            	arg[0] = "";
	            	arg[1] = rest;
	            	
	            	if (filter.reponse == Reponses.found) {
	            		result = new Search(arg, player);
						
						if (result.error) return;
			            else {
			            	if (result.XY == null) co = new int[]{result.player.tileX(), result.player.tileY()};
			            	else co = result.XY;
			            }
	            		
	            	} else {
		            	target = result.player;
		            	
		            	if (result.XY == null) {
							result = new Search(arg, player);
							
							if (result.error) return;
				            else {
				            	if (result.XY == null) co = new int[]{result.player.tileX(), result.player.tileY()};
				            	else co = result.XY;
				            }
							
						} else {
							player.sendMessage("[scarlet]Can't teleport a coordinate to a coordinate or to a player! [lightgray]It's not logic XD.");
		            		return;
						}	
	            	}
            	}
            }
            
            if (co[0] > world.width() || co[0] < 0 || co[1] > world.height() || co[1] < 0) {
                player.sendMessage("[scarlet]Coordinates too large. Max: [orange]" + world.width() + "[]x[orange]" + world.height() + "[]. Min: [orange]0[]x[orange]0[].");
                return;
            }

            if (filter.reponse == Reponses.found) {
            	int x = co[0]*8, y = co[1]*8;
            	filter.execute(ctx -> {
            		Players.tpPlayer(ctx.unit, x, y);
            		if (ctx.player != null) player.sendMessage("[green]You teleported [accent]" + ctx.player.name + "[green] to [accent]" + x/8 + "[green]x[accent]" + y/8 + "[green].");
            	});
            
            }  else {
	            Players.tpPlayer(target.unit(), co[0]*8, co[1]*8);
	            if (arg.length == 2) player.sendMessage("[green]You teleported [accent]" + target.name + "[green] to [accent]" + co[0] + "[green]x[accent]" + co[1] + "[green].");
	            else player.sendMessage("[green]You teleported to [accent]" + co[0] + "[]x[accent]" + co[1] + "[].");
            }
            
        });  
        
        commands.add("spawn", "<unit> [count] [filter|x,y|username] [teamname|~...]", "Spawn a unit", true, (arg, player) -> {
        	StringBuilder builder = new StringBuilder();
        	mindustry.type.UnitType unit = content.units().find(b -> b.name.equals(arg[0]));
        	Player target = player;
        	Team team = target.team();
        	int count = 1, x = (int) target.x, y = (int) target.y;
        	Seq<String> newArg = new Seq<String>().addAll(arg);
        	newArg.remove(0);
        	FilterSearchReponse filter = null;

        	if (unit == null) {
        		player.sendMessage("[scarlet]Available units: []" + content.units().toString("[scarlet], []"));
        		return;
        	}
        	
        	if (arg.length > 1) {
    			if (!Strings.canParseInt(newArg.get(0))) {
    				Players.err(player, "'count' must be number!");
    				return;
    			} else count = Strings.parseInt(newArg.get(0));
    			newArg.remove(0);
        		
        		if (!newArg.isEmpty()) {
        			filter = ArgsFilter.hasFilter(target, newArg.toArray(String.class));
        			
	        		if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
	            	else if (filter.reponse == Reponses.found) newArg.remove(0);
	        		else {
	        			Search result = new Search(newArg.toArray(String.class), player);
	        			newArg.set(new Seq<String>().addAll(result.rest));
	        			
	        			if (result.error) return;
	                    else target = result.player;
	
	        			if (result.XY == null) {
	        				x = (int) target.x;
	        				y = (int) target.y;
	        			} else {
	        				x = result.XY[0]*8;
	        				y = result.XY[1]*8;
	        			}
	        		}	
        		}
        		
        		if (!newArg.isEmpty()) {
        			switch (newArg.get(0)) {
            			case "~": 
            				break;
            			case "sharded": 
            				team = Team.sharded;
            				break;
            			case "blue": 
            				team = Team.blue;
            				break;
            			case "crux": 
            				team = Team.crux;
            				break;
            			case "derelict": 
	            			team = Team.derelict;
	            			break;
            			case "green": 
            				team = Team.green;
            				break;
            			case "purple": 
            				team = Team.purple;
            				break;
            			default: 
            				Players.err(player, "Team not found! []\navailable teams: ");
            				for (Team teamList : Team.baseTeams) builder.append(" - [accent]" + teamList.name + "[]\n");
            				player.sendMessage(builder.toString());
            				return;	
        			}
        			newArg.remove(0);
        		} else team = target.team();
        		
        		if (!newArg.isEmpty()) {
        			Players.err(player, "Too many arguments!");
        			return;
        		}
        	}

        	if (team.cores().isEmpty()) Players.err(player, "The [accent]" + team.name + "[] team has no core! Units cannot spawn");
        	else {
        		if (filter != null && filter.reponse == Reponses.found) {
        			Team teamF = team;
        			int countF = count;
        			
        			filter.execute(ctx -> {
        				int counter = 0;
                		for (int i=0; i<countF; i++) {
                			if (unit.spawn(teamF, ctx.unit.x, ctx.unit.y).isValid()) counter++;
                		}
                    
                		player.sendMessage("[green]You are spawning [accent]" + counter + " " + unit 
                			+ " []for [accent]" + teamF + " []team at [orange]" + (int) ctx.unit.x/8 + "[white],[orange]" + (int) ctx.unit.y/8);
        			});
        		
        		} else {
        			int counter = 0;
            		for (int i=0; i<count; i++) {
            			if (unit.spawn(team, x, y).isValid()) counter++;
            		}
                
            		player.sendMessage("[green]You are spawning [accent]" + counter + " " + unit + " []for [accent]" + team + " []team at [orange]" + x/8 + "[white],[orange]" + y/8);
        		}
        	}
        });
        
        commands.add("godmode", "[filter|username...]", "[scarlet][God][]: [gold]I'm divine!", true, (arg, player) -> {
        	TempData target = TempData.get(player);
        	
        	if (arg.length == 1) {
        		FilterSearchReponse filter = ArgsFilter.hasFilter(player, arg);
        		
        		if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
            	else if (filter.reponse == Reponses.found) {
            		if (Players.errFilterAction("God mode", filter, true)) return;
            		
            		filter.execute(ctx -> {
            			TempData t = TempData.get(ctx.player);
            			
            			t.inGodmode = !t.inGodmode;
            			ctx.unit.health = t.inGodmode ? Integer.MAX_VALUE : ctx.unit.maxHealth;
            			
            			player.sendMessage("[gold]God mode is [green]" + (t.inGodmode ? "enabled" : "disabled") + "[] for [accent]" + ctx.player.name);
                		ctx.player.sendMessage((t.inGodmode ? "[green]You've been put into god mode" : "[red]You have been removed from god mode") + " by [accent]"+ player.name);
            		});
            		return;
            	
            	} else {
	        		target = Players.findByName(arg).data;
	        		
	        		if (target == null) {
	        			Players.errNotOnline(player);
	        			return;
	        		}
        		}
        	}
        	
        	target.inGodmode = !target.inGodmode;
			target.player.unit().health = target.inGodmode ? Integer.MAX_VALUE : target.player.unit().maxHealth;
			
			player.sendMessage("[gold]God mode is [green]" + (target.inGodmode ? "enabled" : "disabled") + (arg.length == 0 ? "" : "[] for [accent]" + target.player.name));
			if (arg.length == 1) 
				target.player.sendMessage((target.inGodmode ? "[green]You've been put into god mode" : "[red]You have been removed from god mode") + " by [accent]"+ player.name);
        });
        
        commands.add("chat", "[on|off]", "Enabled/disabled the chat", true, (arg, player) -> {
        	if (arg.length == 1) {
        		if (Strings.choiseOn(arg[0])) {
        			if (PVars.tchat) {
        				Players.err(player, "Disabled first!");
        				return;
        			}
        			PVars.tchat = true;
        		
        		} else if (Strings.choiseOff(arg[0])) {
        			if (!PVars.tchat) {
        				Players.err(player, "Enabled first!");
        				return;
        			}
        			PVars.tchat = false;
        		
        		} else {
        			Players.err(player, "Invalid arguments.[] \n - The chat is currently [accent]@[].", PVars.tchat ? "enabled" : "disabled");
        			return;
        		}
        		
        		saveAllSettings();
    			Log.info("Chat @ by @.", PVars.tchat ? "enabled" : "disabled", player.name);
    			Call.sendMessage("\n[gold]-------------------- \n[scarlet]/!\\[orange] Chat " + (PVars.tchat ? "enabled" : "disabled") 
    				+ " by " + player.name + "[orange]! \n[gold]--------------------\n");
        	
        	} else player.sendMessage("The chat is currently "+ (PVars.tchat ? "enabled." : "disabled."));
        });   
        
        commands.add("reset", "<filter|username|ID...>", "Resets a player's data (rainbow, GodMode, muted, ...)", true, (arg, player) -> {
        	FilterSearchReponse filter = ArgsFilter.hasFilter(player, arg);
        	
        	if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
        	else if (filter.reponse == Reponses.found) {
        		if (Players.errFilterAction("reset", filter, false)) return;
        		
        		filter.execute(ctx -> {
        			TempData.get(ctx.player).reset();
	            	player.sendMessage("[green]Success to reset data of player " + ctx.player.name);
        		});
        		
        	} else {
        		Players result = Players.findByNameOrID(arg);
	            
	            if (result.found) {
	            	TempData.get(result.player).reset();
	            	player.sendMessage("[green]Success to reset data of player " + result.player.name);
	            
	            } else Players.errNotOnline(player);
        	}
        });
        
        commands.add("mute", "<filter|username|ID> [reason...]", "Mute a person by name or ID", true, (arg, player) -> {
        	FilterSearchReponse filter = ArgsFilter.hasFilter(player, arg);
        	
        	if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
        	else if (filter.reponse == Reponses.found) {
        		if (Players.errFilterAction("mute", filter, false)) return;
    			
        		filter.execute(ctx -> {
    				TempData t = TempData.get(ctx.player);
    				
    				if (!t.isMuted) {
        				t.isMuted = true;
    	            	Call.sendMessage("[scarlet]/!\\" + NetClient.colorizeName(t.player.id, t.player.name) + "[scarlet] has been muted of the server.");
    	            	Call.infoMessage(t.player.con, "You have been muted! [lightgray](by " + t.player.name + "[lightgray]) \n[scarlet]Reason: []" 
    	            		+ (arg.length == 2 && !arg[1].isBlank() ?  arg[1] : "<unknown>"));	
    				
    				} else Players.err(player, "[white]" + t.player.name + "[scarlet] is already muted!");
    			});
        		
        	} else {
	        	Players result = Players.findByNameOrID(arg);
	            
	            if (result.found) {
	            	if (!result.data.isMuted) {
		            	String message = String.join(" ", result.rest);
		            	
		            	result.data.isMuted = true;
		            	Call.sendMessage("[scarlet]/!\\" + NetClient.colorizeName(result.player.id, result.player.name) + "[scarlet] has been muted of the server.");
		            	Call.infoMessage(result.player.con, "You have been muted! [lightgray](by " + player.name + "[lightgray]) \n[scarlet]Reason: []" 
		            		+ (message.isBlank() ?  "<unknown>" : message));
	            	
	            	} else Players.err(player, "[white]" + result.player.name + "[scarlet] is already muted!");
	            } else Players.err(player, "Nobody with that name or ID could be found...");
        	}
        });
        
        commands.add("unmute", "<filter|username|ID...>", "Unmute a person by name or ID", true, (arg, player) -> {
        	FilterSearchReponse filter = ArgsFilter.hasFilter(player, arg);
        	
        	if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
        	else if (filter.reponse == Reponses.found) {
        		if (Players.errFilterAction("unmute", filter, false)) return;
        		
        		filter.execute(ctx -> {
    				TempData t = TempData.get(ctx.player);
    				
    				if (t.isMuted) {
    					t.isMuted = false;
	            		Call.infoMessage(t.player.con, "You have been unmuted! [lightgray](by " + player.name + "[lightgray])");
    					
    				} else Players.err(player, "[white]" + t.player.name + "[scarlet] isn't muted!");
        		});
        		
        	} else {
	        	Players target = Players.findByNameOrID(arg);
	            
	            if (target.found) {
	            	if (target.data.isMuted) {
	            		target.data.isMuted = false;
	            		Call.infoMessage(target.player.con, "You have been unmuted! [lightgray](by " + player.name + "[lightgray])");
	            	
	            	} else Players.err(player, "[white]" + target.player.name + "[scarlet] isn't muted!");
	            } else Players.err(player, "Nobody with that name or ID could be found...");
        	}
        });
        
        commands.add("kick", "<filter|username|ID> [reason...]", "Kick a person by name or ID", true, (arg, player) -> {
            FilterSearchReponse filter = ArgsFilter.hasFilter(player, arg);
        	
        	if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
        	else if (filter.reponse == Reponses.found) {
        		if (Players.errFilterAction("kick", filter, false)) return;
        		
        		filter.execute(ctx -> {
    				Call.sendMessage("[scarlet]/!\\" + NetClient.colorizeName(ctx.player.id, ctx.player.name) + "[scarlet] has been kicked of the server.");
                    if (arg.length == 2) ctx.player.kick("You have been kicked from the server!\n[scarlet]Reason: []" + (arg[1].isBlank() ?  "<unknown>" : arg[1]));
                    else ctx.player.kick(KickReason.kick);
        		});
        	
        	} else {
	        	Players result = Players.findByNameOrID(arg);
	            
	            if (result.found) {
	            	String message = String.join(" ", result.rest);
	                
	            	Call.sendMessage("[scarlet]/!\\" + NetClient.colorizeName(result.player.id, result.player.name) + "[scarlet] has been kicked of the server.");
	                if (arg.length == 2) result.player.kick("You have been kicked from the server!\n[scarlet]Reason: []" + (message.isBlank() ?  "<unknown>" : message));
	                else result.player.kick(KickReason.kick);
	            
	            } else Players.err(player, "Nobody with that name or ID could be found...");
        	}
        });   
        
        commands.add("pardon", "<ID>", "Pardon a player by ID and allow them to join again", true, (arg, player) -> {
        	PlayerInfo info = netServer.admins.getInfoOptional(arg[0]);
        	
        	if (info != null) {
        		info.lastKicked = 0;
        		Players.info(player, "Pardoned player: [accent]%s", info.lastName);
        	
        	} else Players.err(player, "That ID can't be found.");
        });

        commands.add("ban", "<filter|username|ID> [reason...]", "Ban a person", true, (arg, player) -> {
        	FilterSearchReponse filter = ArgsFilter.hasFilter(player, arg);
        	
        	if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
        	else if (filter.reponse == Reponses.found) {
        		if (Players.errFilterAction("ban", filter, false)) return;
        		
        		filter.execute(ctx -> {
        			if (!ctx.player.admin) {
	        			netServer.admins.banPlayer(ctx.player.uuid());
		        		Call.sendMessage("[scarlet]/!\\ " + NetClient.colorizeName(ctx.player.id, ctx.player.name) + "[scarlet] has been banned of the server.");
		        		if (arg.length == 2) ctx.player.kick("You are banned on this server!!\n[scarlet]Reason: []" + (arg[1].isBlank() ?  "<unknown>" : arg[1]));
		                else ctx.player.kick(KickReason.banned);
		        		
        			} else Players.err(player, "Can't ban an admin!");
        		});
        		
        	} else {
	        	Players result = Players.findByNameOrID(arg);
	        	
	        	if (result.found) {
	        		if (!result.player.admin) {
		        		String message = String.join(" ", result.rest);
		        		
		        		netServer.admins.banPlayer(result.player.uuid());
		        		Call.sendMessage("[scarlet]/!\\ " + NetClient.colorizeName(result.player.id, result.player.name) + "[scarlet] has been banned of the server.");
		        		if (arg.length == 2) result.player.kick("You are banned on this server!!\n[scarlet]Reason: []" + (message.isBlank() ?  "<unknown>" : message));
		                else result.player.kick(KickReason.banned);
	        		
	        		} else Players.err(player, "Can't ban an admin!");
	        	} else Players.err(player, "No matches found.");
        	}
        });
        
        commands.add("unban", "<ID>", "Unban a person", true, (arg, player) -> {
            if (netServer.admins.unbanPlayerID(arg[0])) Players.info(player, "Unbanned player: [accent]%s", arg[0]);
            else Players.err(player, "That IP/ID is not banned!");
        });
        
    }
    
	private void loadAll() {
    	Effects.init();
		BM.load();
		AntiVpn.init(true);
		ArgsFilter.load();
		Switcher.load();
		
		if (Core.settings.has("AutoPause")) PVars.autoPause = Core.settings.getBool("AutoPause");
        else saveAllSettings();
    }
    
    private void saveAllSettings() {
    	Core.settings.put("AutoPause", PVars.autoPause);
    	BM.saveSettings();
    	AntiVpn.saveSettings();
    	ArgsFilter.saveSettings();
    	Effects.saveSettings();
    	Switcher.saveSettings();
    }

    private Team getPosTeamLoc(Player p){
        Team newTeam = p.team();
        
        //search a possible team
        int c_index = java.util.Arrays.asList(Team.baseTeams).indexOf(newTeam);
        int i = (c_index+1)%6;
        while (i != c_index){
            if (Team.baseTeams[i].cores().size > 0){
            	newTeam = Team.baseTeams[i];
            	break;
            }
            i = (i + 1) % Team.baseTeams.length;
        }
        
        if (newTeam == p.team()) return null;
        else return newTeam;
    }
    
}