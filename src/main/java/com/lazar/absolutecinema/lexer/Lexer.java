package com.lazar.absolutecinema.lexer;

import com.lazar.absolutecinema.model.Token;
import com.lazar.absolutecinema.model.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
	private static final Map<String, TokenType> keywords = new HashMap<>();

	static {
		keywords.put("var", TokenType.VAR);
		keywords.put("scene", TokenType.SCENE);
		keywords.put("cut", TokenType.CUT);
		keywords.put("scrap", TokenType.SCRAP);
		keywords.put("setup", TokenType.SETUP);
		keywords.put("action", TokenType.ACTION);
		keywords.put("if", TokenType.IF);
		keywords.put("else", TokenType.ELSE);
		keywords.put("keepRollingDuring", TokenType.KEEP_ROLLING_DURING);
		keywords.put("keepRollingIf", TokenType.KEEP_ROLLING_IF);
		keywords.put("skip", TokenType.SKIP);
		keywords.put("exit", TokenType.EXIT);
		// capture/project/entrance are NOT keywords so they can be used as identifiers/calls
		keywords.put("int", TokenType.INT);
		keywords.put("double", TokenType.DOUBLE);
		keywords.put("char", TokenType.CHAR);
		keywords.put("string", TokenType.STRING);
		keywords.put("true", TokenType.TRUE);
		keywords.put("false", TokenType.FALSE);
		keywords.put("null", TokenType.NULL);
	}

	private final String source;
	private final List<Token> tokens = new ArrayList<>();
	private int start = 0;
	private int current = 0;
	private int line = 1;
	private int column = 1;

	public Lexer(String source) {
		this.source = source != null ? source : "";
	}

	public List<Token> lex() {
		while (!isAtEnd()) {
			start = current;
			scanToken();
		}
		tokens.add(new Token(TokenType.EOF, "", null, line, column));
		return tokens;
	}

	private void scanToken() {
		char c = advance();
		switch (c) {
			case ' ':
			case '\r', '\t': {
				break;
			}
			case '\n': {
				line++;
				column = 1;
				break;
			}
			case '(': {
				add(TokenType.LEFT_PAREN);
				break;
			}
			case ')': {
				add(TokenType.RIGHT_PAREN);
				break;
			}
			case '{': {
				add(TokenType.LEFT_BRACE);
				break;
			}
			case '}': {
				add(TokenType.RIGHT_BRACE);
				break;
			}
			case '[': {
				add(TokenType.LEFT_BRACKET);
				break;
			}
			case ']': {
				add(TokenType.RIGHT_BRACKET);
				break;
			}
			case ',': {
				add(TokenType.COMMA);
				break;
			}
			case '.': {
				add(TokenType.DOT);
				break;
			}
			case ';': {
				add(TokenType.SEMICOLON);
				break;
			}
			case ':': {
				add(TokenType.COLON);
				break;
			}
			case '@': {
				add(TokenType.AT);
				break;
			}
			case '!': {
				add(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
				break;
			}
			case '=': {
				add(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
				break;
			}
			case '<': {
				add(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
				break;
			}
			case '>': {
				add(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
				break;
			}
			case '+': {
				if (match('+')) add(TokenType.PLUS_PLUS);
				else if (match('=')) add(TokenType.PLUS_EQUAL);
				else add(TokenType.PLUS);
				break;
			}
			case '-': {
				if (match('-')) add(TokenType.MINUS_MINUS);
				else if (match('=')) add(TokenType.MINUS_EQUAL);
				else add(TokenType.MINUS);
				break;
			}
			case '*': {
				add(match('=') ? TokenType.STAR_EQUAL : TokenType.STAR);
				break;
			}
			case '/': {
				add(match('=') ? TokenType.SLASH_EQUAL : TokenType.SLASH);
				break;
			}
			case '%': {
				add(match('=') ? TokenType.PERCENT_EQUAL : TokenType.PERCENT);
				break;
			}
			case '"': {
				string();
				break;
			}
			case '\'': {
				character();
				break;
			}
			default: {
				if (isDigit(c)) number();
				else if (isAlpha(c)) identifier();
				else error("Unexpected character: '" + c + "'");
			}
		}
	}

	private void number() {
		boolean isDouble = false;
		while (isDigit(peek())) {
			advance();
		}
		if (peek() == '.' && isDigit(peekNext())) {
			isDouble = true;
			do {
				advance();
			}
			while (isDigit(peek()));
		}
		String lexeme = source.substring(start, current);
		if (isDouble) {
			try {
				Double value = Double.parseDouble(lexeme);
				add(TokenType.DOUBLE_LITERAL, value);
			}
			catch (NumberFormatException ex) {
				error("Invalid double literal: " + lexeme);
			}
		}
		else {
			try {
				Integer value = Integer.parseInt(lexeme);
				add(TokenType.INT_LITERAL, value);
			}
			catch (NumberFormatException ex) {
				error("Invalid int literal: " + lexeme);
			}
		}
	}

	private void string() {
		StringBuilder sb = new StringBuilder();
		while (!isAtEnd() && peek() != '"') {
			char c = advance();
			if (c == '\\') {
				if (isAtEnd()) {
					error("Unterminated string escape");
					return;
				}
				char e = advance();
				switch (e) {
					case 'n': {
						sb.append('\n');
						break;
					}
					case 'r': {
						sb.append('\r');
						break;
					}
					case 't': {
						sb.append('\t');
						break;
					}
					case '\\': {
						sb.append('\\');
						break;
					}
					case '"': {
						sb.append('"');
						break;
					}
					case '\'': {
						sb.append('\'');
						break;
					}
					default: {
						sb.append(e);
						break;
					}
				}
			}
			else if (c == '\n') {
				line++;
				column = 1;
				sb.append('\n');
			}
			else {
				sb.append(c);
			}
		}
		if (isAtEnd()) {
			error("Unterminated string literal");
			return;
		}
		advance();
		add(TokenType.STRING_LITERAL, sb.toString());
	}

	private void character() {
		if (isAtEnd()) {
			error("Unterminated char literal");
			return;
		}
		char value;
		if (peek() == '\'') {
			advance();
			if (peek() != '\'') {
				error("Malformed empty char literal");
				return;
			}
			advance();
			add(TokenType.CHAR_LITERAL, "");
			return;
		}
		char c = advance();
		if (c == '\\') {
			if (isAtEnd()) {
				error("Unterminated char escape");
				return;
			}
			char e = advance();
			value = switch (e) {
				case 'n' -> '\n';
				case 'r' -> '\r';
				case 't' -> '\t';
				case '\\' -> '\\';
				case '\'' -> '\'';
				case '"' -> '"';
				default -> e;
			};
		}
		else {
			value = c;
		}
		if (peek() != '\'') {
			error("Unterminated char literal (missing closing quote)");
			return;
		}
		advance();
		add(TokenType.CHAR_LITERAL, value);
	}

	private boolean match(char expected) {
		if (isAtEnd()) {
			return false;
		}
		if (source.charAt(current) != expected) {
			return false;
		}
		advance();
		return true;
	}

	private char peek() {
		if (isAtEnd()) {
			return '\0';
		}
		return source.charAt(current);
	}

	private char peekNext() {
		if (current + 1 >= source.length()) {
			return '\0';
		}
		return source.charAt(current + 1);
	}

	private char advance() {
		char c = source.charAt(current++);
		column++;
		return c;
	}

	private void add(TokenType type) {
		add(type, null);
	}

	private void add(TokenType type, Object literal) {
		String text = source.substring(start, current);
		int tokenColumn = column - (current - start);
		tokens.add(new Token(type, text, literal, line, tokenColumn));
	}

	private void identifier() {
		while (isAlphaNumeric(peek())) advance();
		String text = source.substring(start, current);
		TokenType type = keywords.get(text);
		if (type == null) {
			add(TokenType.IDENTIFIER);
		}
		else {
			add(type);
		}
	}

	private boolean isAtEnd() {
		return current >= source.length();
	}

	private boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private boolean isAlpha(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}

	private boolean isAlphaNumeric(char c) {
		return isAlpha(c) || isDigit(c);
	}

	private void error(String message) {
		throw new RuntimeException("[Lexer] Line " + line + ", Col " + column + ": " + message);
	}
}