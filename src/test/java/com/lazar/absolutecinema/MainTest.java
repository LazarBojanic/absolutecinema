package com.lazar.absolutecinema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

	private void run(String fileName) {
		Main.main(new String[] { fileName });
	}

	// ---------- Correct programs ----------

	@Test void correct01() { assertDoesNotThrow(() -> run("absolutecinema-correct-01.ac")); }
	@Test void correct02() { assertDoesNotThrow(() -> run("absolutecinema-correct-02.ac")); }
	@Test void correct03() { assertDoesNotThrow(() -> run("absolutecinema-correct-03.ac")); }
	@Test void correct04() { assertDoesNotThrow(() -> run("absolutecinema-correct-04.ac")); }
	@Test void correct05() { assertDoesNotThrow(() -> run("absolutecinema-correct-05.ac")); }

	// ---------- Lexical errors ----------

	@Test void lexicalError01() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-lexical-error-01.ac")); }

	@Test void lexicalError02() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-lexical-error-02.ac")); }

	@Test void lexicalError03() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-lexical-error-03.ac")); }

	@Test void lexicalError04() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-lexical-error-04.ac")); }

	@Test void lexicalError05() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-lexical-error-05.ac")); }

	// ---------- Parsing errors ----------

	@Test void parsingError01() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-parsing-error-01.ac")); }

	@Test void parsingError02() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-parsing-error-02.ac")); }

	@Test void parsingError03() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-parsing-error-03.ac")); }

	@Test void parsingError04() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-parsing-error-04.ac")); }

	@Test void parsingError05() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-parsing-error-05.ac")); }

	// ---------- Semantic errors ----------

	@Test void semanticError01() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-semantic-error-01.ac")); }

	@Test void semanticError02() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-semantic-error-02.ac")); }

	@Test void semanticError03() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-semantic-error-03.ac")); }

	@Test void semanticError04() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-semantic-error-04.ac")); }

	@Test void semanticError05() { assertThrows(RuntimeException.class,
		() -> run("absolutecinema-semantic-error-05.ac")); }
}
