package org.kaivos.proceedhl.compiler;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.kaivos.proceed.compiler.Registers;
import org.kaivos.proceedhl.compiler.type.FunctionType;
import org.kaivos.proceedhl.compiler.type.IntegerType;
import org.kaivos.proceedhl.compiler.type.IntegerType.Size;
import org.kaivos.proceedhl.compiler.type.FloatType;
import org.kaivos.proceedhl.compiler.type.PointerType;
import org.kaivos.proceedhl.compiler.type.Type;
import org.kaivos.proceedhl.compiler.type.TypeName;
import org.kaivos.proceedhl.compiler.type.VoidType;
import org.kaivos.proceedhl.parser.ProceedParser;

public class CCodeGenerator extends CodeGenerator {

	public CCodeGenerator(PrintWriter out) {
		super(out);
	}

	private static final int MAIN_BUFFER = 0;
	private static final int TYPE_BUFFER = 1;
	private static final int IMPORT_BUFFER = 2;
	private static final int VARIABLE_BUFFER = 3;
	private static final int FUNCTION_BUFFER = 4;
	
	private static String getCondOp(PILCondition op) {
		switch (op) {
		case EQUALS: return "==";
		case INQUALS: return "!=";
		case GREATER_THAN: return ">";
		case GREATHER_THAN_OR_EQUAL: return ">=";
		case LESS_THAN: return "<";
		case LESS_THAN_OR_EQUAL: return "<=";

		default:
			assert false;
		}
		return "";
	}
	
	private static String censor_(String name) {
		if (name.matches("[0-9]+\\.[0-9]*|\\.[0-9]+")) return name;
		
		String censored = org.kaivos.proceed.compiler.ProceedCompiler.censor2(name);
		if (censored.contains("."))
			System.err.println(name + " => " + censored);
		return censored;
	}
	
	Set<String> declaredFunctions = new HashSet<>();
	Set<String> declaredStatics = new HashSet<>();
	
	Map<String, Type> declaredIdentifiers = new HashMap<>();
	Map<String, TypeName> declaredVariables = new HashMap<>();
	Map<String, Integer> variableCounter = new HashMap<>();
	
	private String censor(String name) {
		switch (name) {
		case "osize":
			return "sizeof(" + new PointerType(new VoidType()).toCString() + ")";
		case "obx":
		case "ocx":
			return "reg_" + name;
		default:
			return declaredVariables.containsKey(name) ? declaredVariables.get(name).getName() : censor_(name);
		}
	}
	
	private void declare(TypeName variable) {
		if (!getAllowPrinting()) return;
		
		int c = variableCounter.get(variable.getName()) == null ? 0 : variableCounter.get(variable.getName());
		
		variableCounter.put(variable.getName(), c+1);
		declaredVariables.put(variable.getName(), new TypeName("i" + c + "_" + censor_(variable.getName()), variable.getType()));
		declaredIdentifiers.put(variable.getName(), variable.getType());
	}
	
	private boolean assign(TypeName variable) {
		if (declaredVariables.containsKey(variable.getName())
				&& declaredVariables.get(variable.getName()).getType().toCString().equals(variable.getType().toCString()))
			return false;
		
		if (declaredVariables.containsKey(variable.getName()))
			System.err.println("Declaring " + variable.getName() + " again, because "
		+ declaredVariables.get(variable.getName()).toCString() + " != " + variable.toCString());
		
		if (declaredStatics.contains(variable.getName()))
			return false;
		
		declare(variable);
		return true;
	}
	
	@SuppressWarnings("unused")
	private Type getTypeOf(String name) {
		 Type type = declaredIdentifiers.get(name);
		 if (type != null) return type;
		 
		 if (constants.contains(name)) return new PointerType(new IntegerType(Size.I8));
		 
		 try {
			 Integer.parseInt(name);
			 return new IntegerType(Size.LONG);
		 } catch (NumberFormatException e) {
			 System.err.println("unknown " + name);
			 return new PointerType(new VoidType());
		 }
	}
	
	@Override
	public void extern(Type ftype, String func) {
		if (!getAllowPrinting()) return;
		
		// uudelleenmäärittelyjä ei sallita TODO tee jotain jos uusi määrittely on tarkempi
		if (declaredIdentifiers.containsKey(func)) return;
		if (declaredFunctions.contains(func)) return;
		
		if (ftype instanceof FunctionType) {
			FunctionType fftype = (FunctionType) ftype;
			println(IMPORT_BUFFER, "extern "
					+ fftype.getReturnType().toCStringWithVariable(
							censor(func) + "(" + Arrays.asList(fftype.getParameterTypes()).stream().map(Type::toCString).collect(Collectors.joining(", ")) + ")")
					+ ";");
		}
		else {
			println(IMPORT_BUFFER, "extern " + ftype.toCStringWithVariable(censor(func)) + ";");
		}
		declaredIdentifiers.put(func, ftype);
	}

	@Override
	public void extern_pil(Type ftype, String func) {
		if (!getAllowPrinting()) return;
		
		extern(ftype, func);
	}

	@Override
	public void staticvar(Type type, String name) {
		if (!getAllowPrinting()) return;
		
		println(VARIABLE_BUFFER, type.toCStringWithVariable(censor(name)) + ";");
		declaredIdentifiers.put(name, type);
		declaredStatics.add(name);
	}
	
	@Override
	public void function(boolean exportable, boolean argreg, Type retType, String name, TypeName... param) {
		if (!getAllowPrinting()) return;
		
		declaredVariables.clear();
		variableCounter.clear();
		
		declaredFunctions.add(name);
		
		Arrays.asList(param).stream()
			.map(a -> new TypeName("var@"+a.getName(), a.getType())).forEach(this::declare);
		
		String decl = retType.toCStringWithVariable(censor(name)
				+ "(" + Arrays.asList(param).stream().map(a -> new TypeName(censor("var@"+a.getName()), a.getType())).map(a -> a.toCString())
				.collect(Collectors.joining(", ")) + ")");
		
		println(VARIABLE_BUFFER, decl + ";");
		
		println(FUNCTION_BUFFER, "// " + name);
		
		println(FUNCTION_BUFFER, decl + " {");
		incrementIndentationLevel(FUNCTION_BUFFER);
		
		println(FUNCTION_BUFFER, retType.toCStringWithVariable("returnValue") + " = 0;");
	}

	@Override
	public void ret(String value) {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		
		println(FUNCTION_BUFFER, "returnValue = " + censor(value) + ";");
	}

	@Override
	public void endfunction() {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		
		println(FUNCTION_BUFFER, "return returnValue;");
		
		decrementIndentationLevel(FUNCTION_BUFFER);
		println(FUNCTION_BUFFER, "}");
		
		declaredVariables.clear();
	}

	@Override
	public void set(boolean alias, Type type, String var, String val) {
		if (!getAllowPrinting()) return;
		
		if (alias) {
			println(FUNCTION_BUFFER, "#define " + var + " " + censor(val) + "");
			declaredIdentifiers.put(var, type);
			declaredVariables.remove(var);
			return;
		}
		
		line(currentLine);
		
		String rvalue = censor(val);
		
		println(FUNCTION_BUFFER, "#undef " + var);
		
		boolean declare = assign(new TypeName(var, type));
		
		println(FUNCTION_BUFFER, (declare ? type.toCStringWithVariable(censor(var)) : censor(var)) + " = " + rvalue + ";");
	}

	@Override
	public void setref(String var, String val) {
		if (!getAllowPrinting()) return;
		
		println(FUNCTION_BUFFER, "*" + censor(var) + " = " + censor(val) + ";");
	}

	@Override
	public void deref(Type type, String var, String ref) {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		
		String rvalue = "*" + censor(ref);
		
		boolean declare = assign(new TypeName(var, type));
		println(FUNCTION_BUFFER, (declare ? type.toCStringWithVariable(censor(var)) : censor(var)) + " = " + rvalue + ";");
	}

	@Override
	public void store(String reg, String val) {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		println(FUNCTION_BUFFER, "reg_" + reg + " = " + censor(val) + ";");
	}

	@Override
	public void load(String var, String reg) {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		println(FUNCTION_BUFFER, "" + censor(var) + " = reg_" + reg + ";");
	}
	
	@Override
	public void setfield(String obj, Type fieldtype, String field, String val) {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		if (fieldtype instanceof FloatType) {
			println(FUNCTION_BUFFER, "*(" + new PointerType(fieldtype).toCString() + ")(((void*) " + censor(obj)
					+ ") + sizeof(" + new PointerType(new VoidType()).toCString() + ") * " + censor(field) + ") = "
					+ censor(val) + ";");
		} else {
			println(FUNCTION_BUFFER, "((" + new PointerType(fieldtype).toCString() + ") " + censor(obj)
					+ ")[" + censor(field) + "] = "
					+ censor(val) + ";");
		}
	}
	
	// TODO muuttujaan asettaminen
	@Override
	public void setfield(boolean alias, Type type, String var, String obj, Type fieldtype, String field, String val) {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		println(FUNCTION_BUFFER, "*(" + new PointerType(fieldtype).toCString() + ")(((void*) " + censor(obj)
				+ ") + sizeof(" + new PointerType(new VoidType()).toCString() + ") * " + censor(field) + ") = "
				+ censor(val) + ";");
	}

	@Override
	public void proceed(String function, String... args) {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		
		switch (function) {
		case "set":
			if (args.length == 3)
				setfield(args[0], new PointerType(new VoidType()), args[1], args[2]);
			break;
		case "@":
			if (args.length == 2)
			println(FUNCTION_BUFFER, "*(" + new PointerType(new PointerType(new VoidType())).toCString() + ")(" + censor(args[0]) + ") = "
					+ censor(args[1]) + ";");
			break;
		/*case "printf":
			println(FUNCTION_BUFFER, censor(function) + "(" + Arrays.asList(args).stream().map(this::censor).collect(Collectors.joining(", ")) + ");");
			break;*/
		default:
			String proceed = "";
			/*if (!declaredFunctions.contains(function)) 
				proceed += "((void(*)("
						+ Arrays.asList(args).stream().map((a) -> getTypeOf(a).toCString()).collect(Collectors.joining(", ")) + ")) "
						+ censor(function) + ")";
			else*/ proceed += censor(function);
			println(FUNCTION_BUFFER, proceed + "(" + Arrays.asList(args).stream().map(this::censor).collect(Collectors.joining(", ")) + ");");
			break;
		}
	}

	@Override
	public void call(boolean alias, Type type, String var, String function, String... args) {
		if (!getAllowPrinting()) return;
		
		String call = "";
		
		switch (function) {
		case "!": case "lib@not":
			assert args.length == 1: "illegal '!'-call";
			if (args.length == 1) {
				call += " !" + censor(args[0]);
				break;
			}
		case "-":
			if (args.length == 1) {
				call += " -" + censor(args[0]);
				break;
			}
		case "&":
			if (args.length == 1) {
				call += " &" + censor(args[0]);
				break;
			}
		case "+":
		case "*":
		case "/":
		case "%":
		case "|":
		case "^":
		case "==":
		case "!=":
		case "<":
		case ">":
		case "<=":
		case ">=":
			call += "(" + Arrays.asList(args).stream().map(this::censor).collect(Collectors.joining(" " + function + " ")) + ")";
			break;
		case "get":
			if (type instanceof FloatType) {
				call += "(*(" + new PointerType(type).toCString() + ")(((" + new PointerType(new VoidType()).toCString() + ") " + censor(args[0])
						+ ") + sizeof(" + new PointerType(new VoidType()).toCString() + ") * " + censor(args[1]) + "))";
			}
			else {
				call += "((" + new PointerType(type).toCString() + ") " + censor(args[0]) + ")[" + censor(args[1]) + "]";
			}
			break;
		case "set":
			if (type instanceof FloatType) {
				call += "(*(" + new PointerType(type).toCString() + ")(((" + new PointerType(new VoidType()).toCString() + ") " + censor(args[0])
						+ ") + sizeof(" + new PointerType(new VoidType()).toCString() + ") * " + censor(args[1]) + ") = "
						+ censor(args[2]) + ")";
			}
			else {
				call += "((" + new PointerType(type).toCString() + ") " + censor(args[0]) + ")[" + censor(args[1]) + "] = " + censor(args[2]);
			}
			break;
		case "@":
			call += "(*(" + new PointerType(type).toCString() + ")(" + censor(args[0]) + "))";
			break;
		case "ptr_size":
			call += "sizeof(" + new PointerType(new VoidType()).toCString() + ")";
			break;
		default:
			/*if (!declaredFunctions.contains(function))
				call += "((" + type.toCString() + "(*)("
					+ Arrays.asList(args).stream().map((a) -> getTypeOf(a).toCString()).collect(Collectors.joining(", ")) + ")) "
					+ censor(function) + ")";
			else*/ call += "((" + type.toCString() + ") " + censor(function);
			
			call += "(" + Arrays.asList(args).stream().map(this::censor).collect(Collectors.joining(", ")) + "))";
			break;
		}
		
		String lvalue;
		
		if (alias) {
			lvalue = "#define " + var + " ";
			declaredIdentifiers.put(var, type);
			declaredVariables.remove(var);
		}
		else {
			if (!Arrays.asList(args).contains(var))
				println(FUNCTION_BUFFER, "#undef " + var);
			boolean declare = assign(new TypeName(var, type));
			lvalue = (declare ? type.toCStringWithVariable(censor(var)) : censor(var)) + " = ";
		}
		
		call = lvalue + call;
		
		if (alias) println(FUNCTION_BUFFER, call);
		else {
			line(currentLine);
			println(FUNCTION_BUFFER, call + ";");
		}
	}

	@Override
	public void label(String name) {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		println(FUNCTION_BUFFER, censor(name) + ":;");
	}

	@Override
	public void go(String label) {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		println(FUNCTION_BUFFER, "goto " + censor(label) + ";");
	}

	@Override
	public void goif(PILCondition cond, String val1, String val2, String to) {
		if (!getAllowPrinting()) return;
		
		line(currentLine);
		println(FUNCTION_BUFFER, "if (" + censor(val1) + " " + getCondOp(cond) + " " + censor(val2) + ") goto " + censor(to) + ";");
	}

	private Set<String> constants = new HashSet<>();
	private StringBuffer cBuffer = new StringBuffer();
	
	@Override
	public void stringConstant(String name, String value) {
		/*String str = "";
		for (int j = 0; j < value.getBytes().length; j++) {
			int c = value.getBytes()[j];
			if (j != 0) str += ", ";
			
			str += "0x" + Integer.toHexString(c);
		}
		
		if (str.length()>0) str = "const char* " + censor(name) + " = {" + str + ", 0x0};";
		else str = "const char* " + censor(name) + " = {0x0};";
		*/
		
		String str = "const char* " + censor(name) + " = \""
				+ value
				.replaceAll("\"", Matcher.quoteReplacement("\\\""))
				.replaceAll("\n", Matcher.quoteReplacement("\\n"))
				.replaceAll("\0", Matcher.quoteReplacement("\\0")) + "\";";
		
		if (!constants.contains(name)) {
			constants.add(name);
			cBuffer.append(str + "\n");
		}
		
		declaredIdentifiers.put(name, new PointerType(new IntegerType(Size.I8)));
	}

	@Override
	public void startfile() {
		if (!getAllowPrinting()) return;
		
		Type floatype = null;
		switch (Registers.REGISTER_SIZE_BIT) {
		default:
		case 32:
			floatype = new FloatType(FloatType.Size.SINGLE);
			break;
		case 64:
			floatype = new FloatType(FloatType.Size.DOUBLE);
			break;
		}
		println(TYPE_BUFFER, "typedef " + floatype.toCStringWithVariable(new FloatType(FloatType.Size.ARCH).toCString()) + ";");
		println(VARIABLE_BUFFER, "extern " + new PointerType(new VoidType()).toCStringWithVariable("reg_obx") + ";");
		println(VARIABLE_BUFFER, "extern " + new PointerType(new VoidType()).toCStringWithVariable("reg_ocx") + ";");
	}

	@Override
	public void endfile() {
		if (!getAllowPrinting()) return;
		
		println(VARIABLE_BUFFER, cBuffer.toString());
	}

	private String currentFile = "";
	private int currentLine = 0;
	
	@Override
	public void file(String path) {
		if (!getAllowPrinting()) return;
		
		println(MAIN_BUFFER, "//%file \"" + path.replace("\"", "\\\"") + "\"");
		currentFile = path;
	}

	@Override
	public void stabs_typedef(String type, String def) {
		if (!getAllowPrinting()) return;
		
		println(TYPE_BUFFER, "//%typedef \"" + type + "\" \"" + def + "\"");
	}

	@Override
	public void type(String name, Type type) {
		if (!getAllowPrinting()) return;
		
		if (ProceedParser.enableDebug) println(TYPE_BUFFER, "//%type \"" + name + "\"");
		String pname = name.substring(0, name.indexOf("<")>=0 ? name.indexOf("<") : name.length());
		if (!pname.contains(":"))
			println(TYPE_BUFFER, "typedef "
					+ type.toCStringWithVariable(pname) + ";");
	}

	@Override
	public void var(String name, String type) {
		if (!getAllowPrinting()) return;
		
		println(FUNCTION_BUFFER, "//%var \"" + type + "\" " + name);
		
		// jos muuttujaa ei ole vielä esitelty, se pitää esitellä
		/*if (!declaredVariables.contains(name)) {
			println("void* " + censor(name) + ";");
			declaredVariables.add(name);
		}*/
	}

	@Override
	public void line(int num) {
		if (!getAllowPrinting()) return;
		
		if (currentLine != 0 && ProceedParser.enableDebug)
			println(FUNCTION_BUFFER, "#line " + num + " \"" + currentFile.replace("\"", "\\\"") + "\"");
		currentLine = num;
	}

	@Override
	public void startblock() {
		if (!getAllowPrinting()) return;
		
		//println(FUNCTION_BUFFER, "{");
		incrementIndentationLevel(FUNCTION_BUFFER);
	}

	@Override
	public void endblock() {
		if (!getAllowPrinting()) return;
		
		decrementIndentationLevel(FUNCTION_BUFFER);
		//println(FUNCTION_BUFFER, "}");
	}
	
	@Override
	public void pil(String code) {
		if (!getAllowPrinting()) return;
		
		println(FUNCTION_BUFFER, "/*\n" + code + "\n*/");
	}

}
