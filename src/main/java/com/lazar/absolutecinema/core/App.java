package com.lazar.absolutecinema.core;

import com.lazar.absolutecinema.generator.*;
import com.lazar.absolutecinema.lexer.Lexer;
import com.lazar.absolutecinema.parser.Parser;
import com.lazar.absolutecinema.parser.ast.Program;
import com.lazar.absolutecinema.semantic.SemanticAnalyzer;
import com.lazar.absolutecinema.util.AstJsonConverter;
import com.lazar.absolutecinema.util.JsonAstSwingViewer;
import com.lazar.absolutecinema.util.Util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class App {
	private Lexer lexer;
	private Parser parser;
	private SemanticAnalyzer semanticAnalyzer;
	private IGenerator generator;
	private File sourceFile;
	private String sourceCode;
	private GeneratorType generatorType;
	private GeneratorMode generatorMode;
	private GenerationResult generationResult;

	public App(String[] args) {
		try {
			if (args.length == 3) {
				sourceFile = Util.loadFileFromResources(args[0]);
				sourceCode = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
				generatorType = GeneratorType.valueOf(args[1].toUpperCase());
				generatorMode = GeneratorMode.valueOf(args[2].toUpperCase());
			}
			else {
				throw new IllegalArgumentException("Invalid number of arguments");
			}
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	public void run() {
		try {
			System.out.println("Lexing...");
			lexer = new Lexer(sourceCode);
			var tokens = lexer.lex();
			Util.printTokenTable(tokens);
			System.out.println("Lexing successful!");
			System.out.println("Parsing...");
			parser = new Parser(tokens);
			Program program = parser.parseProgram();
			System.out.println("Parsing successful!");
			System.out.println("Performing semantic analysis...");

			semanticAnalyzer = new SemanticAnalyzer(program);
			semanticAnalyzer.analyze();

			System.out.println("Semantic analysis successful!");
			System.out.println("Generating IR...");
			if (generatorType.equals(GeneratorType.JVM)) {
				generator = new JVMGenerator(generatorMode);
			}
			else {
				generator = new LLVMGenerator(generatorMode);
			}
			generationResult = generator.generate(program);
			if (generator instanceof JVMGenerator) {
				Util.writeStringToFile(generationResult.getPlainTextIR(), "./Output.j");
				Util.writeBytesToFile(generationResult.getBinaryIR(), "./Output.class");
			}
			else {
				Util.writeStringToFile(generationResult.getPlainTextIR(), "./Output.ll");
				Util.writeBytesToFile(generationResult.getBinaryIR(), "./Output.bc");
			}
			System.out.println("IR generation successful!");
			System.out.println("Converting AST to JSON...");
			AstJsonConverter printer = new AstJsonConverter();
			String json = printer.convert(program);
			Files.writeString(Path.of("./ast.json"), json);
			System.out.println("AST to JSON conversion successful!");
			System.out.println("AST printed to ./ast.json");
			System.out.println("Showing AST in Swing...");
			Path jsonFile = Path.of("ast.json");
			JsonAstSwingViewer.show(jsonFile);
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
}
