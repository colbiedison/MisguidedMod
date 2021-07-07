package dev.sockmower.misguidedmod;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import dev.sockmower.misguidedmod.mixin.ClientConnectionAccessorMixin;
import dev.sockmower.misguidedmod.mixin.MinecraftClientAccessorMixin;
import io.netty.channel.Channel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.channel.ChannelPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
//import net.minecraftforge.common.MinecraftForge;
//import net.minecraftforge.fml.client.FMLClientHandler;
//import net.minecraftforge.fml.common.Mod;
//import net.minecraftforge.fml.common.Mod.EventHandler;
//import net.minecraftforge.fml.common.event.FMLInitializationEvent;
//import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
//import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
//import net.minecraftforge.fml.common.network.FMLNetworkEvent;

//@Mod(modid = MisguidedClientMod.MODID, name = MisguidedClientMod.NAME, version = MisguidedClientMod.VERSION, clientSideOnly = true)
public class MisguidedClientMod implements ClientModInitializer {
    public static final String MODID = "misguidedmod";
    public static final String NAME = "Just A Misguided Mod";
    public static final String VERSION = "1.0";

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static Logger logger;
    private final Set<Pos2> loadedChunks = new HashSet<Pos2>();
    private static CachedWorld cachedWorld;
    private long lastExtraTime = 0;

    @Override
    public void onInitializeClient() {
        logger = LogManager.getLogger();
        logger.info("Initializing MisguidedMod v{}", VERSION);

        ClientPlayConnectionEvents.INIT.register( this::onGameConnected );

        ClientPlayConnectionEvents.DISCONNECT.register( this::onGameDisconnected );
    }

//  TODO: This should probably be a mixin

    public void insertPacketHandler() {
        ClientPlayNetworkHandler mcConnection = mc.getNetworkHandler();
        if (mcConnection == null) {
            logger.error("Could not inject packet handler into pipeline: mc.connection == null");
            return;
        }

        ChannelPipeline pipe = ((ClientConnectionAccessorMixin)(((MinecraftClientAccessorMixin)mc).getConnection())).getChannel().pipeline();

        if (pipe.get(PacketHandler.NAME) != null) {
            logger.warn("game server connection pipeline already contains handler, removing and re-adding");
            pipe.remove(PacketHandler.NAME);
        }

        PacketHandler packetHandler = new PacketHandler(this);
        pipe.addBefore("fml:packet_handler", PacketHandler.NAME, packetHandler);

        logger.info("Packet handler inserted");
    }

    public void removePacketHandler() {
        ClientPlayNetworkHandler mcConnection = (ClientPlayNetworkHandler) mc.getNetworkHandler();
        if (mcConnection == null) {
            logger.error("Could not inject packet handler into pipeline: mc.connection == null");
            return;
        }

        ChannelPipeline pipe = ((ClientConnectionAccessorMixin)(((MinecraftClientAccessorMixin)mc).getConnection())).getChannel().pipeline();

        if (pipe.get(PacketHandler.NAME) != null) {
            pipe.remove(PacketHandler.NAME);
        }
    }

    public void loadChunk(CachedChunk chunk) {
        if (!mc.isOnThread()) {
            logger.warn("Calling loadChunk from non-mc thread");
            return;
        }

        ClientPlayNetworkHandler conn = mc.getNetworkHandler();
        if (conn == null) {
            logger.warn("Connection is null!");
            return;
        }

        unloadChunk(chunk.pos);

        conn.onChunkData(chunk.packet);
        loadedChunks.add(chunk.pos);
    }

    public void unloadChunk(Pos2 pos) {
        if (!mc.isOnThread()) {
            logger.warn("Calling loadChunk from non-mc thread");
            return;
        }

        ClientPlayNetworkHandler conn = mc.getNetworkHandler();
        if (conn == null) {
            logger.warn("Connection is null!");
            return;
        }

        conn.onUnloadChunk(new UnloadChunkS2CPacket(pos.x, pos.z));
        loadedChunks.remove(pos);
    }

    public Pos2 getPlayerChunkPos() {
        if (mc.player == null) {
            return null;
        }
        if (mc.player.getX() == 0 && mc.player.getY() == 0 && mc.player.getZ() == 0) return null;
        if (mc.player.getX() == 8.5 && mc.player.getY() == 65 && mc.player.getZ() == 8.5) return null; // position not set from server yet
        return Pos2.chunkPosFromBlockPos(mc.player.getX(), mc.player.getZ());
    }

    public Set<Pos2> getNeededChunkPositions() {
        final int rdClient = mc.options.viewDistance + 1;
        final Pos2 player = getPlayerChunkPos();

        final Set<Pos2> loadable = new HashSet<>();

        if (player == null) {
            return loadable;
        }

        for (int x = player.x - rdClient; x <= player.x + rdClient; x++) {
            for (int z = player.z - rdClient; z <= player.z + rdClient; z++) {
                Pos2 chunk = new Pos2(x, z);

                if (1 + player.chebyshevDistance(chunk) <= 7) {
                    // do not load extra chunks inside the server's (admittedly, guessed) render distance,
                    // we expect the server to send game chunks here eventually
                    continue;
                }

                if (!loadedChunks.contains(chunk)) {
                    loadable.add(chunk);
                }
            }
        }

        //logger.info("Want to request {} additional chunks", loadable.size());

        return loadable;
    }

    public void unloadOutOfRangeChunks() {
        final int rdClient = mc.options.viewDistance + 1;
        final Pos2 player = getPlayerChunkPos();

        if (player == null) {
            return;
        }

        Set<Pos2> toUnload = new HashSet<>();

        for (Pos2 pos : loadedChunks) {
            if (pos.chebyshevDistance(player) > rdClient) {
                // logger.info("Unloading chunk at {} since it is outside of render distance", pos.toString());
                toUnload.add(pos);
            }
        }

        toUnload.forEach(pos -> mc.execute(() -> unloadChunk(pos)));
    }

    public void onReceiveGameChunk(CachedChunk chunk) throws IOException {
        mc.execute(() -> loadChunk(chunk));

        if ((System.currentTimeMillis() / 1000) - lastExtraTime > 1) {
            lastExtraTime = System.currentTimeMillis() / 1000;
            cachedWorld.addChunksToLoadQueue(getNeededChunkPositions());
            unloadOutOfRangeChunks();
        }

        cachedWorld.writeChunk(chunk);
    }

    public void onGameConnected(ClientPlayNetworkHandler handler, MinecraftClient client) {
        String serverIP = client.world.getServer().getServerIp();
//        if (serverIP == null || serverIP.equals("play.wynncraft.com")) {
        if (serverIP == null) {
            return;
        }
        logger.info("Connected to server {}, client render distance is {}",
                serverIP,
                mc.options.viewDistance);

        insertPacketHandler();

        cachedWorld = new CachedWorld(
                Paths.get(mc.runDirectory.getAbsolutePath() + "/misguidedmod/" + serverIP),
                logger,
                mc,
                this
                );
    }

    public void onGameDisconnected(ClientPlayNetworkHandler handler, MinecraftClient client) {
        loadedChunks.clear();
        logger.info("loadedChunks cleared.");
        try {
            cachedWorld.releaseFiles();
            cachedWorld.cancelThreads();
        } catch (Exception e) { e.printStackTrace(); }

        removePacketHandler();
    }
}
