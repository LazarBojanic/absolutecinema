package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;
import com.lazar.absolutecinema.semantic.ResolvedType;

public final class Logical implements Expr {
	public final Expr left;
	public final Token op;
	public final Expr right;
	private ResolvedType resolvedType;

	public Logical(Expr left, Token op, Expr right) {
		this.left = left;
		this.op = op;
		this.right = right;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitLogical(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}