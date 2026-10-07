package com.lastsunset.client;

import com.lastsunset.LastSunset;
import com.lastsunset.network.BroadcastPayload;
import com.lastsunset.network.CuePayload;
import com.lastsunset.network.RosterPayload;
import com.lastsunset.network.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;

public class LastSunsetClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> ClientState.onSync(payload));
		ClientPlayNetworking.registerGlobalReceiver(CuePayload.TYPE, (payload, context) -> ClientState.onCue(payload));
		ClientPlayNetworking.registerGlobalReceiver(BroadcastPayload.TYPE, (payload, context) -> ClientState.onBroadcast(payload));
		ClientPlayNetworking.registerGlobalReceiver(RosterPayload.TYPE, (payload, context) -> ClientState.onRoster(payload));

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
		// The heliometer's needle is an item-model property driven by the state of the sun.
		RangeSelectItemModelProperties.ID_MAPPER.put(LastSunset.id("solar_flux"), SolarFluxProperty.MAP_CODEC);
	}
}
