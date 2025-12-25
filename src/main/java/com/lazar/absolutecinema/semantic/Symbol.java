package com.lazar.absolutecinema.semantic;

import com.lazar.absolutecinema.parser.ast.LType;

public class Symbol {
	public String name;
	public LType type;

	public Symbol(String name, LType type) {
		this.name = name;
		this.type = type;
	}
	public Symbol() {

	}
}

