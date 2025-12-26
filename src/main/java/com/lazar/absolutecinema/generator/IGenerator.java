package com.lazar.absolutecinema.generator;

import com.lazar.absolutecinema.parser.ast.Program;

public interface IGenerator {
	GenerationResult generate(Program program);
}