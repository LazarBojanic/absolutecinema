package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

public final class Continue implements Stmt {
	public final Token keyword;

	public Continue(Token keyword) {
		this.keyword = keyword;
	}

	@Override
	public <R> R accept(StmtVisitor<R> v) {
		return v.visitContinue(this);
	}
}