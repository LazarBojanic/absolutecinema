package com.lazar.absolutecinema.parser.ast;

import com.lazar.absolutecinema.lexer.Token;

import java.util.ArrayList;
import java.util.List;

public final class RType {
	public final Token name;
	public final List<Token> arrayCapacities;
	public final Integer dimension;

	public RType(Token name, List<Token> arrayCapacities) {
		this.name = name;
		this.arrayCapacities = new ArrayList<>();
		this.arrayCapacities.addAll(arrayCapacities);
		this.dimension = arrayCapacities.size();
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