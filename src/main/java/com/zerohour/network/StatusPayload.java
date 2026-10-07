package com.zerohour.network;

import com.zerohour.ZeroHour;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What is going on in one player's body, sent to that player once a second: absorbed radiation (rad),
 * the dose rate right now (rad/s), how far an infection has progressed (0..1, or -1 if healthy),
 * ticks of iodine protection left, and whether they have been evacuated.
 */
public record StatusPayload(float dose, float rate, float infection, int iodineTicks, boolean evacuated) implements CustomPacketPayload {
	public static final Type<StatusPayload> TYPE = new Type<>(ZeroHour.id("status"));
	public static final StreamCodec<FriendlyByteBuf, StatusPayload> CODEC = CustomPacketPayload.codec(StatusPayload::write, StatusPayload::new);

	private StatusPayload(FriendlyByteBuf buf) {
		this(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeFloat(dose);
		buf.writeFloat(rate);
		buf.writeFloat(infection);
		buf.writeVarInt(iodineTicks);
		buf.writeBoolean(evacuated);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
