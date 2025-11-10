package com.lazar.absolutecinema.parser.ast;

public class IfBranch {
	public final Expr cond;
	public final Block thenBlock;

	public IfBranch(Expr cond, Block thenBlock) {
		this.cond = cond;
		this.thenBlock = thenBlock;
	}
}