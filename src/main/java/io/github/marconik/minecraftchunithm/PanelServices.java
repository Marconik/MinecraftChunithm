package io.github.marconik.minecraftchunithm;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Extension points: providers run on the server thread and must not block on network or file pickers. */
public final class PanelServices {
    private PanelServices(){}
    public interface PlayerInfoProvider { List<NameDisplayData> choices(ServerPlayer player); }
    public interface SongProvider { List<SongDisplayData> choices(ServerPlayer player); }
    private static PlayerInfoProvider players=PlayerLibrary::displayChoices;
    private static SongProvider songs=p->List.of();
    public static void setPlayerInfoProvider(PlayerInfoProvider provider){players=java.util.Objects.requireNonNull(provider);}
    public static void setSongProvider(SongProvider provider){songs=java.util.Objects.requireNonNull(provider);}
    public static List<NameDisplayData> playerChoices(ServerPlayer p){return List.copyOf(players.choices(p));}
    public static List<SongDisplayData> songChoices(ServerPlayer p){return List.copyOf(songs.choices(p));}
}
