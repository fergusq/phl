package org.kaivos.proceedhl.compiler;

import java.io.PrintWriter;

public abstract class CodeGenerator {

	public enum PILCondition {
		EQUALS,
		INQUALS,
		GREATER_THAN,
		GREATHER_THAN_OR_EQUAL,
		LESS_THAN,
		LESS_THAN_OR_EQUAL
	}
	
	private final PrintWriter out;
	private StringBuffer buffer = new StringBuffer();
	
	private boolean allowPrinting = true;
	
	private int indentation;
	
	public CodeGenerator(PrintWriter out) {
		this.out = out;
	}
	
	public void incrementIndentationLevel() {
		indentation++;
	}
	
	public void decrementIndentationLevel() {
		indentation--;
	}
	
	private String indentation() {
		String i = "";
		for (int j = 0; j < indentation; j++) i += "\t";
		return i;
	}
	
	@Deprecated
	public void print(String str) {
		if (allowPrinting)
			this.buffer.append(str);
	}
	
	public void println(String str) {
		if (allowPrinting)
			this.buffer.append(indentation() + str + "\n");
	}
	
	@Deprecated
	public void println() {
		if (allowPrinting)
			this.buffer.append("\n");
	}
	
	public void flush() {
		this.out.print(buffer.toString());
		this.out.flush();
	}
	
	public void setAllowPrinting(boolean allowPrinting) {
		this.allowPrinting = allowPrinting;
	}
	
	public abstract void startfile();
	public abstract void endfile();
	
	public abstract void file(String path);
	public abstract void stabs_typedef(String type, String def);
	public abstract void type(String name);
	public abstract void var(String name, String type);
	public abstract void line(int num);
	public abstract void startblock();
	public abstract void endblock();
	
	public abstract void staticvar(String name);
	
	public abstract void extern(String func);
	public abstract void extern_pil(String func);
	
	public abstract void function(boolean exportable, boolean argreg, String name, String... param);
	public abstract void ret(String value);
	public abstract void endfunction();
	
	public void set(String var, String val) {
		set(false, var, val);
	}
	public abstract void set(boolean alias, String var, String val);
	
	public abstract void setref(String var, String val);
	public abstract void deref(String var, String ref);
	
	public abstract void store(String reg, String val);
	public abstract void load(String reg, String var);
	
	public abstract void proceed(String function, String... args);
	public void call(String var, String function, String... args) {
		call(false, var, function, args);
	}
	public abstract void call(boolean alias, String var, String function, String... args);
	
	public abstract void label(String name);
	public abstract void go(String label);
	public abstract void goif(PILCondition cond, String val1, String val2, String to);

	public abstract void constant(String name, String value);
	
	/*** ÄLÄ KÄYTÄTÄ TÄTÄ!!! ***/
	public abstract void pil(String code);
	
}
