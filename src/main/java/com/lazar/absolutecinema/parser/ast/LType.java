package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

import java.util.ArrayList;
import java.util.List;

public final class LType {
	public final Token name;
	public final Integer dimension;
	public LType(Token name, Integer dimension) {
		this.name = name;
		this.dimension = dimension;
	}
}