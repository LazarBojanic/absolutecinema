package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.semantic.ResolvedType;

public final class Index implements Expr {
	public final Expr array;
	public final Expr index;
	private ResolvedType resolvedType;

	public Index(Expr array, Expr index) {
		this.array = array;
		this.index = index;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitIndex(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}