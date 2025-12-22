package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

import java.util.ArrayList;
import java.util.List;

public final class TypeRef {
	public final Token name;
	public final List<Token> arrayCapacities = new ArrayList<>();

	public TypeRef(Token name, List<Token> arrayCapacities) {
		this.name = name;
		this.arrayCapacities.addAll(arrayCapacities);
	}

	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(name);

		for (Token cap : arrayCapacities) {
			stringBuilder.append("[").append(cap).append("]");
		}
		return stringBuilder.toString();
	}
}