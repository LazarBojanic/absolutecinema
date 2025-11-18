package com.lazar.absolutecinema.core;

import com.lazar.absolutecinema.lexer.Lexer;
import com.lazar.absolutecinema.parser.Parser;
import com.lazar.absolutecinema.parser.ast.Program;
import com.lazar.absolutecinema.util.AstJsonConverter;
import com.lazar.absolutecinema.util.JsonAstSwingViewer;
import com.lazar.absolutecinema.util.Util;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class App {
	private static App instance;
	private Lexer lexer;
	private Parser parser;
	private File sourceFile;
	private String sourceCode;

	private App(String[] args) {
		try {
			String resourceName;
			if (args.length == 0) {
				System.out.println("Please provide a resource name");
				System.exit(1);
				return;
			}
			else {
				resourceName = args[0];
			}

			try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
				if (inputStream == null) {
					System.out.println("Resource does not exist: " + resourceName);
					System.exit(1);
				}
				sourceCode = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			}
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	public static App getInstance(String[] args) {
		if (instance == null) {
			instance = new App(args);
		}
		return instance;
	}

	public void run() {
		try {
			lexer = new Lexer(sourceCode);
			var tokens = lexer.lex();
			Util.printTokenTable(tokens);
			parser = new Parser(tokens);
			Program program = parser.parseProgram();

			AstJsonConverter printer = new AstJsonConverter();
			String json = printer.convert(program);
			Files.writeString(Path.of("./ast.json"), json);
			System.out.println("AST printed to ./ast.json");

			Path jsonFile = Path.of("ast.json");

			JsonAstSwingViewer.show(jsonFile);

		}
		catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
}
