package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.io.*;
import java.nio.file.*;

public class Explorer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> ringSize = sgGeneral.add(new IntSetting.Builder()
        .name("ring-size")
        .description("Distance added per ring.")
        .defaultValue(128)
        .min(64)
        .max(512)
        .sliderRange(64, 512)
        .build()
    );

    private final Setting<Integer> arrivedDist = sgGeneral.add(new IntSetting.Builder()
        .name("arrival-distance")
        .description("Distance to target to consider arrived.")
        .defaultValue(5)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> resetProgress = sgGeneral.add(new BoolSetting.Builder()
        .name("reset-progress")
        .description("Reset spiral progress on next enable.")
        .defaultValue(false)
        .build()
    );

    private static final Path SAVE_FILE = Paths.get("spiral_progress.txt");

    private int targetX, targetZ;
    private int ring = 0;
    private int corner = 0;

    public Explorer() {
        super(AddonTemplate.CATEGORY, "explorer", "Move in a spiral around 0,0");
    }

    @Override
    public void onActivate() {
        if (resetProgress.get()) {
            ring = 0;
            corner = 0;
            targetX = 0;
            targetZ = 0;
            resetProgress.set(false);
            info("Reset progress, starting from 0, 0");
        } else {
            loadProgress();
            info("Resuming spiral at ring %d, target %d, %d", ring, targetX, targetZ);
        }
    }

    @Override
    public void onDeactivate() {
        mc.options.forwardKey.setPressed(false);
        saveProgress();
        info("Paused at ring %d, position %d, %d", ring, targetX, targetZ);
    }

    @EventHandler
    private void onDisconnect(GameLeftEvent event) {
        if (isActive()) {
            saveProgress();
            toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        double px = mc.player.getX();
        double pz = mc.player.getZ();

        double dx = targetX - px;
        double dz = targetZ - pz;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < arrivedDist.get()) {
            nextTarget();
            saveProgress();
            info("Next target: %d, %d (ring %d)", targetX, targetZ, ring);
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mc.player.setYaw(yaw);
        mc.player.setPitch(0);

        mc.options.forwardKey.setPressed(true);
    }

    private void nextTarget() {
        int size = ringSize.get();

        if (ring == 0) {
            ring = 1;
            corner = 0;
            int r = ring * size;
            targetX = r;
            targetZ = r;
            return;
        }

        corner++;
        if (corner > 3) {
            corner = 0;
            ring++;
        }

        int r = ring * size;

        switch (corner) {
            case 0 -> { targetX = r;  targetZ = r;  }
            case 1 -> { targetX = -r; targetZ = r;  }
            case 2 -> { targetX = -r; targetZ = -r; }
            case 3 -> { targetX = r;  targetZ = -r; }
        }
    }

    private void saveProgress() {
        try {
            String data = ring + "," + corner + "," + targetX + "," + targetZ;
            Files.writeString(SAVE_FILE, data);
        } catch (IOException e) {
            error("Failed to save progress: " + e.getMessage());
        }
    }

    private void loadProgress() {
        try {
            if (Files.exists(SAVE_FILE)) {
                String data = Files.readString(SAVE_FILE).trim();
                String[] parts = data.split(",");
                if (parts.length == 4) {
                    ring = Integer.parseInt(parts[0]);
                    corner = Integer.parseInt(parts[1]);
                    targetX = Integer.parseInt(parts[2]);
                    targetZ = Integer.parseInt(parts[3]);
                    return;
                }
            }
        } catch (Exception e) {
            error("Failed to load progress: " + e.getMessage());
        }
        ring = 0;
        corner = 0;
        targetX = 0;
        targetZ = 0;
    }

    @Override
    public String getInfoString() {
        return String.format("R%d %d, %d", ring, targetX, targetZ);
    }
}