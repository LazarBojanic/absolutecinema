package com.lazar.absolutecinema.semantic;

import java.util.HashMap;
import java.util.Map;

class Scope {
	private final Scope parent;
	private final Map<String, Symbol> symbols = new HashMap<>();

	Scope(Scope parent) {
		this.parent = parent;
	}

	void define(Symbol symbol) {
		symbols.put(symbol.name, symbol);
	}

	Symbol resolve(String name) {
		Symbol s = symbols.get(name);
		if (s != null) {
			return s;
		}
		if (parent != null) {
			return parent.resolve(name);
		}
		return null;
	}

	Scope getParent() {
		return parent;
	}
}
