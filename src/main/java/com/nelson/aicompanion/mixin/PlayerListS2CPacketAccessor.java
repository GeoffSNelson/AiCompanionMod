package com.nelson.aicompanion.mixin;

import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.EnumSet;
import java.util.List;

@Mixin(PlayerListS2CPacket.class)
public interface PlayerListS2CPacketAccessor {
    @Mutable
    @Accessor("actions")
    void aicompanion$setActions(EnumSet<PlayerListS2CPacket.Action> actions);

    @Mutable
    @Accessor("entries")
    void aicompanion$setEntries(List<PlayerListS2CPacket.Entry> entries);
}
