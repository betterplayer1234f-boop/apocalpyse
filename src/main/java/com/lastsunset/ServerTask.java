package com.lastsunset;

/** A multi-tick server-side sequence (a falling meteor, an opening fissure...). */
@FunctionalInterface
public interface ServerTask {
	/** @return true once the task is finished. */
	boolean tick();
}
