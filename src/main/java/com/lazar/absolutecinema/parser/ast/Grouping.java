package com.lazar.absolutecinema.parser.ast;

public final class Grouping implements Expr {
	public final Expr expr;

	public Grouping(Expr expr) {
		this.expr = expr;
	}

	@Override
	public <R> R accept(ExprVisitor<R> v) {
		return v.visitGrouping(this);
	}
}