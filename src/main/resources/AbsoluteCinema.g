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
NUMBER: [0-9]+ ('.' [0-9]+)?;
STRING_LITERAL: '"' (~["\\] | '\\' .)* '"';
CHAR_LITERAL: '\'' (~['\\] | '\\' .) '\'';

// Whitespace
WS: [ \t\r\n]+ -> skip;

// Parser Rules
program: declaration* EOF;

declaration: setupDecl | sceneDecl | varDecl | statement;

setupDecl: SETUP IDENT LBRACE (fieldDecl | ctorDecl | methodDecl)* RBRACE;

fieldDecl: VAR IDENT COLON type (ASSIGN expression)? SEMI;

ctorDecl: IDENT LPAREN params? RPAREN block;

methodDecl: SCENE IDENT LPAREN params? RPAREN COLON (SCRAP | type) block;

sceneDecl: SCENE IDENT LPAREN params? RPAREN COLON (SCRAP | type) block;

varDecl: VAR IDENT COLON type (ASSIGN expression)? SEMI;

params: param (COMMA param)*;

param: (VAR)? IDENT COLON type;

// Types
type: (INT | DOUBLE | CHAR | STRING | BOOL | IDENT) (LBRACK RBRACK)*;

// Statements
block: LBRACE declaration* RBRACE;

statement:
    exprStmt
    | ifStmt
    | whileStmt
    | forStmt
    | returnStmt
    | assignStmt
    | block
    | emptyStmt;

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

unary: (NOT | MINUS | INCREMENT | DECREMENT) unary | postfix;

postfix: primary ( LPAREN arguments? RPAREN | LBRACK expression RBRACK | DOT IDENT | INCREMENT | DECREMENT )*;

primary:
    literal
    | IDENT
    | AT
    | LPAREN expression RPAREN
    | arrayLiteral
    | objectInstantiation;

// Access expressions (for assignment LHS)
accessExpression:
    (IDENT | AT | LPAREN expression RPAREN) ( DOT IDENT | LBRACK expression RBRACK | LPAREN arguments? RPAREN )*;

// Literals and special expressions
literal: NUMBER | STRING_LITERAL | CHAR_LITERAL | TRUE | FALSE | NULL;

arrayLiteral: ACTION type LBRACK expression? RBRACK (LBRACE (expression (COMMA expression)*)? RBRACE)?;

objectInstantiation: ACTION IDENT LPAREN arguments? RPAREN;

arguments: expression (COMMA expression)*;