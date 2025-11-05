package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

public final class Param {
	public final Token name;
	public final TypeRef type;

	public Param(Token name, TypeRef type) {
		this.name = name;
		this.type = type;
	}
}