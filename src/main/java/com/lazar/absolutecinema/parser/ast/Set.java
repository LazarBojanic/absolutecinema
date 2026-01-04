package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;
import com.lazar.absolutecinema.semantic.ResolvedType;

public final class Set implements Expr {
	public final Expr object;
	public final Token name;
	public final Token op;
	public final Expr value;
	private ResolvedType resolvedType;

	public Set(Expr object, Token name, Token op, Expr value) {
		this.object = object;
		this.name = name;
		this.op = op;
		this.value = value;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitSet(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}
