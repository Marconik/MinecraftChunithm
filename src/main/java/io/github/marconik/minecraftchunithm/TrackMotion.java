package io.github.marconik.minecraftchunithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Random;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** A bounded display-entity prototype; no scoring or music playback yet. */
public final class TrackMotion {
    public static final String MOTION_TAG = "minecraftchunithm.motion";
    public static final String LINE_TAG = "minecraftchunithm.judgment";
    private static final Map<ServerLevel, Session> SESSIONS = new HashMap<>();
    private static final Set<UUID> LIVE = new HashSet<>();
    private TrackMotion() {}

    public static void initialize() {
        // Update before entity tracking sends this tick's positions, not after it.
        ServerTickEvents.START_LEVEL_TICK.register(level -> {
            Session session = SESSIONS.get(level);
            if (session != null && !session.tick(level)) stop(level);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (var level : new ArrayList<>(SESSIONS.keySet())) stop(level);
            LIVE.clear();
        });
        // Saved/unloaded test notes must never remain frozen in the world after reopening it.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(MOTION_TAG) && !LIVE.contains(entity.getUUID())) entity.discard();
        });
    }

    public static void clearArea(ServerLevel level, Placement placement, int length) {
        AABB area = new AABB(placement.point(0,0,0),placement.point(9,2,length));
        for (var entity : level.getEntitiesOfClass(Display.ItemDisplay.class, area,
                e -> e.entityTags().contains(LINE_TAG) || e.entityTags().contains("minecraftchunithm.tap_preview"))) {
            entity.discard();
        }
    }

    public static void addJudgment(ServerLevel level, Placement placement, int length) {
        var pos=placement.point(4.5,TrackBlock.SURFACE_HEIGHT+0.002,RhythmTiming.judgment(length));
        var line=create(level,"judgment_line",pos.x,pos.y,pos.z,LINE_TAG,8);line.setYRot(placement.yaw());
    }

    public static void start(ServerLevel level, Placement placement, int length, double bpm, double speed) {
        stop(level);
        Session session = new Session(placement, length, bpm, speed);
        SESSIONS.put(level, session);
        session.advance(level, 0); // The first beat starts immediately, followed by quarter notes.
    }

    public static int stop(ServerLevel level) {
        Session previous = SESSIONS.remove(level);
        if (previous == null) return 0;
        int count = previous.notes.size();
        for (Moving note : previous.notes) {
            note.entity.discard();
            LIVE.remove(note.entity.getUUID());
        }
        return count;
    }

    private static Display.ItemDisplay create(ServerLevel level, String item, double x, double y,
                                               double z, String tag, double width) {
        var display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null) throw new IllegalStateException("Cannot create track display");
        display.setPos(x,y,z);
        display.setItemStack(new ItemStack(BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath(MinecraftChunithm.MOD_ID, item))));
        display.setItemTransform(ItemDisplayContext.FIXED);
        display.setBrightnessOverride(new Brightness(15,15));
        display.setWidth((float)width + 0.5F);
        display.setHeight(0.5F);
        display.setPosRotInterpolationDuration(1);
        display.addTag(tag);
        if (tag.equals(MOTION_TAG)) LIVE.add(display.getUUID());
        level.addFreshEntity(display);
        return display;
    }

    private static final class Moving {
        private final Display.ItemDisplay entity;
        private final double bornAt;
        private long reachedAtTick = -1;
        private double across;
        private Moving(Display.ItemDisplay entity, double bornAt) {
            this.entity = entity;
            this.bornAt = bornAt;
        }
    }

    private static final class Session {
        private final Placement placement;
        private final int length;
        private final double bpm, speed;
        private final Random random = new Random();
        private final ArrayList<Moving> notes = new ArrayList<>();
        private long tick, beat, despawned;
        private Session(Placement placement, int length, double bpm, double speed) {
            this.placement=placement; this.length=length; this.bpm=bpm; this.speed=speed;
        }
        private boolean tick(ServerLevel level) {
            for (int z=0;z<length;z++) {
                if (!level.hasChunkAt(placement.block(0,0,z)) || !level.hasChunkAt(placement.block(8,0,z))) return false;
            }
            // Stop if the track was broken or changed; check each complete row once per second.
            if (tick % 20 == 0) for (int z=0;z<length;z++) {
                var bounds = TrackBounds.find(level,placement.block(0,0,z));
                if (bounds == null) return false;
            }
            advance(level, ++tick / 20.0);
            if (Boolean.getBoolean("minecraftchunithm.motionQuickPlay") && tick % 200 == 0) {
                MinecraftChunithm.LOGGER.info("Motion check: ticks={}, beats={}, activeDisplays={}, despawned={}",
                        tick, beat, notes.size(), despawned);
            }
            return true;
        }
        private void advance(ServerLevel level, double now) {
            while (RhythmTiming.beatTime(beat,bpm) <= now + 1e-9) {
                double born = RhythmTiming.beatTime(beat++,bpm);
                int width = random.nextInt(16)+1;
                int lane = random.nextInt(17-width);
                for (int item=0;item<2;item++) {
                    double across=item==0?4.5:0.5+lane*.5+width*.25;
                    var pos=placement.point(across,TrackBlock.SURFACE_HEIGHT+(item==0?.004:.015625),RhythmTiming.position(born,now,speed));
                    var entity=create(level,item==0?"beat_line":"tap_"+width,pos.x,pos.y,pos.z,MOTION_TAG,item==0?8:width*.5);
                    entity.setYRot(placement.yaw());var moving=new Moving(entity,born);moving.across=across;notes.add(moving);
                }
            }
            var iterator=notes.iterator();
            while(iterator.hasNext()) {
                var note=iterator.next();
                if(note.entity.isRemoved() || RhythmTiming.canRemove(note.reachedAtTick,tick)) {
                    note.entity.discard(); LIVE.remove(note.entity.getUUID()); iterator.remove();
                    despawned++;
                } else {
                    var pos=placement.point(note.across,0,RhythmTiming.displayPosition(note.bornAt,now,speed,length));
                    note.entity.setPos(pos.x,note.entity.getY(),pos.z);
                    // Never skip the final location just because this tick overshot the edge.
                    if (note.reachedAtTick < 0 && RhythmTiming.expired(note.bornAt,now,speed,length)) {
                        note.reachedAtTick = tick;
                    }
                }
            }
        }
    }
}
