package filter;

import arc.func.Func;
import arc.struct.Seq;

import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;


public enum FilterType {
  players("a", "Todos los jugadores", "@ @ jugador/es", true, t -> Seq.with(Groups.player).map(p -> p.unit())),
  trigger("p", "Aplica solo a ti", "@ solo a ti", true, t -> Seq.with(t.unit())),
  random("r", "Jugador aleatorio", "@ @", true, t -> Seq.with(Seq.with(Groups.player).random().unit())),
  randomUnit("ru", "Unicad aleatoria", "@ a @", false, t -> Seq.with(Seq.with(Groups.unit).random())),
  units("e", "Todas las unidades y jugadores", "@ @ unidad y jugadores", false, t -> Seq.with(Groups.unit)),
  withoutPlayers("u", "Todas las unidades pero ignorando a jugadores", "@ @ unidades", false, t -> Seq.with(Groups.unit).filter(u -> u.getPlayer() == null)),
  team("t", "Equipo: Todas las unidades y jugadores", "@ @ unidad y jugadores en el equipo @", false, t -> Seq.with(Groups.unit).filter(u -> u.team.equals(t.team()))),
  playersInTeam("ta", "Equipo: Todos los jugadores", "@ @ jugador/es en el equipo @", true, t -> Seq.with(Groups.player).filter(p -> p.team().equals(t.team())).map(p -> p.unit())),
  withoutPlayersInTeam("tu", "Equipo: Todas las unidades pero ignorando jugadores", "@ @ unidades en el equipo @", false, t -> Seq.with(Groups.unit).filter(u -> u.getPlayer() == null && u.team.equals(t.team())));

  public static String prefix = "@";
  public final Func<Player, Seq<Unit>> filter;
  public final String desc, formatedDesc;
  public final boolean onlyPlayers;
  private String value;
  
  private FilterType(String value, String desc, String format, boolean onlyPlayers, Func<Player, Seq<Unit>> filter) {
    this.value = value;
    this.desc = desc;
    this.formatedDesc = format;
    this.onlyPlayers = onlyPlayers;
    this.filter = filter;
  }

  public String getValue() {
    return prefix + this.value;
  }

  public enum Reponses {
    found,
    prefixFound,
    notFound,
    invalidArguments,
    disabled,
    permsDenied
  }
}
