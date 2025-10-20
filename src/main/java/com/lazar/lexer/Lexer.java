package com.lazar.lexer;

import com.lazar.core.model.Token;
import com.lazar.core.model.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final ScannerCore sc;
    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("scrap", TokenType.TYPE_SCRAP),
            Map.entry("int", TokenType.TYPE_INT),
            Map.entry("double", TokenType.TYPE_DOUBLE),
            Map.entry("char", TokenType.TYPE_CHAR),
            Map.entry("string", TokenType.TYPE_STRING),
            Map.entry("setup", TokenType.TYPE_SETUP),
            Map.entry("=", TokenType.OP_ASSIGN),
            Map.entry("+", TokenType.OP_PLUS),
            Map.entry("-", TokenType.OP_MINUS),
            Map.entry("*", TokenType.OP_TIMES),
            Map.entry("/", TokenType.OP_DIVIDE),
            Map.entry("%", TokenType.OP_MODULUS),
            Map.entry("+=", TokenType.OP_PLUS_ASSIGN),
            Map.entry("-=", TokenType.OP_MINUS_ASSIGN),
            Map.entry("*=", TokenType.OP_TIMES_ASSIGN),
            Map.entry("/=", TokenType.OP_DIVIDE_ASSIGN),
            Map.entry("++", TokenType.OP_INCREMENT),
            Map.entry("--", TokenType.OP_DECREMENT),
            Map.entry("==", TokenType.OP_EQUALS),
            Map.entry("!=", TokenType.OP_NOT_EQUALS),
            Map.entry("<=", TokenType.OP_LESS_OR_EQUAL),
            Map.entry(">=", TokenType.OP_GREATER_OR_EQUAL),
            Map.entry("<", TokenType.OP_LESS),
            Map.entry(">", TokenType.OP_GREATER),
            Map.entry("&&", TokenType.OP_AND),
            Map.entry("||", TokenType.OP_OR),
            Map.entry("!", TokenType.OP_NOT),
            Map.entry("(", TokenType.SCOPE_OPEN_PAREN),
            Map.entry(")", TokenType.SCOPE_CLOSE_PAREN),
            Map.entry("{", TokenType.SCOPE_OPEN_CURLY),
            Map.entry("}", TokenType.SCOPE_CLOSE_CURLY),
            Map.entry("[", TokenType.SCOPE_OPEN_BRACKET),
            Map.entry("]", TokenType.SCOPE_CLOSE_BRACKET),
            Map.entry("todo", TokenType.LITERAL_INT),
            Map.entry("todo", TokenType.LITERAL_DOUBLE),
            Map.entry("todo", TokenType.LITERAL_CHAR),
            Map.entry("todo", TokenType.LITERAL_STRING),
            Map.entry("todo", TokenType.IDENTIFIER),
            Map.entry("if", TokenType.CONDITIONAL_IF),
            Map.entry("else", TokenType.CONDITIONAL_ELSE),
            Map.entry("else if", TokenType.CONDITIONAL_ELSE_IF),
            Map.entry("keepRollingDuring", TokenType.CONDITIONAL_KEEP_ROLLING_DURING),
            Map.entry("keepRollingIf", TokenType.CONDITIONAL_KEEP_ROLLING_IF),
            Map.entry("var", TokenType.KEYWORD_VAR),
            Map.entry("scene", TokenType.KEYWORD_SCENE),
            Map.entry("cut", TokenType.KEYWORD_CUT),
            Map.entry("action", TokenType.KEYWORD_ACTION),
            Map.entry("skip", TokenType.KEYWORD_SKIP),
            Map.entry("exit", TokenType.KEYWORD_EXIT),
            Map.entry("entrance", TokenType.KEYWORD_ENTRANCE),
            Map.entry("@", TokenType.SYMBOL_THIS),
            Map.entry("\"\"", TokenType.SYMBOL_DOUBLE_QUOTE),
            Map.entry("''", TokenType.SYMBOL_QUOTE),
            Map.entry(":", TokenType.SYMBOL_COLON),
            Map.entry(";", TokenType.SYMBOL_SEMICOLON),
            Map.entry(",", TokenType.SYMBOL_COMMA),
            Map.entry(".", TokenType.SYMBOL_DOT),
            Map.entry("\n", TokenType.SYMBOL_NEWLINE),
            Map.entry("\0", TokenType.SYMBOL_EOF)


    );

    public Lexer(String source) {
        this.source = source;
        this.sc = new ScannerCore(source);
    }

    public List<Token> scanTokens() {
        while (!sc.isAtEnd()) {
            sc.beginToken();
            scanToken();
        }
        tokens.add(new Token(TokenType.SYMBOL_EOF, "\0", null, sc.getLine(), sc.getCol(), sc.getCol()));
        return tokens;
    }

    private void scanToken() {
        char c = sc.advance();

        switch (c) {
            case '(' -> add(TokenType.LPAREN);
            case ')' -> add(TokenType.RPAREN);
            case '[' -> add(TokenType.LBRACKET);
            case ']' -> add(TokenType.RBRACKET);
            case ',' -> add(TokenType.SEPARATOR_COMMA);
            case ':' -> add(TokenType.TYPE_COLON);
            case '+' -> add(TokenType.ADD);
            case '-' -> add(sc.match('>') ? TokenType.ASSIGN : TokenType.SUBTRACT);
            case '*' -> add(TokenType.MULTIPLY);
            case '/' -> add(TokenType.DIVIDE);
            case '%' -> add(TokenType.PERCENT);
            case '<' -> add(sc.match('=') ? TokenType.LE : TokenType.LT);
            case '>' -> add(sc.match('=') ? TokenType.GE : TokenType.GT);
            case '=' -> add(TokenType.EQ);
            case '!' -> {
                if (sc.match('=')) add(TokenType.NEQ);
                else throw error("Unexpected '!'");
            }
            case '\n' -> tokens.add(new Token(
                    TokenType.NEWLINE, "\n", null, sc.getStartLine(), sc.getStartCol(), sc.getStartCol()
            ));
            case ' ', '\r', '\t' -> {}
            default -> {
                if (Character.isDigit(c)) number();
                else if (isIdentStart(c)) identifier();
                else throw error("Unexpected character");
            }
        }
    }

    private void number() {
        while (Character.isDigit(sc.peek())) sc.advance();
        String text = source.substring(sc.getStartIdx(), sc.getCur());
        char nextChar = sc.peek();
        if (Character.isAlphabetic(nextChar)) {
            throw error("Error: Character in int literal");
        }
        addLiteralInt(text);
    }

    private void identifier() {
        while (isIdentPart(sc.peek())) sc.advance();
        String text = source.substring(sc.getStartIdx(), sc.getCur());
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENT);
        add(type, text);
    }

    private boolean isIdentStart(char c) { return Character.isLetter(c) || c == '_'; }
    private boolean isIdentPart(char c)  { return isIdentStart(c) || Character.isDigit(c); }

    private void add(TokenType type) {
        String lex = source.substring(sc.getStartIdx(), sc.getCur());
        tokens.add(new Token(type, lex, null,
                sc.getStartLine(), sc.getStartCol(), sc.getCol() - 1));
    }

    private void add(TokenType type, String text) {
        tokens.add(new Token(type, text, null,
                sc.getStartLine(), sc.getStartCol(), sc.getCol() - 1));
    }

    private void addLiteralInt(String literal) {
        tokens.add(new Token(TokenType.INT_LIT, literal, Integer.valueOf(literal),
                sc.getStartLine(), sc.getStartCol(), sc.getCol() - 1));
    }

    private RuntimeException error(String msg) {
        String near = source.substring(sc.getStartIdx(), Math.min(sc.getCur(), source.length()));
        return new RuntimeException("LEXER > " + msg + " at " + sc.getStartLine() + ":" + sc.getStartCol() + " near '" + near + "'");
    }
}
