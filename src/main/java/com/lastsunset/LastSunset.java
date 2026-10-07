package com.lastsunset;

import com.lastsunset.content.ModBlocks;
import com.lastsunset.content.ModItems;
import com.lastsunset.content.ModSounds;
import com.lastsunset.network.BroadcastPayload;
import com.lastsunset.network.CuePayload;
import com.lastsunset.network.RosterPayload;
import com.lastsunset.network.SyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LastSunset implements ModInitializer {
	public static final String MOD_ID = "lastsunset";
	public static final Logger LOGGER = LoggerFactory.getLogger("Last Sunset");

	public static final ResourceKey<DamageType> SOLAR_FLARE = ResourceKey.create(Registries.DAMAGE_TYPE, id("solar_flare"));
	public static final ResourceKey<DamageType> FIRESTORM = ResourceKey.create(Registries.DAMAGE_TYPE, id("firestorm"));
	public static final ResourceKey<DamageType> SHELTER_BREACH = ResourceKey.create(Registries.DAMAGE_TYPE, id("shelter_breach"));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModBlocks.init();
		ModItems.init();
		PayloadTypeRegistry.playS2C().register(SyncPayload.TYPE, SyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CuePayload.TYPE, CuePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BroadcastPayload.TYPE, BroadcastPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RosterPayload.TYPE, RosterPayload.CODEC);

		ApocalypseManager.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ApocalypseCommand.register(dispatcher));
		LOGGER.info("The sun is still shining. For now.");
	}
}
