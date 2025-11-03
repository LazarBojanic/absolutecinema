package com.lazar.absolutecinema.lexer;

import com.lazar.absolutecinema.model.Token;
import com.lazar.absolutecinema.model.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
	private final String source;
	private final List<Token> tokens = new ArrayList<>();

	private int start = 0;
	private int current = 0;
	private int line = 1;
	private int column = 1;

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
		keywords.put("capture", TokenType.CAPTURE);
		keywords.put("project", TokenType.PROJECT);
		keywords.put("entrance", TokenType.ENTRANCE);
		keywords.put("int", TokenType.INT);
		keywords.put("double", TokenType.DOUBLE);
		keywords.put("char", TokenType.CHAR);
		keywords.put("string", TokenType.STRING);
	}

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
			case ' ', '\r', '\t' -> {
			}
			case '\n' -> {
				line++;
				column = 1;
			}
			case '(' -> add(TokenType.LEFT_PAREN);
			case ')' -> add(TokenType.RIGHT_PAREN);
			case '{' -> add(TokenType.LEFT_BRACE);
			case '}' -> add(TokenType.RIGHT_BRACE);
			case '[' -> add(TokenType.LEFT_BRACKET);
			case ']' -> add(TokenType.RIGHT_BRACKET);
			case ',' -> add(TokenType.COMMA);
			case '.' -> add(TokenType.DOT);
			case ';' -> add(TokenType.SEMICOLON);
			case ':' -> add(TokenType.COLON);
			case '@' -> add(TokenType.AT);
			case '!' -> add(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
			case '=' -> add(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
			case '<' -> add(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
			case '>' -> add(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
			case '+' -> {
				if (match('+')) {
					add(TokenType.PLUS_PLUS);
				}
				else if (match('=')) {
					add(TokenType.PLUS_EQUAL);
				}
				else {
					add(TokenType.PLUS);
				}
			}
			case '-' -> {
				if (match('-')) {
					add(TokenType.MINUS_MINUS);
				}
				else if (match('=')) {
					add(TokenType.MINUS_EQUAL);
				}
				else {
					add(TokenType.MINUS);
				}
			}
			case '*' -> add(match('=') ? TokenType.STAR_EQUAL : TokenType.STAR);
			case '/' -> add(match('=') ? TokenType.SLASH_EQUAL : TokenType.SLASH);
			case '%' -> add(match('=') ? TokenType.PERCENT_EQUAL : TokenType.PERCENT);
			case '&' -> {
				if (match('&')) {
					add(TokenType.AND_AND);
				}
				else {
					error("Unexpected '&' (did you mean '&&'?)");
				}
			}
			case '|' -> {
				if (match('|')) {
					add(TokenType.OR_OR);
				}
				else {
					error("Unexpected '|' (did you mean '||'?)");
				}
			}
			case '"' -> string();
			case '\'' -> character();
			default -> {
				if (isDigit(c)) {
					number();
				}
				else if (isAlpha(c)) {
					identifier();
				}
				else {
					error("Unexpected character: '" + c + "'");
				}
			}
		}
	}

	private void identifier() {
		while (isAlphaNumeric(peek())) {
			advance();
		}
		String text = source.substring(start, current);
		TokenType type = keywords.get(text);
		if (type == null) {
			type = TokenType.IDENTIFIER;
		}
		add(type);
	}

	private void number() {
		boolean isDouble = false;

		while (isDigit(peek())) {
			advance();
		}

		if (peek() == '.' && isDigit(peekNext())) {
			isDouble = true;
			do advance();
			while (isDigit(peek()));
		}

		if ((peek() == 'e' || peek() == 'E')) {
			isDouble = true;
			int save = current;
			advance();
			if (peek() == '+' || peek() == '-') {
				advance();
			}
			if (!isDigit(peek())) {
				current = save;
			}
			else {
				while (isDigit(peek())) {
					advance();
				}
			}
		}

		if (peek() == 'f' || peek() == 'F' || peek() == 'd' || peek() == 'D') {
			isDouble = true;
			advance();
		}

		String lexeme = source.substring(start, current);
		if (isDouble) {
			try {
				Double value = Double.parseDouble(lexeme
					.replace("F", "")
					.replace("f", "")
					.replace("D", "")
					.replace("d", ""));
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
					case 'n' -> sb.append('\n');
					case 'r' -> sb.append('\r');
					case 't' -> sb.append('\t');
					case '\\' -> sb.append('\\');
					case '"' -> sb.append('"');
					case '\'' -> sb.append('\'');
					default -> sb.append(e);
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
