package org.kaivos.proceedhl.compiler;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.kaivos.proceedhl.compiler.type.Type;
import org.kaivos.proceedhl.compiler.type.TypeName;

public class PILCodeGenerator extends CodeGenerator {

	public PILCodeGenerator(PrintWriter out) {
		super(out);
	}
	
	private static final int MAIN_BUFFER = 0;

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
	public void extern(Type ftype, String func) {
		println(MAIN_BUFFER, "extern " + func);
	}

	@Override
	public void extern_pil(Type ftype, String func) {
		println(MAIN_BUFFER, "extern_pil " + func);
	}

	@Override
	public void staticvar(Type type, String name) {
		println(MAIN_BUFFER, "static " + name);
	}
	
	@Override
	public void function(boolean exportable, boolean argreg, Type retType, String name, TypeName... param) {
		//System.err.println("Generating " + name + "(" + Arrays.asList(param).stream().collect(Collectors.joining(", ")) + ")...");
		println(MAIN_BUFFER, (exportable ? " exportable " : "") + (argreg ? " argreg " : "") + name + "(" + Arrays.asList(param).stream().map(p -> p.getName()).collect(Collectors.joining(", ")) + "):");
		incrementIndentationLevel(MAIN_BUFFER);
	}

	@Override
	public void ret(String value) {
		println(MAIN_BUFFER, "return " + value);
	}

	@Override
	public void endfunction() {
		println(MAIN_BUFFER, "ret");
		decrementIndentationLevel(MAIN_BUFFER);
	}

	@Override
	public void set(boolean alias, Type t, String var, String val) {
		if (alias)
			println(MAIN_BUFFER, "alias " + var + " = " + val);
		else
			println(MAIN_BUFFER, var + " = " + val);
	}

	@Override
	public void setref(String var, String val) {
		println(MAIN_BUFFER, "@" + var + " = " + val);
	}

	@Override
	public void deref(Type type, String var, String ref) {
		println(MAIN_BUFFER, var + " = @" + ref);
	}

	@Override
	public void store(String reg, String val) {
		println(MAIN_BUFFER, "put " + reg + " " + val);
	}

	@Override
	public void load(String var, String reg) {
		println(MAIN_BUFFER, "read " + var + " " + reg);
	}

	@Override
	public void setfield(boolean alias, Type type, String var, String obj,
			Type fieldtype, String field, String val) {
		call(alias, type, var, "set", obj, field, val);
	}
	
	@Override
	public void setfield(String obj, Type fieldtype, String field, String val) {
		proceed("set", obj, field, val);
	}
	
	@Override
	public void proceed(String function, String... args) {
		println(MAIN_BUFFER, "proceed " + function + "(" + String.join(", ", args) + ")");
	}

	@Override
	public void call(boolean alias, Type t, String var, String function, String... args) {
		println(MAIN_BUFFER, (alias ? "alias " : "") + var + " = " + function + "(" + String.join(", ", args) + ")");
	}

	@Override
	public void label(String name) {
		println(MAIN_BUFFER, name + ":");
	}

	@Override
	public void go(String label) {
		println(MAIN_BUFFER, "goto " + label);
	}

	@Override
	public void goif(PILCondition cond, String val1, String val2, String to) {
		if (Arrays.asList("oax", "obx", "ocx", "odx").contains(val1)) {
			println(MAIN_BUFFER, "cmp " + val1 + " " + val2 + " j " + getCondOp(cond) + " " + to);
		}
		else {
			println(MAIN_BUFFER, "if " + getCondOp(cond) + " " + val1 + " " + val2 + " goto " + to);
		}
	}

	private Set<String> constants = new HashSet<>();
	private StringBuffer cBuffer = new StringBuffer();
	
	@Override
	public void stringConstant(String name, String value) {
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
		println(MAIN_BUFFER, cBuffer.toString());
	}

	@Override
	public void file(String path) {
		println(MAIN_BUFFER, "%file \"" + path.replace("\"", "\\\"") + "\"");
	}

	@Override
	public void stabs_typedef(String type, String def) {
		println(MAIN_BUFFER, "%typedef \"" + type + "\" \"" + def + "\"");
	}

	@Override
	public void type(String name, Type type) {
		println(MAIN_BUFFER, "%type \"" + name + "\"");
	}

	@Override
	public void var(String name, String type) {
		println(MAIN_BUFFER, "%var \"" + type + "\" " + name);
	}

	@Override
	public void line(int num) {
		println(MAIN_BUFFER, "%line " + num);
	}

	@Override
	public void startblock() {
		println(MAIN_BUFFER, "%block_begin");
		incrementIndentationLevel(MAIN_BUFFER);
	}

	@Override
	public void endblock() {
		decrementIndentationLevel(MAIN_BUFFER);
		println(MAIN_BUFFER, "%block_end");
	}
	
	@Override
	public void pil(String code) {
		println(MAIN_BUFFER, code);
	}

}
