package util;

import static mindustry.Vars.state;

import arc.util.Log;

import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.maps.Map;
import mindustry.net.WorldReloader;

public class RTV {
	public RTV(Map map, mindustry.game.Team winner) {
		Map temp = state.map;
		try { 
			state.map = null;
			arc.Events.fire(new mindustry.game.EventType.GameOverEvent(null)); 
			Call.gameOver(winner);
		} catch (NullPointerException e) {}
		state.map = temp;
		
        if (state.rules.waves) Log.info("¡GG! Ola alcanzada @ con @ jugadores en linea en el mapa @.", state.wave, Groups.player.size(), Strings.capitalize(Strings.stripColors(state.map.name())));
        else Log.info("¡GG! Votaron para cambiar de mapa con @ jugadores en linea en el mapa @.", Groups.player.size(), Strings.capitalize(Strings.stripColors(state.map.name())));

        //set next map to be played
        Call.infoMessage(Strings.format("@![]\n \nMapa siguiente:[accent] @ [white] por [accent]@ [white].\nEl nuevo juego comienza en 10 segundos.", 
        	state.rules.pvp ? "[accent]Vote to change map" : "[scarlet]¡GG!", map.name(), map.author()));

        state.gameOver = true;
        Call.updateGameOver(winner);
        Log.info("El siguiente mapa es @.", Strings.stripColors(map.name()));

        arc.util.Timer.schedule(() -> {
        	try {
        		WorldReloader reloader = new WorldReloader();
                reloader.begin();

                mindustry.Vars.logic.play();
                reloader.end();

                state.rules = state.map.applyRules(state.rules.mode());
        		
        	} catch (mindustry.maps.MapException e) {
        		Log.err(e.map.name() + ": " + e.getMessage());
        		mindustry.Vars.net.closeServer();
        	}
        	
        }, 10);
	}
}
