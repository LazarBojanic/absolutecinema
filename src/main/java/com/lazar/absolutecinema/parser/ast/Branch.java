package com.lazar.absolutecinema.parser.ast;

public class Branch {
	public final ConditionalType conditionalType;
	public final Expr cond;
	public final Block block;

	public Branch(ConditionalType conditionalType, Expr cond, Block block) {
		this.conditionalType = conditionalType;
		this.cond = cond;
		this.block = block;
	}
}