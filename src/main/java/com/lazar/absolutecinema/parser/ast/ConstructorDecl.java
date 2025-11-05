package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

import java.util.List;

public final class ConstructorDecl implements Node {
	public final Token name;
	public final List<Param> params;
	public final Block body;

	public ConstructorDecl(Token name, List<Param> params, Block body) {
		this.name = name;
		this.params = params;
		this.body = body;
	}
}