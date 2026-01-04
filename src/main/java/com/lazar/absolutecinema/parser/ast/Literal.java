package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.semantic.ResolvedType;

public final class Literal implements Expr {
	public final Object value;
	private ResolvedType resolvedType;

	public Literal(Object value) {
		this.value = value;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitLiteral(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}