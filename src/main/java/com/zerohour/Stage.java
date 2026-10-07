package com.zerohour;

/**
 * The phases of the end of the world, in order.
 * Countdown stages cover a fraction of the total build-up; the rest have fixed or configurable lengths.
 */
public enum Stage {
	IDLE("Peace", "", 0xFFFFFFFF, 0f, 0f),
	TENSIONS("Rising Tensions", "The war is getting closer.", 0xFFD8D0B0, 0f, 0.20f),
	OUTBREAK("Outbreak", "The dead are walking. Don't let them bite you.", 0xFF9AC060, 0.20f, 0.55f),
	LAUNCH("Launch Detected", "Missiles are in the air. Dig a shelter.", 0xFFFFA040, 0.55f, 0.85f),
	INCOMING("Incoming", "Get underground. Now.", 0xFFFF2020, 0.85f, 1.0f),
	DETONATION("Detonation", "", 0xFFFFFFFF, 1f, 1f),
	FALLOUT("Fallout", "The surface is poisoned. Stay below.", 0xFF8AA050, 1f, 1f),
	EVACUATION("Evacuation", "Reach the landing zone.", 0xFFE8C080, 1f, 1f);

	/** Length of the detonation sequence. */
	public static final int DETONATION_TICKS = 600;
	/** When the warhead goes off: everyone is judged by where they stand at this moment. */
	public static final int FLASH_TICK = 60;
	/** When the blast wave sweeps through the area around the players (glass, fires, burning land). */
	public static final int SHOCK_TICK = FLASH_TICK + 40;

	public final String title;
	public final String subtitle;
	public final int color;
	public final float start;
	public final float end;

	Stage(String title, String subtitle, int color, float start, float end) {
		this.title = title;
		this.subtitle = subtitle;
		this.color = color;
		this.start = start;
		this.end = end;
	}

	public boolean isCountdown() {
		return this == TENSIONS || this == OUTBREAK || this == LAUNCH || this == INCOMING;
	}

	public boolean isActive() {
		return this != IDLE;
	}

	/** The bomb has already gone off (or is going off right now). */
	public boolean isPostStrike() {
		return this == DETONATION || this == FALLOUT || this == EVACUATION;
	}

	public static Stage forProgress(float progress) {
		if (progress < TENSIONS.end) return TENSIONS;
		if (progress < OUTBREAK.end) return OUTBREAK;
		if (progress < LAUNCH.end) return LAUNCH;
		return INCOMING;
	}

	public static Stage byId(int id) {
		Stage[] values = values();
		return id >= 0 && id < values.length ? values[id] : IDLE;
	}
}
