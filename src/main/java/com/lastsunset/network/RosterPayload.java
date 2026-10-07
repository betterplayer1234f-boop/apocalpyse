package com.lastsunset.network;

import com.lastsunset.LastSunset;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Shelter status of every player, for the survivor manifest. */
public record RosterPayload(List<Entry> entries) implements CustomPacketPayload {
	public static final int SAFE = 0;
	public static final int PARTIAL = 1;
	public static final int EXPOSED = 2;
	public static final int FALLEN = 3;
	public static final int OBSERVER = 4;

	public record Entry(String name, int status, int percent) {
	}

	public static final Type<RosterPayload> TYPE = new Type<>(LastSunset.id("roster"));
	public static final StreamCodec<FriendlyByteBuf, RosterPayload> CODEC = CustomPacketPayload.codec(RosterPayload::write, RosterPayload::read);

	private static RosterPayload read(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Entry> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(new Entry(buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));
		}
		return new RosterPayload(list);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(entries.size());
		for (Entry e : entries) {
			buf.writeUtf(e.name, 64);
			buf.writeVarInt(e.status);
			buf.writeVarInt(e.percent);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
