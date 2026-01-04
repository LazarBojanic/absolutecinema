package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;
import com.lazar.absolutecinema.semantic.ResolvedType;

public final class Get implements Expr {
	public final Expr object;
	public final Token name;
	private ResolvedType resolvedType;

	public Get(Expr object, Token name) {
		this.object = object;
		this.name = name;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitGet(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}