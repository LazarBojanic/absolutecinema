package com.lazar.absolutecinema.generator;

import com.lazar.absolutecinema.parser.ast.*;

public class LLVMGenerator implements IGenerator {
	private final GeneratorMode generatorMode;

	public LLVMGenerator(GeneratorMode generatorMode) {
		this.generatorMode = generatorMode;
	}

	@Override
	public GenerationResult generate(Program program) {
		if (generatorMode.equals(GeneratorMode.LIBRARY)) {
			return generateWithLibrary(program);
		}
		else {
			return generateManually(program);
		}
	}

	private GenerationResult generateWithLibrary(Program program) {
		return new GenerationResult("", new byte[0]);
	}

	private GenerationResult generateManually(Program program) {
		return new GenerationResult("", new byte[0]);
	}
}