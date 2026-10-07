package com.lastsunset.network;

import com.lastsunset.LastSunset;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A one-shot cinematic cue for the client (quake, siren, impact flash...). */
public record CuePayload(int cue, float strength, int duration, double x, double y, double z) implements CustomPacketPayload {
	public static final int QUAKE = 0;
	public static final int SIREN = 1;
	/** strength = shelter status of the receiving player (0 safe, 1 partial, 2 exposed, 3 observer). */
	public static final int IMPACT = 2;
	public static final int VAPORIZE = 3;
	public static final int BREACH = 4;
	public static final int METEOR_IMPACT = 5;
	public static final int DAWN = 6;
	public static final int SUPPLY_DROP = 7;
	public static final int LIGHTNING = 8;

	public static final Type<CuePayload> TYPE = new Type<>(LastSunset.id("cue"));
	public static final StreamCodec<FriendlyByteBuf, CuePayload> CODEC = CustomPacketPayload.codec(CuePayload::write, CuePayload::new);

	public CuePayload(int cue, float strength, int duration) {
		this(cue, strength, duration, 0, 0, 0);
	}

	private CuePayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(cue);
		buf.writeFloat(strength);
		buf.writeVarInt(duration);
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
