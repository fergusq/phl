package org.kaivos.proceedhl.compiler;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PILCodeGenerator extends CodeGenerator {

	public PILCodeGenerator(PrintWriter out) {
		super(out);
	}

	private static String getCondOp(PILCondition op) {
		switch (op) {
		case EQUALS: return "e";
		case INQUALS: return "ne";
		case GREATER_THAN: return "g";
		case GREATHER_THAN_OR_EQUAL: return "ge";
		case LESS_THAN: return "l";
		case LESS_THAN_OR_EQUAL: return "le";

		default:
			assert false;
		}
		return "";
	}
	
	@Override
	public void extern(String func) {
		println("extern " + func);
	}

	@Override
	public void extern_pil(String func) {
		println("extern_pil " + func);
	}

	@Override
	public void staticvar(String name) {
		println("static " + name);
	}
	
	@Override
	public void function(boolean exportable, boolean argreg, String name, String... param) {
		//System.err.println("Generating " + name + "(" + Arrays.asList(param).stream().collect(Collectors.joining(", ")) + ")...");
		println((exportable ? " exportable " : "") + (argreg ? " argreg " : "") + name + "(" + Arrays.asList(param).stream().collect(Collectors.joining(", ")) + "):");
		incrementIndentationLevel();
	}

	@Override
	public void ret(String value) {
		println("return " + value);
	}

	@Override
	public void endfunction() {
		println("ret");
		decrementIndentationLevel();
	}

	@Override
	public void set(boolean alias, String var, String val) {
		if (alias)
			println("alias " + var + " = " + val);
		else
			println(var + " = " + val);
	}

	@Override
	public void setref(String var, String val) {
		println("@" + var + " = " + val);
	}

	@Override
	public void deref(String var, String ref) {
		println(var + " = @" + ref);
	}

	@Override
	public void store(String reg, String val) {
		println("put " + reg + " " + val);
	}

	@Override
	public void load(String var, String reg) {
		println("read " + var + " " + reg);
	}

	@Override
	public void proceed(String function, String... args) {
		println("proceed " + function + "(" + String.join(", ", args) + ")");
	}

	@Override
	public void call(boolean alias, String var, String function, String... args) {
		println((alias ? "alias " : "") + var + " = " + function + "(" + String.join(", ", args) + ")");
	}

	@Override
	public void label(String name) {
		println(name + ":");
	}

	@Override
	public void go(String label) {
		println("goto " + label);
	}

	@Override
	public void goif(PILCondition cond, String val1, String val2, String to) {
		if (Arrays.asList("oax", "obx", "ocx", "odx").contains(val1)) {
			println("cmp " + val1 + " " + val2 + " j " + getCondOp(cond) + " " + to);
		}
		else {
			println("if " + getCondOp(cond) + " " + val1 + " " + val2 + " goto " + to);
		}
	}

	private Set<String> constants = new HashSet<>();
	private StringBuffer cBuffer = new StringBuffer();
	
	@Override
	public void constant(String name, String value) {
		String str = "";
		for (int j = 0; j < value.getBytes().length; j++) {
			int c = value.getBytes()[j];
			if (j != 0) str += ", ";
			
			str += "0x" + Integer.toHexString(c);
		}
		
		if (str.length()>0) str = "const " + name + " " + str + ", 0x0";
		else str = "const " + name + " 0x0";
		
		if (!constants.contains(name)) {
			constants.add(name);
			cBuffer.append(str + "\n");
		}
	}

	@Override
	public void startfile() {
		// ei mitään
	}

	@Override
	public void endfile() {
		println(cBuffer.toString());
	}

	@Override
	public void file(String path) {
		println("%file \"" + path.replace("\"", "\\\"") + "\"");
	}

	@Override
	public void stabs_typedef(String type, String def) {
		println("%typedef \"" + type + "\" \"" + def + "\"");
	}

	@Override
	public void type(String name) {
		println("%type \"" + name + "\"");
	}

	@Override
	public void var(String name, String type) {
		println("%var \"" + type + "\" " + name);
	}

	@Override
	public void line(int num) {
		println("%line " + num);
	}

	@Override
	public void startblock() {
		println("%block_begin");
		incrementIndentationLevel();
	}

	@Override
	public void endblock() {
		decrementIndentationLevel();
		println("%block_end");
	}
	
	@Override
	public void pil(String code) {
		println(code);
	}

}
