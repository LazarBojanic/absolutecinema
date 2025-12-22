package com.lazar.absolutecinema.parser.ast;

import java.util.List;

public final class Block implements Stmt {
	public final List<Node> statements;

	public Block(List<Node> statements) {
		this.statements = statements;
	}

	@Override
	public <R> R accept(StmtVisitor<R> v) {
		return v.visitBlock(this);
	}
}