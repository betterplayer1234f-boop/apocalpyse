package com.zerohour;

/** A multi-tick server-side sequence (a falling shell, a mushroom cloud, a helicopter extraction...). */
@FunctionalInterface
public interface ServerTask {
	/** @return true once the task is finished. */
	boolean tick();
}
