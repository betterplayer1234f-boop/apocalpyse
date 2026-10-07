package com.lastsunset.network;

import com.lastsunset.LastSunset;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** An emergency broadcast shown in the radio panel. */
public record BroadcastPayload(int style, String header, String body, int duration) implements CustomPacketPayload {
	public static final int BULLETIN = 0;
	public static final int EMERGENCY = 1;
	public static final int FINAL = 2;
	public static final int GLITCH = 3;
	public static final int DAWN = 4;

	public static final Type<BroadcastPayload> TYPE = new Type<>(LastSunset.id("broadcast"));
	public static final StreamCodec<FriendlyByteBuf, BroadcastPayload> CODEC = CustomPacketPayload.codec(BroadcastPayload::write, BroadcastPayload::new);

	private BroadcastPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(style);
		buf.writeUtf(header);
		buf.writeUtf(body);
		buf.writeVarInt(duration);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
