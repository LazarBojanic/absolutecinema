grammar AbsoluteCinema;

// Lexer Rules

// Keywords
SETUP: 'setup';
SCENE: 'scene';
VAR: 'var';
SCRAP: 'scrap';
CUT: 'cut';
ACTION: 'action';
IF: 'if';
ELSE: 'else';
ELIF: 'elif';
KEEP_ROLLING_DURING: 'keepRollingDuring';
KEEP_ROLLING_IF: 'keepRollingIf';
INT: 'int';
DOUBLE: 'double';
CHAR: 'char';
STRING: 'string';
BOOL: 'bool';
TRUE: 'true';
FALSE: 'false';
NULL: 'null';

// Operators
ASSIGN: '=';
PLUS: '+';
MINUS: '-';
MULT: '*';
DIV: '/';
MOD: '%';
PLUS_ASSIGN: '+=';
MINUS_ASSIGN: '-=';
MULT_ASSIGN: '*=';
DIV_ASSIGN: '/=';
MOD_ASSIGN: '%=';
INCREMENT: '++';
DECREMENT: '--';

// Comparison operators
EQ: '==';
NEQ: '!=';
LE: '<=';
GE: '>=';
LT: '<';
GT: '>';

// Logical operators
AND: '&&';
OR: '||';
NOT: '!';

// Symbols
AT: '@';
DOT: '.';
COMMA: ',';
COLON: ':';
SEMI: ';';
LPAREN: '(';
RPAREN: ')';
LBRACE: '{';
RBRACE: '}';
LBRACK: '[';
RBRACK: ']';

// Literals
IDENT: [a-zA-Z_][a-zA-Z0-9_]*;
DOUBLE_LITERAL: [0-9]+ '.' [0-9]+;
INT_LITERAL: [0-9]+;
STRING_LITERAL: '"' (~["\\] | '\\' .)* '"';
CHAR_LITERAL: '\'' (~['\\] | '\\' .) '\'';

// Whitespace
WS: [ \t\r\n]+ -> skip;

// Parser Rules
program: (setupDecl | sceneDecl | varDecl)* EOF; // top-level statements are not allowed

declaration: setupDecl | sceneDecl | varDecl; // helper (not used at top-level rule directly)

setupDecl: SETUP IDENT LBRACE (fieldDecl | ctorDecl | methodDecl | SEMI)* RBRACE;

fieldDecl: VAR IDENT COLON type (ASSIGN expression)? SEMI;

ctorDecl: IDENT LPAREN params? RPAREN block; // name equality to setup name is enforced by the parser code, not the grammar

methodDecl: SCENE IDENT LPAREN params? RPAREN COLON (SCRAP | type) block;

sceneDecl: SCENE IDENT LPAREN params? RPAREN COLON (SCRAP | type) block;

varDecl: VAR IDENT COLON type (ASSIGN expression)? SEMI;

params: param (COMMA param)*;

param: VAR? IDENT COLON type;

// Types — allow optional dimension sizes as integer literals; empty [] means undefined-size
// Example: int[3][], MyType[][10]
// The size, when omitted, is handled in code as "undefined-size".
type: (INT | DOUBLE | CHAR | STRING | BOOL | IDENT) (LBRACK INT_LITERAL? RBRACK)*;

// Statements
block: LBRACE (varDecl | statement | SEMI)* RBRACE; // varDecl allowed inside blocks

statement:
    exprStmt
    | ifStmt
    | whileStmt
    | forStmt
    | returnStmt
    | assignStmt
    | block
    | emptyStmt
    ;

exprStmt: expression SEMI;

ifStmt: IF LPAREN expression RPAREN block (ELIF LPAREN expression RPAREN block)* (ELSE block)?;

whileStmt: KEEP_ROLLING_IF LPAREN expression RPAREN block;

forStmt: KEEP_ROLLING_DURING LPAREN (varDecl | exprStmt | SEMI) expression? SEMI expression? RPAREN block;

returnStmt: CUT expression? SEMI;

assignStmt: accessExpression ASSIGN expression SEMI;

emptyStmt: SEMI;

// Expressions
expression: assignment;

assignment: accessExpression ASSIGN assignment | logical_or;

logical_or: logical_and (OR logical_and)*;

logical_and: equality (AND equality)*;

equality: comparison ((EQ | NEQ) comparison)*;

comparison: term ((LT | LE | GT | GE) term)*;

term: factor ((PLUS | MINUS) factor)*;

factor: unary ((MULT | DIV | MOD) unary)*;

// include unary PLUS to match parser
unary: (NOT | PLUS | MINUS | INCREMENT | DECREMENT) unary | postfix;

postfix: primary ( LPAREN arguments? RPAREN | LBRACK expression RBRACK | DOT IDENT | INCREMENT | DECREMENT )*;

primary:
    literal
    | IDENT
    | AT
    | LPAREN expression RPAREN
    | arrayLiteral
    | objectInstantiation
    ;

// Access expressions (for assignment LHS)
accessExpression:
    (IDENT | AT | LPAREN expression RPAREN) ( DOT IDENT | LBRACK expression RBRACK | LPAREN arguments? RPAREN )*;

// Literals and special expressions
literal: INT_LITERAL | DOUBLE_LITERAL | STRING_LITERAL | CHAR_LITERAL | TRUE | FALSE | NULL;

// In code, `action` takes a type, then either constructs with (args) or provides an array literal with {...}
arrayLiteral: ACTION type LBRACE (expression (COMMA expression)*)? RBRACE;

objectInstantiation: ACTION type LPAREN arguments? RPAREN;

arguments: expression (COMMA expression)*;