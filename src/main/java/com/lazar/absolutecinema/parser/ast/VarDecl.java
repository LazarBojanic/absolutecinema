package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

public final class VarDecl implements Decl {
	public final Token name;
	public final TypeRef type;
	public final Expr initializer;

	public VarDecl(Token name, TypeRef type, Expr initializer) {
		this.name = name;
		this.type = type;
		this.initializer = initializer;
	}

	@Override
	public <R> R accept(DeclVisitor<R> v) {
		return v.visitVar(this);
	}
}