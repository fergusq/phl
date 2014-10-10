package org.kaivos.proceedhl.parser;

public class NumberParser {

	private NumberParser() {}
	
	public static int parseHex(String hex) {
		if (hex.startsWith("0x")) hex = hex.substring(2);
		
		int a = 0;
		
		for (int i = 0; i < hex.length(); i++) {
			char ch = hex.toLowerCase().charAt(i);
			a *= 16;
			switch (ch) {
			case 'f': a += 1;
			case 'e': a += 1;
			case 'd': a += 1;
			case 'c': a += 1;
			case 'b': a += 1;
			case 'a': a += 1;
			case '9': a += 1;
			case '8': a += 1;
			case '7': a += 1;
			case '6': a += 1;
			case '5': a += 1;
			case '4': a += 1;
			case '3': a += 1;
			case '2': a += 1;
			case '1': a += 1;
			case '0': break;
			default:
				throw new NumberFormatException("Bad hex: " + hex);
			}
		}
		
		return a;
	}
	
	public static int parseOct(String oct) {
		if (oct.startsWith("0o")) oct = oct.substring(2);
		
		int a = 0;
		
		for (int i = 0; i < oct.length(); i++) {
			char ch = oct.toLowerCase().charAt(i);
			a *= 8;
			switch (ch) {
			case '7': a += 1;
			case '6': a += 1;
			case '5': a += 1;
			case '4': a += 1;
			case '3': a += 1;
			case '2': a += 1;
			case '1': a += 1;
			case '0': break;
			default:
				throw new NumberFormatException("Bad oct: " + oct);
			}
		}
		
		return a;
	}
	
	public static int parseBin(String bin) {
		if (bin.startsWith("0b")) bin = bin.substring(2);
		
		int a = 0;
		
		for (int i = 0; i < bin.length(); i++) {
			char ch = bin.toLowerCase().charAt(i);
			a *= 2;
			switch (ch) {
			case '1': a += 1;
			case '0': break;
			default:
				throw new NumberFormatException("Bad bin: " + bin);
			}
		}
		
		return a;
	}

	private static void testHex(String str) {
		System.out.println(str + " -> " + parseHex(str));
	}
	
	private static void testOct(String str) {
		System.out.println(str + " -> " + parseOct(str));
	}
	
	private static void testBin(String str) {
		System.out.println(str + " -> " + parseBin(str));
	}
	
	public static void main(String[] args) {
		testHex("0xFF");
		testHex("0xAB");
		testHex("0x0B");
		testOct("17");
		testBin("1011");
	}
	
}
