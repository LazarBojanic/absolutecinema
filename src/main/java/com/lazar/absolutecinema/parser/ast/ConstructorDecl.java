package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.model.Token;
import java.util.List;

public final class ConstructorDecl implements Node {
	public final Token name; public final java.util.List<Param> params; public final Block body;
	public ConstructorDecl(Token name, java.util.List<Param> params, Block body) {
		this.name = name; this.params = params; this.body = body;
	}
}