package com.noobfly.containersearcher;

import java.util.ArrayList;
import java.util.List;

// someone opened a remembered container while we were close enough to see the lid move
public final class ContainerEvent {
	public long time;
	// true when the player broke one half of a double chest instead of opening it
	public boolean broke;
	public List<String> players = new ArrayList<>();
}
