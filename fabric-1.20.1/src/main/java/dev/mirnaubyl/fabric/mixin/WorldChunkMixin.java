package dev.mirnaubyl.fabric.mixin;

import dev.mirnaubyl.fabric.MirNaUbyl;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {
    @Shadow public abstract World getWorld();
    @ModifyVariable(method="setBlockState", at=@At("HEAD"), argsOnly=true)
    private BlockState mirnaubyl$filter(BlockState state) {
        return getWorld() instanceof ServerWorld server ? MirNaUbyl.filter(server,state) : state;
    }
}
