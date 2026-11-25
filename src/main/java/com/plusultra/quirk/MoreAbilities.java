package com.plusultra.quirk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Abilities for Creation, Invisibility, Fierce Wings, Dark Shadow, Black Whip, Acid, Earphone Jack and Tape. */
public final class MoreAbilities {
    private MoreAbilities() {}

    // ------------------------------------------------------------------ helpers

    private static ParticleOptions iron() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState());
    }

    private static LivingEntity nearest(ServerPlayer p, double range, double cos) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Abilities.cone(p, range, cos)) {
            double d = e.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static List<LivingEntity> nearestN(ServerPlayer p, double range, double cos, int n) {
        List<LivingEntity> all = new ArrayList<>(Abilities.cone(p, range, cos));
        all.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return all.size() > n ? all.subList(0, n) : all;
    }

    private static Vec3 center(LivingEntity e) {
        return e.getBoundingBox().getCenter();
    }

    private static void pull(LivingEntity t, ServerPlayer p, double strength) {
        Vec3 d = p.position().subtract(t.position());
        if (d.lengthSqr() < 1e-4) return;
        Vec3 n = d.normalize();
        t.push(n.x * strength, 0.25, n.z * strength);
        t.hurtMarked = true;
    }

    private static Vec3 right(ServerPlayer p) {
        Vec3 f = Abilities.flat(p.getLookAngle());
        return new Vec3(-f.z, 0, f.x);
    }

    // ------------------------------------------------------------------ Creation

    public static void forgeShield(ServerPlayer p, double power) {
        Abilities.effect(p, MobEffects.ABSORPTION, 300, 2);
        Abilities.effect(p, MobEffects.DAMAGE_RESISTANCE, 300, 1);
        ServerLevel l = Abilities.level(p);
        Vec3 c = p.position().add(0, 1, 0);
        Fx.sphere(l, iron(), c, 1.4, 44);
        Fx.sphere(l, Fx.dust(0xE67E22, 1.2f), c, 1.0, 26);
        Fx.ring(l, ParticleTypes.CRIT, p.position().add(0, 0.2, 0), 1.6, 18);
        Fx.startAura(p, 300);
        Abilities.sound(p, SoundEvents.ANVIL_USE, 1.2f);
    }

    public static void creationCannon(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        LivingEntity t = nearest(p, 26, 0.96);
        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        Vec3 to = t != null ? center(t) : p.pick(26, 1.0f, false).getLocation();
        Fx.beam(l, Fx.dust(0xE67E22, 1.4f), from, to, 0.4);
        Fx.beam(l, ParticleTypes.CRIT, from, to, 0.9);
        Fx.burst(l, iron(), to, 30, 0.5, 0.15);
        Fx.ring(l, Fx.dust(0xE67E22, 1.3f), to, 1.2, 14);
        if (t != null) {
            Abilities.hurt(p, t, 10 * power);
            Abilities.shove(t, Abilities.flat(p.getLookAngle()), 1.5, 0.3);
        }
        Abilities.sound(p, SoundEvents.ANVIL_LAND, 1.8f);
    }

    public static void ironGuardians(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        Vec3 look = Abilities.flat(p.getLookAngle());
        Vec3 right = right(p);
        for (int s = -1; s <= 1; s += 2) {
            IronGolem g = EntityType.IRON_GOLEM.create(l);
            if (g == null) continue;
            Vec3 pos = p.position().add(look.scale(2)).add(right.scale(2.0 * s));
            g.moveTo(pos.x, p.getY(), pos.z, p.getYRot(), 0f);
            g.setPlayerCreated(true);
            l.addFreshEntity(g);
            Fx.sphere(l, iron(), pos.add(0, 1.3, 0), 1.5, 30);
            Fx.ring(l, Fx.dust(0xE67E22, 1.4f), pos.add(0, 0.1, 0), 1.6, 18);
            Scheduler.schedule(700, g::discard);
        }
        Fx.lightning(l, p.position());
        Abilities.sound(p, SoundEvents.ANVIL_USE, 0.6f);
    }

    // ------------------------------------------------------------------ Invisibility

    public static void vanish(ServerPlayer p, double power) {
        Abilities.effect(p, MobEffects.INVISIBILITY, 200, 0);
        Abilities.effect(p, MobEffects.MOVEMENT_SPEED, 200, 1);
        ServerLevel l = Abilities.level(p);
        Fx.sphere(l, Fx.dust(0xB0D0E8, 1.3f), p.position().add(0, 1, 0), 1.0, 30);
        Fx.ring(l, ParticleTypes.END_ROD, p.position().add(0, 0.1, 0), 1.2, 14);
        Abilities.sound(p, SoundEvents.ENDER_EYE_LAUNCH, 1.8f);
    }

    public static void lightBendStrike(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        LivingEntity t = nearest(p, 9, 0.7);
        Fx.burst(l, Fx.dust(0xB0D0E8, 1.4f), p.position().add(0, 1, 0), 20, 0.4, 0.05);
        if (t == null) {
            Fx.ring(l, ParticleTypes.END_ROD, p.position().add(0, 0.1, 0), 1.0, 12);
            return;
        }
        Vec3 face = Abilities.flat(t.getLookAngle());
        Vec3 behind = t.position().subtract(face.scale(1.6));
        p.connection.teleport(behind.x, t.getY(), behind.z, t.getYRot(), 0f);
        Fx.beam(l, Fx.dust(0xB0D0E8, 1.2f), p.getEyePosition(), center(t), 0.3);
        Abilities.hurt(p, t, 11 * power);
        Fx.burst(l, ParticleTypes.SWEEP_ATTACK, center(t), 3, 0.3, 0);
        Fx.burst(l, ParticleTypes.CRIT, center(t), 20, 0.4, 0.2);
        Abilities.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f);
    }

    public static void mirage(ServerPlayer p, double power) {
        Abilities.effect(p, MobEffects.INVISIBILITY, 300, 0);
        Abilities.effect(p, MobEffects.MOVEMENT_SPEED, 300, 2);
        ServerLevel l = Abilities.level(p);
        for (LivingEntity t : Abilities.around(p, p.position(), 9)) {
            Abilities.effect(t, MobEffects.BLINDNESS, 160, 0);
            Abilities.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 100, 1);
        }
        Fx.sphere(l, Fx.dust(0xB0D0E8, 1.6f), p.position().add(0, 1, 0), 9, 90);
        Fx.ring(l, ParticleTypes.END_ROD, p.position().add(0, 0.2, 0), 9, 40);
        Fx.startAura(p, 300);
        Abilities.sound(p, SoundEvents.ENDER_DRAGON_GROWL, 1.9f);
    }

    // ------------------------------------------------------------------ Fierce Wings

    private static void wings(ServerPlayer p, int rgb) {
        ServerLevel l = Abilities.level(p);
        Vec3 r = right(p);
        Vec3 base = p.position().add(0, 1.4, 0).subtract(Abilities.flat(p.getLookAngle()).scale(0.3));
        for (int side = -1; side <= 1; side += 2) {
            Vec3 tip1 = base.add(r.scale(2.8 * side)).add(0, 1.2, 0);
            Vec3 tip2 = base.add(r.scale(2.4 * side)).add(0, -0.1, 0);
            Vec3 tip3 = base.add(r.scale(1.6 * side)).add(0, -0.9, 0);
            Fx.beam(l, Fx.dust(rgb, 1.3f), base, tip1, 0.25);
            Fx.beam(l, Fx.dust(rgb, 1.3f), base, tip2, 0.25);
            Fx.beam(l, Fx.dust(rgb, 1.1f), base, tip3, 0.25);
        }
    }

    public static void windGale(ServerPlayer p, double power) {
        Vec3 look = p.getLookAngle();
        for (LivingEntity t : Abilities.cone(p, 6, 0.5)) {
            Abilities.hurt(p, t, 3 * power);
            Abilities.shove(t, Abilities.flat(look), 1.6, 0.5);
        }
        p.setDeltaMovement(look.x * 1.4, 0.9, look.z * 1.4);
        p.hurtMarked = true;
        p.fallDistance = 0;
        Abilities.effect(p, MobEffects.SLOW_FALLING, 100, 0);
        wings(p, 0xD9382A);
        Fx.ring(Abilities.level(p), ParticleTypes.CLOUD, p.position().add(0, 0.1, 0), 1.8, 18);
        Abilities.sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1.0f);
    }

    public static void featherBarrage(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        Vec3 from = p.getEyePosition().add(0, -0.2, 0);
        for (LivingEntity t : nearestN(p, 15, 0.85, 5)) {
            Abilities.hurt(p, t, 6 * power);
            Fx.beam(l, Fx.dust(0xD9382A, 1.1f), from, center(t), 0.4);
            Fx.burst(l, ParticleTypes.CRIT, center(t), 10, 0.3, 0.15);
        }
        Vec3 look = p.getLookAngle();
        for (int i = 2; i <= 10; i += 2) {
            Abilities.particles(p, Fx.dust(0xFF7A59, 1.0f), from.add(look.scale(i)), 6, 0.5 + i * 0.05, 0);
        }
        Abilities.sound(p, SoundEvents.ARROW_SHOOT, 1.6f);
    }

    public static void soar(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        for (LivingEntity t : Abilities.around(p, p.position(), 5)) {
            Abilities.hurt(p, t, 8 * power);
            Vec3 away = t.position().subtract(p.position());
            Abilities.shove(t, away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize(), 1.4, 0.6);
        }
        Vec3 look = p.getLookAngle();
        p.setDeltaMovement(look.x * 0.8, 2.2, look.z * 0.8);
        p.hurtMarked = true;
        p.fallDistance = 0;
        Abilities.effect(p, MobEffects.SLOW_FALLING, 400, 0);
        Abilities.effect(p, MobEffects.MOVEMENT_SPEED, 400, 1);
        Abilities.effect(p, MobEffects.JUMP, 400, 2);
        wings(p, 0xD9382A);
        Fx.spiral(l, Fx.dust(0xD9382A, 1.4f), p.position(), 3, 1.0, 3, 40);
        Fx.ring(l, ParticleTypes.CLOUD, p.position().add(0, 0.1, 0), 4, 28);
        Fx.startAura(p, 300);
        Abilities.sound(p, SoundEvents.ENDER_DRAGON_FLAP, 0.7f);
    }

    // ------------------------------------------------------------------ Dark Shadow

    public static void shadowJab(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        for (LivingEntity t : Abilities.cone(p, 5.5, 0.7)) {
            Abilities.hurt(p, t, 9 * power);
            Abilities.effect(t, MobEffects.WITHER, 60, 0);
            Abilities.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
            Abilities.shove(t, Abilities.flat(p.getLookAngle()), 1.2, 0.3);
            Fx.burst(l, ParticleTypes.SQUID_INK, center(t), 16, 0.4, 0.1);
        }
        Vec3 from = p.getEyePosition().add(0, -0.4, 0);
        Fx.beam(l, Fx.dust(0x2C2C54, 1.6f), from, from.add(p.getLookAngle().scale(5)), 0.3);
        Fx.beam(l, ParticleTypes.SMOKE, from, from.add(p.getLookAngle().scale(5)), 0.5);
        Abilities.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f);
    }

    public static void shadowVeil(ServerPlayer p, double power) {
        boolean dark = p.level().getMaxLocalRawBrightness(p.blockPosition()) < 8;
        Abilities.effect(p, MobEffects.DAMAGE_RESISTANCE, 300, dark ? 2 : 1);
        Abilities.effect(p, MobEffects.DAMAGE_BOOST, 300, dark ? 1 : 0);
        Abilities.effect(p, MobEffects.ABSORPTION, 300, 1);
        ServerLevel l = Abilities.level(p);
        Fx.sphere(l, Fx.dust(0x2C2C54, 1.8f), p.position().add(0, 1, 0), 1.3, 50);
        Fx.spiral(l, ParticleTypes.SQUID_INK, p.position(), 2.2, 0.8, 3, 30);
        Fx.startAura(p, 300);
        Abilities.sound(p, SoundEvents.WITHER_SPAWN, 1.8f);
    }

    public static void rampage(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        Vec3 c = p.position();
        for (LivingEntity t : Abilities.around(p, c, 7)) {
            Abilities.hurt(p, t, 12 * power);
            Abilities.effect(t, MobEffects.WITHER, 100, 1);
            Vec3 away = t.position().subtract(c);
            Abilities.shove(t, away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize(), 1.6, 0.5);
        }
        Abilities.effect(p, MobEffects.DAMAGE_BOOST, 300, 2);
        Abilities.effect(p, MobEffects.MOVEMENT_SPEED, 300, 1);
        for (int r = 2; r <= 7; r += 1) Fx.ring(l, Fx.dust(0x2C2C54, 1.8f), c.add(0, 0.3 + r * 0.2, 0), r, 10 + r * 5);
        Fx.spiral(l, ParticleTypes.SQUID_INK, c, 4, 1.4, 4, 60);
        Fx.burst(l, ParticleTypes.LARGE_SMOKE, c.add(0, 1, 0), 40, 1.5, 0.05);
        Fx.startAura(p, 300);
        Abilities.sound(p, SoundEvents.ENDER_DRAGON_GROWL, 0.6f);
    }

    // ------------------------------------------------------------------ Black Whip

    public static void lash(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        LivingEntity t = nearest(p, 15, 0.92);
        Vec3 from = p.getEyePosition().add(0, -0.4, 0);
        if (t == null) {
            Vec3 end = from.add(p.getLookAngle().scale(10));
            Fx.beam(l, Fx.dust(0x4A235A, 1.3f), from, end, 0.3);
            return;
        }
        Fx.beam(l, Fx.dust(0x4A235A, 1.3f), from, center(t), 0.3);
        Fx.beam(l, ParticleTypes.SMOKE, from, center(t), 0.8);
        Abilities.hurt(p, t, 6 * power);
        pull(t, p, 1.5);
        Fx.burst(l, Fx.dust(0x4A235A, 1.5f), center(t), 14, 0.4, 0.05);
        Abilities.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f);
    }

    public static void tetherSwing(ServerPlayer p, double power) {
        boolean hooked = Abilities.grapple(p, 30, 2.2);
        ServerLevel l = Abilities.level(p);
        if (!hooked) {
            Vec3 from = p.getEyePosition().add(0, -0.4, 0);
            Fx.beam(l, Fx.dust(0x4A235A, 1.2f), from, from.add(p.getLookAngle().scale(8)), 0.4);
        } else {
            Fx.spiral(l, Fx.dust(0x4A235A, 1.1f), p.position(), 1.8, 0.5, 2, 18);
        }
    }

    public static void constrict(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        Vec3 from = p.getEyePosition().add(0, -0.4, 0);
        for (LivingEntity t : Abilities.around(p, p.position(), 8)) {
            Abilities.hurt(p, t, 8 * power);
            Abilities.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 100, 3);
            Abilities.effect(t, MobEffects.WEAKNESS, 100, 0);
            Fx.beam(l, Fx.dust(0x4A235A, 1.4f), from, center(t), 0.3);
            Fx.sphere(l, Fx.dust(0x4A235A, 1.3f), center(t), 0.8, 18);
            pull(t, p, 1.1);
        }
        Fx.ring(l, Fx.dust(0x4A235A, 1.6f), p.position().add(0, 0.2, 0), 8, 40);
        Fx.spiral(l, ParticleTypes.SQUID_INK, p.position(), 3, 1.2, 3, 36);
        Abilities.sound(p, SoundEvents.WITHER_SHOOT, 0.7f);
    }

    // ------------------------------------------------------------------ Acid

    private static void pool(ServerPlayer p, Vec3 c, double radius, double damage, int pulses) {
        if (pulses <= 0) return;
        ServerLevel l = Abilities.level(p);
        Fx.ring(l, Fx.dust(0xA6E22E, 1.4f), c.add(0, 0.15, 0), radius, (int) (12 + radius * 6));
        l.sendParticles(ParticleTypes.ITEM_SLIME, c.x, c.y + 0.2, c.z, 10, radius * 0.5, 0.1, radius * 0.5, 0.02);
        l.sendParticles(Fx.dust(0xA6E22E, 1.2f), c.x, c.y + 0.4, c.z, 14, radius * 0.5, 0.3, radius * 0.5, 0);
        for (LivingEntity t : Abilities.around(p, c, radius)) {
            Abilities.hurt(p, t, damage);
            Abilities.effect(t, MobEffects.POISON, 40, 0);
            Abilities.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 30, 0);
        }
        Scheduler.schedule(10, () -> pool(p, c, radius, damage, pulses - 1));
    }

    public static void acidSpit(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        LivingEntity t = nearest(p, 17, 0.93);
        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        Vec3 to = t != null ? center(t) : p.pick(17, 1.0f, false).getLocation();
        Fx.beam(l, Fx.dust(0xA6E22E, 1.4f), from, to, 0.35);
        Fx.burst(l, ParticleTypes.ITEM_SLIME, to, 20, 0.4, 0.05);
        Fx.burst(l, Fx.dust(0xA6E22E, 1.5f), to, 16, 0.4, 0.05);
        if (t != null) {
            Abilities.hurt(p, t, 6 * power);
            Abilities.effect(t, MobEffects.POISON, 100, 0);
            Abilities.effect(t, MobEffects.WEAKNESS, 100, 0);
        }
        Abilities.sound(p, SoundEvents.SLIME_SQUISH, 0.8f);
    }

    public static void acidPool(ServerPlayer p, double power) {
        Vec3 c = p.position().add(Abilities.flat(p.getLookAngle()).scale(4));
        Abilities.sound(p, SoundEvents.SLIME_SQUISH, 0.5f);
        pool(p, c, 3.0, 2.0 * power, 10);
    }

    public static void acidFlood(ServerPlayer p, double power) {
        Abilities.effect(p, MobEffects.MOVEMENT_SPEED, 300, 1);
        Abilities.effect(p, MobEffects.DAMAGE_RESISTANCE, 160, 0);
        Abilities.sound(p, SoundEvents.SLIME_SQUISH, 0.4f);
        Fx.sphere(Abilities.level(p), Fx.dust(0xA6E22E, 1.6f), p.position().add(0, 1, 0), 1.3, 40);
        pool(p, p.position(), 6.5, 3.0 * power, 16);
    }

    // ------------------------------------------------------------------ Earphone Jack

    public static void heartbeatPulse(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        Vec3 look = p.getLookAngle();
        for (LivingEntity t : Abilities.cone(p, 9, 0.8)) {
            Abilities.hurt(p, t, 6 * power);
            Abilities.effect(t, MobEffects.CONFUSION, 100, 0);
            Abilities.shove(t, Abilities.flat(look), 1.3, 0.3);
        }
        Vec3 from = p.getEyePosition();
        for (int i = 1; i <= 9; i += 2) {
            Vec3 at = from.add(look.scale(i));
            Abilities.particles(p, ParticleTypes.NOTE, at, 2, 0.3 + i * 0.05, 1);
            Fx.ring(l, Fx.dust(0xFF69B4, 1.2f), at, 0.6 + i * 0.15, 10);
        }
        Abilities.particles(p, ParticleTypes.SONIC_BOOM, from.add(look.scale(2)), 1, 0, 0);
        Abilities.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f);
    }

    public static void soundSense(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        Abilities.effect(p, MobEffects.NIGHT_VISION, 400, 0);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24),
                x -> x != p && x.isAlive())) {
            Abilities.effect(e, MobEffects.GLOWING, 200, 0);
        }
        for (int r = 4; r <= 24; r += 4) Fx.ring(l, Fx.dust(0xFF69B4, 1.0f), p.position().add(0, 0.2, 0), r, 12 + r * 2);
        Abilities.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f);
    }

    public static void heartbeatFuzz(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        Vec3 c = p.position();
        for (LivingEntity t : Abilities.around(p, c, 10)) {
            Abilities.hurt(p, t, 10 * power);
            Abilities.effect(t, MobEffects.CONFUSION, 160, 0);
            Abilities.effect(t, MobEffects.WEAKNESS, 160, 0);
            Vec3 away = t.position().subtract(c);
            Abilities.shove(t, away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize(), 1.6, 0.5);
        }
        for (int r = 2; r <= 10; r += 2) {
            Fx.ring(l, Fx.dust(0xFF69B4, 1.5f), c.add(0, 0.4, 0), r, 12 + r * 5);
            Fx.ring(l, ParticleTypes.NOTE, c.add(0, 1.0, 0), r, 8 + r * 2);
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Abilities.particles(p, ParticleTypes.SONIC_BOOM, c.add(Math.cos(a) * 4, 1.2, Math.sin(a) * 4), 1, 0, 0);
        }
        Abilities.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.2f);
    }

    // ------------------------------------------------------------------ Tape

    public static void tapeShot(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        LivingEntity t = nearest(p, 15, 0.92);
        Vec3 from = p.getEyePosition().add(0, -0.4, 0);
        Vec3 to = t != null ? center(t) : from.add(p.getLookAngle().scale(10));
        Fx.beam(l, Fx.dust(0xD0D0D0, 1.3f), from, to, 0.3);
        if (t != null) {
            Abilities.hurt(p, t, 4 * power);
            Abilities.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 80, 4);
            pull(t, p, 0.7);
            Fx.sphere(l, Fx.dust(0xD0D0D0, 1.2f), center(t), 0.7, 14);
        }
        Abilities.sound(p, SoundEvents.SLIME_SQUISH, 1.6f);
    }

    public static void tapeSwing(ServerPlayer p, double power) {
        boolean hooked = Abilities.grapple(p, 28, 2.0);
        ServerLevel l = Abilities.level(p);
        if (!hooked) {
            Vec3 from = p.getEyePosition().add(0, -0.4, 0);
            Fx.beam(l, Fx.dust(0xD0D0D0, 1.2f), from, from.add(p.getLookAngle().scale(8)), 0.4);
        }
    }

    public static void tapeCocoon(ServerPlayer p, double power) {
        ServerLevel l = Abilities.level(p);
        for (LivingEntity t : Abilities.around(p, p.position(), 7.5)) {
            Abilities.hurt(p, t, 5 * power);
            Abilities.effect(t, MobEffects.MOVEMENT_SLOWDOWN, 100, 7);
            Abilities.effect(t, MobEffects.WEAKNESS, 100, 1);
            Fx.sphere(l, Fx.dust(0xD0D0D0, 1.5f), center(t), 1.0, 30);
            Fx.beam(l, Fx.dust(0xD0D0D0, 1.1f), p.getEyePosition().add(0, -0.4, 0), center(t), 0.4);
        }
        Abilities.effect(p, MobEffects.DAMAGE_RESISTANCE, 100, 0);
        Fx.ring(l, Fx.dust(0xD0D0D0, 1.5f), p.position().add(0, 0.2, 0), 7.5, 40);
        Abilities.sound(p, SoundEvents.SLIME_SQUISH, 0.5f);
    }
}
