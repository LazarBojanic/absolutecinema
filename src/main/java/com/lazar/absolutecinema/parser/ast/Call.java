package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.semantic.ResolvedType;

import java.util.List;

public final class Call implements Expr {
	public final Expr callee;
	public final List<Expr> arguments;
	private ResolvedType resolvedType;

	public Call(Expr callee, List<Expr> arguments) {
		this.callee = callee;
		this.arguments = arguments;
	}

	@Override public <R> R accept(ExprVisitor<R> v) { return v.visitCall(this); }
	@Override public void setType(ResolvedType type) { this.resolvedType = type; }
	@Override public ResolvedType getType() { return resolvedType; }
}