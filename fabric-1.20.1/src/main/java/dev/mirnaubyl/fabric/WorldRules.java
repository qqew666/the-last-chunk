package dev.mirnaubyl.fabric;

import dev.mirnaubyl.RoundClock;
import net.minecraft.nbt.*;
import net.minecraft.world.PersistentState;
import net.minecraft.server.MinecraftServer;
import java.util.*;

public final class WorldRules extends PersistentState {
    public RoundClock clock = new RoundClock(0);
    public final Set<String> blocks = new LinkedHashSet<>();
    public boolean water, food;
    public int round;
    public String offered = "minecraft:stone";
    public static WorldRules get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(WorldRules::read, WorldRules::new, "mirnaubyl");
    }
    private static WorldRules read(NbtCompound n) {
        WorldRules r = new WorldRules();
        r.clock = new RoundClock(n.getInt("elapsed"));
        r.water = n.getBoolean("water"); r.food = n.getBoolean("food");
        r.round = n.getInt("round");
        if(n.contains("offered")) r.offered = n.getString("offered");
        NbtList list = n.getList("blocks", NbtElement.STRING_TYPE);
        for(int i=0;i<list.size();i++) r.blocks.add(list.getString(i));
        return r;
    }
    @Override public NbtCompound writeNbt(NbtCompound n) {
        n.putInt("elapsed",clock.elapsed()); n.putBoolean("water",water); n.putBoolean("food",food);
        n.putInt("round",round); n.putString("offered",offered);
        NbtList list = new NbtList(); blocks.forEach(s -> list.add(NbtString.of(s))); n.put("blocks",list);
        return n;
    }
}
