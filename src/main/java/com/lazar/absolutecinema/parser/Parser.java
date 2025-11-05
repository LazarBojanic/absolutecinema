package com.lazar.absolutecinema.parser;

import com.lazar.absolutecinema.lexer.Token;
import com.lazar.absolutecinema.lexer.TokenType;
import com.lazar.absolutecinema.parser.ast.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Parser {
	// ==========================
	// Parser – State & Entry
	// ==========================

	private final List<Token> tokens;
	private int current = 0;

	public Parser(List<Token> tokens) {
		this.tokens = tokens != null ? tokens : Collections.emptyList();
	}

	public Program parseProgram() {
		List<Decl> decls = new ArrayList<>();
		while (!isAtEnd()) {
			if (match(TokenType.SEMICOLON)) {
				continue;
			}
			if (match(TokenType.SETUP)) {
				decls.add(parseSetupDecl());
			}
			else if (match(TokenType.SCENE)) {
				decls.add(parseSceneDecl(false));
			}
			else if (match(TokenType.VAR)) {
				decls.add(parseTopLevelVarDecl());
			}
			else {
				error(peek(), "Expected a top-level declaration: 'setup', 'scene', or 'var'.");
			}
		}
		return new Program(decls);
	}

	// ==========================
	// Declarations
	// ==========================

	private Decl parseTopLevelVarDecl() {
		Token name = consume(TokenType.IDENTIFIER, "Expected variable name.");
		consume(TokenType.COLON, "Expected ':' after variable name.");
		TypeRef type = parseTypeRef();
		Expr init = null;
		if (match(TokenType.EQUAL)) {
			init = expression();
		}
		consume(TokenType.SEMICOLON, "Expected ';' after variable declaration.");
		return new VarDecl(name, type, init);
	}

	private SetupDecl parseSetupDecl() {
		Token name = consume(TokenType.IDENTIFIER, "Expected setup name.");
		consume(TokenType.LEFT_BRACE, "Expected '{' to begin setup body.");
		List<VarDecl> fields = new ArrayList<>();
		ConstructorDecl ctor = null;
		List<SceneDecl> methods = new ArrayList<>();

		while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
			if (match(TokenType.VAR)) {
				Token fname = consume(TokenType.IDENTIFIER, "Expected field name.");
				consume(TokenType.COLON, "Expected ':' after field name.");
				TypeRef ftype = parseTypeRef();
				Expr init = null;
				if (match(TokenType.EQUAL)) {
					init = expression();
				}
				consume(TokenType.SEMICOLON, "Expected ';' after field declaration.");
				fields.add(new VarDecl(fname, ftype, init));
			}
			else if (check(TokenType.IDENTIFIER) && peek().getLexeme().equals(name.getLexeme())) {
				Token ctorName = advance();
				consume(TokenType.LEFT_PAREN, "Expected '(' after constructor name.");
				List<Param> params = parseParamList();
				consume(TokenType.RIGHT_PAREN, "Expected ')' after constructor parameters.");
				Block body = parseBlock();
				ctor = new ConstructorDecl(ctorName, params, body);
			}
			else if (match(TokenType.SCENE)) {
				methods.add(parseSceneDecl(true));
			}
			else if (match(TokenType.SEMICOLON)) {
				// skip stray
			}
			else {
				error(peek(), "Expected field, constructor, or scene method in setup: '" + name.getLexeme() + "'.");
			}
		}

		consume(TokenType.RIGHT_BRACE, "Expected '}' after setup body.");
		return new SetupDecl(name, fields, ctor, methods);
	}

	private SceneDecl parseSceneDecl(boolean isMethod) {
		Token name = consume(TokenType.IDENTIFIER, "Expected scene name.");
		consume(TokenType.LEFT_PAREN, "Expected '(' after scene name.");
		List<Param> params = parseParamList();
		consume(TokenType.RIGHT_PAREN, "Expected ')' after parameters.");
		consume(TokenType.COLON, "Expected ':' before return type.");

		TypeRef retType;
		if (match(TokenType.SCRAP)) {
			retType = new TypeRef(previous(), 0);
		}
		else {
			retType = parseTypeRef();
		}

		Block body = parseBlock();
		return new SceneDecl(name, params, retType, body, isMethod);
	}

	private List<Param> parseParamList() {
		List<Param> params = new ArrayList<>();
		if (check(TokenType.RIGHT_PAREN)) {
			return params;
		}
		do {
			if (match(TokenType.VAR)) {
				Token pname = consume(TokenType.IDENTIFIER, "Expected parameter name.");
				consume(TokenType.COLON, "Expected ':' after parameter name.");
				TypeRef ptype = parseTypeRef();
				params.add(new Param(pname, ptype));
			}
			else {
				Token pname = consume(TokenType.IDENTIFIER, "Expected parameter name (use 'var name: Type').");
				consume(TokenType.COLON, "Expected ':' after parameter name.");
				TypeRef ptype = parseTypeRef();
				params.add(new Param(pname, ptype));
			}
		}
		while (match(TokenType.COMMA));
		return params;
	}

	private Block parseBlock() {
		consume(TokenType.LEFT_BRACE, "Expected '{' to start block.");
		List<Node> items = new ArrayList<>();
		while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
			if (match(TokenType.VAR)) {
				items.add(new Var(parseLocalVarDecl()));
			}
			else if (match(TokenType.SCENE)) {
				error(previous(), "Nested scenes are not allowed.");
				synchronizeTo(TokenType.RIGHT_BRACE);
			}
			else if (match(TokenType.SETUP)) {
				error(previous(), "Nested setups are not allowed.");
				synchronizeTo(TokenType.RIGHT_BRACE);
			}
			else {
				items.add(statement());
			}
		}
		consume(TokenType.RIGHT_BRACE, "Expected '}' after block.");
		return new Block(items);
	}

	private Block parseBlockFromAlreadyConsumedBrace() {
		List<Node> items = new ArrayList<>();
		while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
			if (match(TokenType.VAR)) {
				items.add(new Var(parseLocalVarDecl()));
			}
			else {
				items.add(statement());
			}
		}
		consume(TokenType.RIGHT_BRACE, "Expected '}' after block.");
		return new Block(items);
	}

	private VarDecl parseLocalVarDecl() {
		Token name = consume(TokenType.IDENTIFIER, "Expected variable name.");
		consume(TokenType.COLON, "Expected ':' after variable name.");
		TypeRef type = parseTypeRef();
		Expr init = null;
		if (match(TokenType.EQUAL)) {
			init = expression();
		}
		consume(TokenType.SEMICOLON, "Expected ';' after variable declaration.");
		return new VarDecl(name, type, init);
	}

	// ==========================
	// Statements
	// ==========================

	private Stmt statement() {
		if (match(TokenType.LEFT_BRACE)) {
			current--;
			return parseBlock();
		}
		if (match(TokenType.IF)) {
			return parseIf();
		}
		if (match(TokenType.KEEP_ROLLING_IF)) {
			return parseWhile();
		}
		if (match(TokenType.KEEP_ROLLING_DURING)) {
			return parseFor();
		}
		if (match(TokenType.CUT)) {
			return parseReturn();
		}
		if (match(TokenType.EXIT)) {
			consume(TokenType.SEMICOLON, "Expected ';' after 'exit'.");
			return new Break(previous());
		}
		if (match(TokenType.SKIP)) {
			consume(TokenType.SEMICOLON, "Expected ';' after 'skip'.");
			return new Continue(previous());
		}
		Expr expr = expression();
		consume(TokenType.SEMICOLON, "Expected ';' after expression.");
		return new ExprStmt(expr);
	}

	private Stmt parseIf() {
		consume(TokenType.LEFT_PAREN, "Expected '(' after 'if'.");
		Expr cond = expression();
		consume(TokenType.RIGHT_PAREN, "Expected ')' after if condition.");
		consume(TokenType.LEFT_BRACE, "Expected '{' to start 'if' block.");
		Block thenBlock = parseBlockFromAlreadyConsumedBrace();
		Stmt elseBranch = null;
		if (match(TokenType.ELSE)) {
			if (match(TokenType.IF)) {
				elseBranch = parseIf();
			}
			else {
				consume(TokenType.LEFT_BRACE, "Expected '{' to start 'else' block.");
				elseBranch = parseBlockFromAlreadyConsumedBrace();
			}
		}
		return new If(cond, thenBlock, elseBranch);
	}

	private Stmt parseWhile() {
		consume(TokenType.LEFT_PAREN, "Expected '(' after 'keepRollingIf'.");
		Expr cond = expression();
		consume(TokenType.RIGHT_PAREN, "Expected ')' after while condition.");
		consume(TokenType.LEFT_BRACE, "Expected '{' to start loop block.");
		Block body = parseBlockFromAlreadyConsumedBrace();
		return new While(cond, body);
	}

	private Stmt parseFor() {
		consume(TokenType.LEFT_PAREN, "Expected '(' after 'keepRollingDuring'.");
		Node initializer;
		if (match(TokenType.VAR)) {
			initializer = new Var(parseLocalVarDecl());
		}
		else if (!check(TokenType.SEMICOLON)) {
			Expr initExpr = expression();
			consume(TokenType.SEMICOLON, "Expected ';' after for initializer.");
			initializer = new ExprStmt(initExpr);
		}
		else {
			consume(TokenType.SEMICOLON, "Expected ';' after for initializer.");
			initializer = null;
		}
		Expr condition = null;
		if (!check(TokenType.SEMICOLON)) {
			condition = expression();
		}
		consume(TokenType.SEMICOLON, "Expected ';' after for condition.");
		Expr increment = null;
		if (!check(TokenType.RIGHT_PAREN)) {
			increment = expression();
		}
		consume(TokenType.RIGHT_PAREN, "Expected ')' after for clauses.");
		consume(TokenType.LEFT_BRACE, "Expected '{' to start loop block.");
		Block body = parseBlockFromAlreadyConsumedBrace();
		return new For(initializer, condition, increment, body);
	}

	private Stmt parseReturn() {
		Token kw = previous();
		Expr value = null;
		if (!check(TokenType.SEMICOLON)) {
			value = expression();
		}
		consume(TokenType.SEMICOLON, "Expected ';' after 'cut' value.");
		return new Return(kw, value);
	}

	// ==========================
	// Expressions
	// ==========================

	private Expr expression() {
		return assignment();
	}

	private Expr assignment() {
		Expr expr = or();
		if (match(TokenType.EQUAL, TokenType.PLUS_EQUAL, TokenType.MINUS_EQUAL, TokenType.STAR_EQUAL, TokenType.SLASH_EQUAL, TokenType.PERCENT_EQUAL)) {
			Token op = previous();
			Expr value = assignment();
			if (expr instanceof Variable || expr instanceof Get || expr instanceof Index) {
				if (expr instanceof Get g) {
					return new Set(g.object, g.name, op, value);
				}
				else {
					return new Assign(expr, op, value);
				}
			}
			error(op, "Invalid assignment target.");
		}
		return expr;
	}

	private Expr or() {
		Expr expr = and();
		while (match(TokenType.OR_OR)) {
			Token op = previous();
			Expr right = and();
			expr = new Logical(expr, op, right);
		}
		return expr;
	}

	private Expr and() {
		Expr expr = equality();
		while (match(TokenType.AND_AND)) {
			Token op = previous();
			Expr right = equality();
			expr = new Logical(expr, op, right);
		}
		return expr;
	}

	private Expr equality() {
		Expr expr = comparison();
		while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
			Token op = previous();
			Expr right = comparison();
			expr = new Binary(expr, op, right);
		}
		return expr;
	}

	private Expr comparison() {
		Expr expr = term();
		while (match(TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL)) {
			Token op = previous();
			Expr right = term();
			expr = new Binary(expr, op, right);
		}
		return expr;
	}

	private Expr term() {
		Expr expr = factor();
		while (match(TokenType.PLUS, TokenType.MINUS)) {
			Token op = previous();
			Expr right = factor();
			expr = new Binary(expr, op, right);
		}
		return expr;
	}

	private Expr factor() {
		Expr expr = unary();
		while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
			Token op = previous();
			Expr right = unary();
			expr = new Binary(expr, op, right);
		}
		return expr;
	}

	private Expr unary() {
		if (match(TokenType.BANG, TokenType.MINUS, TokenType.PLUS_PLUS, TokenType.MINUS_MINUS)) {
			Token op = previous();
			Expr right = unary();
			return new Unary(op, right);
		}
		return postfix();
	}

	private Expr postfix() {
		Expr expr = call();
		while (true) {
			if (match(TokenType.LEFT_PAREN)) {
				List<Expr> args = new ArrayList<>();
				if (!check(TokenType.RIGHT_PAREN)) {
					do {
						args.add(expression());
					}
					while (match(TokenType.COMMA));
				}
				Token paren = consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments.");
				expr = new Call(expr, paren, args);
			}
			else if (match(TokenType.PLUS_PLUS, TokenType.MINUS_MINUS)) {
				expr = new Postfix(expr, previous());
			}
			else if (match(TokenType.LEFT_BRACKET)) {
				Expr index = expression();
				consume(TokenType.RIGHT_BRACKET, "Expected ']' after index.");
				expr = new Index(expr, index);
			}
			else if (match(TokenType.DOT)) {
				Token name = consume(TokenType.IDENTIFIER, "Expected property name after '.'.");
				expr = new Get(expr, name);
			}
			else {
				break;
			}
		}
		return expr;
	}

	private Expr call() {
		Expr expr = primary();
		while (true) {
			if (match(TokenType.LEFT_PAREN)) {
				List<Expr> args = new ArrayList<>();
				if (!check(TokenType.RIGHT_PAREN)) {
					do {
						args.add(expression());
					}
					while (match(TokenType.COMMA));
				}
				Token paren = consume(TokenType.RIGHT_PAREN, "Expected ')' after arguments.");
				expr = new Call(expr, paren, args);
			}
			else {
				break;
			}
		}
		return expr;
	}

	private Expr primary() {
		if (match(TokenType.FALSE)) {
			return new Literal(false);
		}
		if (match(TokenType.TRUE)) {
			return new Literal(true);
		}
		if (match(TokenType.NULL)) {
			return new Literal(null);
		}
		if (match(TokenType.INT_LITERAL)) {
			return new Literal(previous().getLiteral());
		}
		if (match(TokenType.DOUBLE_LITERAL)) {
			return new Literal(previous().getLiteral());
		}
		if (match(TokenType.STRING_LITERAL)) {
			return new Literal(previous().getLiteral());
		}
		if (match(TokenType.CHAR_LITERAL)) {
			return new Literal(previous().getLiteral());
		}
		if (match(TokenType.IDENTIFIER)) {
			return new Variable(previous());
		}
		if (match(TokenType.AT)) {
			return new This(previous());
		}
		if (match(TokenType.ACTION)) {
			Token action = previous();
			TypeRef type = parseTypeRef();
			if (match(TokenType.LEFT_PAREN)) {
				List<Expr> args = new ArrayList<>();
				if (!check(TokenType.RIGHT_PAREN)) {
					do {
						args.add(expression());
					}
					while (match(TokenType.COMMA));
				}
				consume(TokenType.RIGHT_PAREN, "Expected ')' after constructor args.");
				return new ActionNew(action, type, args, null, null);
			}
			else if (match(TokenType.LEFT_BRACE)) {
				List<Expr> elements = new ArrayList<>();
				if (!check(TokenType.RIGHT_BRACE)) {
					do {
						elements.add(expression());
					}
					while (match(TokenType.COMMA));
				}
				consume(TokenType.RIGHT_BRACE, "Expected '}' after array literal.");
				return new ArrayLiteral(elements);
			}
			else if (match(TokenType.LEFT_BRACKET)) {
				Expr capacity = expression();
				consume(TokenType.RIGHT_BRACKET, "Expected ']' after array capacity.");
				List<Expr> init = null;
				if (match(TokenType.LEFT_BRACE)) {
					init = new ArrayList<>();
					if (!check(TokenType.RIGHT_BRACE)) {
						do {
							init.add(expression());
						}
						while (match(TokenType.COMMA));
					}
					consume(TokenType.RIGHT_BRACE, "Expected '}' after array initializer.");
				}
				return new ActionNew(action, type, null, capacity, init);
			}
			else {
				error(peek(), "Expected '(' for constructor call or '[' for array capacity after type in 'action'.");
			}
		}
		if (match(TokenType.LEFT_PAREN)) {
			Expr expr = expression();
			consume(TokenType.RIGHT_PAREN, "Expected ')' after expression.");
			return new Grouping(expr);
		}
		error(peek(), "Expected expression.");
		return new Literal(null);
	}

	private TypeRef parseTypeRef() {
		if (match(TokenType.INT, TokenType.DOUBLE, TokenType.CHAR, TokenType.STRING, TokenType.IDENTIFIER)) {
			Token name = previous();
			int depth = 0;
			// Only treat '[]' as array type brackets. If '[' is not immediately followed by ']',
			// it belongs to array capacity (e.g., action double[3]) or indexing and must not be consumed here.
			while (check(TokenType.LEFT_BRACKET) && checkNext(TokenType.RIGHT_BRACKET)) {
				advance(); // '['
				advance(); // ']'
				depth++;
			}
			return new TypeRef(name, depth);
		}
		error(peek(), "Expected a type name.");
		return new TypeRef(previous(), 0);
	}

	// ==========================
	// Helpers
	// ==========================

	private boolean match(TokenType... types) {
		for (TokenType t : types) {
			if (check(t)) {
				advance();
				return true;
			}
		}
		return false;
	}

	private boolean check(TokenType type) {
		if (isAtEnd()) {
			return false;
		}
		return peek().getType() == type;
	}

	private boolean checkNext(TokenType type) {
		if (current >= tokens.size() - 1) {
			return false;
		}
		return tokens.get(current + 1).getType() == type;
	}

	private Token advance() {
		if (!isAtEnd()) {
			current++;
		}
		return previous();
	}

	private boolean isAtEnd() {
		return peek().getType() == TokenType.EOF;
	}

	private Token peek() {
		return tokens.get(current);
	}

	private Token previous() {
		return tokens.get(current - 1);
	}

	private Token consume(TokenType type, String message) {
		if (check(type)) {
			return advance();
		}
		error(peek(), message);
		return previous();
	}

	private void error(Token token, String message) {
		String where = token.getType() == TokenType.EOF ? " at end" : " at '" + token.getLexeme() + "'";
		throw new ParseError("PARSER ERROR" + where + ": " + message + " (line: " + token.getLine() + ", col: " + token.getColumn() + ")");
	}

	private void synchronizeTo(TokenType stopAt) {
		while (!isAtEnd() && !check(stopAt)) {
			advance();
		}
	}

	private static final class ParseError extends RuntimeException {
		ParseError(String msg) {
			super(msg);
		}
	}
}