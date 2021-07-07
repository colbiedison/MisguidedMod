package dev.sockmower.misguidedmod;

import java.net.SocketAddress;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;

/**
 * Hook into Minecraft's packet pipeline
 * to filter out unload packets to keep chunks rendered
 */
@ChannelHandler.Sharable
public class PacketHandler extends SimpleChannelInboundHandler<Packet<?>> implements ChannelOutboundHandler {

    static final String NAME = MisguidedClientMod.MODID + ":packet_handler";

    private final MisguidedClientMod moreChunks;

    public PacketHandler(MisguidedClientMod moreChunks) {
        this.moreChunks = moreChunks;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet<?> packet) throws Exception {
        if (packet instanceof UnloadChunkS2CPacket) {
            return; // ignore packet, we manually unload our chunks
        }
        if (packet instanceof ChunkDataS2CPacket) {
            try {
                final ChunkDataS2CPacket chunkPacket = (ChunkDataS2CPacket) packet;
                if (chunkPacket.isFullChunk()) {
                    // full chunk, not just a section
                    final Pos2 pos = new Pos2(chunkPacket.getX(), chunkPacket.getZ());
                    moreChunks.onReceiveGameChunk(new CachedChunk(pos, chunkPacket));
                    return; // drop packet, we load it manually
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        ctx.fireChannelRead(packet);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ctx.write(msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
}
