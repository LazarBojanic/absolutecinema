package com.lazar.absolutecinema.generator;

public class GenerationResult {
	private final String plainTextIR;
	private final byte[] binaryIR;

	public GenerationResult(String plainTextIR, byte[] binaryIR) {
		this.plainTextIR = plainTextIR;
		this.binaryIR = binaryIR;
	}

	public String getPlainTextIR() {
		return plainTextIR;
	}

	public byte[] getBinaryIR() {
		return binaryIR;
	}
}
