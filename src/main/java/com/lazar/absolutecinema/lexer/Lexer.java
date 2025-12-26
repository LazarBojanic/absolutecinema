package com.lazar.absolutecinema.lexer;

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
		keywords.put("elif", TokenType.ELIF);
		keywords.put("else", TokenType.ELSE);
		keywords.put("keepRollingDuring", TokenType.KEEP_ROLLING_DURING);
		keywords.put("keepRollingIf", TokenType.KEEP_ROLLING_IF);
		keywords.put("int", TokenType.INT);
		keywords.put("double", TokenType.DOUBLE);
		keywords.put("char", TokenType.CHAR);
		keywords.put("string", TokenType.STRING);
		keywords.put("bool", TokenType.BOOL);
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
			case '\r':
			case '\t':
				break;
			case '\n':
				line++;
				column = 1;
				break;
			case '(':
				add(TokenType.LEFT_PAREN);
				break;
			case ')':
				add(TokenType.RIGHT_PAREN);
				break;
			case '{':
				add(TokenType.LEFT_BRACE);
				break;
			case '}':
				add(TokenType.RIGHT_BRACE);
				break;
			case '[':
				add(TokenType.LEFT_BRACKET);
				break;
			case ']':
				add(TokenType.RIGHT_BRACKET);
				break;
			case ',':
				add(TokenType.COMMA);
				break;
			case '.':
				add(TokenType.DOT);
				break;
			case ';':
				add(TokenType.SEMICOLON);
				break;
			case ':':
				add(TokenType.COLON);
				break;
			case '@':
				add(TokenType.AT);
				break;
			case '!':
				add(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
				break;
			case '=':
				add(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
				break;
			case '<':
				add(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
				break;
			case '>':
				add(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
				break;
			case '+':
				if (match('+')) {
					add(TokenType.PLUS_PLUS);
				}
				else if (match('=')) {
					add(TokenType.PLUS_EQUAL);
				}
				else {
					add(TokenType.PLUS);
				}
				break;
			case '-':
				if (match('-')) {
					add(TokenType.MINUS_MINUS);
				}
				else if (match('=')) {
					add(TokenType.MINUS_EQUAL);
				}
				else {
					add(TokenType.MINUS);
				}
				break;
			case '*':
				add(match('=') ? TokenType.STAR_EQUAL : TokenType.STAR);
				break;
			case '/':
				add(match('=') ? TokenType.SLASH_EQUAL : TokenType.SLASH);
				break;
			case '%':
				add(match('=') ? TokenType.PERCENT_EQUAL : TokenType.PERCENT);
				break;
			case '&':
				if (match('&')) {
					add(TokenType.AND_AND);
				}
				else {
					error("Unexpected '&'");
				}
				break;
			case '|':
				if (match('|')) {
					add(TokenType.OR_OR);
				}
				else {
					error("Unexpected '|'");
				}
				break;
			case '"':
				string();
				break;
			case '\'':
				character();
				break;
			default:
				if (isDigit(c)) {
					number();
				}
				else if (isAlpha(c)) {
					identifier();
				}
				else {
					error("Unexpected character: '" + c + "'");
				}
				break;
		}
	}

	private void number() {
		while (isDigit(peek())) {
			advance();
		}
		if (peek() == '.' && isDigit(peekNext())) {
			advance();
			while (isDigit(peek())) {
				advance();
			}
			add(TokenType.DOUBLE_LITERAL, Double.parseDouble(source.substring(start, current)));
		}
		else {
			add(TokenType.INT_LITERAL, Integer.parseInt(source.substring(start, current)));
		}
	}

	private void string() {
		StringBuilder sb = new StringBuilder();
		while (!isAtEnd() && peek() != '"') {
			char c = advance();
			if (c == '\\') {
				char e = advance();
				switch (e) {
					case 'n' -> sb.append('\n');
					case 'r' -> sb.append('\r');
					case 't' -> sb.append('\t');
					case '"' -> sb.append('"');
					case '\\' -> sb.append('\\');
					default -> sb.append(e);
				}
			}
			else {
				sb.append(c);
			}
		}
		if (isAtEnd()) {
			error("Unterminated string");
		}
		advance();
		add(TokenType.STRING_LITERAL, sb.toString());
	}

	private void character() {
		char value = advance();
		if (value == '\\') {
			char e = advance();
			value = switch (e) {
				case 'n' -> '\n';
				case 'r' -> '\r';
				case 't' -> '\t';
				case '\'' -> '\'';
				case '\\' -> '\\';
				default -> e;
			};
		}
		if (peek() != '\'') {
			error("Unterminated char literal");
		}
		advance();
		add(TokenType.CHAR_LITERAL, value);
	}

	private void identifier() {
		while (isAlphaNumeric(peek())) {
			advance();
		}
		String text = source.substring(start, current);
		add(keywords.getOrDefault(text, TokenType.IDENTIFIER));
	}

	private boolean match(char expected) {
		if (isAtEnd() || source.charAt(current) != expected) {
			return false;
		}
		current++;
		column++;
		return true;
	}

	private char advance() {
		column++;
		return source.charAt(current++);
	}

	private char peek() {
		return isAtEnd() ? '\0' : source.charAt(current);
	}

	private char peekNext() {
		return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
	}

	private boolean isAlpha(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}

	private boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private boolean isAlphaNumeric(char c) {
		return isAlpha(c) || isDigit(c);
	}

	private boolean isAtEnd() {
		return current >= source.length();
	}

	private void add(TokenType type) {
		add(type, null);
	}

	private void add(TokenType type, Object literal) {
		String text = source.substring(start, current);
		tokens.add(new Token(type, text, literal, line, column - text.length()));
	}

	private void error(String message) {
		throw new RuntimeException("LEXER ERROR at line " + line + ": " + message);
	}
}