package com.lazar.absolutecinema.parser.ast;

public final class Index implements Expr {
	public final Expr array;
	public final Expr index;

	public Index(Expr array, Expr index) {
		this.array = array;
		this.index = index;
	}

	@Override
	public <R> R accept(ExprVisitor<R> v) {
		return v.visitIndex(this);
	}
}