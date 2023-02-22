/*
 * Total code lines (all files combined): 3 972 lines
 */

import static mindustry.Vars.content;
import static mindustry.Vars.maps;
import static mindustry.Vars.netServer;
import static mindustry.Vars.state;
import static mindustry.Vars.world;

import arc.Core;
import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Timer;
import mindustry.content.Blocks;
import mindustry.core.GameState.State;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.Administration.Config;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.Packets.KickReason;
import mindustry.type.Item;

import util.*;
import data.*;
import filter.*;
import filter.FilterType.Reponses;
import manager.*;


public class moreCommandsPlugin extends mindustry.mod.Plugin {
  @SuppressWarnings("unchecked")
  public moreCommandsPlugin() {
    Log.info("|-> MoreCommands, rtv");

    // init other classes and load settings
    Effects.init();
    BansManager.load();
    AntiVpn.init(true);
    ArgsFilter.load();
    Switcher.load();
    ALog.init();

    // Because i do big error on previus version, i do fix it on new version
    // and lot of clone of plugin have this error
    try {
      if (Core.settings.has("Tags")) PVars.tags = Core.settings.getBool("Tags");
      else Core.settings.put("Tags", PVars.tags);
    } catch (ClassCastException e) {
      Core.settings.put("Tags", PVars.tags);
    }

    if (Core.settings.has("PlayersTags")) PVars.playerTags = Core.settings.getJson("PlayersTags", ObjectMap.class, ObjectMap::new);
    else Core.settings.putJson("PlayersTags", new ObjectMap<String, String>());

    if (Core.settings.has("AutoPause")) PVars.autoPause = Core.settings.getBool("AutoPause");
    else Core.settings.put("AutoPause", PVars.autoPause);

    if (Core.settings.has("BansReason")) PVars.bansReason = Core.settings.getJson("BansReason", ObjectMap.class, ObjectMap::new);
    else Core.settings.putJson("BansReason", new ObjectMap<String, String>());

    // init events
    ContentRegister.initEvents();
  }

  // Called after all plugins have been created and commands have been registered.
  public void init() {
    // check if a new update is available
    arc.util.Http.get(mindustry.Vars.ghApi + "/repos/ZetaMap/moreCommands/releases/latest", s -> {
      String[] lastestV = arc.util.serialization.Jval.read(s.getResultAsString()).get("tag_name").asString().substring(1).split("\\."),
          pluginV = mindustry.Vars.mods.getMod("morecommands").meta.version.split("\\.");

      if (Strings.parseFloat(lastestV[1].isBlank() ? lastestV[0]
          : lastestV[0] + (lastestV[1].length() == 1 ? ".0" : ".") + lastestV[1]) > Strings.parseFloat(
              pluginV[1].isBlank() ? pluginV[0] : pluginV[0] + (pluginV[1].length() == 1 ? ".0" : ".") + pluginV[1]))
        Log.info( "Nueva versión disponible 'github.com/ZetaMap/moreCommands/releases' ve");
    }, null);

    ContentRegister.initFilters(); // init chat and actions filters
    CommandsManager.init(); // init the commands manager

    // pause the game if no one is connected
    if (PVars.autoPause) {
      state.set(State.paused);
      Log.info("auto-pause: Game paused...");
    }

    Log.info("|-> MoreCommands Plugin cargado, versión rainbow foradmin =)");
  }

  // register commands that run on the server
  @Override
  public void registerServerCommands(CommandHandler handler) {
    ContentRegister.CommandsRegister commands = ContentRegister.setHandler(handler);

    handler.removeCommand("bans");
    handler.removeCommand("ban");
    handler.removeCommand("unban");
    commands.add("ban", "<list|add|remove|reset> [type-id|ip] [ID|IP] [reason...]", "List all banned IP/ID or ban/unban an ID/IP",
      BansManager::bansCommand);

    handler.removeCommand("fillitems");
    commands.add("fillitems", "[team|all] [items...]", "Rellena el núcleo con los elementos seleccionados.", arg -> {
      if (!state.is(State.playing)) {
        Log.err("Not playing. Host or unpause first.");
        return;
      }

      Seq<Team> teams = Seq.with(Team.all).filter(t -> !state.teams.cores(t).isEmpty());
      Seq<Item> items = content.items().copy();

      if (arg.length == 0) {
        Log.info("Items de todos los equipos:");
        teams.each(t -> {
          mindustry.world.modules.ItemModule coreItems = t.cores().first().items;
          Seq<String> teamItems = items.select(i -> coreItems.has(i)).map(i -> i.name + "=" + coreItems.get(i) + ", ");
          String builder = "| | ";
          int best = Strings.bestLength(teamItems);

          Log.info("| Team @: Total: @ items ", t.name, coreItems.total());
          for (int i=0; i<teamItems.size; i++) {
            builder += teamItems.get(i);
            if (i%3 == 0 && i != 0) {
              Log.info(builder);
              builder = "| | ";
            }else builder += Strings.repeat("\t", (int)(teamItems.get(i).length()/10));
          }
          Log.info(builder);
        });
        return;
      }

      if (!arg[0].equals("all")) {
        Team team = Players.findTeam(arg[0]);

        if (team == null) {
          Log.err("No se encontró ningún equipo con ese nombre.");
          return;
        } else if (state.teams.cores(team).isEmpty()) {
          Log.err("Ese equipo no tiene núcleos.");
          return;
        }

        teams.clear();
        teams.add(team);
      }

      if (arg.length == 2) {
        items.clear();
        Item found;

        for (String name : arg[1].split(" ")) {
          found = content.item(name);
          if (found == null) {
            Log.err("El item '@' no existe", name);
            return;
          }
          items.add(found);
        }
      }

      Seq<mindustry.world.blocks.storage.CoreBlock.CoreBuild> cores;
      for (Team team : teams) {
        cores = state.teams.cores(team);

        if (!cores.isEmpty()) {
          for (Item item : items) cores.first().items.set(item, cores.first().storageCapacity);
        }
      }

      Log.info("Core of team@ filled@.",
        (teams.size > 1 ? "&frs &lb" : " ") + teams.toString(", "),
        (arg.length == 1 ? "" : "&fr with item" + (items.size > 1 ? "&frs &lb" : " ") + items.toString(", ")));
    });

    commands.add("auto-pause", "Pausa el juego si no hay nadie conectado", arg -> {
      PVars.autoPause = !PVars.autoPause;
      Config.autoPause.set(false);
      Log.info("Auto pause @...", PVars.autoPause ? "enabled" : "disabled");
      saveSettings();

      if (PVars.autoPause && Groups.player.size() == 0) {
        state.set(State.paused);
        Log.info("auto-pause: Game paused...");
      }
    });

    commands.add("chat", "[on|off]", "Habilita/bloquea el chat", arg -> {
      if (arg.length == 1) {
        if (Strings.choiseOn(arg[0])) {
          if (PVars.chat) {
            Log.err("Bloquea primero");
            return;
          }
          PVars.chat = true;

        } else if (Strings.choiseOff(arg[0])) {
          if (!PVars.chat) {
            Log.err("Habilita primero");
            return;
          }
          PVars.chat = false;

        } else {
          Log.err("Argumentos inválidos. \n - El chat está actualmente @.", PVars.chat ? "Habilitado" : "Bloqueado");
          return;
        }

        Log.info("Chat @ ...", PVars.chat ? "Habilitado" : "Bloqueado");
        Call.sendMessage("\n[gold]-------------------- \n[yellow]/!\\[slate] Chat " + (PVars.chat ? "Habilitado" : "Bloqueado")
          + " mediante la [scarlet][[Consola][]! \n[gold]--------------------\n");
        ALog.write("Chat", "[Server] @ the chat", PVars.chat ? "enabled" : "disabled");

      } else Log.info("El chat está actualmente @.", PVars.chat ? "Habilitado" : "Bloqueado");
    });

    commands.add("nice-welcome", "Nice welcome for me", arg -> {
      PVars.niceWelcome = !PVars.niceWelcome;
      Log.info(PVars.niceWelcome ? "Enabled..." : "Disabled...");
    });

    commands.add("commands", "<list|reset|name> [on|off]", "Enable/Disable a command. /!\\Requires server restart to apply changes.", arg -> {
      if (arg[0].equals("list")) {
        StringBuilder builder = new StringBuilder();
        Seq<CommandsManager.Commands> client = CommandsManager.getCommands().filter(c -> c.name.startsWith("/"));
        Seq<CommandsManager.Commands> server = CommandsManager.getCommands().filter(c -> !c.name.startsWith("/"));
        int best1 = Strings.bestLength(client.map(c -> c.name));
        int best2 = Strings.bestLength(server.map(c -> c.name));

        Log.info("List of all commands: ");
        Log.info(Strings.lJust("| Server commands: Total:" + server.size, 28 + best2) + "Client commands: Total:" + client.size);
        for (int i=0; i<Math.max(client.size, server.size); i++) {
          try { builder.append(Strings.mJust("| | Name: " + server.get(i).name," - Enabled: " + (server.get(i).isActivate ? "true " : "false"), 27 + best2)); } 
          catch (IndexOutOfBoundsException e) { builder.append("|" + Strings.repeat(" ", best1 + 20)); }
          try { builder.append(Strings.lJust(" | Name: " + client.get(i).name, 9 + best1) + " - Enabled: " + client.get(i).isActivate);}
          catch (IndexOutOfBoundsException e) {}

          Log.info(builder.toString());
          builder = new StringBuilder();
        }

      } else if (arg[0].equals("reset")) {
        CommandsManager.getCommands().each(c -> CommandsManager.get(c.name).set(true));
        CommandsManager.save();
        CommandsManager.update(handler);
        Log.info("All command statuses have been reset.");

      } else {
        CommandsManager.Commands command = CommandsManager.get(arg[0]);

        if (command == null) Log.err("This command doesn't exist!");
        else if (arg.length > 1) {
          if (Strings.choiseOn(arg[1])) command.set(true);
          else if (Strings.choiseOff(arg[1])) {
            if (command.name == "commands")Log.err("Why do that ? It can destroy the config!");
            else command.set(false);
          }else {
            Log.err("Invalid value");
            return;
          }

          Log.info("@ ...", command.isActivate ? "Enabled" : "Disabled");
          CommandsManager.save();
          CommandsManager.update(handler);

        } else Log.info("The command '" + command.name + "' is currently " + (command.isActivate ? "enabled" : "disabled"));
      }
    });

    commands.add("clear-map", "[y|n]", "Elimina Unidades y bloques excepto los núcleos", arg -> {
      if (!state.is(mindustry.core.GameState.State.playing)) Log.err("Not playing. Host first.");
      else {
        if (arg.length == 1 && !PVars.clearConfirm) {
          Log.err("Usa primero: 'clear-map', antes de confirmar el comando.");
          return;
        } else if (!PVars.clearConfirm) {
          Log.warn("¡Este comando puede bloquear el servidor! ¿Estás seguro de que quieres que se ejecute? (clear-map <y|n>)");
          PVars.clearConfirm = true;
          return;
        } else if (arg.length == 0 && PVars.clearConfirm) {
          Log.warn("¡Este comando puede bloquear el servidor! ¿Estás seguro de que quieres que se ejecute? (clear-map <y|n>)");
          PVars.clearConfirm = true;
          return;
        }

        switch (arg[0]) {
          case "y": case "yes":
            Log.info("Empezando...");
            Call.infoMessage("[scarlet]El mapa se restablecerá en [orange]10[] segundos! \n[]Todas las unidades, jugadores y edificios (excepto los núcleos) serán destruido.");
            try { Thread.sleep(10000); } 
            catch (InterruptedException e) {}

            mindustry.gen.Building block;
            int unitCounter = Groups.unit.size(), blockCounter = 0;

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

            Log.info("Mapa limpiado! (Se eliminó @ unidades y @ bloques)", unitCounter, blockCounter);
            Call.infoMessage(Strings.format("[green]Mapa limpiado! [lightgray](Se eliminó [scarlet]@[] unidades y [scarlet]@[] bloques)", unitCounter, blockCounter));
            break;

          default: Log.info("Confirmación cancelada...");
        }
        PVars.clearConfirm = false;
      }
    });

    commands.add("gamemode", "[name]", "Cambiar el modo de juego del mapa actual", arg -> {
      if (state.is(mindustry.core.GameState.State.playing)) {
        if (arg.length == 1) {
          try {
            state.rules = state.map.applyRules(Gamemode.valueOf(arg[0]));
            Call.worldDataBegin();
            Groups.player.each(p -> netServer.sendWorldData(p));
            Log.info("Gamemode seteado en '@'", arg[0]);

          } catch (IllegalArgumentException e) { Log.err("No gamemode '@' found.", arg[0]); }
        } else Log.info("The gamemode is curently '@'", state.rules.mode().name());
      } else Log.err("Not playing. Host first.");
    });

    commands.add("blacklist", "<list|add|remove|clear> <name|ip> [value...]",
      "Players using a nickname or ip in the blacklist cannot connect to the server (spaces on the sides, colors, and emojis are cut off when checking out)",
        BansManager::blacklistCommand);

    commands.add("anti-vpn", "[on|off|token] [your_token]", "Anti VPN service. (By default daily limit is 100 but with free account is 1000 and more with plans)", arg -> {
      if (arg.length == 0) {
        Log.info("Anti VPN is currently @.", AntiVpn.isEnabled ? "enabled" : "disabled");
        return;
      }

      if (arg[0].equals("token")) {
        if (arg.length == 2) {
          AntiVpn.apiToken = arg[1];
          AntiVpn.saveSettings();

          if (AntiVpn.apiToken.isBlank()) Log.info("token removed");
          else {
            Log.info("token saved");

            arc.util.Http.get("https://vpnapi.io/api/1.1.1.1?key=" + AntiVpn.apiToken, s -> {
              String result = s.getResultAsString();
              if (!result.contains("\"security\":"))
                throw new Exception(result.substring(result.indexOf("\"security\":") + 12, result.length() - 2).replace("\"", ""));
            }, f -> {
              Log.warn("Error occurred while testing token.");
              Log.warn("Error: " + f.getLocalizedMessage());
            });
          }

        } else Log.info(AntiVpn.apiToken.isBlank() ? "No token defined" : "Vpnapi.io token is currently " + AntiVpn.apiToken);
        return;

      } else if (Strings.choiseOn(arg[0])) {
        if (AntiVpn.isEnabled) {
          Log.err("Disabled first!");
          return;
        }
        AntiVpn.isEnabled = true;
        if (!AntiVpn.fullLoaded) AntiVpn.init();

      } else if (Strings.choiseOff(arg[0])) {
        if (!AntiVpn.isEnabled) {
          Log.err("Enabled first!");
          return;
        }
        AntiVpn.isEnabled = false;

      } else {
        Log.err("Invalid arguments. - Anti VPN is currently @.", AntiVpn.isEnabled ? "enabled" : "disabled");
        return;
      }

      Log.info("Anti VPN @ ...", AntiVpn.isEnabled ? "enabled" : "disabled");
      AntiVpn.saveSettings();
    });

    commands.add("filters", "<help|on|off>", "Enabled/disabled filters", arg -> {
      if (arg[0].equals("help")) {
        Log.info("Filters are currently " + (ArgsFilter.enabled ? "enabled." : "disabled."));
        Log.info("Help for all filters: ");
        for (FilterType type : FilterType.values()) Log.info(" - " + type.getValue() + ": this filter targets " + type.desc + ".");
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

    commands.add("effect", "<default|list|id|name> [on|off] [forAdmin]", "Enabled/disabled a particles effect (default: set to default values, not reset)", arg -> {
      Effects effect;

      if (arg[0].equals("default")) {
        Effects.setToDefault();
        Effects.saveSettings();
        Log.info("Effects set to default values");

      } else if (arg[0].equals("list")) {
        Seq<Effects> effects = Effects.copy(true, true);
        int name = Strings.bestLength(effects.map(e -> e.name)) + 7, id = Strings.bestLength(effects.map(e -> e.id + "")) + 12;

        Log.info("List of all effects: Total: " + effects.size);
        effects.each(e -> Log.info("| Name: " + Strings.mJust(e.name, " - ID: ", name)
            + Strings.mJust(e.id + "", " - Enabled: ", id) + !e.disabled
            + (e.disabled ? "" : " ") + " - ForAdmin: " + e.forAdmin));

      } else if (Strings.canParseInt(arg[0])) {
        effect = Effects.getByID(Strings.parseInt(arg[0]) - 1);

        if (effect != null) {
          if (arg.length > 1) {
            if (Strings.choiseOn(arg[1])) effect.disabled = false;
            else if (Strings.choiseOff(arg[1])) effect.disabled = true;
            else {
              Log.err("arg[1]: Invalid arguments.");
              return;
            }

            if (arg.length == 3) {
              if (Strings.choiseOn(arg[2])) effect.forAdmin = true;
              else if (Strings.choiseOff(arg[2])) effect.forAdmin = false;
              else {
                Log.err("arg[2]: Invalid arguments.");
                return;
              }

              Log.info("effect '@' set to @, and admin to @", effect.name, !effect.disabled, effect.forAdmin);
            } else Log.info("effect '@' set to @", effect.name, !effect.disabled);

            Effects.saveSettings();

          } else Log.info("effect '@' is curently @", effect.name, effect.disabled ? "disabled" : "enabled");
        } else Log.err("no effect with id '@'", arg[0]);

      } else {
        effect = Effects.getByName(arg[0]);

        if (effect != null) {
          if (arg.length > 1) {
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
            int name = Strings.bestLength(Switcher.names()) + 7, ip = Strings.bestLength(Switcher.ips()) + 9,
                port = Strings.bestLength(Switcher.ports().map(i -> i + ""));

            Log.info("Switch servers list: Total:" + Switcher.size());
            Switcher.each(true, i -> Log.info("| Name: " + Strings.mJust(i.name, " - IP: ", name) +
                Strings.mJust(i.ip, " - Port: ", ip) + Strings.mJust(i.port + "", " - ForAdmin: ", port) + i.forAdmin));
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
                Log.info(server.changed ? server.name + " set to " + server.address() + ", for admins: " + server.forAdmin + " ..."
                    : "Added " + server.name + ".");
                Switcher.saveSettings();

              } else Log.err("Bad IP format");
            } else Log.err("Empty server name (without emoji)");
          } else Log.err("3 arguments are expected ");
          break;

        case "remove":
          Switcher server = Switcher.remove(arg[1]);
          if (server == null) Log.err("This server name isn't in the list");
          else {
            Log.info("Removed " + server.name);
            Switcher.saveSettings();
          }
          break;

        case "clear":
          Switcher.clear();
          Switcher.saveSettings();
          Log.info("Servers list cleared");
          break;
        default: Log.err("Invalid arguments.");
      }
    });

    commands.add("tag", "<help|arg0> [ID] [tagName...]", "Configure the tag system", arg -> {
      if (Strings.choiseOn(arg[0])) {
        PVars.tags = true;
        saveSettings();
        TempData.each(d -> d.applyTag());
        Log.info("tags enabled ...");

      } else if (Strings.choiseOff(arg[0])) {
        PVars.tags = false;
        saveSettings();
        TempData.each(d -> d.applyTag());
        Log.info("tags disabled ...");

      } else {
        switch (arg[0]) {
          case "help":
            Log.info("Tags Help:");
            Log.info("| Tag system is currently @ ...", PVars.tags ? "enabled" : "disabled");
            Log.info("| Posible arguments:");
            Log.info("| | on - enable the tag system. (if the player is admin, the tag '<Admin>' will be applied by default)");
            Log.info("| | off - disable the tag system.");
            Log.info("| | list - displays the list of ID with a tag.");
            Log.info("| | add - add a tag associated with an ID in the list. (the tag will be applied automatically to the player if he is connected)");
            Log.info("| | | ID - the ID of the player to which the tag will be applied.");
            Log.info("| | | tagName - the name of the tag. (you can give it colors or emojis)");
            Log.info("| | remove - remove the tag in the list. (the tag will be removed automatically to the player if he is connected)");
            Log.info("| | | ID - the ID of the player to which the tag will be removed.");
            break;

          case "list":
            if (PVars.playerTags.isEmpty()) Log.info("no tag in the list");
            else {
              Log.info("Tag List:");
              PVars.playerTags.each((k, v) -> Log.info("| PlayerID: " + Strings.lJust(k, 24) + " - Tag: " + v));
            }
            break;

          case "add":
            if (arg.length == 3) {
              PlayerInfo target = netServer.admins.getInfoOptional(arg[1]);

              if (target != null) {
                PVars.playerTags.put(target.id, arg[2]);
                saveSettings();
                Log.info("tag added");

                Players find = Players.findByID(arg[1]);

                if (find.found) {
                  find.data.applyTag();
                  Log.info("player online, tag added to this player");
                }

              } else Log.info("no player found with id '@'", arg[1]);
            } else Log.err("3 arguments are expected");
            break;

          case "remove":
            if (arg.length == 2) {
              if (PVars.playerTags.get(arg[1]) != null) {
                PVars.playerTags.remove(arg[1]);
                saveSettings();
                Log.info("tag removed");

                Players find = Players.findByID(arg[1]);

                if (find.found) {
                  find.data.applyTag();
                  Log.info("player online, tag removed to this player");
                }

              } else Log.err("no tag associated with this ID");
            } else Log.err("2 arguments are expected");
            break;

          default: Log.err("Invalid arguments.");
        }
      }
    });

    commands.add("alogs", "[on|off|reset] [y|n]", "Configure admins logs", arg -> {
      Fi path = Core.files.local(PVars.ALogPath);
      Seq<Fi> files = path.exists() ? path.findAll() : null;

      if (arg.length > 0) {
        if (arg[0].equals("reset")) {
          if (arg.length == 2 && !PVars.alogConfirm) {
            Log.err("Use first: 'alogs reset', before confirming the command.");
            return;
          } else if (!PVars.alogConfirm) {
            Log.warn("This will delete all admin logs files! Are you sure you want it executed (alogs reset [y|n])");
            PVars.alogConfirm = true;
            return;
          } else if (arg.length == 0 && PVars.alogConfirm) {
            Log.warn("This will delete all admin logs files! Are you sure you want it executed (alogs reset [y|n])");
            PVars.alogConfirm = true;
            return;
          }

          switch (arg[1]) {
            case "y": case "yes":
              if (path.exists()) {
                int size = files.size;

                path.deleteDirectory();
                Log.info(size + " files deleted in: " + path.path());
                Log.info("directory deleted");

              } else Log.err("Files directory not found.");
              ALog.files = 0;
              break;

            default: Log.err("Confirmation canceled ...");
          }
          PVars.alogConfirm = false;

        } else if (Strings.choiseOn(arg[0])) ALog.isEnabled = true;
        else if (Strings.choiseOff(arg[0])) ALog.isEnabled = false;
        else {
          Log.err("Invalid arguments.");
          return;
        }

        ALog.saveSettings();
        if (!arg[0].equals("reset")) {
          Log.info("Admin logs @ ...", ALog.isEnabled ? "enabled" : "disabled");
          if (ALog.isEnabled) ALog.init();
        }

      } else {
        Log.info("Admin Logs Help:");
        Log.info("| Admin logs is @ ...", ALog.isEnabled ? "enabled" : "disabled");
        Log.info("| @ files created since the last reset.", ALog.files+1);
        Log.info("| Logs path: @", PVars.ALogPath);
        Log.info("");
        Log.info("Logs files:");

        if (path.exists()) {
          int best = Strings.bestLength(files.map(f -> f.name()));

          if (!files.isEmpty()) files.each(f -> Log.info("| Name: " + Strings.lJust(f.name(), best) + " - Size: " + f.length() + " bytes"));
          else Log.err("| No files in the directory.");
        } else Log.err("| Files directory not found.");
      }
    });
    /*
    commands.add("reset", "<ID>", "Reset all player's data (names, ips, ...)", arg -> {
      PlayerInfo target = netServer.admins.getInfoOptional(arg[0]);

      if (target == null) Log.err("no player found with id '@'", arg[0]);
      else {
        Players player = Players.findByID(arg[0]);
        if (player.found) player.player.kick("Player data reseted.");

        target = new PlayerInfo();
        target.id = arg[0];
        

        Log.info("player data reseted");
      }
    });
    */
  }

  // register commands that player can invoke in-game
  @Override
  public void registerClientCommands(CommandHandler handler) {
    ContentRegister.CommandsRegister commands = ContentRegister.setHandler(handler);

    handler.removeCommand("t");
    commands.add("t", "<message...>", "Envía un mensaje solo a tus compañeros de equipo", false, false, (arg, data) -> {
      if (PVars.chat && data.isMuted) util.Players.err(data.player, "Estás silenciado/a, no puedes hablar.");
      else if (!PVars.chat && !data.player.admin) data.player.sendMessage("[scarlet]Chat desactivado, solo los moderadores pueden hablar!");
      else Groups.player.each(p -> p.team() == data.player.team(), p -> p.sendMessage(Strings.format("[accent]<[#@]Team[]> @ [accent]» @",
              data.player.team().color.toString(), data.nameColor + data.realName, arg[0]), data.player, arg[0]));
    });

    handler.removeCommand("a");
    commands.add("a", "<message...>", "Chat privado entre moderadores", true, false, (arg, data) ->
      Groups.player.each(p -> p.admin, p -> p.sendMessage(Strings.format("[accent]<[scarlet]A[]> @ [scarlet]»[accent] @",
        data.nameColor + data.realName, arg[0]), data.player, arg[0]))
    );

    handler.removeCommand("help");
    commands.add("help", "[page|filter]", "Lista de todos los comandos", false, false, (arg, data) -> {
      StringBuilder result = new StringBuilder();
      FilterSearchReponse filter = ArgsFilter.hasFilter(data.player, arg);

      if (arg.length == 1) {
        if (data.player.admin) {
          if (arg[0].equals("filter") || arg[0].equals("filters")) {
            result.append("Info de todos los filtros ");
            for (FilterType type : FilterType.values()) result.append("\n - [gold]" + type.getValue() + "[]: Aplica: [sky]" + type.desc + "[].");
            data.player.sendMessage(result.toString());
            return;

          } else if (filter.reponse == Reponses.found) {
            data.player.sendMessage("Ayuda de filtro [gold]" + filter.type.getValue() + "[]: \nAplica: [sky]" + filter.type.desc + "[].");
            return;

          } else if (filter.reponse == Reponses.notFound) {
            if (!Strings.canParseInt(arg[0])) {
              data.player.sendMessage("[scarlet]'page' debe ser un número.");
              return;
            }

          } else {
            filter.sendIfError();
            return;
          }

        } else if (!Strings.canParseInt(arg[0])) {
          data.player.sendMessage("[scarlet]'page' debe ser un número.");
          return;
        }
      }

      Seq<CommandHandler.Command> cList = data.player.admin ? handler.getCommandList() : handler.getCommandList().select(c -> !PVars.adminCommands.contains(c.text));
      CommandHandler.Command c;
      int lines = 8, page = arg.length == 1 ? Strings.parseInt(arg[0]) : 1, pages = Mathf.ceil(cList.size / lines);
      if (cList.size % lines != 0) pages++;

      if (page > pages || page < 1) {
        data.player.sendMessage("[scarlet]'page' debe ser un número entre[orange] 1[] a[orange] " + pages + "[].");
        return;
      }

      result.append(Strings.format("[orange]-- Página de comandos[lightgray] @[gray]/[lightgray]@[orange] --\n", page, pages));
      for (int i=(page - 1) * lines; i < lines * page; i++) {
        try {
          c = cList.get(i);
          result.append("\n[orange] " + handler.getPrefix() + c.text + "[white] " + c.paramText + "[lightgray] - " + c.description);
        } catch (IndexOutOfBoundsException e) { break; }
      }

      data.player.sendMessage(result.toString());
    });

    commands.add("ut", "[filter|username...]", "El nombre de la unidad actual", false, false, (arg, data) -> {
      TempData target = data;

      if (arg.length == 1) {
        FilterSearchReponse filter = ArgsFilter.hasFilter(data.player, arg);

        if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
        else if (filter.reponse == Reponses.found) {
          filter.execute(ctx -> {
            if (ctx.player == null) data.player.sendMessage(ctx.unit.type.name + " at [accent]" + ctx.unit.tileX() + "[],[accent]" + ctx.unit.tileY());
            else data.player.sendMessage((arg.length == 1 ? "[accent]" + ctx.data.realName + "[white] is " : "You're ")
                   + (ctx.player.unit().type == null ? "[sky]invisible ..." : "a [sky]" + ctx.player.unit().type.name) + "[white].");
          });
          return;

        } else {
          Players t = Players.findByName(arg);

          if (t.found) target = t.data;
          else {
            Players.errNotOnline(data.player);
            return;
          }
        }
      }

      data.player.sendMessage((arg.length == 1 ? "[accent]" + target.realName + "[white] is " : "You're ")
          + (target.player.unit().type == null ? "[sky]invisible ..." : "a [sky]" + target.player.unit().type.name) + "[white].");
    });

    commands.add("msg", "<username|ID> <message...>", "Creas un chat privado con un jugador, luego usa /r <message> para charlar", false, false, (arg, data) -> {
      Players result = Players.findByNameOrID(arg);

      if (result.found) {
        String message = String.join(" ", result.rest);

        if (!Strings.stripColors(message).isBlank()) {
          result.data.msgData.setTarget(data);
          Call.sendMessage(data.player.con, message, "[sky]Enviado [green] » " + result.data.nameColor + result.data.realName, data.player);
          Call.sendMessage(result.player.con, message, result.data.nameColor + result.data.realName + " [scarlet]» [sky]Recibido", data.player);

        } else Players.err(data.player, "No dejes el mensaje en blanco.");
      } else Players.errNotOnline(data.player);
    });

    commands.add("r", "<message...>", "Responder al último mensaje privado recibido, solo si tienes un chat privado abierto.", false, false, (arg, data) -> {
      if (data.msgData.target != null) {
        if (data.msgData.targetOnline) {
          if (!Strings.stripColors(arg[0]).isBlank()) {
            Call.sendMessage(data.player.con, arg[0], "[sky]Enviado [green] » " + data.msgData.target.nameColor + data.msgData.target.realName, data.player);
            Call.sendMessage(data.msgData.target.player.con, arg[0], data.nameColor + data.realName + " [scarlet]» [sky]Recibido", data.player);

          } else Players.err(data.player, "No dejes el mensaje en blanco.");
        } else Players.err(data.player, "El jugador se desconectó");
      } else Players.err(data.player, "No hay chat privado iniciado.");
    });

    commands.add("maps", "[page]", "Lista de mapas", false, false, (arg, data) -> {
      if (arg.length == 1 && !Strings.canParseInt(arg[0])) {
        data.player.sendMessage("[scarlet]'page' debe ser un número.");
        return;
      }

      StringBuilder builder = new StringBuilder();
      Seq<Map> list = mindustry.Vars.maps.all();
      Map map;
      int page = arg.length == 1 ? Strings.parseInt(arg[0]) : 1, lines = 8, pages = Mathf.ceil(list.size / lines);
      if (list.size % lines != 0) pages++;

      if (page > pages || page < 1) {
        data.player.sendMessage("[scarlet]'page' el número debe ser entre[orange] 1[] y [orange]" + pages + "[].");
        return;
      }

      builder.append("\n[lightgray]Mapa actual: " + state.map.name() + "[white]\n[orange]---- [gold]Lista de mapas [lightgray]" + page + "[gray]/[lightgray]" + pages + "[orange] ----");
      for (int i=(page - 1) * lines; i < lines * page; i++) {
        try {
          map = list.get(i);
          builder.append("\n[orange]  - [white]" + map.name() +
              "[orange] | [white]" + map.width + "x" + map.height +
              "[orange] | [green]" + (map.custom ? "Custom" : "Builtin") +
              "[orange] | By: [sky]" + map.author());
        } catch (IndexOutOfBoundsException e) { break; }
      }
      data.player.sendMessage(builder.toString() + "\n[orange]-----------------------");
    });

    commands.add("vnw", "[number]", "Vota para saltar oleadas", false, false, (arg, data) -> {
      if (!PVars.canVote) return;
      else if (Groups.player.size() < 1 && !data.player.admin) {
        data.player.sendMessage("[scarlet]1 este mensaje no tiene que aparecer.");
        return;
      } else if (data.votedVNW) {
        data.player.sendMessage("Ya votaste." + (PVars.waveVoted != 1 ? " [lightgray](" + PVars.waveVoted + " oleadas)" : ""));
        return;
      } else if (!PVars.vnwCooldown.get()) {
        data.player.sendMessage("[orange]Espera unos segundos para volver utilizar");
        return;
      }

      if (arg.length == 1) {
        if (!PVars.vnwSession.isScheduled()) {
          if (data.player.admin) {
            if (Strings.canParseInt(arg[0])) {
              PVars.waveVoted = (short) Strings.parseInt(arg[0]);
              ALog.write("VNW", "@ [@] inicia una votación para saltar @ oleada", data.stripedName, data.player.uuid(), PVars.waveVoted);

            } else {
              Players.err(data.player, "Por favor escriba un número");
              return;
            }

          } else {
            Players.errPermDenied(data.player);
            return;
          }
        } else {
          Players.err(data.player, "¡Ya hay una votación para saltarse la oleada! " + (PVars.waveVoted != 1 ? "[lightgray](" + PVars.waveVoted + " oleadas)" : ""));
          return;
        }
      } else if (!PVars.vnwSession.isScheduled())
        PVars.waveVoted = 1;

      data.votedVNW = true;
      int cur = TempData.count(p -> p.votedVNW), req = Mathf.ceil(0.6f * Groups.player.size());
      Call.sendMessage(data.nameColor + data.realName + "[orange] ha votado a favor "
        + (PVars.waveVoted == 1 ? "para saltar la oleada" : "saltar [green]" + PVars.waveVoted + " oleadas") + ". [lightgray](" + (req - cur) + " faltan votos)");

      if (!PVars.vnwSession.isScheduled()) Timer.schedule(PVars.vnwSession, 30);
      if (cur < req) return;

      PVars.vnwSession.cancel();
      Call.sendMessage("[green]Vote for " + (PVars.waveVoted == 1 ? "sending a new wave" : "skiping [scarlet]" + PVars.waveVoted + "[] waves")
        + " is Passed. New Wave will be Spawned.");

      if (PVars.waveVoted > 0) {
        while (PVars.waveVoted-- > 0) {
          try {
            state.wavetime = 0f;
            Thread.sleep(30);
          } catch (Exception e) {
            break;
          }
        }

      } else state.wave += PVars.waveVoted;
    });

    commands.add("rtv", "[mapName...]", "Cambia de mapa, es aleatorio si no seleccionas un mapa", false, false, (arg, data) -> {
      if (!PVars.canVote) return;
      else if (Groups.player.size() < 1 && !data.player.admin) {
        data.player.sendMessage("[scarlet]2 players are required or be an admin to start a vote.");
        return;
      } else if (data.votedRTV) {
        data.player.sendMessage("Ya Votaste. [lightgray](Mapa seleccionado:[white] " + PVars.selectedMap.name() + "[lightgray])");
        return;
      } else if (!PVars.rtvCooldown.get()) {
        data.player.sendMessage("[orange]>:C");
        return;
      }

      if (arg.length == 1) {
        if (!PVars.rtvSession.isScheduled()) {
          PVars.selectedMap = maps.all().find(map -> Strings.stripColors(map.name()).replace(' ', '_').equalsIgnoreCase(Strings.stripColors(arg[0]).replace(' ', '_')));

          if (PVars.selectedMap == null) {
            Players.err(data.player, "No hay un mapa con el nombre '@' ", arg[0]);
            return;
          } else maps.queueNewPreview(PVars.selectedMap);

        } else {
          Players.err(data.player, "Ya hay una votación en marcha para cambiar de mapa! [lightgray](Mapa seleccionado:[white] " + PVars.selectedMap.name() + "[lightgray])");
          return;
        }
      } else if (!PVars.rtvSession.isScheduled()) PVars.selectedMap = maps.getNextMap(Gamemode.valueOf(Core.settings.getString("lastServerMode")), state.map);

      data.votedRTV = true;
      int RTVsize = TempData.count(p -> p.votedRTV), req = Mathf.ceil(0.6f * Groups.player.size());
      Call.sendMessage("[scarlet]RTV: [accent]" + data.nameColor + data.realName
          + " [white]quiere cambiar el mapa, [green]" + RTVsize + "[white]/[green]" + req
          + " []votes. [lightgray](Mapa seleccionado: [white]" + PVars.selectedMap.name() + "[lightgray])");

      if (!PVars.rtvSession.isScheduled()) Timer.schedule(PVars.rtvSession, 60);
      if (RTVsize < req) return;

      PVars.rtvSession.cancel();
      Call.sendMessage("[scarlet]RTV: [green]Votación aprobada, se cambiará de mapa a [white]" + PVars.selectedMap.name() + " [green]...");
      new RTV(PVars.selectedMap, data.player.team());
    });

    commands.add("lobby", "", "Switch to lobby server", false, true, (arg, data) -> {
      if (Switcher.lobby == null) Players.err(data.player, "Lobby server not defined");
      else {
        Switcher.ConnectReponse connect = Switcher.lobby.connect(data.player);
        Call.infoMessage(data.player.con, (connect.failed ? "[scarlet]Error connecting to server: \n[]" : "") + connect.message);
      }
    });

    commands.add("switch", "<list|name...>", "Switch to another server", false, true, (arg, data) -> {
      if (arg[0].equals("list")) {
        if (Switcher.isEmpty()) Players.err(data.player, "No server in the list");
        else {
          data.player.sendMessage("[orange]\ue86a Checking servers ...");
          StringBuilder builder = new StringBuilder();

          Switcher.each(data.player.admin, s -> {
            mindustry.net.Host ping = s.ping();
            builder.append("[lightgray]\n - [orange]" + s.name + " [white]| " + (ping == null ? "[scarlet]Offline"
                : "[green]" + ping.players + " players online" + " [lightgray](map: [accent]" + ping.mapname + "[lightgray])"));
          });
          data.player.sendMessage("Available servers:" + builder.toString());
        }

      } else {
        Switcher server = Switcher.getByName(arg[0]);

        if (server == null) Players.err(data.player, "no server with name '@'", arg[0]);
        else {
          Switcher.ConnectReponse connect = server.connect(data.player);
          Call.infoMessage(data.player.con, (connect.failed ? "[scarlet]Error connecting to server: \n[]" : "") + connect.message);
        }
      }
    });

    commands.add("info-all", "[ID|username...]", "Obtienes info de los jugadores", false, false, (arg, data) -> {
      StringBuilder builder = new StringBuilder();
      ObjectSet<PlayerInfo> infos = ObjectSet.with(data.player.getInfo());
      Players test;
      int i = 1;
      boolean mode = true;

      if (arg.length == 1) {
        test = Players.findByName(arg);

        if (!test.found) {
          if (data.player.admin) {
            test = Players.findByID(arg);

            if (!test.found) {
              infos = netServer.admins.searchNames(arg[0]);
              if (infos.size == 0) infos = ObjectSet.with(netServer.admins.getInfoOptional(arg[0]));
              if (infos.size == 0) {
                Players.err(data.player, "Ningún nombre de jugador tiene [orange]'@'[].", arg[0]);
                return;
              }

            } else infos = ObjectSet.with(test.player.getInfo());

          } else {
            if (Players.findByID(arg).found) Players.err(data.player, "Solo moderadores pueden buscar ID");
            else Players.errNotOnline(data.player);
            return;
          }

        } else infos = ObjectSet.with(test.player.getInfo());
        mode = false;
      }

      if (data.player.admin && !mode) data.player.sendMessage("[gold]----------------------------------------\n[scarlet]-----" + "\n[white]Jugadores encontrados: [gold]" + infos.size + "\n[scarlet]-----");
      for (PlayerInfo pI : infos) {
        if (data.player.admin && !mode) data.player.sendMessage("[gold][" + i++ + "] [white]Trace info for player [accent]'" + pI.lastName.replaceAll("\\[", "[[")
          + "[accent]'[white] / ID [accent]'" + pI.id + "' ");
        else builder.append("[white]Player name [accent]'" + pI.lastName.replaceAll("\\[", "[[") + "[accent]'"
          + (mode ? "[white] / ID [accent]'" + pI.id + "'" : "") + "\n[gold]----------------------------------------[]\n");

        test = Players.findByID(pI.id + " ");

        builder.append("[white] - All names used:[accent] [[[white]" + pI.names.toString("[accent], [white]") + "[accent]]"
          + (test.found ? "\n[white] - [green]Online" + "\n[white] - Country: [accent]" + test.player.locale : "")
          + (TempData.creatorID.equals(pI.id) ? "\n[white] - [sky]Creator of moreCommands [lightgray](the plugin used by this server)" : "")
          + (data.player.admin ? "\n[white] - IP: [accent]" + pI.lastIP + "\n[white] - All IPs used: [accent]" + pI.ips : "")
          + "\n[white] - Times joined: [green]" + pI.timesJoined
          + "\n[white] - Times kicked: [scarlet]" + pI.timesKicked
          + (data.player.admin ? "\n[white] - Is baned: [accent]" + pI.banned
            + (pI.banned ? "\n[white] - Reason: [accent]" + PVars.bansReason.get(pI.id, "<unknown>") : "") : "")
          + "\n[white] - Is admin: [accent]" + pI.admin
          + "\n[gold]----------------------------------------");

        if (mode) Call.infoMessage(data.player.con, builder.toString());
        else {
          data.player.sendMessage(builder.toString());
          builder = new StringBuilder();
        }
      }
    });

    commands.add("rainbow", "[filter|ID|username...]", "[#ff0000]A[#ff7f00]R[#ffff00]C[#00ff00]O[#0000ff]I[#2e2b5f]R[#8B00ff]I[#ff0000]S[#ff7f00]!", true, false, (arg, data) -> {
      TempData target = data;

      if (arg.length == 1) {
        FilterSearchReponse filter = ArgsFilter.hasFilter(data.player, arg[0]);

        if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
        else if (filter.reponse == Reponses.found) {
          if (Players.errFilterAction("Rainbow", filter, true)) return;

          filter.execute(ctx -> {
            if (ctx.data.spectate()) Players.err(data.player, "¡No se puede iniciar el arcoíris en el modo Vanish!");
            else {
              ctx.data.rainbowed = !ctx.data.rainbowed;
              if (ctx.data.rainbowed) ctx.data.hasEffect = false;
              else ctx.data.applyTag();

              data.player.sendMessage(Strings.format("[sky]Efecto arcoiris @ @[].",
                ctx.data.rainbowed ? "activado" : "apagado", " para el jugador [accent]" + ctx.data.realName));
              ALog.write("Rainbow", "@ [@] @ el efecto arcoíris para @ [@]", data.stripedName, data.player.uuid(),
                ctx.data.rainbowed ? "start" : "remove", ctx.data.stripedName, ctx.player.uuid());
            }
          });
          return;

        } else if (arg[0].equals("me"));
        else if (data.player.admin) {
          target = Players.findByNameOrID(arg).data;

          if (target == null) {
            Players.errNotOnline(data.player);
            return;

          } else ALog.write("Rainbow", "@ [@] @ el efecto arcoíris para @ [@]", data.stripedName, data.player.uuid(),
                   target.rainbowed ? "remove" : "start", target.stripedName, target.player.uuid());

        } else {
          Players.errPermDenied(data.player);
          return;
        }
      }

      if (target.spectate()) {
        Players.err(data.player, "¡No se puede iniciar el arcoíris en el modo Vanish!");
        return;
      }

      target.rainbowed = !target.rainbowed;
      if (target.rainbowed) {
        if (arg.length == 1 && arg[0].equals("me"));
        else target.hasEffect = false;

      } else target.applyTag();
      
      data.player.sendMessage(Strings.format( "[sky]Efecto arcoiris @ @[].", target.rainbowed ? "activado" : "apagado", 
        arg.length == 1 ? " para el jugador [accent]" + target.realName: ""));
    });

    commands.add("effect", "[list|name|id] [page|ID|username...]", "Juega con efectos de particulas y lucete ante tu rival o compañeros", false, false, (arg, data) -> {
      Seq<Effects> effects = Effects.copy(data.player.admin, false);
      Effects e;
      StringBuilder builder = new StringBuilder();
      TempData target = data;

      if (arg.length >= 1 && arg[0].equals("list")) {
        if (arg.length == 2 && !Strings.canParseInt(arg[1])) {
          data.player.sendMessage("[scarlet]'page' debe ser un número.");
          return;
        }

        int page = arg.length == 2 ? Strings.parseInt(arg[1]) : 1, lines = 12, pages = Mathf.ceil(effects.size / lines);
        if (effects.size % lines != 0) pages++;

        if (page > pages || page < 0) {
          data.player.sendMessage("[scarlet]'page' debe ser un número entre[orange] 1[] y[orange] " + pages + "[scarlet].");
          return;
        }

        data.player.sendMessage("\n[orange]---- [gold]Lista de efectos [lightgray]" + page + "[gray]/[lightgray]" + pages + "[orange] ----");
        for (int i=(page - 1) * lines; i < lines * page; i++) {
          try {
            e = effects.get(i);
            builder.append("  [orange]- [lightgray]ID:[white] " + e.id + "[orange] | [lightgray]Name:[white] " + e.name
              + (e.forAdmin ? "[orange] | [scarlet]Admin" : "") + "\n");
          } catch (Exception err) {
            break;
          }
        }
        data.player.sendMessage(builder.toString());
        return;

      } else if (arg.length == 0) {
        if (target.hasEffect) {
          target.hasEffect = false;
          data.player.sendMessage("[green]Efecto de partículas eliminadas.");
          return;

        } else if (target.spectate()) {
          Players.err(data.player, "¡No se puede aplicar efectos en el modo Vanish!");
          return;

        } else {
          target.rainbowed = false;
          target.applyTag();
          target.hasEffect = true;
          target.effect = effects.random();

          data.player.sendMessage("Efecto aleatorio...");
          data.player.sendMessage("[green]Efecto aplicado [accent]" + target.effect.id + "[scarlet] - []" + target.effect.name);

        }

      } else if (arg.length == 2) {
        if (data.player.admin) {
          target = Players.findByNameOrID(arg[1]).data;

          if (target == null) Players.errNotOnline(data.player);

          else if (target.spectate()) {
            Players.err(data.player, "¡No se puede aplicar efectos en el modo Vanish!");
            return;

          } else {
            if (target.hasEffect) {
              target.hasEffect = false;
              data.player.sendMessage("[green]Efecto removido [accent]" + target.realName);

            } else {
              if (Strings.canParseInt(arg[0])) e = Effects.getByID(Strings.parseInt(arg[0]) - 1);
              else e = Effects.getByName(arg[0]);

              if (e == null) {
                Players.err(data.player, "Este efecto no existe");
                return;

              } else if (e.disabled) {
                Players.err(data.player, "Este efecto fue bloqueado");
                return;

              } else if (e.forAdmin && !data.player.admin) {
                Players.err(data.player, "Este efecto es exclusivo");
                return;

              } else {
                target.rainbowed = false;
                target.hasEffect = true;
                target.effect = e;

                target.applyTag();
                data.player.sendMessage("[green]Efecto aplicado [accent]" + e.id + "[scarlet] - []" + e.name + "[] por [accent]" + target.realName);
              }
            }
            ALog.write("Effect", "@ [@] @ particles effect to @ [@]", data.stripedName, data.player.uuid(),
              target.hasEffect ? "start" : "remove", target.stripedName, target.player.uuid());
          }
        } else Players.errPermDenied(data.player);
        return;

      } else {
        if (target.spectate()) {
          Players.err(data.player, "¡No se puede aplicar efectos en el modo Vanish!");
          return;
        }

        if (Strings.canParseInt(arg[0])) e = Effects.getByID(Strings.parseInt(arg[0]) - 1);
        else e = Effects.getByName(arg[0]);

        if (e == null) {
          Players.err(data.player, "Este efecto no existe");
          return;

        } else if (e.disabled) {
          Players.err(data.player, "Este efecto fue bloqueado");
          return;

        } else if (e.forAdmin && !data.player.admin) {
          Players.err(data.player, "Este efecto es exclusivo");
          return;

        } else {
          target.rainbowed = false;
          target.hasEffect = true;
          target.effect = e;

          target.applyTag();
          data.player.sendMessage("[green]Efecto aplicado [accent]" + e.id + "[scarlet] - []" + e.name);
        }
      }
    });

    commands.add("team2", "[list|teamName|vanish|~] [filter|username...]", "Cambia de team", true, false, (args, data) -> {
      StringBuilder builder = new StringBuilder();
      Team ret = null;
      FilterSearchReponse filter = null;
      TempData target = data;
      boolean noCore;

      if (args.length == 2) {
        filter = ArgsFilter.hasFilter(data.player, args[1]);

        if (filter.reponse == Reponses.notFound) {
          target = Players.findByName(args[1]).data;

          if (target == null) {
            Players.errNotOnline(data.player);
            return;
          }

        } else if (filter.sendIfError()) return;
      }

      if (filter != null && filter.reponse == Reponses.found) {
        filter.execute(ctx -> {
          if (ctx.player != null) {
            TempData t = TempData.get(ctx.player);

            if (t.spectate()) {
              t.player.sendMessage(">[orange] transferido a un team");
              t.player.team(t.spectate);
              Call.setPlayerTeamEditor(t.player, t.spectate);
              t.spectate = null;
              t.applyTag();
            }
          }
        });
        return;

      } else if (target.spectate()) {
        target.player.sendMessage(">[orange] transferido a un team");
        target.player.team(target.spectate);
        Call.setPlayerTeamEditor(target.player, target.spectate);
        target.spectate = null;
        target.applyTag();
        return;
      }

      if (args.length >= 1) {
        Team retTeam;
        switch (args[0]) {
          case "~":
            retTeam = data.player.team();
            break;

          case "vanish":
            if (filter != null && filter.reponse == Reponses.found) {
              if (Players.errFilterAction("Vanish team", filter, true)) return;

              int counter = filter.execute(ctx -> {
                TempData t = TempData.get(ctx.player);
                t.spectate = t.player.unit().team;
                t.rainbowed = false;
                t.hasEffect = false;

                t.player.team(Team.all[8]);
                Call.setPlayerTeamEditor(t.player, Team.all[8]);
                t.player.unit().kill();
                t.player.name = "";

                t.player.sendMessage("[green]VANISH MODE[] \nuse /team para salir del modo Vanish");
                ALog.write("Team", "@ [@] vanished @ [@]", data.stripedName, data.player.uuid(), t.stripedName, t.player.uuid());
              });
              data.player.sendMessage("You put [green]" + counter + "[] players in vanish mode");

            } else {
              target.spectate = target.player.unit().team;
              target.rainbowed = false;
              target.hasEffect = false;

              target.player.team(Team.all[8]);
              Call.setPlayerTeamEditor(target.player, Team.all[8]);
              target.player.unit().kill();
              target.player.name = "";

              target.player.sendMessage("[green]VANISH MODE[] \nuse /team para salir del modo Vanish.");
              data.player.sendMessage("Te has seteado [accent]" + target.realName + "[white] el vanish mode");
              ALog.write("Team", "@ [@] vanished @ [@]", data.stripedName, data.player.uuid(), target.stripedName, target.player.uuid());
            }
            return;

          default:
            retTeam = Players.findTeam(args[0]);

            if (retTeam == null) Players.err(data.player, "Team no encontrado!");
            else break;

          case "list":
            builder.append("team disponible: \n - [accent]vanish[]\n");
            for (Team team : Team.baseTeams) {
              builder.append(" - [accent]" + team.name + "[]");
              if (!team.cores().isEmpty()) builder.append(" | [green]" + team.cores().size + "[] core(s) encontrado");
              builder.append("\n");
            }
            data.player.sendMessage(builder.toString());
            return;
        }

        noCore = retTeam.cores().isEmpty();
        ret = retTeam;

      } else {
        ret = getPosTeamLoc(target.player);
        noCore = false;
      }

      // move team mechanic
      if (ret != null || noCore) {
        Team retF = ret;
        if (noCore) Players.warn(data.player, "El team no tiene core!");

        if (filter != null && filter.reponse == Reponses.found) {
          int counter = filter.execute(ctx -> {
            if (ctx.player != null) {
              if (!noCore) Call.setPlayerTeamEditor(ctx.player, retF);
              ctx.player.team(retF);

              data.player.sendMessage("> Seteado [accent]" + (args.length == 2 ? ctx.data.realName : "") + "[white] al team [sky]" + retF.name);
              ALog.write("Team", "@ [@] changed @ [@] to the team @", data.stripedName, data.player.uuid(), ctx.data.stripedName, ctx.player.uuid(), retF.name);
              return false;

            } else {
              ctx.unit.team(retF);
              return true;
            }
          });
          if (!filter.type.onlyPlayers) data.player.sendMessage("> Seteado [green]" + counter + "[] unidades al team [sky]" + retF.name);

        } else {
          if (!noCore) Call.setPlayerTeamEditor(target.player, ret);
          target.player.team(ret);

          data.player.sendMessage("> Seteado [accent]" + (args.length == 2 ? target.realName : "") + "[white] al team [sky]" + ret.name);
          ALog.write("Team", "@ [@] changed @ [@] to the team @", data.stripedName, data.player.uuid(), target.stripedName, target.player.uuid(), retF.name);
        }

      } else Players.err(data.player, "Team sin core, prueba con otro comando si eres administrador.");
    });

    commands.add("players", "<help|searchFilter> [page]", "Muestra la lista de jugadores", true, false, (arg, data) -> {
      String message;
      Seq<String> list = new Seq<>();
      StringBuilder builder = new StringBuilder();

      switch (arg[0]) {
        case "ban":
          if (netServer.admins.getBanned().isEmpty()) {
            data.player.sendMessage("[green]No hay jugadores baneados");
            return;
          }
          message = "\nLista de baneados : [green]" + netServer.admins.getBanned().size + ". \n[gold]-------------------------------- \n[accent]Jugadores baneados:";
          netServer.admins.getBanned().each(p -> list.add("[white] - [lightgray]Names:[accent] [[[white]"
            + p.names.toString("[accent], [white]") + "[accent]][white] - [lightgray]ID: [accent]'" + p.id + "'"
            + "[white] - [lightgray]Kicks: [accent]" + p.timesKicked + (p.admin ? "[white] | [scarlet]Admin[]" : "") + "\n"));
          break;

        case "mute":
          if (PVars.recentMutes.size == 0) {
            data.player.sendMessage("[green]No hay jugadores muteados");
            return;
          }

          message = "\nLista de muteados : [green]" + PVars.recentMutes.size + ". \n[gold]-------------------------------- \n[accent]Jugadores muteados:";
          PVars.recentMutes.each(p -> {
            PlayerInfo pl = netServer.admins.getInfoOptional(p);
            list.add("[white] - [lightgray]Names: [accent][[[white]" + pl.names.toString("[accent], [white]")
                + "[accent]][white] - [lightgray]ID: [accent]'" + p + "'"
                + (pl.admin ? "[white] | [scarlet]Admin[]" : "")
                + (Players.findByID(p).found ? "[white] | [green]Online" : "") + "\n");
          });
          break;

        case "online":
          message = "\nLista de jugadores en linea: [green]" + Groups.player.size() + "[].\n[gold]--------------------------------[]\n[accent]Conectados:";
          Groups.player.each(p -> list.add(" - [lightgray]" + TempData.get(p).realName + "[] : [accent]'" + p.uuid() + "'[]" + (p.admin ? "[white] | [scarlet]Admin[]" : "") + "\n[accent]"));
          break;

        case "admin":
          message = "\nLista de Moderadores: [green]" + netServer.admins.getAdmins().size + "[].\n[gold]--------------------------------[]\n[accent]Jugadores:";
          netServer.admins.getAdmins().each(p -> list.add("[white] - [lightgray]Names: [accent][[[white]"
            + p.names.toString("[accent], [white]") + "[accent]][white] - [lightgray]ID: [accent]'" + p.id + "'"
            + (p.banned ? "[white] | [orange]Banned" : "")
            + (Players.findByID(p.id).found ? "[white] | [green]Online" : "") + "\n"));
          break;

        case "all":
          message = "\nTodos los jugadores: [green]" + netServer.admins.getWhitelisted().size + "[].\n[gold]--------------------------------[]\n[accent]Lista:";
          netServer.admins.getWhitelisted().each(p -> list.add("[white] - [lightgray]Names: [accent][[[white]"
            + p.names.toString("[accent], [white]") + "[accent]][white] - [lightgray]ID: [accent]'" + p.id + "'"
            + (p.admin ? "[white] | [scarlet]Admin" : "")
            + (p.banned ? "[white] | [orange]Banned" : "")
            + (Players.findByID(p.id).found ? "[white] | [green]Online" : "") + "\n"));
          break;

        default: Players.err(data.player, "El argumento quedó en silla de rueda..");
        case "help":
          data.player.sendMessage("[scarlet]Argumentos disponibles: []"
            + "\n[lightgray] - [accent]ban[]: [white] Muestra los baneados"
            + "\n[lightgray] - [accent]mute[]: [white] Muestra los muteados"
            + "\n[lightgray] - [accent]online[]: [white] Muestra los conectado"
            + "\n[lightgray] - [accent]admin[]: [white] Muestra los Moderadores"
            + "\n[lightgray] - [accent]all[]: [white] Muestra todos los jugadores"
            + "\n[lightgray] - [accent]help[]: [white] hola, ya lo ejecutaste");
          return;
      }

      if (arg.length == 2 && !Strings.canParseInt(arg[1])) {
        data.player.sendMessage("[scarlet]'page' debe ser un número.");
        return;
      }

      int lines = 15, page = arg.length == 2 ? Strings.parseInt(arg[1]) : 1, pages = Mathf.ceil(list.size / lines);
      if (list.size % lines != 0) pages++;

      if (page > pages || page < 1) {
        data.player.sendMessage("[scarlet]'page' debe ser un número entre[orange] 1[] y[orange] " + pages + "[].");
        return;
      }

      data.player.sendMessage(message + "[orange] Page [lightgray]" + page + "[gray]/[lightgray]" + pages + "[accent]:");
      for (int i=(page - 1) * lines; i < lines * page; i++) {
        try { builder.append(list.get(i)); } 
        catch (IndexOutOfBoundsException e) { break; }
      }

      data.player.sendMessage(builder.toString());
    });

    commands.add("kill", "[filter|username...]", "Kill a jugadores o unidades", true, false, (arg, data) -> {
      if (arg.length == 0) {
        data.player.unit().kill();
        data.player.sendMessage("[green]Te mataste a ti mismo, pendeja");

      } else {
        FilterSearchReponse reponse = ArgsFilter.hasFilter(data.player, arg);

        if (reponse.reponse != Reponses.notFound && reponse.sendIfError()) return;
        else if (reponse.reponse == Reponses.found) {
          int counter = reponse.execute(ctx -> {
            ctx.unit.kill();
            if (ctx.type == FilterType.random || ctx.type == FilterType.randomUnit || ctx.type == FilterType.trigger) {
              data.player.sendMessage(Strings.format(ctx.type.formatedDesc, "[green]Killed",
                  ctx.type == FilterType.random ? ctx.data.realName : ctx.unit.type.name));
              ALog.write("Kill", "@ [@] @", data.stripedName, data.player.uuid(), Strings.format(ctx.type.formatedDesc, "killed", 
                ctx.type == FilterType.random ? ctx.data.stripedName : ctx.unit.type.name));
            }
          });

          if (reponse.type != FilterType.random || reponse.type != FilterType.randomUnit || reponse.type != FilterType.trigger) {
            data.player.sendMessage(Strings.format(reponse.type.formatedDesc, "[green]Killed[orange]", counter + "[]", "[accent]" + data.player.team().name));
            ALog.write("Kill", "@ [@] @", data.stripedName, data.player.uuid(), Strings.format(reponse.type.formatedDesc, "killed", counter, data.player.team().name));
          }

        } else {
          TempData other = Players.findByName(arg).data;

          if (other != null) {
            other.player.unit().kill();
            data.player.sendMessage("[green]Killed [accent]" + other.realName);
            ALog.write("Kill", "@ [@] killed @ [@]", data.stripedName, data.player.uuid(), other.stripedName, other.player.uuid());

          } else Players.errNotOnline(data.player);
        }
      }
    });

    commands.add("core", "[small|medium|big] [teamName|~]", "Coloca cores", true, false, (arg, data) -> {
      if (data.spectate()) {
        Players.err(data.player, "Sal del modo vanish!");
        return;
      }

      mindustry.world.Block core = Blocks.coreShard;
      Team team = data.player.team();

      if (arg.length > 0) {
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
            Players.err(data.player, "No hay un core con el name '@'", arg[0]);
            return;
        }
      }

      if (arg.length == 2 && !arg[1].equals("~")) {
        team = Players.findTeam(arg[1]);

        if (team == null) {
          StringBuilder builder = new StringBuilder();

          Players.err(data.player, "Team no encontrado! []\nteams disponible: ");
          for (Team teamList : Team.baseTeams) builder.append(" - [accent]" + teamList.name + "[]\n");
          data.player.sendMessage(builder.toString());
          return;
        }
      }

      Call.constructFinish(data.player.tileOn(), core, data.player.unit(), (byte) 0, team, false);
      data.player.sendMessage("[green]Core colocado" + (arg.length == 2 ? "al team [accent]" + team.name : ""));
      ALog.write("Core", "@ [@] build a @ at @,@ for team @", data.stripedName, data.player.uuid(), core.name, data.player.tileX(), data.player.tileY(), team.name);
    });

    commands.add("tp", "<filter|name|x,y> [~|to_name|x,y...]", "Te tepea al jugador", true, false, (arg, data) -> {
      int[] co = { data.player.tileX(), data.player.tileY() };
      TempData target = data;
      Search result = null;
      FilterSearchReponse filter = ArgsFilter.hasFilter(data.player, arg);
      Seq<String> newArg = Seq.with(arg);

      if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
      else if (filter.reponse == Reponses.found) newArg.remove(0);
      else {
        result = new Search(arg, data);
        newArg = Seq.with(result.rest);

        if (result.error) return;
        else if (result.XY == null) co = new int[] { result.player.player.tileX(), result.player.player.tileY() };
        else co = result.XY;
      }

      if (newArg.isEmpty() && filter.reponse == Reponses.found) {
        Players.err(data.player, "2 arguments are required to use filters");
        return;

      } else if (!newArg.isEmpty()) {
        if (String.join(" ", newArg).equals("~")) {
          if (result != null && result.XY != null) {
            data.player.sendMessage("[scarlet]No te puedes tepear a una coordenada de una coordenada de la coordenada [lightgray]No tiene lógica XD.");
            return;
          }

        } else if (filter.reponse == Reponses.found) {
          result = new Search(newArg.toArray(String.class), data);

          if (result.error) return;
          else if (result.XY == null) co = new int[] { result.player.player.tileX(), result.player.player.tileY() };
          else co = result.XY;

        } else {
          target = result.player;

          if (result.XY == null) {
            result = new Search(newArg.toArray(String.class), data);

            if (result.error) return;
            else if (result.XY == null) co = new int[] { result.player.player.tileX(), result.player.player.tileY() };
            else co = result.XY;

          } else {
            data.player.sendMessage("[scarlet]No te puedes tepear a una coordenada de una coordenada de la coordenada [lightgray]No tiene lógica XD.");
            return;
          }
        }
      }

      if (co[0] > world.width() || co[0] < 0 || co[1] > world.height() || co[1] < 0) {
        data.player.sendMessage("[scarlet]La coordenada es muy grande, como mi pija. Max: [orange]" + world.width() + "[]x[orange]" + world.height() + "[]. Min: [orange]0[]x[orange]0[].");
        return;
      }

      int x = co[0] * 8, y = co[1] * 8;
      if (filter.reponse == Reponses.found) {
        int counter = filter.execute(ctx -> {

          Players.tpPlayer(ctx.unit, x, y);
          if (ctx.player != null) {
            data.player.sendMessage("[green]Tepeado [accent]" + ctx.data.realName + "[green] a [accent]" + x / 8 + "[green]x[accent]" + y / 8 + "[green].");
            ALog.write("Tp", "@ [@] teleported @ [@] at @,@", data.stripedName, data.player.uuid(), ctx.data.stripedName, ctx.player.uuid(), x / 8, x / 8);
            return false;
          } else return true;
        });

        if (!filter.type.onlyPlayers)
          data.player.sendMessage("[green]Tepeado [orange]" + counter + "[] unidades a [accent]" + co[0] + "[]x[accent]" + co[1] + "[].");

      } else {
        Players.tpPlayer(target.player.unit(), x, y);
        if (arg.length == 2) data.player.sendMessage("[green]Tepeado [accent]" + target.realName + "[green] a [accent]" + co[0]
          + "[green]x[accent]" + co[1] + "[green].");
        else data.player.sendMessage("[green]Tepeado [accent]" + co[0] + "[]x[accent]" + co[1] + "[].");
        ALog.write("Tp", "@ [@] teleported @ [@] at @,@", data.stripedName, data.player.uuid(), target.stripedName, target.player.uuid(), co[0], co[1]);
      }
    });

    commands.add("spawn", "<unit> [count] [filter|x,y|username] [teamName|~...]", "Spawn de unidades", true, false, (arg, data) -> {
      mindustry.type.UnitType unit = content.units().find(b -> b.name.equals(arg[0]));
      Player target = data.player;
      Team team = target.team();
      int count = 1, x = (int) target.x, y = (int) target.y;
      boolean thisTeam;
      Seq<String> newArg = Seq.with(arg);
      newArg.remove(0);
      FilterSearchReponse filter = null;

      if (unit == null) {
        data.player.sendMessage("[scarlet]Unit disponible: []" + content.units().toString("[scarlet], []"));
        return;
      }

      if (arg.length > 1) {
        if (!Strings.canParseInt(newArg.get(0))) {
          Players.err(data.player, "'count' pone un número pao.");
          return;
        } else count = Strings.parseInt(newArg.get(0));
        newArg.remove(0);

        if (!newArg.isEmpty()) {
          filter = ArgsFilter.hasFilter(target, newArg.toArray(String.class));

          if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
          else if (filter.reponse == Reponses.found) newArg.remove(0);
          else {
            Search result = new Search(newArg.toArray(String.class), data);
            newArg.set(Seq.with(result.rest));

            if (result.error) return;
            else target = result.player.player;

            if (result.XY == null) {
              x = (int) target.x;
              y = (int) target.y;
            } else {
              x = result.XY[0] * 8;
              y = result.XY[1] * 8;
            }
          }
        }

        if (!newArg.isEmpty()) {
          if (!newArg.get(0).equals("~")) {
            team = Players.findTeam(newArg.get(0));

            if (team == null) {
              StringBuilder builder = new StringBuilder();

              Players.err(data.player, "Team no encontrado! []\notra ve esta weá, EQUIPO DISPONIBLE: ");
              for (Team teamList : Team.baseTeams) builder.append(" - [accent]" + teamList.name + "[]\n");
              data.player.sendMessage(builder.toString());
              return;
            }
          }
          newArg.remove(0);
          thisTeam = true;

        } else {
          team = target.team();
          thisTeam = false;
        }

        if (!newArg.isEmpty()) {
          Players.err(data.player, "¡Demasiados argumentos! me dará ansiedad ctm");
          return;
        }

      } else thisTeam = true;

      if (team.cores().isEmpty()) Players.err(data.player, "The es <el> pero sin la T y la Hache xd. El [accent]" + team.name + "[] equipo no tiene core así que las unidades no pueden aparecer");
      else {
        if (filter != null && filter.reponse == Reponses.found) {
          Team teamF = team;
          int countF = count;

          filter.execute(ctx -> {
            int counter = 0;
            for (int i=0; i < countF; i++) {
              if (unit.spawn(thisTeam ? teamF : ctx.unit.team, ctx.unit.x, ctx.unit.y).isValid()) counter++;
            }

            data.player.sendMessage("[green]Spawneaste [accent]" + counter + " " + unit
                + " []a [accent]" + teamF + " []team en [orange]" + ctx.unit.tileX() + "[white],[orange]" + ctx.unit.tileY());
            ALog.write("Spawn", "@ [@] spawn @ @ at @,@", data.stripedName, data.player.uuid(), counter, unit.name, ctx.unit.tileX(), ctx.unit.tileY());
          });

        } else {
          int counter = 0;
          for (int i=0; i < count; i++) {
            if (unit.spawn(team, x, y).isValid()) counter++;
          }

          data.player.sendMessage("[green]Spawneaste [accent]" + counter + " " + unit.name + " []a [accent]"
              + team + " []team en [orange]" + x / 8 + "[white],[orange]" + y / 8);
          ALog.write("Spawn", "@ [@] spawn @ @ at @,@", data.stripedName, data.player.uuid(), counter, unit.name, x / 8, y / 8);
        }
      }
    });

    commands.add("godmode", "[filter|username...]", "[scarlet][God][]: [gold]Divino como gei.", true, false, (arg, data) -> {
      TempData target = data;

      if (arg.length == 1) {
        FilterSearchReponse filter = ArgsFilter.hasFilter(data.player, arg);

        if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
        else if (filter.reponse == Reponses.found) {
          int counter = filter.execute(ctx -> {
            if (ctx.player != null) {
              TempData t = TempData.get(ctx.player);

              t.inGodmode = !t.inGodmode;
              ctx.unit.health = t.inGodmode ? Integer.MAX_VALUE : ctx.unit.maxHealth;

              data.player.sendMessage("[gold]God mode está [green]" + (t.inGodmode ? "habilitado" : "apagado") + "[] para [accent]" + ctx.data.realName);
              ctx.player.sendMessage((t.inGodmode ? "[green]Te han dado el godmode" : "[red]y ahora te quitan el godmode")
                + " by [accent]" + data.realName);
              ALog.write("Godmode", "@ [@] @ @ [@] in godmode", data.stripedName, data.player.uuid(),
                ctx.data.inGodmode ? "put" : "remove", ctx.data.stripedName, ctx.player.uuid());
              return false;

            } else {
              ctx.unit.health = ctx.unit.health == Integer.MAX_VALUE ? ctx.unit.maxHealth : Integer.MAX_VALUE;
              return true;
            }
          });

          if (!filter.type.onlyPlayers) {
            data.player.sendMessage("[gold]God mode change for [green]" + counter + "[] units");
            ALog.write("Godmode", "@ [@] change the godmode of @ units", data.stripedName, data.player.uuid(), counter);
          }
          return;

        } else {
          target = Players.findByName(arg).data;

          if (target == null) {
            Players.errNotOnline(data.player);
            return;
          }
        }
      }

      target.inGodmode = !target.inGodmode;
      target.player.unit().health = target.inGodmode ? Integer.MAX_VALUE : target.player.unit().maxHealth;

      data.player.sendMessage("[gold]God mode está [green]" + (target.inGodmode ? "habilitado" : "apagado") + (arg.length == 0 ? "" : "[] para [accent]" + target.realName));
      if (arg.length == 1)
        target.player.sendMessage((target.inGodmode ? "[green]Te han dado el godmode" : "[red]y ahora te quitan el godmode")
          + " by [accent]" + data.realName);
      ALog.write("Godmode", "@ [@] @ @ [@] in godmode", data.stripedName, data.player.uuid(), target.inGodmode ? "put" : "remove", target.stripedName, target.player.uuid());
    });

    commands.add("chat", "[on|off]", "Habilita/bloquea el chat", true, false, (arg, data) -> {
      if (arg.length == 1) {
        if (data.spectate()) {
          Players.err(data.player, "Sal del modo vanish para usar esto");
          return;

        } else if (Strings.choiseOn(arg[0])) {
          if (PVars.chat) {
            Players.err(data.player, "Bloquea primero");
            return;
          }
          PVars.chat = true;

        } else if (Strings.choiseOff(arg[0])) {
          if (!PVars.chat) {
            Players.err(data.player, "Habilita primero");
            return;
          }
          PVars.chat = false;

        } else {
          Players.err(data.player, "El argumento quedó en silla de rueda.[] \n - El chat está actualmente [accent]@[].", PVars.chat ? "habilitado" : "bloqueado");
          return;
        }

        Log.info("Chat @ by @.", PVars.chat ? "habilitado" : "bloqueado", data.realName);
        Call.sendMessage("\n[gold]--------------------\n[yellow]/!\\[slate] Chat " + (PVars.chat ? "habilitado" : "bloqueado")
          + " por " + data.realName + "[yellow]! \n[gold]--------------------\n");
        ALog.write("Chat", "@ [@] @ the chat", data.stripedName, data.player.uuid(), PVars.chat ? "habilitado" : "bloqueado");

      } else data.player.sendMessage("El chat actualmente esta " + (PVars.chat ? "habilitado." : "bloqueado."));
    });

    commands.add("reset", "<filter|username|ID...>", "Resetea los datos no importantes de los jugadores (rainbow, GodMode, muted, ...)", true, false, (arg, data) -> {
      FilterSearchReponse filter = ArgsFilter.hasFilter(data.player, arg);

      if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
      else if (filter.reponse == Reponses.found) {
        if (Players.errFilterAction("reset", filter, false)) return;

        filter.execute(ctx -> {
          TempData.get(ctx.player).reset();
          data.player.sendMessage("[green]Realizado... " + ctx.data.realName);
          ALog.write("Reset", "@ [@] restablecer todos los datos del juego de @ [@]", data.stripedName, data.player.uuid(), ctx.data.stripedName, ctx.player.uuid());
        });

      } else {
        Players result = Players.findByNameOrID(arg);

        if (result.found) {
          TempData.get(result.player).reset();
          data.player.sendMessage("[green]Realizado... " + result.data.realName);
          ALog.write("Reset", "@ [@] restablecer todos los datos del juego de @ [@]", data.stripedName, data.player.uuid(), result.data.stripedName, result.player.uuid());

        } else Players.errNotOnline(data.player);
      }
    });

    commands.add("mute", "<filter|username|ID> [reason...]", "Para meterle un calcetín en la jeta", true, false, (arg, data) -> {
      FilterSearchReponse filter = ArgsFilter.hasFilter(data.player, arg);
      String reason;

      if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
      else if (filter.reponse == Reponses.found) {
        if (Players.errFilterAction("mute", filter, false)) return;

        reason = String.join(" ", filter.rest);
        filter.execute(ctx -> {
          TempData t = TempData.get(ctx.player);

          if (!t.isMuted) {
            t.isMuted = true;
            PVars.recentMutes.add(ctx.player.uuid());

            Call.sendMessage("\n[gold]--------------------\n[yellow]/!\\" + t.nameColor + t.realName
              + "[slate] le han metido un calcetín en la boca."
              + "\nMotivo: [white]" + (reason.isBlank() ? "<unknown>" : reason) + "\n[gold]--------------------\n");
            Call.infoMessage(t.player.con, "Has sido silenciado/a con un calcetín [lightgray](por " + data.realName + "[lightgray]) \n[scarlet]Motivo: []"
              + (arg.length == 2 && !reason.isBlank() ? reason : "<unknown>"));
            ALog.write("Mute", "@ [@] muted @ [@] for reason: @", data.stripedName, data.player.uuid(),
              ctx.data.stripedName, ctx.player.uuid(), reason.isBlank() ? "<unknown>" : reason);

          } else Players.err(data.player, "[white]" + t.realName + "[scarlet] Ya está silenciado!");
        });

      } else {
        Players result = Players.findByNameOrID(arg);

        if (result.found) {
          if (!result.data.isMuted) {
            reason = String.join(" ", result.rest);
            result.data.isMuted = true;
            PVars.recentMutes.add(result.player.uuid());

            Call.sendMessage("\n[gold]--------------------\n[yellow]/!\\" + result.data.nameColor + result.data.realName
              + "[slate] le han metido un calcetín en la boca.\nMotivo: [white]"
              + (reason.isBlank() ? "<unknown>" : reason) + "\n[gold]--------------------\n");
            Call.infoMessage(result.player.con, "Has sido silenciado/a con un calcetín [lightgray](por " + data.realName + "[lightgray]) \n[scarlet]Motivo: []"
              + (reason.isBlank() ? "<unknown>" : reason));
            ALog.write("Mute", "@ [@] muted @ [@] for reason: @", data.stripedName, data.player.uuid(),
                result.data.stripedName, result.player.uuid(), reason.isBlank() ? "<unknown>" : reason);

          } else Players.err(data.player, "[white]" + result.data.realName + "[scarlet] Ya está silenciado!");
        } else Players.err(data.player, "No se encontró a nadie con la ID o name...");
      }
    });

    commands.add("unmute", "<filter|username|ID...>", "Le quitas el calcetín", true, false, (arg, data) -> {
      FilterSearchReponse filter = ArgsFilter.hasFilter(data.player, arg);

      if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
      else if (filter.reponse == Reponses.found) {
        if (Players.errFilterAction("unmute", filter, false)) return;

        filter.execute(ctx -> {
          TempData t = TempData.get(ctx.player);

          if (t.isMuted) {
            t.isMuted = false;
            PVars.recentMutes.remove(t.player.uuid());

            Call.infoMessage(t.player.con, "Te han qutado el calcetín de la boca. [lightgray](por " + data.realName + "[lightgray])");
            Players.info(data.player, "Player unmuted");
            ALog.write("Unmute", "@ [@] unmuted @ [@]", data.stripedName, data.player.uuid(), ctx.data.stripedName, ctx.player.uuid());

          } else Players.err(data.player, "[white]" + t.realName + "[scarlet] no está muteado");
        });

      } else {
        Players target = Players.findByNameOrID(arg);

        if (target.found) {
          if (target.data.isMuted) {
            target.data.isMuted = false;
            PVars.recentMutes.remove(target.player.uuid());

            Call.infoMessage(target.player.con, "Te han qutado el calcetín de la boca. [lightgray](por " + data.realName + "[lightgray])");
            Players.info(data.player, "Player unmuted");
            ALog.write("Unmute", "@ [@] unmuted @ [@]", data.stripedName, data.player.uuid(), target.data.stripedName, target.player.uuid());

          } else Players.err(data.player, "[white]" + target.data.realName + "[scarlet] No está muteado");

        } else if (PVars.recentMutes.contains(arg[0])) {
          PVars.recentMutes.remove(arg[0]);
          Players.info(data.player, "Player unmuted");
          ALog.write("Unmute", "@ [@] unmuted @ [@] by his ID", data.stripedName, data.player.uuid(), netServer.admins.getInfoOptional(arg[0]).lastName, arg[0]);

        } else Players.err(data.player, "Este jugador no existe o no extá conectado [lightgray]Si el jugador está muteado usa su ID para quitarle el mute.");
      }
    });

    commands.add("kick", "<filter|username|ID> [reason...]", "con esto le paeas el culo, utiliza el argumento reason para dale a conocer el Motivo", true, false, (arg, data) -> {
      FilterSearchReponse filter = ArgsFilter.hasFilter(data.player, arg);
      String reason;

      if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
      else if (filter.reponse == Reponses.found) {
        if (Players.errFilterAction("kick", filter, false)) return;

        reason = String.join(" ", filter.rest);
        filter.execute(ctx -> {
          Call.sendMessage("\n[gold]--------------------\n[yellow]/!\\" + ctx.data.nameColor + ctx.data.realName
              + "[slate] ha sido kickeado del servidor."
              + "\nMotivo: [white]" + (reason.isBlank() ? "<unknown>" : reason) + "\n[gold]--------------------\n");
          ALog.write("Kick", "@ [@] kicked @ [@] for the reason: @", data.stripedName, data.player.uuid(),
              ctx.data.stripedName, ctx.player.uuid(), reason.isBlank() ? "<unknown>" : reason);
          
          if (reason.isBlank()) ctx.player.kick(KickReason.kick);
          else ctx.player.kick("Has sido kickeado del servidor!\n[scarlet]Motivo: []" + reason);
        });

      } else {
        Players result = Players.findByNameOrID(arg);

        if (result.found) {
          reason = String.join(" ", result.rest);

          Call.sendMessage("\n[gold]--------------------\n[yellow]/!\\" + result.data.nameColor
              + result.data.realName + "[slate] ha sido kickeado del servidor."
              + "\nMotivo: [white]" + (reason.isBlank() ? "<unknown>" : reason) + "\n[gold]--------------------\n");
          ALog.write("Kick", "@ [@] kicked @ [@] for the reason: @", data.stripedName, data.player.uuid(),
              result.data.stripedName, result.player.uuid(), reason.isBlank() ? "<unknown>" : reason);
          if (reason.isBlank()) result.player.kick(KickReason.kick);
          else result.player.kick("Has sido kickeado del servidor\n[scarlet]Motivo: []" + reason);

        } else Players.err(data.player, "No se pudo encontrar a nadie con ese nombre o ID...");
      }
    });

    commands.add("pardon", "<ID>", "Este comando elimina el kick hecho por jugadores, debes obtener su ID", true, false, (arg, data) -> {
      PlayerInfo info = netServer.admins.getInfoOptional(arg[0]);

      if (info != null) {
        info.lastKicked = 0;
        Players.info(data.player, "Pardoned player: [accent]" + info.lastName);
        ALog.write("Pardon", "@ [@] pardonned @ [@] by his ID", data.stripedName, data.player.uuid(), info.lastName, info.id);

      } else Players.err(data.player, "No se ha encontrado a nadie.");
    });

    commands.add("ban", "<filter|username|ID> [reason...]", "No olvides especificar la razón del baneo", true, false, (arg, data) -> {
      FilterSearchReponse filter = ArgsFilter.hasFilter(data.player, arg);
      String reason;

      if (filter.reponse != Reponses.notFound && filter.sendIfError()) return;
      else if (filter.reponse == Reponses.found) {
        if (Players.errFilterAction("ban", filter, false)) return;

        reason = String.join(" ", filter.rest);
        filter.execute(ctx -> {
          if (!ctx.player.admin) {
            netServer.admins.banPlayer(ctx.player.uuid());
            Call.sendMessage("\n[gold]--------------------\n[scarlet]/!\\ " + ctx.data.nameColor + ctx.data.realName
                + "[slate] fue azotado con el mazo del [scarlet]BAN."
                + "\nMotivo: [white]" + (reason.isBlank() ? "<unknown>" : reason) + "\n[gold]--------------------\n");
            ALog.write("Ban", "@ [@] banned @ [@] for reason: @", data.stripedName, data.player.uuid(),
                ctx.data.stripedName, ctx.player.uuid(), reason.isBlank() ? "<unknown>" : reason);
            if (reason.isBlank()) ctx.player.kick(KickReason.banned);
            else {
              ctx.player.kick("[slate]Si fue injustificado ingresa a nuestro discord\nFuiste baneado del servidor!\n[scarlet]Motivo: []" + reason);
              PVars.bansReason.put(ctx.player.uuid(), reason);
            }

          } else Players.err(data.player, "Este weón es un Moderador, baneate el ojete!");
        });
        saveSettings();

      } else {
        Players result = Players.findByNameOrID(arg);

        if (result.found) {
          if (!result.player.admin) {
            reason = String.join(" ", result.rest);

            netServer.admins.banPlayer(result.player.uuid());
            Call.sendMessage("\n[gold]--------------------\n[scarlet]/!\\ " + result.data.nameColor + result.data.realName
              + "[slate] fue azotado por el mazo del [scarlet]BAN.\nMotivo: [white]"
              + (reason.isBlank() ? "<unknown>" : reason) + "\n[gold]--------------------\n");
            ALog.write("Ban", "@ [@] banned @ [@] for reason: @", data.stripedName, data.player.uuid(),
                result.data.stripedName, result.player.uuid(), reason.isBlank() ? "<unknown>" : reason);
            if (reason.isBlank()) result.player.kick(KickReason.banned);
            else {
              result.player.kick("[slate]Si fue injustificado ingresa a nuestro discord\nFuiste baneado del servidor!\n[scarlet]Motivo: []" + reason);
              PVars.bansReason.put(result.player.uuid(), reason);
              saveSettings();
            }

          } else Players.err(data.player, "Este weón es un Moderador, baneate el ojete!");
        } else Players.err(data.player, "Invalido, como tu awela.");
      }
    });

    commands.add("unban", "<ID>", "Le quitas el ban mediante la ID", true, false, (arg, data) -> {
      if (netServer.admins.unbanPlayerID(arg[0])) {
        PlayerInfo info = netServer.admins.getInfoOptional(arg[0]);

        Players.info(data.player, "Unbanned player: [accent]" + arg[0]);
        ALog.write("Unban", "@ [@] unbaned @ [@] by his ID", data.stripedName, data.player.uuid(), info.lastName, info.id);
        PVars.bansReason.remove(arg[0]);
        saveSettings();

      } else Players.err(data.player, "Esta ID no está baneada!");
    });
  }

  private void saveSettings() {
    Core.settings.put("Tags", PVars.tags);
    Core.settings.putJson("PlayersTags", PVars.playerTags);
    Core.settings.put("AutoPause", PVars.autoPause);
    Core.settings.putJson("BansReason", PVars.bansReason);
  }

  private Team getPosTeamLoc(Player p) {
    Team newTeam = p.team();

    // search a possible team
    int c_index = java.util.Arrays.asList(Team.baseTeams).indexOf(newTeam), i = (c_index + 1) % 6;
    while (i != c_index) {
      if (Team.baseTeams[i].cores().size > 0) {
        newTeam = Team.baseTeams[i];
        break;
      }
      i = (i + 1) % Team.baseTeams.length;
    }

    if (newTeam == p.team()) return null;
    else return newTeam;
  }

}
