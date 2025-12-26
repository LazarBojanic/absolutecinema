package com.lazar.absolutecinema.semantic;

import java.util.HashMap;
import java.util.Map;

abstract class Symbol {
	final String name;
	final Type type;

	Symbol(String name, Type type) {
		this.name = name;
		this.type = type;
	}
}