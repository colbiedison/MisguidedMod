package dev.sockmower.misguidedmod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessorMixin {
    @Accessor("connection")
    ClientConnection getConnection();
}
