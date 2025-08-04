package net.thvazik.deathhistorynotes.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DeathhistorynotesClient implements ClientModInitializer {
    private static final String NOTES_RELATIVE_PATH = "notes/local/";
    private static final String DEATH_HISTORY_FILENAME = "Death History.txt";
    private static String lastWorldName = null;
    private static int lastDeathCount = 0;

    @Override
    public void onInitializeClient() {
        // Reset on world join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            lastWorldName = getWorldName(client);
            lastDeathCount = 0;
        });

        // Tick event to check for deaths
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null || client.isPaused()) return;
            String worldName = getWorldName(client);
            if (worldName == null) return;

            // Detect death
            if (client.player.deathTime == 1) {
                recordDeath(client.player, worldName, client);
            }
        });
    }

    private static String getWorldName(MinecraftClient client) {
        if (client.getServer() != null) {
            // Singleplayer
            return client.getServer().getSaveProperties().getLevelName();
        } else if (client.getCurrentServerEntry() != null) {
            // Multiplayer
            // I don't know if this will work, I haven't tested it.
            return client.getCurrentServerEntry().address.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        } else if (client.world != null) {
            // Try to extract world name from path
            Path worldPath = client.runDirectory.toPath()
                    .resolve("saves").resolve(client.world.getRegistryKey().getValue().getPath());
            return worldPath.getFileName().toString();
        }
        return null;
    }

    private static void recordDeath(ClientPlayerEntity player, String worldName, MinecraftClient client) {
        // Make note file path
        File notesDir = new File(client.runDirectory, NOTES_RELATIVE_PATH + worldName);
        if (!notesDir.exists()) notesDir.mkdirs();
        File deathHistoryFile = new File(notesDir, DEATH_HISTORY_FILENAME);
        boolean fileExisted = deathHistoryFile.exists();
        StringBuilder sb = new StringBuilder();

        // Add comment for note
        if (!fileExisted) {
            sb.append("# This note was created automatically by DeathHistoryNotes mod.\n\n");
        }

        // Compose death info
        String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String pos = String.format("x=%d, y=%d, z=%d",
                (int)player.getX(), (int)player.getY(), (int)player.getZ());
        String reason = player.getDamageTracker().getDeathMessage().getString();
        String entry = String.format("[%s] %s at %s\n", dateTime, reason, pos);

        sb.append(entry);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(deathHistoryFile, true))) {
            writer.write(sb.toString());
        } catch (Exception e) {
            client.player.sendMessage(Text.literal("[DeathHistoryNotes] Failed to record death: " + e.getMessage()).setStyle(Style.EMPTY.withColor(Formatting.RED)), false);
        }
    }
}