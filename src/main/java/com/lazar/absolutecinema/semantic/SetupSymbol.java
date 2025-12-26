package com.lazar.absolutecinema.semantic;

import java.util.HashMap;
import java.util.Map;

class SetupSymbol extends Symbol {
	final Map<String, Symbol> members = new HashMap<>();

	SetupSymbol(String name) {
		super(name, new Type(name, 0));
	}
}