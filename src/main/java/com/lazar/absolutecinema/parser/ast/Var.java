package com.lazar.absolutecinema.parser.ast;

public final class Var implements Stmt {
	public final VarDecl decl;

	public Var(VarDecl decl) {
		this.decl = decl;
	}

	@Override
	public <R> R accept(StmtVisitor<R> v) {
		return v.visitVar(this);
	}
}