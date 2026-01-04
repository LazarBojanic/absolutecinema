package com.lazar.absolutecinema.generator;

import com.lazar.absolutecinema.parser.ast.*;
import org.objectweb.asm.*;

import java.util.*;

public class JVMGenerator implements IGenerator {
	private final GeneratorMode generatorMode;

	public JVMGenerator(GeneratorMode generatorMode) {
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
		return new GenerationResult("; Library mode not implemented.", new byte[0]);
	}

	private GenerationResult generateManually(Program program) {
		return new GenerationResult("; Manual mode not implemented.", new byte[0]);
	}
}