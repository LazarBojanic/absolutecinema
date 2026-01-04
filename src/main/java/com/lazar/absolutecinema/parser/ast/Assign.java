package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;
import com.lazar.absolutecinema.semantic.ResolvedType;

public final class Assign implements Expr {
	public final Expr target;
	public final Token op;
	public final Expr value;
	private ResolvedType resolvedType;

	public Assign(Expr target, Token op, Expr value) {
		this.target = target;
		this.op = op;
		this.value = value;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitAssign(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}
