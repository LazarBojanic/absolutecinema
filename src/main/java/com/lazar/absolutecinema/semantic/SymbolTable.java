package com.lazar.absolutecinema.semantic;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    public final SymbolTable parent;
    public final Map<String, Symbol> symbols = new HashMap<>();

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
    }

    public void define(Symbol symbol) {
        if (symbols.containsKey(symbol.name)) {
            throw new RuntimeException("Variable '" + symbol.name + "' already defined in this scope.");
        }
        symbols.put(symbol.name, symbol);
    }

    public Symbol resolve(String name) {
	    if (symbols.containsKey(name)) {
		    return symbols.get(name);
	    }
	    if (parent != null) {
		    return parent.resolve(name);
	    }
        return null;
    }

}