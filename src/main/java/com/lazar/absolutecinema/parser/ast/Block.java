package com.lazar.absolutecinema.parser.ast;

public final class Block implements Stmt {
	public final java.util.List<Node> statements;

	public Block(java.util.List<Node> statements) {
		this.statements = statements;
	}

	@Override
	public <R> R accept(StmtVisitor<R> v) {
		return v.visitBlock(this);
	}
}