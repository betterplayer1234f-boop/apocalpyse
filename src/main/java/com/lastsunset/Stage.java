package com.lastsunset;

/**
 * The phases of the end of the world, in order.
 * Countdown stages cover a fraction of the total countdown; the rest have fixed or configurable lengths.
 */
public enum Stage {
	IDLE("Calm", "", 0xFFFFFFFF, 0f, 0f),
	OMENS("Omens", "Something is wrong with the sun.", 0xFFFFD27A, 0f, 0.20f),
	EVACUATION("Evacuation", "Leave the surface. Dig deep. Seal the way in.", 0xFFFFA040, 0.20f, 0.55f),
	COLLAPSE("Collapse", "The sky is burning.", 0xFFFF5A2A, 0.55f, 0.85f),
	FINAL_MINUTES("Final Minutes", "Get underground. Now.", 0xFFFF2020, 0.85f, 1.0f),
	IMPACT("Impact", "", 0xFFFFFFFF, 1f, 1f),
	AFTERMATH("Aftermath", "The surface is burning. Stay below.", 0xFFB04030, 1f, 1f),
	WASTELAND("Day One", "The fire is gone. Start again.", 0xFFE8C080, 1f, 1f);

	public static final int IMPACT_TICKS = 600;
	public static final int IMPACT_BOOM_TICK = 60;

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
		return this == OMENS || this == EVACUATION || this == COLLAPSE || this == FINAL_MINUTES;
	}

	public boolean isActive() {
		return this != IDLE;
	}

	/** Roman numeral shown on the stage title card. */
	public String numeral() {
		return switch (this) {
			case OMENS -> "I";
			case EVACUATION -> "II";
			case COLLAPSE -> "III";
			case FINAL_MINUTES -> "IV";
			case IMPACT -> "V";
			case AFTERMATH -> "VI";
			case WASTELAND -> "VII";
			default -> "";
		};
	}

	public static Stage forProgress(float progress) {
		if (progress < OMENS.end) return OMENS;
		if (progress < EVACUATION.end) return EVACUATION;
		if (progress < COLLAPSE.end) return COLLAPSE;
		return FINAL_MINUTES;
	}

	public static Stage byId(int id) {
		Stage[] values = values();
		return id >= 0 && id < values.length ? values[id] : IDLE;
	}
}
