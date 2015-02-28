package org.kaivos.proceedhl.compiler;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.kaivos.proceedhl.compiler.type.Type;
import org.kaivos.proceedhl.compiler.type.TypeName;

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
	private List<StringBuffer> buffers = new ArrayList<>();
	private List<Integer> bufferIndentationLevels = new ArrayList<>();
	
	private boolean allowPrinting = true;
	
	public CodeGenerator(PrintWriter out) {
		this.out = out;
		buffers.add(new StringBuffer());
	}
	
	public void incrementIndentationLevel(int buffer) {
		while (bufferIndentationLevels.size() <= buffer) bufferIndentationLevels.add(0);
		bufferIndentationLevels.set(buffer, bufferIndentationLevels.get(buffer)+1);
	}
	
	public void decrementIndentationLevel(int buffer) {
		while (bufferIndentationLevels.size() <= buffer) bufferIndentationLevels.add(0);
		bufferIndentationLevels.set(buffer, bufferIndentationLevels.get(buffer)-1);
	}
	
	private String indentation(int buffer) {
		while (bufferIndentationLevels.size() <= buffer) bufferIndentationLevels.add(0);
		
		String i = "";
		for (int j = 0; j < bufferIndentationLevels.get(buffer); j++) i += "\t";
		return i;
	}
	
	@Deprecated
	public void println(String str) {
		println(0, str);
	}
	
	public void println(int buffer, String str) {
		while (buffers.size() <= buffer) buffers.add(new StringBuffer());
		if (allowPrinting)
			this.buffers.get(buffer).append(indentation(buffer) + str + "\n");
	}
	
	public void flush() {
		int i = 0;
		for (StringBuffer buffer : buffers) {
			this.out.print(buffer.toString().trim());
			this.out.println();
		}
		this.out.flush();
	}
	
	public void setAllowPrinting(boolean allowPrinting) {
		this.allowPrinting = allowPrinting;
	}
	
	public boolean getAllowPrinting() {
		return allowPrinting;
	}
	
	public abstract void startfile();
	public abstract void endfile();
	
	public abstract void file(String path);
	public abstract void stabs_typedef(String type, String def);
	public abstract void type(String name, Type type);
	public abstract void var(String name, String type);
	public abstract void line(int num);
	public abstract void startblock();
	public abstract void endblock();
	
	public abstract void staticvar(Type type, String name);
	
	public abstract void extern(Type ftype, String func);
	public abstract void extern_pil(Type ftype, String func);
	
	public abstract void function(boolean exportable, boolean argreg, Type returnType, String name, TypeName... param);
	public abstract void ret(String value);
	public abstract void endfunction();
	
	public void set(Type type, String var, String val) {
		set(false, type, var, val);
	}
	public abstract void set(boolean alias, Type type, String var, String val);
	
	public abstract void setref(String var, String val);
	public abstract void deref(Type type, String var, String ref);
	
	public abstract void setfield(String obj, Type fieldtype, String field, String val);
	
	public void setfield(Type type, String var, String obj, Type fieldtype, String field, String val) {
		setfield(false, type, var, obj, fieldtype, field, val);
	}
	public abstract void setfield(boolean alias, Type type, String var, String obj, Type fieldtype, String field, String val);
	
	// TODO alias
	//public abstract void getfield(Type type, String var, String obj, Type fieldtype, String field);
	
	public abstract void store(String reg, String val);
	public abstract void load(String reg, String var);
	
	public abstract void proceed(String function, String... args);
	public void call(Type type, String var, String function, String... args) {
		call(false, type, var, function, args);
	}
	public abstract void call(boolean alias, Type type, String var, String function, String... args);
	
	public abstract void label(String name);
	public abstract void go(String label);
	public abstract void goif(PILCondition cond, String val1, String val2, String to);

	public abstract void stringConstant(String name, String value);
	
	/*** ÄLÄ KÄYTÄTÄ TÄTÄ!!! ***/
	public abstract void pil(String code);
	
}
