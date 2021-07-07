package dev.sockmower.misguidedmod;

import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;

public class CachedChunk {
    public final Pos2 pos;
    public final ChunkDataS2CPacket packet;
    public boolean poison;

    @Override
    public String toString() {
        return String.format("Chunk{%s}", pos);
    }

    public CachedChunk(Pos2 pos, ChunkDataS2CPacket packet) {
        this.pos = pos;
        this.packet = packet;
        this.poison = false;
    }

    public void poison() {
        poison = true;
    }
}
