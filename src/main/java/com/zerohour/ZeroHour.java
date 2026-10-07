package com.zerohour;

import com.zerohour.content.ModBlocks;
import com.zerohour.content.ModItems;
import com.zerohour.content.ModSounds;
import com.zerohour.network.BroadcastPayload;
import com.zerohour.network.CuePayload;
import com.zerohour.network.RosterPayload;
import com.zerohour.network.StatusPayload;
import com.zerohour.network.SyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZeroHour implements ModInitializer {
	public static final String MOD_ID = "zerohour";
	public static final Logger LOGGER = LoggerFactory.getLogger("Zero Hour");

	public static final ResourceKey<DamageType> NUCLEAR_BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, id("nuclear_blast"));
	public static final ResourceKey<DamageType> SHELTER_BREACH = ResourceKey.create(Registries.DAMAGE_TYPE, id("shelter_breach"));
	public static final ResourceKey<DamageType> RADIATION = ResourceKey.create(Registries.DAMAGE_TYPE, id("radiation"));
	public static final ResourceKey<DamageType> INFECTION = ResourceKey.create(Registries.DAMAGE_TYPE, id("infection"));

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
		PayloadTypeRegistry.playS2C().register(StatusPayload.TYPE, StatusPayload.CODEC);

		ApocalypseManager.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ApocalypseCommand.register(dispatcher));
		LOGGER.info("The world is still at peace. For now.");
	}
}
