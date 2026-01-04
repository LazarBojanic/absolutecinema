package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.semantic.ResolvedType;

import java.util.List;

public final class ArrayLiteral implements Expr {
	public final List<Expr> elements;
	private ResolvedType resolvedType;

	public ArrayLiteral(List<Expr> elements) {
		this.elements = elements;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitArrayLiteral(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}