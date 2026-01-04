package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;
import com.lazar.absolutecinema.semantic.ResolvedType;

public final class This implements Expr {
	public final Token atToken;
	private ResolvedType resolvedType;

	public This(Token atToken) {
		this.atToken = atToken;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitThis(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}