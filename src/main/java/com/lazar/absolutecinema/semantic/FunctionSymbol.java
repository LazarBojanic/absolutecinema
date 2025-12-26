package com.lazar.absolutecinema.semantic;

import java.util.List;

class FunctionSymbol extends Symbol {
	final List<Type> params;

	FunctionSymbol(String name, Type returnType, List<Type> params) {
		super(name, returnType);
		this.params = params;
	}
}
