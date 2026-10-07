package com.zerohour.network;

import com.zerohour.ZeroHour;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Periodic snapshot of the apocalypse clock and the places that matter, sent to every player. */
public record SyncPayload(int stage, long elapsed, long duration, int stageTicks, int stageLength, boolean paused, float requiredShielding,
	boolean hasStrike, int strikeX, int strikeY, int strikeZ,
	boolean hasLz, int lzX, int lzY, int lzZ, boolean evacOpen, int evacTicksLeft,
	boolean hasSafeZone, int safeX, int safeZ)
	implements CustomPacketPayload {
	public static final SyncPayload EMPTY = new SyncPayload(0, 0, 1, 0, 0, false, 24f, false, 0, 0, 0, false, 0, 0, 0, false, 0, false, 0, 0);
	public static final Type<SyncPayload> TYPE = new Type<>(ZeroHour.id("sync"));
	public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = CustomPacketPayload.codec(SyncPayload::write, SyncPayload::read);

	private static SyncPayload read(FriendlyByteBuf buf) {
		return new SyncPayload(buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readFloat(),
			buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
			buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readVarInt(),
			buf.readBoolean(), buf.readInt(), buf.readInt());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(stage);
		buf.writeLong(elapsed);
		buf.writeLong(duration);
		buf.writeVarInt(stageTicks);
		buf.writeVarInt(stageLength);
		buf.writeBoolean(paused);
		buf.writeFloat(requiredShielding);
		buf.writeBoolean(hasStrike);
		buf.writeInt(strikeX);
		buf.writeInt(strikeY);
		buf.writeInt(strikeZ);
		buf.writeBoolean(hasLz);
		buf.writeInt(lzX);
		buf.writeInt(lzY);
		buf.writeInt(lzZ);
		buf.writeBoolean(evacOpen);
		buf.writeVarInt(evacTicksLeft);
		buf.writeBoolean(hasSafeZone);
		buf.writeInt(safeX);
		buf.writeInt(safeZ);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
