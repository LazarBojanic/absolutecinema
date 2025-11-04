package com.lazar.absolutecinema.parser.ast;

public final class ExprStmt implements Stmt {
	public final Expr expr;

	public ExprStmt(Expr expr) {
		this.expr = expr;
	}

	@Override
	public <R> R accept(StmtVisitor<R> v) {
		return v.visitExpr(this);
	}
}