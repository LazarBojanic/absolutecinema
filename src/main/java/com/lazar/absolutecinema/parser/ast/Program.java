package com.lazar.absolutecinema.parser.ast;

import java.util.List;

public final class Program {
	public final List<Decl> declarations;

	public Program(List<Decl> declarations) {
		this.declarations = declarations;
	}
}