package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.semantic.ResolvedType;

public final class Grouping implements Expr {
	public final Expr expr;
	private ResolvedType resolvedType;

	public Grouping(Expr expr) {
		this.expr = expr;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitGrouping(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}