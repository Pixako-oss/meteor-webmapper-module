package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public class Webmap extends Module {

    // private final Settings settings = new Settings();
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder().name("distance").description("Maximum chunk distance from 0, 0 to map").defaultValue(6250).min(1).sliderMax(125000).build());
    private final Setting<Integer> minPlayers = sgGeneral.add(new IntSetting.Builder().name("min-players").description("Minimum number of players online to upload chunks (use this to bypass lobbies)").defaultValue(100).min(0).sliderMax(125000).build());
    private final Setting<String> token = sgGeneral.add(new StringSetting.Builder().name("token").description("Authentication token to upload chunks to the webmap").defaultValue("").build());

    public Webmap() {
        super(AddonTemplate.CATEGORY, "webmap", "Uploads chunk data to the webmap");
    }

    private boolean isOn6b6t() {
        if (mc.getCurrentServerEntry() == null) return false;
        return mc.getCurrentServerEntry().address.toLowerCase().contains("6b6t.org");
    }

    private String getDimension() {
        if (mc.world == null) return null;
        RegistryKey<World> dim = mc.world.getRegistryKey();
        if (dim == World.OVERWORLD) return "overworld";
        if (dim == World.NETHER) return "nether";
        if (dim == World.END) return "end";
        return null;
    }

    @EventHandler
    public void onChunkLoad(ChunkDataEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (!isOn6b6t()) return;

        int playerCount = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerList().size() : 0;
        if (playerCount < minPlayers.get()) return;

        String dimension = getDimension();
        if (dimension == null) return;

        ChunkPos chunkPos = event.chunk().getPos();
        int limit = maxDistance.get();
        if (chunkPos.x > limit - 1 || chunkPos.x < -limit || chunkPos.z > limit - 1 || chunkPos.z < -limit) return;

        String playerName = mc.player.getName().getString();
        StringBuilder str = new StringBuilder();
        str.append(playerName).append("ñ").append(dimension).append("ñ").append(chunkPos.x).append("_").append(chunkPos.z).append("@");

        boolean isNether = dimension.equals("nether");

        // o3 rah
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int startY;
                if (isNether) {
                    startY = 127;
                    while (startY > mc.world.getBottomY()) {
                        BlockState state = mc.world.getBlockState(new BlockPos(chunkPos.getStartX() + x, startY, chunkPos.getStartZ() + z));
                        if (state.isAir()) break;
                        startY--;
                    }
                } else {
                    startY = event.chunk().sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
                }

                boolean foundBlock = false;
                for (int y = startY; y >= mc.world.getBottomY(); y--) {
                    BlockPos pos = new BlockPos(chunkPos.getStartX() + x, y, chunkPos.getStartZ() + z);
                    BlockState state = mc.world.getBlockState(pos);

                    String blockName = state.getBlock().toString().replace("Block{minecraft:", "").replace("}", "");

                    VoxelShape shape = state.getCollisionShape(mc.world, pos);
                    boolean solid = !shape.isEmpty() && shape.getMax(Direction.Axis.X) - shape.getMin(Direction.Axis.X) > 0.75 && shape.getMax(Direction.Axis.Z) - shape.getMin(Direction.Axis.Z) > 0.75;

                    if (blockName.equals("water") || blockName.equals("lava") || blockName.equals("snow") || (!blockName.endsWith("_bed") && !blockName.endsWith("glass") && solid)) {
                        str.append(blockName).append(",").append(y).append(";");
                        foundBlock = true;
                        break;
                    }
                }

                if (!foundBlock) {
                    str.append("void,").append(mc.world.getBottomY()).append(";");
                }
            }
        }

        final String payload = str.toString();
        final String currentToken = token.get();
        new Thread(() -> {
            try {
                HttpPost.send(payload, false, currentToken);
            } catch (Exception e) {
                warning("Webmap upload failed: %s", e.getMessage());
            }
        }).start();
    }
}
