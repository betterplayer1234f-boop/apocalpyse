package com.zerohour.client;

import com.zerohour.ZeroHour;
import com.zerohour.network.BroadcastPayload;
import com.zerohour.network.CuePayload;
import com.zerohour.network.RosterPayload;
import com.zerohour.network.StatusPayload;
import com.zerohour.network.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;

public class ZeroHourClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> ClientState.onSync(payload));
		ClientPlayNetworking.registerGlobalReceiver(CuePayload.TYPE, (payload, context) -> ClientState.onCue(payload));
		ClientPlayNetworking.registerGlobalReceiver(BroadcastPayload.TYPE, (payload, context) -> ClientState.onBroadcast(payload));
		ClientPlayNetworking.registerGlobalReceiver(RosterPayload.TYPE, (payload, context) -> ClientState.onRoster(payload));
		ClientPlayNetworking.registerGlobalReceiver(StatusPayload.TYPE, (payload, context) -> ClientState.onStatus(payload));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.isPaused()) return;
			ClientState.tick(mc);
			ClientEffects.tick(mc);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			ClientState.reset();
			ClientEffects.reset();
		});
		HudOverlay.register();
		// The Geiger counter's needle is an item-model property driven by the radiation around you.
		RangeSelectItemModelProperties.ID_MAPPER.put(ZeroHour.id("radiation"), RadiationProperty.MAP_CODEC);
	}
}
