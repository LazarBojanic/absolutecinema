package com.lazar.absolutecinema.parser.ast;

public final class OtherIf implements Stmt {
	public final Expr condition;
	public final Stmt thenBranch;
	public final Stmt elseBranch;

	public OtherIf(Expr condition, Stmt thenBranch, Stmt elseBranch) {
		this.condition = condition;
		this.thenBranch = thenBranch;
		this.elseBranch = elseBranch;
	}

	@Override
	public <R> R accept(StmtVisitor<R> v) {
		return v.visitOtherIf(this);
	}
}