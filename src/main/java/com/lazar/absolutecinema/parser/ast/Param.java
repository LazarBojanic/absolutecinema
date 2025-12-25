package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

public final class Param {
	public final Token name;
	public final LType type;

	public Param(Token name, LType type) {
		this.name = name;
		this.type = type;
	}
}