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
	private File sourceFile;
	private String sourceCode;
	private Lexer lexer;
	private Parser parser;
	private SemanticAnalyzer semanticAnalyzer;
	private boolean codeGen;
	private Generator generator;
	private GenerationResult generationResult;

	public App(String[] args) {
		try {
			if (args.length == 2) {
				sourceFile = Util.loadFileFromResources(args[0]);
				sourceCode = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
				codeGen = Boolean.parseBoolean(args[1]);
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
			if(codeGen){
				System.out.println("Generating IR...");
				generator = new Generator();
				generationResult = generator.generate(program);
				Util.writeStringToFile(generationResult.getPlainTextIR(), "./Main.j");
				Util.writeBytesToFile(generationResult.getBinaryIR(), "./Main.class");
				System.out.println("IR generation successful!");
			}
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
			e.printStackTrace();
		}
	}
}
