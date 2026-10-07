package com.lastsunset.network;

import com.lastsunset.LastSunset;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Periodic snapshot of the apocalypse clock, sent to every player. */
public record SyncPayload(int stage, long elapsed, long duration, int stageTicks, int stageLength, boolean paused, float requiredShielding)
	implements CustomPacketPayload {
	public static final Type<SyncPayload> TYPE = new Type<>(LastSunset.id("sync"));
	public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = CustomPacketPayload.codec(SyncPayload::write, SyncPayload::new);

	private SyncPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readFloat());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(stage);
		buf.writeLong(elapsed);
		buf.writeLong(duration);
		buf.writeVarInt(stageTicks);
		buf.writeVarInt(stageLength);
		buf.writeBoolean(paused);
		buf.writeFloat(requiredShielding);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
