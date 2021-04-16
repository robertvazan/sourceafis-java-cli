// Part of SourceAFIS for Java CLI: https://sourceafis.machinezoo.com/java
package com.machinezoo.sourceafis.cli.utils.args;

import java.util.*;
import it.unimi.dsi.fastutil.ints.*;

public class CommandGroup {
	final Map<String, CommandGroup> subcommands = new HashMap<>();
	final Int2ObjectMap<Command> overloads = new Int2ObjectOpenHashMap<>();
	void register(int depth, Command command) {
		if (depth < command.path.size())
			subcommands.computeIfAbsent(command.path.get(depth), c -> new CommandGroup()).register(depth + 1, command);
		else
			overloads.put(command.parameters.size(), command);
	}
}