package com.lazar.absolutecinema.parser.ast;

public final class For implements Stmt {
	public final Node initializer; public final Expr condition; public final Expr increment; public final Stmt body;
	public For(Node initializer, Expr condition, Expr increment, Stmt body) { this.initializer = initializer; this.condition = condition; this.increment = increment; this.body = body; }
	@Override public <R> R accept(StmtVisitor<R> v) { return v.visitFor(this); }
}