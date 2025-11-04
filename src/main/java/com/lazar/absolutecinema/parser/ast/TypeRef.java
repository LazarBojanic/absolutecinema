package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;

public final class TypeRef {
	public final Token name;
	public final int arrayDepth;
	public TypeRef(Token name, int arrayDepth) { this.name = name; this.arrayDepth = arrayDepth; }
	@Override public String toString() { return name.getLexeme() + "[]".repeat(arrayDepth); }
}