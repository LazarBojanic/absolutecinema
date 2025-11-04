package com.lazar.absolutecinema.parser.ast;

public final class While implements Stmt {
	public final Expr condition;
	public final Stmt body;

	public While(Expr condition, Stmt body) {
		this.condition = condition;
		this.body = body;
	}

	@Override
	public <R> R accept(StmtVisitor<R> v) {
		return v.visitWhile(this);
	}
}