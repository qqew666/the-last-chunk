package dev.thelastchunk.fabric;

import dev.thelastchunk.RoundClock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.chunk.WorldChunk;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

public final class TheLastChunk implements ModInitializer {
    public static final Identifier SYNC = new Identifier("mirnaubyl","sync"), CHOOSE = new Identifier("mirnaubyl","choose");
    private static final Map<MinecraftServer, TheLastChunk> RUNNING = new IdentityHashMap<>();
    private WorldRules rules;
    private final Set<WorldChunk> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<Sweep> sweeps = new ArrayDeque<>();
    private int ticks;
    private static final String[] CANDIDATES = {"stone","dirt","grass_block","sand","gravel","oak_log","oak_leaves","deepslate","netherrack","end_stone","cobblestone","andesite","diorite","granite","coal_ore","iron_ore","copper_ore","gold_ore","redstone_ore","diamond_ore","clay","snow_block","ice","terracotta","basalt","blackstone","tuff","calcite","spruce_log","birch_log","oak_planks","glass","obsidian","sandstone","moss_block"};
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { TheLastChunk game = RUNNING.computeIfAbsent(s, key -> new TheLastChunk()); game.rules=WorldRules.get(s); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> RUNNING.remove(s));
        ServerChunkEvents.CHUNK_LOAD.register((w,c) -> { TheLastChunk g=RUNNING.get(w.getServer()); if(g!=null) {g.loaded.add(c); g.sweeps.add(new Sweep(w,c));} });
        ServerChunkEvents.CHUNK_UNLOAD.register((w,c) -> { TheLastChunk g=RUNNING.get(w.getServer()); if(g!=null) g.loaded.remove(c); });
        // Initial spawn chunks can load before SERVER_STARTED.
        ServerLifecycleEvents.SERVER_STARTING.register(s -> { TheLastChunk game=new TheLastChunk(); RUNNING.put(s,game); });
        ServerTickEvents.END_SERVER_TICK.register(s -> { TheLastChunk g=RUNNING.get(s); if(g!=null && g.rules!=null) g.tick(s); });
        ServerPlayConnectionEvents.JOIN.register((h,sender,s) -> { TheLastChunk g=RUNNING.get(s); if(g!=null) g.sync(h.player); });
        ServerPlayNetworking.registerGlobalReceiver(CHOOSE,(s,p,h,b,response) -> {
            int round=b.readVarInt(), option=b.readVarInt();
            s.execute(() -> { TheLastChunk g=RUNNING.get(s); if(g!=null && !p.isSpectator()) g.choose(s,round,option); });
        });
        CommandRegistrationCallback.EVENT.register((d,a,e) -> d.register(literal("worldchoice").requires(src -> src.hasPermissionLevel(2))
            .then(literal("now").executes(ctx -> {TheLastChunk g=RUNNING.get(ctx.getSource().getServer());g.rules.clock=new RoundClock(RoundClock.INTERVAL);g.rules.markDirty();g.syncAll(ctx.getSource().getServer());return 1;}))));
    }
    public static BlockState filter(ServerWorld world, BlockState state) {
        TheLastChunk g=RUNNING.get(world.getServer());
        if(g==null || g.rules==null) return state;
        if(g.rules.blocks.contains(Registries.BLOCK.getId(state.getBlock()).toString())) return Blocks.AIR.getDefaultState();
        if(g.rules.food && (state.isOf(Blocks.CAKE) || state.isOf(Blocks.CARROTS) || state.isOf(Blocks.POTATOES) || state.isOf(Blocks.BEETROOTS) || state.isOf(Blocks.WHEAT) || state.isOf(Blocks.MELON) || state.isOf(Blocks.PUMPKIN) || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.COCOA))) return Blocks.AIR.getDefaultState();
        if(g.rules.water && state.isOf(Blocks.WATER_CAULDRON)) return Blocks.CAULDRON.getDefaultState();
        if(g.rules.water && state.getFluidState().isIn(FluidTags.WATER)) {
            if(state.contains(Properties.WATERLOGGED)) return state.with(Properties.WATERLOGGED,false);
            return Blocks.AIR.getDefaultState();
        }
        return state;
    }
    private void tick(MinecraftServer s) {
        ticks++;
        if(!s.getPlayerManager().getPlayerList().isEmpty() && !rules.clock.ready()) rules.clock.tick();
        if(ticks%20==0) {
            rules.markDirty(); syncAll(s);
            for(ServerPlayerEntity p:s.getPlayerManager().getPlayerList()) {
                clean(p.getInventory()); clean(p.getEnderChestInventory());
                for(var slot:p.currentScreenHandler.slots) cleanStack(slot.getStack(),0);
                cleanStack(p.currentScreenHandler.getCursorStack(),0);
                p.currentScreenHandler.sendContentUpdates();
            }
            for(ServerWorld w:s.getWorlds()) for(Entity e:w.iterateEntities()) {
                if(e instanceof ItemEntity drop) {cleanStack(drop.getStack(),0);if(drop.getStack().isEmpty()) drop.discard();}
                if(e instanceof Inventory inv) clean(inv);
                if(e instanceof LivingEntity living) for(ItemStack stack:living.getItemsEquipped()) cleanStack(stack,0);
            }
            for(WorldChunk c:loaded) for(var be:new ArrayList<>(c.getBlockEntities().values())) if(be instanceof Inventory inv) clean(inv);
        }
        int budget=8192;
        while(budget>0 && !sweeps.isEmpty()) {
            Sweep sweep=sweeps.peek();
            if(!loaded.contains(sweep.chunk)) {sweeps.remove();continue;}
            int used=sweep.step(budget);budget-=used;
            if(sweep.done()) sweeps.remove();
        }
    }
    private void choose(MinecraftServer s,int round,int option) {
        if(!rules.clock.ready() || round!=rules.round || option<0 || option>2 || (option==1 && rules.water) || (option==2 && rules.food)) return;
        Text removed;
        if(option==0) {rules.blocks.add(rules.offered);removed=Registries.BLOCK.get(new Identifier(rules.offered)).getName();}
        else if(option==1) {rules.water=true;removed=Text.literal("вода");}
        else {rules.food=true;removed=Text.literal("еда");}
        rules.round++; rules.clock.reset();
        List<String> pool=new ArrayList<>();
        for(String id:CANDIDATES) if(!rules.blocks.contains("minecraft:"+id)) pool.add("minecraft:"+id);
        // Fall back to other registered blocks after the handpicked pool is exhausted.
        if(pool.isEmpty()) Registries.BLOCK.getIds().stream().filter(id -> !id.equals(new Identifier("air")) && !id.equals(new Identifier("bedrock")) && !rules.blocks.contains(id.toString())).forEach(id -> pool.add(id.toString()));
        if(!pool.isEmpty()) rules.offered=pool.get(s.getOverworld().random.nextInt(pool.size()));
        rules.markDirty();
        sweeps.clear(); for(WorldChunk c:loaded) sweeps.add(new Sweep((ServerWorld)c.getWorld(),c));
        s.getPlayerManager().broadcast(Text.literal("Мировой выбор: исчезает ").append(removed).append("!"),false);
        syncAll(s);
    }
    private void clean(Inventory inv) {for(int i=0;i<inv.size();i++) cleanStack(inv.getStack(i),0); inv.markDirty();}
    private void cleanStack(ItemStack stack,int depth) {
        if(stack.isEmpty())return;
        Item item=stack.getItem();
        if((rules.food && stack.isFood()) || (rules.water && (item==Items.WATER_BUCKET || item instanceof EntityBucketItem)) || (item instanceof BlockItem bi && rules.blocks.contains(Registries.BLOCK.getId(bi.getBlock()).toString()))) {stack.setCount(0);return;}
        // Covers food/resources inside carried shulkers and bundles, without unbounded NBT recursion.
        if(depth>=16 || !stack.hasNbt())return;
        NbtCompound n=stack.getNbt();
        if(n.contains("BlockEntityTag",NbtElement.COMPOUND_TYPE)) cleanList(n.getCompound("BlockEntityTag").getList("Items",NbtElement.COMPOUND_TYPE),depth+1);
        if(n.contains("Items",NbtElement.LIST_TYPE)) cleanList(n.getList("Items",NbtElement.COMPOUND_TYPE),depth+1);
    }
    private void cleanList(NbtList list,int depth) {
        for(int i=list.size()-1;i>=0;i--) {
            NbtCompound n=list.getCompound(i);ItemStack stack=ItemStack.fromNbt(n);cleanStack(stack,depth);
            if(stack.isEmpty())list.remove(i);else {NbtCompound out=stack.writeNbt(new NbtCompound());if(n.contains("Slot"))out.putByte("Slot",n.getByte("Slot"));list.set(i,out);}
        }
    }
    private void syncAll(MinecraftServer s) {for(ServerPlayerEntity p:s.getPlayerManager().getPlayerList())sync(p);}
    private void sync(ServerPlayerEntity p) {
        if(rules==null || !ServerPlayNetworking.canSend(p,SYNC))return;
        PacketByteBuf b=PacketByteBufs.create();b.writeVarInt(rules.clock.elapsed());b.writeVarInt(rules.round);b.writeString(rules.offered);b.writeBoolean(rules.water);b.writeBoolean(rules.food);ServerPlayNetworking.send(p,SYNC,b);
    }
    private static final class Sweep {
        final ServerWorld world;final WorldChunk chunk; int index;
        Sweep(ServerWorld w,WorldChunk c){world=w;chunk=c;}
        boolean done(){return index>=world.getHeight()*256;}
        int step(int budget){int used=0;BlockPos.Mutable pos=new BlockPos.Mutable();while(used<budget && !done()) {
            int i=index++;used++;pos.set(chunk.getPos().getStartX()+(i&15),world.getBottomY()+(i>>8),chunk.getPos().getStartZ()+((i>>4)&15));
            BlockState old=chunk.getBlockState(pos), next=filter(world,old);
            if(old!=next) world.setBlockState(pos,next,Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }return used;}
    }
}
