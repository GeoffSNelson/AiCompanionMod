package com.nelson.aicompanion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;

@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerListS2CPacketAccessor {
    @Mutable
    @Accessor("actions")
    void aicompanion$setActions(EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions);

    @Mutable
    @Accessor("entries")
    void aicompanion$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);
}
