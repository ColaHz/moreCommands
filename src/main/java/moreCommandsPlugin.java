import static mindustry.Vars.maps;
import static mindustry.Vars.netServer;
import static mindustry.Vars.state;
import static mindustry.Vars.world;

import java.util.Arrays;
import java.util.HashSet;

import arc.Events;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.core.NetClient;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.NetConnection;
import mindustry.net.Packets.KickReason;
import mindustry.world.Tile;

public class moreCommandsPlugin extends Plugin {
	Timer.Task task;
	private double ratio = 0.6;
    private HashSet<String> votesVNW = new HashSet<>();
    private HashSet<String> votesRTV = new HashSet<>();
    private ObjectMap<Player, Team> rememberSpectate = new ObjectMap<>();
    private boolean confirm = false;
    private boolean autoPause = false;

    public moreCommandsPlugin() {
    	//clear RTV votes on game over
        Events.on(GameOverEvent.class, e -> {
            this.votesRTV.clear();
        });
        
        //clear VNW votes on game over
        Events.on(GameOverEvent.class, e -> {
            this.votesVNW.clear();
        });
        
        // auto pause the game if autoPause = true
        Events.on(PlayerJoin.class, e -> {
        	if (Groups.player.size() >= 1 && autoPause) {
        		state.serverPaused = false;
        		Log.info("auto-pause: " + Groups.player.size() + " player connected -> Game unpaused ...");
        	}
        });
        Events.on(PlayerLeave.class, e -> {
        	if (Groups.player.size()-1 < 1 && autoPause) {
        		state.serverPaused = true;
        		Log.info("auto-pause: " + Groups.player.size() + " player connected -> Game paused ...");
        	}
        });
    }
    
    
    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){
    	handler.register("unban-all", "[y|n]", "Unban all IP and ID", arg -> {
    		if (arg.length == 1 && confirm == false) {
    			Log.err("Use first: 'unban-all', before confirming the command.");
    			return;
    		} else if (confirm == false) {
    			Log.info("Are you sure to unban all all IP and ID ? (unban-all [y|n])");
    			confirm = true;
    			return;
    		} else if (arg.length == 0 && confirm == true) {
    			Log.info("Are you sure to unban all all IP and ID ? (unban-all [y|n])");
    			confirm = true;
    			return;
    		}

    		switch (arg[0]) {
    			case "y":
    				Administration pBanned = netServer.admins;
    				pBanned.getBanned().each(unban -> pBanned.unbanPlayerID(unban.id));
    				pBanned.getBannedIPs().each(ip -> pBanned.unbanPlayerIP(ip));
    				Log.info("All all IP and ID have been unbanned!");
    				confirm = false;
    				break;
    			case "n":
    				Log.info("You canceled the confirmation...");
    				confirm = false;
    				break;
    			default: 
    				Log.err("Invalid arguments!");
    				confirm = false;
    		}
        });
    	
    	handler.register("auto-pause", "[on|off]", "Pause the game if there is no one connected", arg -> {
    		if (arg.length == 0) {
    			Log.err("Invalid arguments! Please use the argument [on|off] to enabled/disabled the auto pause if there is no one connected.");
    			return;
    		}
    		
    		switch (arg[0]) {
    			case "on":
    				autoPause = true;
    				Log.info("Auto pause is enabled.");
    				
    				if (Groups.player.size() < 1 && autoPause) {
    					state.serverPaused = true;
    					Log.info("auto-pause: " + Groups.player.size() + " player connected -> Game paused ...");
    				}
    				break;
    			case "off":
    				autoPause = false;
    				Log.info("Auto pause is disabled.");
    				
    	        	state.serverPaused = false;
    	        	Log.info("Game unpaused ...");
    				break;
    			default: 
    				Log.err("Invalid arguments! Please use the argument [on|off] to enabled/disabled the auto pause if there is no one connected.");
    		}
    		
    		
    	});
    }
    
    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("ut","unit type", (args, player) ->{
           player.sendMessage("You're a [sky]" + player.unit().type().name + "[].");
        });
        
        handler.<Player>register("vnw", "(VoteNewWave) Vote for Sending a new Wave", (args, player) -> {
        	if (this.votesVNW.contains(player.uuid()) || this.votesVNW.contains(netServer.admins.getInfo(player.uuid()).lastIP)) {
                player.sendMessage("You have Voted already.");
                return;
        	}

            this.votesVNW.add(player.uuid());
            int cur = this.votesVNW.size();
            int req = (int) Math.ceil(ratio * Groups.player.size());
            Call.sendMessage("[scarlet]VNW: [orange]" + NetClient.colorizeName(player.id, player.name) + "[white] has voted for a new wave, [green]" + cur + "[white] votes, [green]" + req + "[white] required.");
 
            if (cur < req) return;

            this.votesVNW.clear();
            Call.sendMessage("[scarlet]VNW: [green]Vote passed. New Wave will be Spawned!");
            state.wavetime = 0f;
            task.cancel();
		});

        handler.<Player>register("maps", "[page]", "List all maps on server", (arg, player) -> {
            int page;
			if (!(arg.length == 0)) page = Strings.parseInt(arg[0]);
            else page = 1;

			int lines = 6;
            int index;
            Seq<Map> list = maps.all();
            int pages = Mathf.ceil(list.size / lines);
            if (list.size % lines != 0) pages++;
            index=(page-1)*lines;
            
            if (page > pages || page < 1) {
            	player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and [orange]" + pages + "[].");
            	return;
            }
            
            player.sendMessage("\n[orange]---- [][gold]Maps list [lightgray]" + page + "[white]/[lightgray]" + pages + "[orange] ----");
            for (int i=0; i<lines;i++) {
            	try {
            		player.sendMessage("[orange]  - []" + list.get(index).name() + "[][orange] | []" + list.get(index).width + "x" + list.get(index).height);
            		index++;
            	} catch (IndexOutOfBoundsException e) {
            		break;
            	}
            }
            player.sendMessage("[orange]-----------------------");
            
        });
        
        handler.<Player>register("rtv", "Rock the vote to change map", (arg, player) -> {
        	if (this.votesRTV.contains(player.uuid()) || this.votesRTV.contains(netServer.admins.getInfo(player.uuid()).lastIP)) {
                player.sendMessage("You have Voted already.");
                return;
        	}
        	
        	this.votesRTV.add(player.uuid());
            int cur2 = this.votesRTV.size();
            int req2 =  (int) Math.ceil(ratio * Groups.player.size());
            Call.sendMessage("[scarlet]RTV: [accent]" + NetClient.colorizeName(player.id, player.name) + " [white]wants to change the map, [green]" + cur2 + "[white] votes, [green]" + req2 + "[white] required.");
            
            if (cur2 < req2) return;
            
            this.votesRTV.clear();
            Call.sendMessage("[scarlet]RTV: [green]Vote passed, changing map...");
            Events.fire(new GameOverEvent(Team.crux));
        });

        handler.<Player>register("info-all", "[username]", "Get all player information", (arg, player) -> {
        	StringBuilder builder = new StringBuilder();
        	ObjectSet<PlayerInfo> infos;
        	PlayerInfo pI = netServer.admins.findByIP(player.ip());
        	boolean type = false;
        	
			if(arg.length == 1) {
        		if (player.admin()) {
            		infos = netServer.admins.findByName(arg[0]);
            		type = true;
            	} else { 
            		player.sendMessage("[scarlet]You don't have the permission to use arguments!");
            		return;
            	}
        	} else infos = netServer.admins.findByName(player.name);

			
            if (infos.size > 0) {
            	int i = 1;
            	
            	builder.append("[gold]------------------------------------------");
            	if (type == true) builder.append("\n[scarlet]-----"+ "\n[white]Players found: [gold]" + infos.size + "\n[scarlet]-----");
            	player.sendMessage(builder.toString());
            	builder = new StringBuilder();
            	
                for (PlayerInfo info : infos) {
                	if (type == false) {
                		if (i > 1) break;
                		player.sendMessage("Player name [accent]'" + infos.get(pI).lastName + "[accent]'[white] / ID [accent]'" + infos.get(pI).id + "' ");
                	}
                	else {
                		pI = info;
                		player.sendMessage("[gold][" + i++ + "] [white]Trace info for admin [accent]'" + infos.get(pI).lastName + "[accent]'[white] / ID [accent]'" + infos.get(pI).id + "' ");
                	}
                	builder.append("[] - All names used: [accent]" + infos.get(pI).names +
                			"\n[] - IP: [accent]" + infos.get(pI).lastIP +
                			"\n[] - All IPs used: [accent]" + infos.get(pI).ips +
                			"\n[] - Times joined: [green]" + infos.get(pI).timesJoined);
                	if (player.admin()) builder.append("\n[] - Times kicked: [scarlet]" + infos.get(pI).timesKicked + 
                								"\n[] - Is baned: [accent]" + infos.get(pI).banned +
                								"\n[] - Is admin: [accent]" + infos.get(pI).admin);
                	builder.append("\n[][gold]------------------------------------------");
                	
                	player.sendMessage(builder.toString());
                	builder = new StringBuilder();
                }
           } else player.sendMessage("[accent]This player doesn't exist!");
        });
      
        handler.<Player>register("team", "[teamname]","change team", (args, player) ->{
            if(!player.admin()){
                player.sendMessage("[scarlet]Only admins can change team !");
                return;
            }
            
            if(rememberSpectate.containsKey(player)){
                player.sendMessage(">[orange] transferring back to last team");
                player.team(rememberSpectate.get(player));
                Call.setPlayerTeamEditor(player, rememberSpectate.get(player));
                rememberSpectate.remove(player);
                return;
            }
            coreTeamReturn ret = null;
            if(args.length == 1){
                Team retTeam;
                switch (args[0]) {
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
                    default:
                        player.sendMessage("[scarlet]ABORT: Team not found[] - available teams:");
                        for (int i = 0; i < 6; i++) {
                            if (!Team.baseTeams[i].cores().isEmpty()) {
                                player.sendMessage(Team.baseTeams[i].name);
                            }
                        }
                        return;
                }
                if(retTeam.cores().isEmpty()){
                    player.sendMessage("This team has no core, can't change!");
                    return;
                }else{
                    Tile coreTile = retTeam.core().tileOn();
                    ret =  new coreTeamReturn(retTeam, coreTile.drawx(), coreTile.drawy());
                }
            }else ret = getPosTeamLoc(player);

            //move team mechanic
            if(ret != null) {
                Call.setPlayerTeamEditor(player, ret.team);
                player.team(ret.team);
                player.sendMessage("> You changed to team [sky]" + ret.team);
            }else player.sendMessage("[scarlet]You can't change teams ...");
        });

        handler.<Player>register("spectate", "[scarlet]Admin only[]", (args, player) -> {
        	if (!adminVerif(player)) return;
        	
        	Team spectateTeam = Team.all[8];
            
            if(rememberSpectate.containsKey(player)){
                player.team(rememberSpectate.get(player));
                Call.setPlayerTeamEditor(player, rememberSpectate.get(player));
                rememberSpectate.remove(player);
                player.sendMessage("[gold]PLAYER MODE[]");
            }else{
                rememberSpectate.put(player, player.unit().team);
                player.team(spectateTeam);
                Call.setPlayerTeamEditor(player, spectateTeam);
                player.unit().kill();
                player.sendMessage("[green]SPECTATE MODE[]");
                player.sendMessage("use /team or /spectate to go back to player mode");
            }
        });
        
        handler.<Player>register("am", "<message...>", "Send a message as admin", (arg, player) -> {
        	if (!adminVerif(player)) return;
        	Groups.player.each(p -> p.sendMessage(arg[0], player, "[scarlet]<Admin>" + NetClient.colorizeName(player.id, player.name)));
        });
        
        handler.<Player>register("players", "<all|online|ban>", "Gives the list of players according to the type of filter given", (arg, player) -> {
        	if (!adminVerif(player)) return;
        	
        	StringBuilder builder = new StringBuilder();
        	Seq<PlayerInfo> bannedPlayers = netServer.admins.getBanned();
        	
            switch (arg[0]) {
            	case "ban":
            		builder.append("\nTotal banned players : [green]").append(netServer.admins.getBanned().size).append("[].\n[gold]--------------------------------[]").append("\n[accent]Banned Players:");
            		player.sendMessage(builder.toString());
            		bannedPlayers.each(p -> {
            			player.sendMessage("[white]======================================================================\n" +
            					"[lightgray]" + p.id +"[white] / Name: [lightgray]" + p.lastName + "[white]\n" +
            					" / IP: [lightgray]" + p.lastIP + "[white] / # kick: [lightgray]" + p.timesKicked);
            		});
            		break;
            
            	case "online":
            		builder.append("\nTotal online players: [green]").append(Groups.player.size()).append("[].\n[gold]--------------------------------[]").append("\n[accent]List of players: \n");
            		for (Player p : Groups.player) {
            			if (!p.admin) {
            				p.name = p.name.replaceAll("\\[", "[[");
            				builder.append("[white]");
            			}
            			if (p.admin) builder.append("[white]\uE828 ");
            			builder.append(" - [lightgray]").append(p.name).append("[]: [accent]'").append(p.uuid()).append("'[]");
            			if (p.admin) builder.append("[white] | [scarlet]Admin[]");
            			builder.append("\n[accent]");
            		}
            		player.sendMessage(builder.toString());
            		break;
            	
            	case "all":
            		Seq<PlayerInfo> all = netServer.admins.getWhitelisted();
            		builder.append("\nTotal players: [green]").append(all.size).append("[].\n[gold]--------------------------------[]").append("\n[accent]List of players: []\n");
            		for (PlayerInfo p : all) {
            			builder.append("[white] - [lightgray]Names: [accent]").append(p.names).append("[white] - [lightgray]ID: [accent]'").append(p.id).append("'");
            			if (p.admin) builder.append("[white] | [scarlet]Admin");
            			if (p.banned) builder.append("[white] | [orange]Banned");
            			for (Player pID: Groups.player) {
            				if (p.id.equals(pID.uuid())) {
            					builder.append("[white] | [green]Online");
            					break;
            				}
            			}
            			builder.append("\n");
            		}
            		player.sendMessage(builder.toString());
            		break;
            	
            	default:
            		player.sendMessage("[scarlet]Invalid usage:[lightgray] Invalid arguments.");
            }
            
        });

        handler.<Player>register("kill", "[username]", "Kill a player", (arg, player) -> {
        	if (!adminVerif(player)) return;
            
        	if (arg.length == 0) player.unit().kill();
            else {
                Player other = Groups.player.find(p -> p.name().equalsIgnoreCase(arg[0]));
                if (other != null) other.unit().kill();
                else player.sendMessage("[scarlet]This player doesn't exist or not connected!");
            }
        });
        
        handler.<Player>register("tp", "<name|x,y> [to_name|x,y]", "Teleport to position or player", (arg, player) -> {
        	if (!adminVerif(player)) return;
            
        	int x = 0, y = 0, to_x = 0, to_y = 0;
        	boolean foundName = false, foundToName = false;
        	String[] __temp__;
            ObjectSet<PlayerInfo> name = netServer.admins.findByName(arg[0]);
            ObjectSet<PlayerInfo> toName = netServer.admins.findByName("");
            NetConnection playerCon = player.con;
            
            if (arg.length == 2) toName = netServer.admins.findByName(arg[1]);

            if (name.size > 0 || toName.size > 0) {
            	for (Player pPos : Groups.player) {
            		for (PlayerInfo p : name) {
            			if (p.id.equals(pPos.uuid())) {
            				playerCon = pPos.con;
            				x = Math.round(pPos.x/8);
            				y = Math.round(pPos.y/8);
            				foundName = true;
            				break;
            			}
            		}
            		for (PlayerInfo to_p : toName) {
            			if (to_p.id.equals(pPos.uuid())) {
            				to_x = Math.round(pPos.x/8);
            				to_y = Math.round(pPos.y/8);
            				foundToName = true;
            				break;
            			}
            		}
            	}
            	if (arg.length == 2 && toName.size == 0) {
            		try {
            			__temp__ = arg[1].split(",");
            			to_x = Integer.parseInt(__temp__[0]);
            			to_y = Integer.parseInt(__temp__[1]);
            		
            			if (__temp__.length > 2) {
            				player.sendMessage("[scarlet]Wrong coordinates!");
            				return;
            			}
            			foundToName = true;
            		} catch (NumberFormatException e) {
            			player.sendMessage("[scarlet]Player doesn't exist or wrong coordinates!");
            			return;
            		} catch (ArrayIndexOutOfBoundsException e) {
            			player.sendMessage("[scarlet]Wrong coordinates!");
            			return;
            		}
            	}
            } else {
            	try {
            		__temp__ = arg[0].split(",");
            		x = Integer.parseInt(__temp__[0]);
            		y = Integer.parseInt(__temp__[1]);
            		
            		if (__temp__.length > 2) {
            			player.sendMessage("[scarlet]Wrong coordinates!");
                		return;
            		}
            		foundName = true;
            	} catch (NumberFormatException e) {
            		player.sendMessage("[scarlet]Player doesn't exist or wrong coordinates!");
            		return;
            	} catch (ArrayIndexOutOfBoundsException e) {
            		player.sendMessage("[scarlet]Wrong coordinates!");
            		return;
            	}
            }
            
            if (foundName == false && name.size != 0) {
            		player.sendMessage("[scarlet]Player [orange]" + arg[0] + "[] not connected!");
    				return;
            } else if (foundToName == false && arg.length == 2 && toName.size != 0) {
            	player.sendMessage("[scarlet]Player [orange]" + arg[1] + "[] not connected!");
				return;
            }
            
            if (arg.length == 2) {
            	if (to_x > world.width() || to_x < 0 || to_y > world.height() || to_y < 0) {
               	 	player.sendMessage("[scarlet]Coordinates too large. Max: [orange]" + world.width() + "[]x[orange]" + world.height() + "[]. Min: [orange]0[]x[orange]0[].");
                    return;
                }
            	if (!((name.size != 0 && toName.size != 0) || (name.size != 0 && toName.size == 0))) {
            		player.sendMessage("[scarlet]Cannot teleport Coordinates to a Coordinates or Coordinates to a player! [lightgray]It's not logic XD.");
            		return;
            	}
            	
            	playerCon.player.unit().set(to_x*8, to_y*8);
            	Call.setPosition(playerCon, to_x*8, to_y*8);
            	playerCon.player.snapSync();
            	player.sendMessage("You teleported [accent]" + playerCon.player.name + "[] to [accent]" + to_x + "[]x[accent]" + to_y + "[].");
            
            } else {
            	if (x > world.width() || x < 0 || y > world.height() || y < 0) {
            		player.sendMessage("[scarlet]Coordinates too large. Max: [orange]" + world.width() + "[]x[orange]" + world.height() + "[]. Min : [orange]0[]x[orange]0[].");
            		return;
            	}
            	
            	player.unit().set(x*8, y*8);
            	Call.setPosition(player.con, x*8, y*8);
            	player.snapSync();
            	player.sendMessage("You teleported to [accent]" + x + "[]x[accent]" + y + "[].");
            }		
        });   
        
        handler.<Player>register("kick", "<username>", "Kick a person by name", (arg, player) -> {
            if (!adminVerif(player)) return;

            Player target = Groups.player.find(p -> p.name().equals(arg[0]));
            if (target != null) {
                Call.sendMessage("[scarlet]" + target.name() + "[scarlet] has been kicked by the server.");
                target.kick(KickReason.kick);
                info(player, "It is done.");
            } else info(player, "Nobody with that name could be found...");
        });   
        
        handler.<Player>register("pardon", "<ID>", "Pardon a player by ID and allow them to join again", (arg, player) -> {
        	if (!adminVerif(player)) return;
        	
        	PlayerInfo info = netServer.admins.getInfoOptional(arg[0]);
        	
        	if (info != null) {
        		info.lastKicked = 0;
        		info(player, "Pardoned player: [accent]%s", info.lastName);
        	} else err(player, "That ID can't be found.");
        });

        handler.<Player>register("ban", "<id|name|ip> <username|IP|ID...>", "Ban a person", (arg, player) -> {
        	if (!adminVerif(player)) return;

            if (arg[0].equals("id")) {
                netServer.admins.banPlayerID(arg[1]);
                info(player, "Banned.");
            } else if (arg[0].equals("name")) {
                Player target = Groups.player.find(p -> p.name().equalsIgnoreCase(arg[1]));
                if (target != null) {
                    netServer.admins.banPlayer(target.uuid());
                    info(player, "Banned.");
                } else err(player, "No matches found.");
            } else if (arg[0].equals("ip")) {
                netServer.admins.banPlayerIP(arg[1]);
                info(player, "Banned.");
            } else err(player, "Invalid type.");

            
            for (Player gPlayer : Groups.player) {
                if (netServer.admins.isIDBanned(gPlayer.uuid())) {
                    Call.sendMessage("[scarlet]" + gPlayer.name + " has been banned.");
                    gPlayer.con.kick(KickReason.banned);
                }
            }
        });
        
        handler.<Player>register("unban", "<ip|ID>", "Unban a person", (arg, player) -> {
        	if (!adminVerif(player)) return;

            if (netServer.admins.unbanPlayerIP(arg[0]) || netServer.admins.unbanPlayerID(arg[0])) info(player, "Unbanned player: [accent]%s", arg[0]);
            else err(player, "That IP/ID is not banned!");
        });

    }

    
    private void err(Player player, String fmt, Object... msg) {
    	player.sendMessage("[scarlet]Error: " + String.format(fmt, msg));
    }
    private void info(Player player, String fmt, Object... msg) {
    	player.sendMessage("Info: " + String.format(fmt, msg));
    }
    private boolean adminVerif(Player player) {
    	if(!player.admin()){
    		player.sendMessage("[scarlet]This command is only for admins!");
            return false;
    	} else return true;
    }
    
    //leave spectate mode
    public void SpectateLeave(){
        Events.on(PlayerLeave.class, event -> {
            if(rememberSpectate.containsKey(event.player)){
                rememberSpectate.remove(event.player);
            }
        });
    }
    //search a possible team
    private Team getPosTeam(Player p){
        Team currentTeam = p.team();
        int c_index = Arrays.asList(Team.baseTeams).indexOf(currentTeam);
        int i = (c_index+1)%6;
        while (i != c_index){
            if (Team.baseTeams[i].cores().size > 0){
                return Team.baseTeams[i];
            }
            i = (i + 1) % Team.baseTeams.length;
        }
        return currentTeam;
    }

    private coreTeamReturn getPosTeamLoc(Player p){
        Team currentTeam = p.team();
        Team newTeam = getPosTeam(p);
        if (newTeam == currentTeam){
            return null;
        }else{
            Tile coreTile = newTeam.core().tileOn();
            return new coreTeamReturn(newTeam, coreTile.drawx(), coreTile.drawy());
        }
    }

    class coreTeamReturn{
        Team team;
        float x,y;
        public coreTeamReturn(Team _t, float _x, float _y){
            team = _t;
            x = _x;
            y = _y;
        }
    }

}
