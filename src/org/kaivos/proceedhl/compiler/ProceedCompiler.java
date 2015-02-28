package org.kaivos.proceedhl.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import org.kaivos.proceed.compiler.Registers;
import org.kaivos.proceedhl.parser.NumberParser;
import org.kaivos.proceedhl.compiler.CompilerError;
import org.kaivos.proceedhl.compiler.CodeGenerator.PILCondition;
import org.kaivos.proceedhl.compiler.type.FloatType;
import org.kaivos.proceedhl.compiler.type.FunctionType;
import org.kaivos.proceedhl.compiler.type.IntegerType;
import org.kaivos.proceedhl.compiler.type.IntegerType.Size;
import org.kaivos.proceedhl.compiler.type.ArrayType;
import org.kaivos.proceedhl.compiler.type.PointerType;
import org.kaivos.proceedhl.compiler.type.ReferenceType;
import org.kaivos.proceedhl.compiler.type.TypeName;
import org.kaivos.proceedhl.compiler.type.VoidType;
import org.kaivos.proceedhl.documentgen.PHLHeaderCreator;
import org.kaivos.proceedhl.parser.ProceedParser;
import org.kaivos.proceedhl.parser.ProceedTree;
import org.kaivos.proceedhl.parser.ProceedTree.ExpressionTree;
import org.kaivos.proceedhl.parser.ProceedTree.FieldTree;
import org.kaivos.proceedhl.parser.ProceedTree.Flag;
import org.kaivos.proceedhl.parser.ProceedTree.FunctionTree;
import org.kaivos.proceedhl.parser.ProceedTree.GenericStruct;
import org.kaivos.proceedhl.parser.ProceedTree.InterfaceTree;
import org.kaivos.proceedhl.parser.ProceedTree.LineTree;
import org.kaivos.proceedhl.parser.ProceedTree.LineTree.StaticCommand;
import org.kaivos.proceedhl.parser.ProceedTree.LineTree.StaticCondition;
import org.kaivos.proceedhl.parser.ProceedTree.MethodCallTree;
import org.kaivos.proceedhl.parser.ProceedTree.NonLocal;
import org.kaivos.proceedhl.parser.ProceedTree.StartTree;
import org.kaivos.proceedhl.parser.ProceedTree.ExpressionTree.Type;
import org.kaivos.proceedhl.parser.ProceedTree.StructTree;
import org.kaivos.proceedhl.parser.ProceedTree.TypeTree;
import org.kaivos.proceedhl.plugins.CompilerPlugin;

// TODO Tee Protokollat !!!!

public class ProceedCompiler {

	public static final String TMP_TYPE = ":::void:::";
	
	public static final String TOP_TYPE = "Any";
	public static final String UNIT_TYPE = "Void";
	public static final String NULL_TYPE = "Null";
	
	public static final String OBJ_TYPE = "Object";
	
	public static final String INT_TYPE = "Integer";
	public static final String FLOAT_TYPE = "Float";
	
	public static final String BOOL_TYPE = "Boolean";
	public static final String STR_TYPE = "String";
	public static final String LIST_TYPE = "List";
	
	public static final String ARRAY_TYPE = "Array";
	public static final String PTR_TYPE = "Pointer";
	
	public static final String EXCEPTION_TYPE = "Exception";
	
	public static final String FUNC_TYPE = "Function";
	public static final String CLOSURE_TYPE = "Closure";
	public static final String EXT_FUNC_TYPE = "ExternalFunction";
	public static final String METHOD_TYPE = "Method";
	public static final boolean INCLUDE_DEBUG = true;
	
	public Set<CompilerPlugin> plugins = new HashSet<>();
	
	public static Set<String> importPath = new HashSet<>();
	public static String outputPath = "./out/";
	
	static {
		importPath.add("./lib/");
	}
	
	public static Set<String> importedModules = new HashSet<String>();
	
	public String func;
	
	public FunctionTree currFunc;

	public ArrayList<FunctionTree> funcs = new ArrayList<>();
	public static HashMap<String, FunctionTree> functions = new HashMap<>();
	
	public static HashMap<String, InterfaceTree> interfaces = new HashMap<>();
	
	public static HashMap<String, StructTree> structures = new HashMap<>();

	public static Set<String> svarargfuncs = new HashSet<>();
	
	public static Set<String> pilfuncs = new HashSet<>();
	public static HashMap<String, TypeTree> externs = new HashMap<>();
	
	public static HashMap<String, TypeTree> statics = new HashMap<>();
		
	public HashMap<String, TypeTree> vars = new HashMap<>();
	
	public HashMap<String, Integer> consts = new HashMap<>();
	
	public Set<String> imports = new HashSet<>();
	
	/**
	 * Kaikki funktiot, joita kutsutaan jostain, lisätään tähän,
	 * ja lopuksi nämä lisätään outputtiin riippuvaisuuksiksi
	 */
	public Set<TypeName> markExterns = new HashSet<>();
	
	private int ccounter;
	private static int lambdaCounter;
	private static int constantCounter;
	private static int loopCounter;
	
	//private StringBuffer fBuffer = new StringBuffer();
	
	private PHLHeaderCreator documentator;

	private boolean createDocs = false;
	public static boolean useAttributes = true;
	public static boolean useStdLib = true;
	
	/**
	 * ulostulovirta
	 */
	private CodeGenerator out;
	
	public String currModuleFile = "";
	private String currModule = "";
	
	/**
	 * Jos virhe on heitetty, kääntäjän ei tarvitse esim. tulostaa ulostuloa
	 */
	private static boolean errorThrown = false;
	
	/**
	 * Jos out on null, ei ole ulostuloa
	 */
	public static boolean nullOut = false;
	
	public enum Target { PIL, C }
	
	public static Target target;
	
	public ProceedCompiler(String module, boolean createDocs) {
		this.createDocs  = createDocs;
		if (createDocs) try {
			documentator = new PHLHeaderCreator(new PrintWriter("docgen/" + module + ".phh"), new PrintWriter("docgen/" + module + ".html"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Kääntää PHL-moduulin
	 * 
	 * @param tree Moduulipuu
	 * @param file Moduulin tiedosto
	 * @param out1 Ulostulo
	 * @throws CompilerError Jos moduulissa on virheitä
	 */
	@SuppressWarnings("unchecked")
	public void compile(StartTree tree, String file, PrintWriter out1) throws CompilerError {
		if (errorThrown) setAllowPrinting(false);
		
		if (!plugins.isEmpty()) {
			for (CompilerPlugin plugin : plugins) plugin.pre_ast(tree);
		}
		
		if (!tree.module.isEmpty() && createDocs) {
			try {
				documentator = new PHLHeaderCreator(new PrintWriter("docgen/" + tree.module.replace("::", "_") + ".phh"),
						new PrintWriter("docgen/" + tree.module.replace("::", "_") + ".html"));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		if (!tree.module.isEmpty()) {
			currModuleFile = tree.module.replace("::", "/") + ".phl";
			currModule = tree.module;
			if (out1 != null || nullOut) out = target == Target.PIL ? new PILCodeGenerator(out1) : new CCodeGenerator(out1);
			else try {
				if (target == Target.PIL)
					out = new PILCodeGenerator(new PrintWriter(outputPath + tree.module.replace("::", "_") + ".pil"));
				else
					out = new CCodeGenerator(new PrintWriter(outputPath + tree.module.replace("::", "_") + ".c"));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			importedModules.add(tree.module);
		}
		
		if (!importedModules.contains("phl::lang::core") && useStdLib) {
			for (String path : importPath) {
				if (new File(path + "phl/lang/core.phl").exists())
				{
					importedModules.add("phl::lang::core");
					ProceedParser.parseFile(path + "phl/lang/core.phl");
					break;
				}
			}
		}
		
		imports.add("phl::lang::core");
		
		for (String s : tree.imports) {
			imports.add(s);
			
			if (importedModules.contains(s)) continue;
			
			boolean found = false;
			for (String path : importPath)
				if (new File(path + s.replace("::", "/") + ".phl").exists())
				{
					importedModules.add(s);
					ProceedParser.parseFile(path + s.replace("::", "/") + ".phl");
					
					found = true;
					break;
				}
			
			if (!found) err("!E Module " + s + " not found");
			
		}
		
		ArrayList<String> sortedFuncs = new ArrayList<String>(functions.keySet());
		
		Collections.sort(sortedFuncs);
		
		for (int i = 0; i < sortedFuncs.size(); i++) 
		{
			sortedFuncs.set(i, "function@" + sortedFuncs.get(i));
		}
		
		ArrayList<String> privateFunctions = new ArrayList<>();
		
		/** Funktiot, joita kutsutaan main-funktion alussa */
		List<String>[] onloads = new ArrayList[2];
		for (int i = 0; i < 2; i++) onloads[i] = new ArrayList<>();
		
		for (String s : functions.keySet()) {
			privateFunctions.add(s);
			if (s.startsWith("modulef@")) {
				if (s.matches("modulef@([a-z]\\d.*)*\\.onload")) {
					onloads[0].add("function@" + s);
				}
				if (functions.get(s).flags.contains("unittest") && ProceedParser.enableDebug) {
					onloads[
					        functions.get(s).flags.contains("loadLast") ? 1 : 0
					        ].add("function@" + s);
				}
			}
		}
		
		ArrayList<String> privateExterns = new ArrayList<>();
		
		for (String s : externs.keySet()) {
			privateExterns.add(s);
		}
		
		ArrayList<String> privateInterfaces = new ArrayList<>();
		
		for (String s : interfaces.keySet()) {
			privateInterfaces.add(s);
		}
		
		ArrayList<String> privateStructures = new ArrayList<>();
		
		for (String s : structures.keySet()) {
			privateStructures.add(s);
		}
		
		ArrayList<String> privateStatics = new ArrayList<>();
		
		for (String s : statics.keySet()) {
			privateStatics.add("static@" + s);
		}
		
		ArrayList<String> privatePilFunctions = new ArrayList<>();
		
		for (String s : pilfuncs) {
			privatePilFunctions.add(s);
		}
		
		if (!tree.module.equals("phl::lang::core")) {
			//println("extern_pil lib@add extern_pil lib@sub extern_pil lib@mul extern_pil lib@div extern_pil lib@mod extern_pil lib@and " +
			//		"extern_pil lib@or extern_pil lib@xor extern_pil lib@lt extern_pil lib@gt extern_pil lib@le extern_pil lib@ge " +
			//		"extern_pil lib@eq extern_pil lib@neq extern_pil lib@not");
		}
		
		for (String s : tree.externs) {
			if (!externs.containsKey(s)) out.extern(new FunctionType(new PointerType(new VoidType())), s);
			externs.put(s, TypeTree.getDefault(EXT_FUNC_TYPE, TOP_TYPE));
		}
		
		
		
		for (Entry<String, TypeTree> s : tree.excepts.entrySet()) {
			if (!externs.containsKey(s.getKey())) pilfuncs.add(s.getKey());
			externs.put(s.getKey(), s.getValue());
		}
		
		/* Debug-tietoja: tiedoston nimi */
		{
			File f = new File(file);
			out.file(f.getAbsolutePath());
		}
		
		out.startfile();
		
		out.extern(new FunctionType(new PointerType(new VoidType()), new VoidType()), "GC_init");
		
		for (String s : tree.svarargfuncs) svarargfuncs.add(s);
		
		if (createDocs) documentator.startDocument(tree.module);
		
		if (createDocs) documentator.startStatics();
		
		// Staattiset muuttujat rekisteröidään tässä, mutta syötetään ulostuloon vasta myöhemmin,
		// koska tyyppejä ei ole tässä vaiheessa vielä rekisteröity
		for (Entry<String, TypeTree> s : tree.statics.entrySet()) {
			statics.put(s.getKey(), s.getValue());
			if (createDocs) documentator.docStatic(s.getKey(), s.getValue());
		}
		
		boolean mainFound = false;
		
		if (createDocs) {
			documentator.endStatics();
			documentator.startFuncs();
		}
		
		// Lisää funktiot listaan ja manglaa tyyppiparametrit
		for (FunctionTree t : tree.functions) {
			if (createDocs) documentator.docFunc(t);
			if (!functions.containsKey(t.name)) {
				functions.put(t.name, t);
				for (int i = 0; i < t.typeargs.size(); i++) {
					t.typeargs.set(i, "f" + t.name + ":" + t.typeargs.get(i));
				}
				t.genericHandler.add("f" + t.name);
				
				if (t.name.equals("main")) mainFound = true;
			} else {
				err("Function " + t.name + " already exists!");
			}
		}

		if (mainFound) {
			out.function(true, false, new IntegerType(Size.I32),
					"main", new TypeName("argc", new IntegerType(Size.I32)), new TypeName("argv", new ArrayType(new PointerType(new IntegerType(Size.I8)))));
			out.proceed("GC_init");
			for (List<String> onloadl : onloads) {
				for (String s : onloadl) {
					out.proceed(s);
					markExterns.add(new TypeName(s, new FunctionType(new PointerType(new VoidType()))));
				}
			}
			out.proceed("function@main", "var@argc", "var@argv");
			out.endfunction();
		}
		
		if (createDocs) {
			documentator.endFuncs();
			documentator.startInterfaces();
		}

		// Lisää rajapinnat listaan
		for (InterfaceTree t : tree.interfaces) {
			if (interfaces.containsKey(t.name)) {
				err("!E Interface " + t.name + " already exists!");
			}
			if (createDocs) documentator.docInterface(t);
			for (int i = 0; i < t.typeargs.size(); i++) {
				t.typeargs.set(i, "i" + t.name + ":" + t.typeargs.get(i));
			}
			interfaces.put(t.name, t);
		}
		
		if (createDocs) {
			documentator.endInterfaces();
		}
		
		// Lisää rakenteet ja rakenteita vastaavat rajapinnat listaan
		for (StructTree t : tree.structs) {
			if (interfaces.containsKey(t.name)) {
				err("!E Interface " + t.name + " already exists!");
			}
			InterfaceTree i = new InterfaceTree();
			i.name = t.name;
			i.castable = false;
			i.data = TypeTree.getDefault(UNIT_TYPE);
			i.functions = t.functions;
			i.typeargs = t.typeargs;
			i.module = t.module;
			i.flags = t.flags;
			i.flags1 = t.flags1;
			
			for (int j = 0; j < i.typeargs.size(); j++) {
				i.typeargs.set(j, "i" + i.name + ":" + i.typeargs.get(j));
			}
			
			interfaces.put(i.name, i);
			structures.put(t.name, t);
		}
		
		if (createDocs) {
			documentator.startStructs();
			for (StructTree t : tree.structs) documentator.docStruct(t);
			documentator.endStructs();
		}
		
		funcs = (ArrayList<FunctionTree>) tree.functions.clone();
		
		// Alustaa rajapintojen funktiot – Lisää this-parametrit ja manglaa tyyppiparametrit
		for (InterfaceTree t : tree.interfaces) {
			for (int i = 0; i < t.functions.size(); i++) {
				FunctionTree t1 = t.functions.get(i);
				
				t1.params.add(0, "this");
				t1.paramtypes.add(0, TypeTree.getDefault(t.name, t.typeargs.toArray(new String[t.typeargs.size()])));
				
				for (int j = 0; j < t1.typeargs.size(); j++) {
					t1.typeargs.set(j, "f" + t.name + "." + t1.name + ":" + t1.typeargs.get(j));
				}
				t1.typeargs.addAll(0, t.typeargs);
				
				//t1.template = false; // t.template; TODO interface-templatet
				t1.genericHandler.add("i" + t.name);
				t1.genericHandler.add("f" + t.name + "." + t1.name);
				
				t1.name = "method@" + t.name + "." + t1.name;
				
				t1.module = t.module;
				
				if (functions.containsKey(t1.name)) err("Function " + demangle(t1.name) + " already exists!");
				
				funcs.add(t1);
				functions.put(t1.name, t1);
			}
		}
		
		// Varmistaa, että ylityypin mahdolliset tyyppiargumentit on manglattu
		for (StructTree t : tree.structs) {
			currFunc = new FunctionTree();
			currFunc.genericHandler.add("i" + t.name);
			currFunc.typeargs = t.typeargs;
			currFunc.template = false; // t.template; TODO struct-templatet
			t.superType = checkTypeargs(t.superType);
		}
		
		// Käy läpi rakenteet ja päivittää tyyppitiedon ja ylirakenteiden metodit
		for (StructTree t : tree.structs) {
			FunctionTree newf = null;
			for (FunctionTree t1 : t.functions) {
				if (t1.name.equals("new")) newf = t1;
			}
			if (newf == null) {
				newf = new FunctionTree();
				newf.name = "new";
				newf.returnType = TypeTree.getDefault(UNIT_TYPE);
				newf.owner = t.name;
				t.functions.add(newf);
			}
			{ // konstruktoriin tyyppitieto
				LineTree line = new LineTree();
				{
					line.type = LineTree.Type.EXPRESSION;
					line.expr = new ExpressionTree();
					{
						line.expr.type = Type.FUNCTION_CALL_LISP;
						line.expr.function = new MethodCallTree();
						line.expr.function.type = MethodCallTree.Type.METHOD;
						line.expr.function.expr = new ExpressionTree("this");
						line.expr.function.method = "setType";
						line.expr.args.add(new ExpressionTree("\""+t.name+"\""));
						// esim. (this:setType "Type")
					}
				}

				newf.lines.add(0, line);
			}
			
			// (Jos rakenne on luokka, metodit ovat virtuaalisia)
			// Luo wrapperimetodit ja tallentaa metodit luokkaan
			if (t.isClass) {
				
				for (int i = 0; i < t.functions.size(); i++) {
					FunctionTree t1 = t.functions.get(i);
					if (t1.name.equals("new")) continue;
					
					currFunc = new FunctionTree();
					currFunc.typeargs = (ArrayList<String>) t1.typeargs.clone();
					currFunc.typeargs.addAll(0, t.typeargs);
					currFunc.genericHandler.add("f" + t.name + "." + t1.name);
					currFunc.genericHandler.add("i" + t.name);
					
					t1.returnType = checkTypeargs(t1.returnType);
					
					for (int j = 0; j < t1.paramtypes.size(); j++) {
						t1.paramtypes.set(j, checkTypeargs(t1.paramtypes.get(j)));
					}
					
					{ // lisätään funktio new-metodiin
						LineTree line = new LineTree();
						{
							line.type = LineTree.Type.EXPRESSION;
							line.expr = new ExpressionTree();
							{
								line.expr.type = Type.FUNCTION_CALL_LISP;
								line.expr.function = new MethodCallTree();
								line.expr.function.type = MethodCallTree.Type.METHOD;
								line.expr.function.expr = new ExpressionTree(
										"this");
								line.expr.function.method = "method@set_"
										+ t1.name;
								
								ArrayList<TypeTree> typeargs = new ArrayList<>();
								for (String ta : t.typeargs) typeargs.add(TypeTree.getDefault(ta));
								
								line.expr.args.add(new ExpressionTree(
										"vmethod@" + t.name + "." + t1.name, typeargs)); // esim. (this:method@set_m vmethod@c.m~<@T>)
							}
						}

						newf.lines.add(0, line);
					}
					{ // lisätään metodi funktiolistaan
						FunctionTree t2 = new FunctionTree();

						t2.lines = (ArrayList<LineTree>) t1.lines.clone();
						
						t2.params = (ArrayList<String>) t1.params.clone();
						t2.paramtypes = (ArrayList<TypeTree>) t1.paramtypes.clone();
						
						t2.params.add(0, "this");
						t2.paramtypes.add(0, TypeTree.getDefault(t.name, t.typeargs.toArray(new String[t.typeargs.size()])));
						
						t2.returnType = t1.returnType;
						
						t2.alias = t1.alias;
						t2.throwsEx = t1.throwsEx;
						
						t2.name = "vmethod@" + t.name + "." + t1.name;
						t2.owner = t.name;

						t2.typeargs = (ArrayList<String>) t1.typeargs.clone(); // TODO aiemmin t.typeargs
						t2.typeargs.addAll(0, t.typeargs);
						t2.template = t1.template; // t.template; TODO struct-templatet TESTI
						t2.genericHandler.add("i" + t.name);
						t2.genericHandler.add("f" + t.name + "." + t2.name);
						
						t2.module = t.module;
						
						funcs.add(t2);
						functions.put(t2.name, t2);
					}
					if (!containsMethod(t1.name, t.superType)) { // muutetaan metodi wrapperimetodiksi
						LineTree line = new LineTree();
						{
							line.type = LineTree.Type.RETURN;
							line.expr = new ExpressionTree();
							{
								line.expr.type = Type.FUNCTION_CALL_LISP;
								line.expr.function = new MethodCallTree();
								line.expr.function.type = MethodCallTree.Type.EXPRESSION;
								line.expr.function.expr = new ExpressionTree();
								line.expr.function.expr.type = Type.FUNCTION_CALL_LISP;
								line.expr.function.expr.function = new MethodCallTree();
								line.expr.function.expr.function.type = MethodCallTree.Type.METHOD;
								line.expr.function.expr.function.expr = new ExpressionTree(
										"this");
								line.expr.function.expr.function.method = "method@get_"
										+ t1.name;							// esim. return ((this:method@get_m) args);
								
								line.expr.args.add(new ExpressionTree("this"));
								
								for (String s : t1.params) {
									line.expr.args.add(new ExpressionTree(s));
								}
							}
						}
						t1.lines = new ArrayList<>();
						t1.lines.add(line);
						
						if (INCLUDE_DEBUG) {
						
							line = new LineTree();
							{
								line.type = LineTree.Type.EXPRESSION;
								line.expr = new ExpressionTree();
								line.expr.type = Type.FUNCTION_CALL;
								line.expr.var = "nullPtrCheck";
								line.expr.args.add(new ExpressionTree("this"));
							}
							t1.lines.add(0, line);
						
						}
						
						t1.alias = null;
					} else {
						t.functions.remove(i--); // poistetaan metodi, se lisätään uudestaan yliluokasta
					}
					if (!containsField("method@" + t1.name, t.superType)) { // lisätään attribuutti (field)
						
						
						
						// rakenna @Function -tyyppinen esitys metodista
						ArrayList<TypeTree> subtypes = new ArrayList<>();
						subtypes.add(t1.returnType);
						subtypes.add(TypeTree.getDefault(t.name, t.typeargs.toArray(new String[t.typeargs.size()])));
						for (int j = 0; j < t1.paramtypes.size(); j++) subtypes.add(t1.paramtypes.get(j));
						
						FieldTree field = new FieldTree("method@" + t1.name, TypeTree.getDefault(FUNC_TYPE, subtypes
								.toArray(new TypeTree[subtypes.size()])), "method@get_" + t1.name, "method@set_" + t1.name);
						
						t.fields.add(field);
						t.fieldsOrg.add(field);
					}
					
				}
				addSuperMethods(t, t.superType, t.functions);
				if (!t.superType.name.equals(TOP_TYPE)) {
					LineTree line = new LineTree();
					{
						line.type = LineTree.Type.EXPRESSION;
						line.expr = new ExpressionTree();
						{
							line.expr.type = Type.FUNCTION_CALL_LISP;
							line.expr.function = new MethodCallTree();
							line.expr.function.type = MethodCallTree.Type.METHOD;
							line.expr.function.expr = new ExpressionTree("super");
							line.expr.function.method = "new";
							// esim. (super:new)
						}
					}
	
					newf.lines.add(0, line);
				}
			} else {
				// Rakenne – metodit eivät ole virtuaalisia
				addSuperMethodsNonVirtual(t, t.superType, t.functions);
			}
		}
		
		// Luo rakenteiden kenttien asettaja- ja antajametodit
		for (StructTree t : tree.structs) {
			addSuperFields(t, t.superType, t.fields);
			for (int i = 0; i < t.fields.size(); i++) {
				FieldTree f = t.fields.get(i);
				
				// getteri
				{
					FunctionTree t1 = new FunctionTree();

					{
						LineTree line = new LineTree();
						if (INCLUDE_DEBUG) {
							
							line = new LineTree();
							{
								line.type = LineTree.Type.EXPRESSION;
								line.expr = new ExpressionTree();
								line.expr.type = Type.FUNCTION_CALL;
								line.expr.var = "nullPtrCheck";
								line.expr.args.add(new ExpressionTree("this"));
							}
							t1.lines.add(0, line);
						
						}
						
						LineTree t2 = new LineTree();
						t2.type = LineTree.Type.RETURN;
						ExpressionTree e = new ExpressionTree();
						{
							e.type = Type.TYPE_CAST;
							e.typeCast = f.type;
							
							ExpressionTree e1 = new ExpressionTree();
							{
								e1.type = Type.FUNCTION_CALL;
								e1.var = "get";
								ExpressionTree e2 = new ExpressionTree();
								{
									e2.type = Type.VALUE;
									e2.var = "this";
								}
								e1.args.add(e2);
								e2 = new ExpressionTree();
								{
									e2.type = Type.VALUE;
									e2.var = "" + i;
								}
								e1.args.add(e2);
							}
							e.expr = e1;
						}
						t2.expr = e;
						
						t1.lines.add(t2);
					}
					
					t1.params.add(0, "this");
					t1.paramtypes.add(0, TypeTree.getDefault(t.name, t.typeargs.toArray(new String[t.typeargs.size()])));
					
					t1.returnType = f.type;
					
					t1.name = "method@" + t.name + "." + f.getter;
					t1.typeargs = (ArrayList<String>) t.typeargs.clone();
					t1.template = false;
					t1.genericHandler.add("i" + t.name);
					t1.owner = t.name;
					t1.field = i;
					t1.flags.add("getter");
					t1.flags.add(":getter:");
					t1.flags.add(":argreg:");
					t1.flags.addAll(f.getter_flags);
					t1.flags1.putAll(f.getter_flags1);
					
					t1.module = t.module;
					funcs.add(t1);
					functions.put(t1.name, t1);
				}
				
				// setteri
				{
					FunctionTree t1 = new FunctionTree();

					{
						
						LineTree line = new LineTree();
						
						if (INCLUDE_DEBUG) {
							
							line = new LineTree();
							{
								line.type = LineTree.Type.EXPRESSION;
								line.expr = new ExpressionTree();
								line.expr.type = Type.FUNCTION_CALL;
								line.expr.var = "nullPtrCheck";
								line.expr.args.add(new ExpressionTree("this"));
							}
							t1.lines.add(0, line);
						
						}
						
						LineTree t2 = new LineTree();
						t2.type = LineTree.Type.EXPRESSION;
						ExpressionTree e = new ExpressionTree();
						{
							e.type = Type.FUNCTION_CALL;
							e.var = "set";
							ExpressionTree e2 = new ExpressionTree();
							{
								e2.type = Type.VALUE;
								e2.var = "this";
							}
							e.args.add(e2);
							e2 = new ExpressionTree();
							{
								e2.type = Type.VALUE;
								e2.var = "" + i;
							}
							e.args.add(e2);
							e2 = new ExpressionTree();
							{
								e2.type = Type.VALUE;
								e2.var = "value";
							}
							e.args.add(e2);
						}
						t2.expr = e;
						
						t1.lines.add(t2);
					}
					
					t1.params.add(0, "this");
					t1.paramtypes.add(0, TypeTree.getDefault(t.name, t.typeargs.toArray(new String[t.typeargs.size()])));
					
					t1.params.add("value");
					t1.paramtypes.add(f.type);
					
					t1.returnType = TypeTree.getDefault(UNIT_TYPE);
					
					t1.name = "method@" + t.name + "." + f.setter;
					t1.typeargs = (ArrayList<String>) t.typeargs.clone();
					t1.template = false; // t.template; TODO struct-templatet
					t1.genericHandler.add("i" + t.name);
					t1.owner = t.name;
					t1.field = i;
					t1.flags.add("setter");
					t1.flags.add(":setter:");
					t1.flags.add(":argreg:");
					t1.flags.addAll(f.setter_flags);
					t1.flags1.putAll(f.setter_flags1);
					
					t1.module = t.module;
					funcs.add(t1);
					functions.put(t1.name, t1);
				}
				
				// viittaus
				{
					FunctionTree t1 = new FunctionTree();

					{
						LineTree line = new LineTree();
						if (INCLUDE_DEBUG) {
							
							line = new LineTree();
							{
								line.type = LineTree.Type.EXPRESSION;
								line.expr = new ExpressionTree();
								line.expr.type = Type.FUNCTION_CALL;
								line.expr.var = "nullPtrCheck";
								line.expr.args.add(new ExpressionTree("this"));
							}
							t1.lines.add(0, line);
						
						}
						
						LineTree t2 = new LineTree();
						t2.type = LineTree.Type.RETURN;
						t2.expr = ExpressionTree.casttree(TypeTree.getDefault(PTR_TYPE, f.type),
								ExpressionTree.calltree("+",
										ExpressionTree.casttree(TypeTree.getDefault(INT_TYPE), ExpressionTree.casttree(TypeTree.getDefault(TOP_TYPE), ExpressionTree.vartree("this"))),
										ExpressionTree.vartree(""+i)));
						
						t1.lines.add(t2);
					}
					
					t1.params.add(0, "this");
					t1.paramtypes.add(0, TypeTree.getDefault(t.name, t.typeargs.toArray(new String[t.typeargs.size()])));
					
					t1.returnType = TypeTree.getDefault(PTR_TYPE, f.type);
					
					t1.name = "method@" + t.name + ".getref@" + f.name;
					t1.typeargs = (ArrayList<String>) t.typeargs.clone();
					t1.template = false;
					t1.genericHandler.add("i" + t.name);
					t1.owner = t.name;
					t1.field = i;
					t1.flags.add("ref");
					t1.flags.add(":refgetter:");
					t1.flags.add(":argreg:");
					t1.flags.add("warn");
					t1.flags1.put("warn", new Flag("CRITICAL_ONLY"));
					
					t1.module = t.module;
					funcs.add(t1);
					functions.put(t1.name, t1);
				}
			}
			
			// Lisää kaikki "tavalliset" metodit listaan
			// Antaja-, asettaja- ja virtuaaliset metodit ym. on lisätty aiemmin (yleensä heti niiden luonnin jälkeen)
			for (int i = 0; i < t.functions.size(); i++) {
				FunctionTree t1 = t.functions.get(i);
				/*t1.params.add(0, "this");
				t1.paramtypes.add(0, TypeTree.getDefault(t.name, (String[]) t.typeargs.toArray(new String[t.typeargs.size()])));
				t1.name = "method@" + t.name + "." + t1.name;
				if (!t1.typeargsAlreadySet) {
					t1.typeargs = (ArrayList<String>) t.typeargs.clone();
					t1.template = false; // t.template; TODO struct-templatet
				}
				t1.genericHandler = "i" + t1.owner;
				*/
				
				
				FunctionTree t2 = new FunctionTree();

				t2.lines = (ArrayList<LineTree>) t1.lines.clone();
				
				t2.params = (ArrayList<String>) t1.params.clone();
				t2.paramtypes = (ArrayList<TypeTree>) t1.paramtypes.clone();
				
				t2.params.add(0, "this");
				t2.paramtypes.add(0, TypeTree.getDefault(t.name, t.typeargs.toArray(new String[t.typeargs.size()])));
				
				t2.returnType = t1.returnType;
				
				t2.alias = t1.alias;
				t2.throwsEx = t1.throwsEx;
				
				t2.name = "method@" + t.name + "." + t1.name;
				t2.owner = t.name;
				t2.field = t1.field;

				if (!t1.typeargsAlreadySet) {
					t2.typeargs = (ArrayList<String>) t1.typeargs.clone();
					t2.typeargs.addAll(0, t.typeargs);
					t2.template = t1.template; // t.template; TODO struct-templatet
				}
				t2.genericHandler.add("i" + t1.owner);
				t2.genericHandler.add("f" + t.name + "." + t1.name);
				
				t2.flags = t1.flags;
				t2.flags1 = (HashMap<String, Flag>) t1.flags1.clone();
				
				t2.module = t1.module;
				t2.isAbstract = t1.isAbstract;
				t2.isExtern = t1.isExtern;
				
				if (functions.containsKey(t2.name)) err("Function @" + demangle(t2.name) + " already exists!");
				
				funcs.add(t2);
				functions.put(t2.name, t2);
			}
		}
		
		// Varmistaa, että funktioiden parametrien ja palautusarvon tyypit eivät sisällä manglaamattomia tyyppiparametreja
		for (int i = 0; i < funcs.size(); i++) {
			
			FunctionTree t = currFunc = funcs.get(i);
			
			//System.err.println("\nTranslating " + t.name + "... " + t.typeargs + t.genericHandler);
			
			t.returnType = checkTypeargs(t.returnType);
			
			for (int j = 0; j < t.paramtypes.size(); j++) {
				t.paramtypes.set(j, checkTypeargs(t.paramtypes.get(j)));
			}
		}
		
		/* Pluginit */
		
		if (!plugins.isEmpty()) {
			for (InterfaceTree i : tree.interfaces) {
				for (CompilerPlugin plugin : plugins) plugin.post_interface(i);
			}
			
			for (StructTree i : tree.structs) {
				for (CompilerPlugin plugin : plugins) plugin.post_struct(i);
			}
			
			for (int i = 0; i < funcs.size(); i++) {
				for (CompilerPlugin plugin : plugins) plugin.post_function(funcs.get(i));
			}
		}
		
		/* Staattiset muuttujat */
		for (Entry<String, TypeTree> e : tree.statics.entrySet()) {
			out.staticvar(typeFromTree(e.getValue()), "static@" + e.getKey());
		}
		
		/* Funktiot */
		for (int i = 0; i < funcs.size(); i++) {
			
			ccounter = 0;
			
			FunctionTree t = funcs.get(i);
			
			if (!t.template) compileFunction(t);
		}
		
		for (TypeName ts : markExterns) {
			String s = ts.getName();
			if (sortedFuncs.contains(s) || privatePilFunctions.contains(s) || privateStatics.contains(s)) {
				out.extern_pil(ts.getType(), s);
			}
			else if (privateExterns.contains(s))
				out.extern(ts.getType(), s);
		}
		
		/* Debug-tiedot */
		{
			uniqueListFreeze = true;
			
			for (int i = 0; i < uniqueTypes.size(); i++) {
				TypeTree t = uniqueTypes.get(i);
				if (structures.get(t.name) != null) {
					out.stabs_typedef(t.toString().replace("@", ""), STABS_structType(t, structures.get(t.name)));
				}
				
				out.type(t.toString().replace("@", ""), new PointerType(new PointerType(new VoidType())));
			}
			
			uniqueListFreeze = false;
		}
		
		// TODO importtaus vain yhdellä tasolla
		
		/*for (String s : privateFunctions) {
			System.err.println("Removed " + s + " from " + tree.module);
			functions.remove(s);
		}
		for (String s : privateExterns) {
			externs.remove(s);
		}
		for (String s : privateInterfaces) {
			interfaces.remove(s);
		}
		for (String s : privateStructures) {
			structures.remove(s);
		}*/

		out.endfile();
		
		for (String s : tree.pil) out.pil(s.substring(1, s.length()-1));
		
		if (createDocs) {
			documentator.endDocument();
			documentator.flush();
		}
		if (out != null) out.flush();
	}

	/**
	 * Lista kaikista käytetyistä tyypeistä, mukaanlukien rajapintojen geneeriset/template tyypit
	 */
	//private Set<TypeTree> uniqueTypes = new HashSet<ProceedTree.TypeTree>();
	private List<TypeTree> uniqueTypes = new ArrayList<>();
	private boolean uniqueListFreeze = false;
	
	private String STABS_structType(TypeTree t, StructTree s) throws CompilerError {
		
		currFunc = new FunctionTree();
		currFunc.typeargs = s.typeargs;
		currFunc.genericHandler.add("i" + s.name);
		
		int size = 0;
		for (int i = 0; i < s.fields.size(); i++) size += Registers.REGISTER_SIZE;
		
		String members = "";
		for (int i = 0; i < s.fields.size(); i++) {
			TypeTree type = fixTypeargs(checkTypeargs(s.fields.get(i).type), t, s);
			if (!uniqueTypes.contains(type)) {
				uniqueTypes.add(type);
			}
			members += demanglef(s.fields.get(i).name) + ":" + "!" + type.toString().replace("@", "") + "?" + "," + i*Registers.REGISTER_SIZE_BIT + "," + Registers.REGISTER_SIZE_BIT + ";";
		}
		return "*s" + size + members + ";";
	}
	
	public static org.kaivos.proceedhl.compiler.type.Type typeFromTree(TypeTree t) {
		switch (t.name) {
		case INT_TYPE:
			return new IntegerType(Size.LONG);
		case FLOAT_TYPE:
			return new FloatType(FloatType.Size.ARCH);
		case BOOL_TYPE:
			return new IntegerType(Size.LONG);
		case STR_TYPE:
			return new PointerType(new IntegerType(Size.I8), true);
		case PTR_TYPE:
			if (t.subtypes.size() > 0)
				return new PointerType(typeFromTree(t.subtypes.get(0)));
			return new PointerType(new VoidType());
		case LIST_TYPE:
			return new PointerType(new VoidType());
		case NULL_TYPE:
		case UNIT_TYPE:
			return new IntegerType(Size.I8);
		case METHOD_TYPE:
			return new FunctionType(typeFromTree(t.subtypes.get(0)));
		case FUNC_TYPE:
		case EXT_FUNC_TYPE:
			if (t.subtypes.size() > 0)
				return new FunctionType(typeFromTree(t.subtypes.get(0)), t.subtypes.stream().skip(1).map(t_ -> typeFromTree(t_)).collect(Collectors.toList()));
		case OBJ_TYPE:
		default:
			if (structures.containsKey(t.name))
				return new ReferenceType(t.name);
			if (interfaces.containsKey(t.name)) {
				if (interfaces.get(t.name).data != null)
					return typeFromTree(interfaces.get(t.name).data);
			}
			return new PointerType(new VoidType());
		}
	}

	Stack<String> superTypes = new Stack<>();

	private void addSuperFields(StructTree currStruct, TypeTree superType, ArrayList<FieldTree> fields) throws CompilerError {
		if (superTypes.contains(superType.name)) {
			err("Invalid super structure " + superType + ", circulation error");
		}
		if (superType.name.equals(TOP_TYPE)) return;
		if (!structures.containsKey(superType.name)) {
			err("Invalid super structure " + superType + " not resolved");
		}
		
		ArrayList<FieldTree> fields2 = new ArrayList<>();
		
		StructTree t = structures.get(superType.name);
		
		if (t.flags.contains("final")) {
			err("Invalid super structure: can't inherit final structure " + superType);
		}
		
		if (!t.superType.name.equals(TOP_TYPE)) {
			superTypes.push(superType.name);
			addSuperFields(currStruct, t.superType, fields2);
			superTypes.pop();
		}

		for (int i = 0; i < t.fieldsOrg.size(); i++) {
			
			for (FieldTree f : currStruct.fieldsOrg) {
				if (f.name.equals(t.fieldsOrg.get(i).name) ||
						f.getter.equals(t.fieldsOrg.get(i).getter) ||
						f.setter.equals(t.fieldsOrg.get(i).setter)) {
					err("Duplicate field " + t.fieldsOrg.get(i).name + " and " + f.name);
				}
			}
			
			fields2.add(
					new FieldTree(
							t.fieldsOrg.get(i).name,
							fixTypeargs(t.fieldsOrg.get(i).type, // TODO testaa toimiiko!
									currStruct.superType, t),
									t.fieldsOrg.get(i).getter,
									t.fieldsOrg.get(i).setter,
									t.fieldsOrg.get(i).getter_flags,
									t.fieldsOrg.get(i).getter_flags1,
									t.fieldsOrg.get(i).setter_flags,
									t.fieldsOrg.get(i).setter_flags1
					)
			);
		}
		
		fields.addAll(0, fields2);
	}
	
	private boolean containsField(String field, TypeTree superType) throws CompilerError {
		if (superTypes.contains(superType.name)) {
			err("Invalid structure " + superType + ", circulation error");
		}
		if (superType.name.equals(TOP_TYPE)) return false;
		if (!structures.containsKey(superType.name)) {
			err("Invalid structure " + superType + " not resolved");
		}
		
		StructTree t = structures.get(superType.name);
		if (!t.superType.name.equals(TOP_TYPE)) {
			superTypes.push(superType.name);
			if (containsField(field, t.superType)) {
				superTypes.pop();
				return true;
			}
			superTypes.pop();
		}

		for (int i = 0; i < t.fieldsOrg.size(); i++) {
			
			if (t.fieldsOrg.get(i).name.equals(field)) return true;
		}
		
		return false;
	}
	
	@SuppressWarnings("unchecked")
	private void addSuperMethods(StructTree currStruct, TypeTree superType, ArrayList<FunctionTree> methods) throws CompilerError {
		if (superTypes.contains(superType.name)) {
			err("Invalid super class " + superType + ", circulation error");
		}
		if (superType.name.equals(TOP_TYPE)) return;
		if (!structures.containsKey(superType.name)) {
			err("Invalid super class " + superType + " not resolved");
		}
		
		if (!structures.get(superType.name).isClass) {
			ArrayList<FunctionTree> methods2 = new ArrayList<>();
			addSuperMethodsNonVirtual(currStruct, superType, methods2);
			methods.addAll(0, methods2);
			return;
		}
		
		ArrayList<FunctionTree> methods2 = new ArrayList<>();
		
		StructTree t = structures.get(superType.name);
		if (!t.superType.name.equals(TOP_TYPE)) {
			superTypes.push(superType.name);
			addSuperMethods(currStruct, t.superType, methods2);
			superTypes.pop();
		}

		for (int i = 0; i < t.functionsOrg.size(); i++) {
			FunctionTree f = t.functionsOrg.get(i);
			
			FunctionTree t1 = f.clonec();
			{ // tehdään wrapperimetodi
				LineTree line = new LineTree();
				{
					line.type = LineTree.Type.RETURN;
					line.expr = new ExpressionTree();
					{
						line.expr.type = Type.FUNCTION_CALL_LISP;
						line.expr.function = new MethodCallTree();
						line.expr.function.type = MethodCallTree.Type.EXPRESSION;
						line.expr.function.expr = new ExpressionTree();
						line.expr.function.expr.type = Type.FUNCTION_CALL_LISP;
						line.expr.function.expr.function = new MethodCallTree();
						line.expr.function.expr.function.type = MethodCallTree.Type.METHOD;
						line.expr.function.expr.function.expr = new ExpressionTree(
								"this");
						line.expr.function.expr.function.method = "method@get_"
								+ f.name;							// esim. return ((this:method@get_m) this args);
						
						line.expr.args.add(new ExpressionTree("this"));
						
						for (String s : f.params) {
							line.expr.args.add(new ExpressionTree(s));
						}
					}
				}
				t1.lines = new ArrayList<>();
				t1.lines.add(line);
				
				if (INCLUDE_DEBUG) {
					
					line = new LineTree();
					{
						line.type = LineTree.Type.EXPRESSION;
						line.expr = new ExpressionTree();
						line.expr.type = Type.FUNCTION_CALL;
						line.expr.var = "nullPtrCheck";
						line.expr.args.add(new ExpressionTree("this"));
					}
					t1.lines.add(0, line);
				
				}
				
				t1.alias = null;
				
				t1.returnType = fixTypeargs(f.returnType, currStruct.superType, t);
				
				for (int j = 0; j < t1.paramtypes.size(); j++) {
					t1.paramtypes.set(j, fixTypeargs(t1.paramtypes.get(j), currStruct.superType, t));
				}

				t1.typeargs = (ArrayList<String>) f.typeargs.clone();
				t1.typeargs.addAll(0, t.typeargs);
				t1.template = false; // t.template; TODO struct-templatet
				t1.typeargsAlreadySet = true;

				t1.genericHandler = new HashSet<>();
				t1.genericHandler.addAll(f.genericHandler);
			}
			methods2.add(t1);
		}
		methods.addAll(0, methods2);
	}
	
	private void addSuperMethodsNonVirtual(StructTree currStruct, TypeTree superType, ArrayList<FunctionTree> methods) throws CompilerError {
		if (superTypes.contains(superType.name)) {
			err("Invalid super class " + superType + ", circulation error");
		}
		if (superType.name.equals(TOP_TYPE)) return;
		if (!structures.containsKey(superType.name)) {
			err("Invalid super class " + superType + " not resolved");
		}
		
		ArrayList<FunctionTree> methods2 = new ArrayList<>();
		
		StructTree t = structures.get(superType.name);
		if (!t.superType.name.equals(TOP_TYPE)) {
			superTypes.push(superType.name);
			addSuperMethods(currStruct, t.superType, methods2);
			superTypes.pop();
		}

		Set<String> currStructFunctions = new HashSet<>();
		for (FunctionTree f2 : currStruct.functionsOrg) currStructFunctions.add(f2.name);
		
		for (int i = 0; i < t.functionsOrg.size(); i++) {
			FunctionTree f = t.functionsOrg.get(i);
			
			if (f.name.equals("new")) continue;
			
			if (currStructFunctions.contains(f.name)) {
				continue;
			}
			
			FunctionTree t1 = f.clonec();
			{ // Kopioidaan metodi
				t1.returnType = fixTypeargs(f.returnType, currStruct.superType, t);
				for (int j = 0; j < t1.paramtypes.size(); j++) {
					t1.paramtypes.set(j, fixTypeargs(t1.paramtypes.get(j), currStruct.superType, t));
				}

				t1.typeargs.addAll(0, t.typeargs);
				t1.typeargsAlreadySet = true;
				
				//System.err.println(demanglefunction(t1.name) + "" + t1.typeargs + "" + t1.template);
				
				t1.genericHandler = new HashSet<>();
				t1.genericHandler.addAll(f.genericHandler);
			}
			methods2.add(t1);
		}
		methods.addAll(0, methods2);
	}

	private boolean containsMethod(String method, TypeTree superType) throws CompilerError {
		if (superTypes.contains(superType.name)) {
			err("Invalid structure " + superType + ", circulation error");
		}
		if (superType.name.equals(TOP_TYPE)) return false;
		if (!structures.containsKey(superType.name)) {
			err("Invalid structure " + superType + " not resolved");
		}
		
		StructTree t = structures.get(superType.name);
		if (!t.superType.name.equals(TOP_TYPE)) {
			superTypes.push(superType.name);
			if (containsMethod(method, t.superType)) {
				superTypes.pop();
				return true;
			}
			superTypes.pop();
		}

		for (int i = 0; i < t.functionsOrg.size(); i++) {
			
			if (t.functionsOrg.get(i).name.equals(method)) return true;
		}
		
		return false;
	}
	
	private void compileFunction(FunctionTree tree) throws CompilerError {
		
		func = tree.name;
		currFunc = tree;
		
		if (tree.alias != null) {
			String alias = getIdent(tree.alias, new ArrayList<TypeTree>(), null).value;
			if (functions.containsKey(alias) && functions.get(alias).throwsEx && !tree.throwsEx) {
				err("Invalid alias declaration: " + tree.name + " need to be declared with throws ex modifier");
			}
			return;
		}
		
		TypeName[] params = new TypeName[tree.params.size()];
		org.kaivos.proceedhl.compiler.type.Type[] tparams = new org.kaivos.proceedhl.compiler.type.Type[tree.params.size()];
		for (int i = 0; i < tree.params.size(); i++)
			params[i] = new TypeName(tree.params.get(i), tparams[i] = typeFromTree(changeTemplateTypes(tree.paramtypes.get(i), currFunc.templateChanges)));
		
		if (tree.isExtern) {
			if (allowPrinting) markExterns.add(new TypeName("function@" + tree.name,
					new FunctionType(
							typeFromTree(changeTemplateTypes(tree.returnType, currFunc.templateChanges)), 
							tparams
					)));
			return;
		}
		
		out.function(false, tree.flags.contains(":argreg:"),
				typeFromTree(changeTemplateTypes(tree.returnType, currFunc.templateChanges)), "function@"+tree.name, params);
		
		vars.clear();
		
		for (int i = 0; i < tree.params.size(); i++) {
			vars.put(tree.params.get(i), changeTemplateTypes(tree.paramtypes.get(i), currFunc.templateChanges));
			out.var("var@"+tree.params.get(i), changeTemplateTypes(tree.paramtypes.get(i), currFunc.templateChanges).toString().replace("@", ""));
		}
		
		// onEnter = viesti joka tulostetaan kun funktiota kutsutaan, auttaa debuggaamaan
		if (useAttributes && tree.flags.contains("on_enter")) {
			String function = "printf";
			String arg = "\"on_enter "+tree.name+"\\n\"";
			for (String s: tree.flags1.get("on_enter").args) {
				if (s.startsWith("function=")) function = s.substring("function=".length());
				else if (s.startsWith("arg=")) arg = s.substring("arg=".length());
				else arg = s;
			}
			if (arg.startsWith("\"")) out.proceed(function, getConstant(arg.substring(1, arg.length()-1)));
		}
		
		// funktion sisältö, lauseet
		for (LineTree t : tree.lines) {
			compileLine(t, false);
			emptyLine();
		}
		
		if (useAttributes && tree.flags.contains("on_leave"))
			out.set(typeFromTree(changeTemplateTypes(tree.returnType, currFunc.templateChanges)), "_RV", "0");
		
		out.ret("0");
		
		out.label("__out_" + func);
		
		
		// onLeave = viesti joka tulostetaan kun funktiota poistutaan, auttaa debuggaamaan
		if (useAttributes && tree.flags.contains("on_leave")) {
			out.set(new PointerType(new VoidType()), "rtmp", "_RV");
			String function = "printf";
			String arg = "\"on_leave "+tree.name+"\\n\"";
			for (String s: tree.flags1.get("on_leave").args) {
				if (s.startsWith("function=")) function = s.substring("function=".length());
				else if (s.startsWith("arg=")) arg = s.substring("arg=".length());
				else arg = s;
			}
			if (arg.startsWith("\"")) out.proceed(function, getConstant(arg.substring(1, arg.length()-1)));
			out.ret("rtmp");
		}
		
		out.endfunction();
	}

	private String catchLabel = null;
	
	private String breakLabel = null;
	
	public int currLine = 0;
	
	/**
	 * Kääntää lause
	 * 
	 * @param t lausepuu
	 * @param inTry ollaanko try-lohkossa
	 * @throws CompilerError Jos lause on virheellinen
	 */
	private void compileLine(LineTree t, boolean inTry) throws CompilerError {
		currLine = t.line;
		try {
			if (t.line > 0)
				out.line(t.line+1);
			
			if (t.type == LineTree.Type.EXPRESSION) {
				compileExpression(t.expr, inTry);
			} else if (t.type == LineTree.Type.RETURN) {
				TypeTree a = compileExpression(t.expr, "_RV", changeTemplateTypes(currFunc.returnType, currFunc.templateChanges), inTry);
				
				if (a.name.equals(NULL_TYPE)) {
					warn(W_STRICT, "Return value is null");
				}
				
				if (currFunc.flags.contains("lambda") && changeTemplateTypes(currFunc.returnType, currFunc.templateChanges).name.equals(UNIT_TYPE)) {
					// TODO hyväksyisi vain tyyppiä # arvo, ei #[return arvo;]
					// TODO iterator control pitäisi myös hyväksyä... mieti asiaa
					out.ret("0");
				}
				else if (!a.equals(changeTemplateTypes(currFunc.returnType, currFunc.templateChanges)))
				{
					if (!doCasts(a, changeTemplateTypes(currFunc.returnType, currFunc.templateChanges), "_RV"))
						err("Invalid return value: can't cast from " + a + " to " + changeTemplateTypes(currFunc.returnType, currFunc.templateChanges));
				}
				out.ret("_RV");
				out.go("__out_" + func);
			} else if (t.type == LineTree.Type.THROW) {
				TypeTree a = compileExpression(t.expr, "_RV", TypeTree.getDefault(EXCEPTION_TYPE), inTry);
				
				if (a.name.equals(NULL_TYPE)) {
					warn(W_CRITICAL, "Exception is null (this may lead to an undefined behaviour)");
				}
				
				if (!doCasts(a, TypeTree.getDefault(EXCEPTION_TYPE), "_RV"))
					err("Invalid exception value: can't cast from " + a + " to @" + EXCEPTION_TYPE);
				out.store("obx", "1");		// BX = 1
				out.store("ocx", "_RV");	// CX = Virhemuuttuja
				out.go("__out_" + func); 	// Poistu funktiosta
			} else if (t.type == LineTree.Type.ASSIGN) assign: {
				
				//if (vars.get(t.var) != null && t.typedef != null) err("(" + t.var + ") can't declare type twice");
				
				TypeTree a = null, b = null;
				String var = null;
				
				if (currFunc.nonlocal != null && vars.get(t.var) == null) {
					for (int i = 0; i < currFunc.nonlocal.size(); i++) {
						NonLocal nl = currFunc.nonlocal.get(i);
						if (nl.name.equals(t.var)) {
							{
								warn(W_STRICT, "Modified a non-local variable " + t.var + " which has not been declared. This can lead to an undefined behaviour.");
							}
							a = compileExpression(t.expr, var="_NLV", b=nl.type, inTry);
							if (!doCasts(a, b, var)) err("(" + t.var + ") Invalid assigment: can't cast from " + a + " to " + b);
							
							out.call(new PointerType(typeFromTree(b)), "odx", "get", "var@nonlocal", (i*2+2) + "");
							out.setref("odx", "_NLV");
							if (allowPrinting) markExterns.add(new TypeName("get", new FunctionType(new PointerType(new VoidType()))));
							break assign;
						}
					}
				}
				
				if (vars.get(t.var) == null) {
					if (statics.get(t.var) != null) {
						a = compileExpression(t.expr, var="static@" + t.var, b=statics.get(t.var), inTry);
					}
					else if (t.typedef == null && t.vardef) { // var-avainsana
						a = compileExpression(t.expr, var="var@" + t.var, TypeTree.getDefault(TOP_TYPE), inTry);
						vars.put(t.var, b=a);
						out.var(var,  b.toString().replace("@", ""));
					} else if (t.vardef) { // tyyppi annettu
						vars.put(t.var, b=changeTemplateTypes(checkTypeargs(t.typedef), currFunc.templateChanges));
						a = compileExpression(t.expr, var="var@" + t.var, vars.get(t.var), inTry);
						
						out.var(var,  b.toString().replace("@", ""));
					} else {
						err("(" + t.var + ") Invalid assigment: " + t.var + " cannot be resolved");
					}
				} else {
					if (t.typedef != null) {
						err("(" + t.var + ") Invalid assigment: " + t.var + " is already declared as " + vars.get(t.var));
					}
					a = compileExpression(t.expr, var="var@" + t.var, vars.get(t.var), inTry);
					b = vars.get(t.var);
				}
				
				if (!doCasts(a, b, var)) err("(" + t.var + ") Invalid assigment: can't cast from " + a + " to " + b);
			} else if (t.type == LineTree.Type.NONLOCAL) {
				
				if (currFunc.nonlocal == null) {
					err("Function " + currFunc.name + " does not have nonlocals!");
				}
				for (String s : t.args) {
					boolean found = false;
					for (int i = 0; i < currFunc.nonlocal.size(); i++) {
						NonLocal nl = currFunc.nonlocal.get(i);
						if (nl.name.equals(s)) {
							if (vars.get(s) != null) {
								err("Invalid nonlocal variable: " + s + " is already declared as " + vars.get(s));
							}
							vars.put(s, nl.type);
							out.call(new PointerType(typeFromTree(nl.type)), "odx", "get", "var@nonlocal",  (i*2+2) + "");
							out.deref(typeFromTree(nl.type), "var@" + s, "odx");
							if (allowPrinting) markExterns.add(new TypeName("get", new FunctionType(new PointerType(new VoidType()))));
							found = true;
							break;
						}
					}
					if (!found) {
						err("Invalid nonlocal variable: " + s + " not resolved");
					}
				}
			} else if (t.type == LineTree.Type.IF) {
				TypeTree a = compileExpression(t.expr, "_CV", TypeTree.getDefault(BOOL_TYPE), inTry);
				
				if (!doCasts(a, TypeTree.getDefault(BOOL_TYPE), "_CV")) err("(" + t.var + ") Invalid condition: can't cast from " + a + " to @" + BOOL_TYPE);
				
				out.goif(PILCondition.EQUALS, "_CV", "0", "_if_else"+ ++loopCounter);
				int counter = loopCounter;
				
				compileLine(t.block, inTry);
				
				out.go("_if"+counter);
				
				out.label("_if_else"+ counter);
				
				if (t.elseBlock != null) compileLine(t.elseBlock, inTry);
				
				out.label("_if"+counter);
			} else if (t.type == LineTree.Type.WHILE) {
				if (t.elseBlock == null) {
					out.label("_while"+ ++loopCounter +"_continue");
					TypeTree a = compileExpression(t.expr, "_CV", TypeTree.getDefault(BOOL_TYPE), inTry);
					
					if (!doCasts(a, TypeTree.getDefault(BOOL_TYPE), "_CV")) err("(" + t.var + ") Invalid condition: can't cast from " + a + " to @" + BOOL_TYPE);
					
					out.goif(PILCondition.EQUALS, "_CV", "0", "_while"+ loopCounter);
					int counter = loopCounter;
					
					String tmp = breakLabel;
					breakLabel = "_while"+ counter+"";
					
					compileLine(t.block, inTry);
					
					breakLabel = tmp;
					
					out.go("_while"+ counter+"_continue");
					out.label("_while"+ counter);
				}
				else {
					TypeTree a = compileExpression(t.expr, "_CV", TypeTree.getDefault(BOOL_TYPE), inTry);
					
					if (!doCasts(a, TypeTree.getDefault(BOOL_TYPE), "_CV")) err("(" + t.var + ") Invalid condition: can't cast from " + a + " to @" + BOOL_TYPE);
					
					out.goif(PILCondition.EQUALS, "_CV", "0", "_while"+ ++loopCounter+"_else");
					
					out.label("_while"+ loopCounter +"_continue");
					
					
					int counter = loopCounter;
					
					String tmp = breakLabel;
					breakLabel = "_while"+ counter+"";
					
					compileLine(t.block, inTry);
					
					breakLabel = tmp;
					
					a = compileExpression(t.expr, "_CV", TypeTree.getDefault(BOOL_TYPE), inTry);
					
					if (!doCasts(a, TypeTree.getDefault(BOOL_TYPE), "_CV")) err("(" + t.var + ") Invalid condition: can't cast from " + a + " to @" + BOOL_TYPE);
					
					out.goif(PILCondition.EQUALS, "_CV", "0", "_while"+ counter);
					out.go("_while"+ counter+"_continue");
					
					out.label("_while"+ counter+"_else");
					
					compileLine(t.elseBlock, inTry);
					
					out.label("_while"+ counter);
				}
			} else if (t.type == LineTree.Type.FOR) {
				
				compileLine(t.block2, inTry); // Assigment
				
				out.label("_for"+ ++loopCounter +"_continue");
				TypeTree a = compileExpression(t.expr, "_CV", TypeTree.getDefault(BOOL_TYPE), inTry);
				
				if (!doCasts(a, TypeTree.getDefault(BOOL_TYPE), "_CV")) err("(" + t.var + ") Invalid condition: can't cast from " + a + " to @" + BOOL_TYPE);
				
				out.goif(PILCondition.EQUALS, "_CV", "0", "_for"+ loopCounter);
				int counter = loopCounter;
				
				String tmp = breakLabel;
				breakLabel = "_for"+ counter+"";
				
				compileLine(t.block, inTry);
				
				compileLine(t.elseBlock, inTry);
				
				breakLabel = tmp;
				
				out.go("_for"+ counter+"_continue");
				out.label("_for"+ counter);
			} else if (t.type == LineTree.Type.DO_WHILE) {
				out.label("_while"+ ++loopCounter +"_continue");
				
				int counter = loopCounter;
				
				String tmp = breakLabel;
				breakLabel = "_while"+ counter+"";
				
				compileLine(t.block, inTry);
				
				breakLabel = tmp;
				
				TypeTree a = compileExpression(t.expr, "_CV", TypeTree.getDefault(BOOL_TYPE), inTry);
				
				if (!doCasts(a, TypeTree.getDefault(BOOL_TYPE), "_CV")) err("(" + t.var + ") Invalid condition: can't cast from " + a + " to @" + BOOL_TYPE);
				
				out.goif(PILCondition.INQUALS, "_CV", "0", "_while"+ counter+"_continue");
				out.label("_while"+ counter);
			} else if (t.type == LineTree.Type.BREAK) {
				out.go(breakLabel);
			} else if (t.type == LineTree.Type.TRY_CATCH) {
				if (inTry) err("Nested trys are illegal!");
				int counter = ++loopCounter;
				
				catchLabel = 	"ex" + counter;
				
				compileLine(t.block, true);
				
				catchLabel = null;
				
				out.go("out_try" + counter);
				
				
				out.label("ex" + counter);
				
				if (vars.get(t.var) != null) {
					err("Invalid catch: variable " + t.var + " is already defined");
				}
				
				vars.put(t.var, TypeTree.getDefault(EXCEPTION_TYPE)); // Virhemuuttuja
				
				out.set(typeFromTree(TypeTree.getDefault(EXCEPTION_TYPE)), "var@"+t.var, "0");
				out.load("var@"+t.var, "ocx"); // Exception-muuttuja on CX:ssä
				out.store("obx", "0");         // Resetoi BX
				out.store("ocx", "0");         // Resetoi CX
				
				compileLine(t.elseBlock, false); // Catch-osio
				
				vars.remove(t.var);
				
				out.label("out_try" + counter);
			} else if (t.type == LineTree.Type.BLOCK) {
				out.startblock();
				
				ArrayList<String> variables = new ArrayList<>();
				for (LineTree t1 : t.lines) {
					if (t1.type == LineTree.Type.ASSIGN) {
						if (!vars.containsKey(t1.var)) {
							variables.add(t1.var);
						}
					}
					compileLine(t1, inTry);
					emptyLine();
				}
				for (String s : variables) {
					vars.remove(s);
				}
				
				out.endblock();
			} else if (t.type == LineTree.Type.STATIC_IF) {
				boolean cond = false;
				if (t.cond == StaticCondition.EQUALS_TYPE) {
					cond = changeTemplateTypes(checkTypeargs(t.typedef), currFunc.templateChanges)
							.equals(changeTemplateTypes(checkTypeargs(t.typedef2), currFunc.templateChanges));
				}
				else if (t.cond == StaticCondition.CASTABLE) {
					cond = castable(changeTemplateTypes(checkTypeargs(t.typedef), currFunc.templateChanges),
							changeTemplateTypes(checkTypeargs(t.typedef2), currFunc.templateChanges));
				}
				else if (t.cond == StaticCondition.MANUALLY_CASTABLE) {
					cond = manuallyCastable(changeTemplateTypes(checkTypeargs(t.typedef), currFunc.templateChanges),
							changeTemplateTypes(checkTypeargs(t.typedef2), currFunc.templateChanges));
				}
				else if (t.cond == StaticCondition.IS_STRUCT) {
					cond = structures.containsKey(changeTemplateTypes(checkTypeargs(t.typedef), currFunc.templateChanges).name);
				}
				else if (t.cond == StaticCondition.IS_CLASS) {
					TypeTree tt = changeTemplateTypes(checkTypeargs(t.typedef), currFunc.templateChanges);
					cond = structures.containsKey(tt.name) && structures.get(tt.name).isClass;
				}
				else if (t.cond == StaticCondition.HAS_ATTRIBUTE) {
					TypeTree tt = changeTemplateTypes(checkTypeargs(t.typedef), currFunc.templateChanges);
					cond = interfaces.containsKey(tt.name) && (interfaces.get(tt.name).flags1.containsValue(t.flag)
							|| t.flag.args.size() == 0 && interfaces.get(tt.name).flags.contains(t.flag.name));
				}
				
				if (t.inverseCond) cond = !cond;
				
				if (cond)
					compileLine(t.block, inTry);
				
				else if (t.elseBlock != null) compileLine(t.elseBlock, inTry);
			} else if (t.type == LineTree.Type.STATIC_COMMAND) {
				if (t.command == StaticCommand.ERROR) {
					err("static __error(" + t.message + ")");
				} else if (t.command == StaticCommand.WARN) {
					warn(W_NORMAL, "static __warn(" + t.message + ")");
				}
			} 
		} catch (Exception ex) {
			//System.err.println("; E: " + currModuleFile + ":" + (t.line+1));
			throw ex;
		}
	}

	public static boolean warnErr = false;
	
	public static final int W_CRITICAL = 0;
	public static final int W_NORMAL = 1;
	public static final int W_STRICT = 2;
	
	public static int w_level = W_NORMAL;
	
	/**
	 * Tulostaa varoitusviestin
	 * 
	 * @param level Taso
	 * @param str Viesti
	 */
	public void warn(int level, String str) {
		
		if (currFunc != null && useAttributes && currFunc.flags.contains("no_warnings")) return;
		
		int max_level = w_level;
		
		if (useAttributes && currFunc != null) {
			if (currFunc.flags.contains("warn")) {
				if (currFunc.flags1.get("warn").args.size() != 0) {
					switch (currFunc.flags1.get("warn").args.get(0)) {
					case "CRITICAL_ONLY":
						max_level = W_CRITICAL;
						break;
					case "NORMAL":
						max_level = W_NORMAL;
						break;
					case "STRICT":
						max_level = W_STRICT;
						break;
					default:
						break;
					}
				}
			}
		}
		if (level > max_level) return;
		
		System.err.print("; W: [" + currModuleFile + ":" + (currLine+1));
		System.err.println("] " + str);
	}
	
	/**
	 * Laukaisee virheen
	 * 
	 * @param str Virhe
	 */
	public void err(String str) throws CompilerError {
		
		if (!errorThrown && !allowPrinting) return;
		
		System.err.print("; E: [" + currModuleFile + ":" + (currLine+1));
		System.err.println("] " + (func != null ? ("(in function " + demangle(func) + ") ") :"") + str);
		
		setAllowPrinting(false);
		errorThrown = true;
		
		ProceedParser.errors++;
	}
	
	/**
	 * <p>Tarkistaa, onko tyyppi olemassa ja onko tyypillä oikea määrä tyyppiargumentteja</p>
	 * 
	 * <p>Muuntaa tyypin tyyppiparametrit muodosta <tt>@Tyyppi</tt> muotoon <tt>@iRajapinta::Tyyppi</tt> kutsumalla funktiota {@link org.kaivos.proceedhl.compiler.ProceedCompiler.checkTypesAndTypeargs}.</p>
	 * 
	 * @param typedef Tarkistettava tyyppi
	 * @return Uusi tyyppi
	 * @throws CompilerError Jos tyyppi on virheellinen
	 * 
	 */
	private TypeTree checkTypeargs(TypeTree typedef) throws CompilerError {
		typedef = (TypeTree) typedef.clone();
		
		if (interfaces.containsKey(typedef.name)) {
			if (interfaces.get(typedef.name).typeargs.size() != typedef.subtypes.size() && typedef.subtypes.size() != 0) {
				err("Invalid typeargs: " + typedef + " is not valid");
			}
			
			InterfaceTree i = interfaces.get(typedef.name);
			
			if (!currModule.equals(i.module) && !imports.contains(i.module)) {
				err(typedef + " cannot be resolved (consider importing " + i.module + ")");
			}
		} else {
			boolean isNotTypepar = false;
			for (String genericHandler : currFunc.genericHandler)
				isNotTypepar &= !currFunc.typeargs.contains(genericHandler + ":" + typedef.name);
			if (isNotTypepar && !typedef.name.contains(":")) {
				if (!typedef.name.equals(TOP_TYPE) && !typedef.name.equals(UNIT_TYPE) && !typedef.name.equals(FUNC_TYPE) && !typedef.name.equals(CLOSURE_TYPE))
				warn(W_CRITICAL,"(function=" + currFunc.name + ") " + typedef + " cannot be resolved");
			}
		}
		
		return checkTypesAndTypeargs(typedef);
	}
	
	/**
	 * <p>Muuntaa tyypin tyyppiparametrit muodosta <tt>@Tyyppi</tt> muotoon <tt>@iRajapinta::Tyyppi</tt>.</p>
	 * 
	 * @param typedef Tarkistettava tyyppi
	 * @param print DEBUG
	 * @return Uusi tyyppi
	 * @throws CompilerError Jos tyyppi on virheellinen
	 */
	private TypeTree checkTypesAndTypeargs(TypeTree typedef, boolean...print) throws CompilerError {
		//if (print.length==0) System.err.print("(In "+currFunc.name+") From " + currFunc.typeargs + ", " + currFunc.genericHandler + ": " + typedef + " to ");
		for (String genericHandler : currFunc.genericHandler)
			if (currFunc.typeargs.contains(genericHandler + ":" + typedef.name)) {
				typedef.name = genericHandler + ":" + typedef.name;
			}
		
		for (TypeTree t : typedef.subtypes) {
			checkTypesAndTypeargs(t, true);
		}
		
		if (!uniqueListFreeze && !uniqueTypes.contains(typedef)) uniqueTypes.add(typedef);
		
		//if (print.length==0) System.err.println(typedef);
		
		return typedef;
		
	}

	public String getConstant(String str1) {
		if (!consts.containsKey(str1)) {
		
			consts.put(str1, ++constantCounter);
			
			out.stringConstant("_c@" + constantCounter, str1);
			
			return "_c@" + constantCounter;
		} else {
			return "_c@" + consts.get(str1);
		}
	}
	
	private boolean allowPrinting = true;
	
	public boolean isAllowingPrinting() {
		return allowPrinting;
	}

	public void setAllowPrinting(boolean allowPrinting) {
		this.allowPrinting = allowPrinting;
		out.setAllowPrinting(allowPrinting);
	}

	private void emptyLine() {
		if (allowPrinting && out != null) out.println("");
	}
	
	class TypeValue {
		String value;
		String value2;
		TypeTree type;
		public TypeValue() {
			this(null, null, null);
		}
		public TypeValue(String value, TypeTree type) {
			this(value, null, type);
		}
		public TypeValue(String value, String value2, TypeTree type) {
			super();
			this.value = value;
			this.value2 = value2;
			this.type = type;
		}
	}
	
	/**
	 * Palauttaa viittauksen tai vakion arvon (merkkijono, numero, totuusarvo, yms)
	 * 
	 * @param ident vakio
	 * @param typeargs tyyppiargumentit
	 * @param expectedType oletettu tyyppi
	 * @return viittaus tai arvo
	 * @throws CompilerError jos ei löydy
	 */
	public TypeValue getAtomic(String ident, ArrayList<TypeTree> typeargs, TypeTree expectedType) throws CompilerError {
		
		if (ident.startsWith("\"")) {
			String str1 = ident.substring(1, ident.length()-1);
			
			return new TypeValue(getConstant(str1), TypeTree.getDefault(STR_TYPE));
		} else if (ident.startsWith("'")) {
			String str1 = ident.substring(1, ident.length()-1);
			
			return new TypeValue(""+(int)str1.charAt(0), TypeTree.getDefault(INT_TYPE));
		} else if (ident.equals("true")) {
			return new TypeValue("1", TypeTree.getDefault(BOOL_TYPE));
		} else if (ident.equals("false")) {
			return new TypeValue("0", TypeTree.getDefault(BOOL_TYPE));
		} else if (ident.equals("void")) {
			return new TypeValue("0", TypeTree.getDefault(UNIT_TYPE));
		} else if (ident.equals("null")) {
			return new TypeValue("0", TypeTree.getDefault(NULL_TYPE));
		} else if (ident.startsWith("0x")) {
			return new TypeValue(NumberParser.parseHex(ident) + "", TypeTree.getDefault(INT_TYPE));
		} else if (ident.startsWith("0b")) {
			return new TypeValue(NumberParser.parseBin(ident) + "", TypeTree.getDefault(INT_TYPE));
		} else if (ident.matches("[0-9]+((\\.[0-9]*f?)|f)|\\.[0-9]+f?")) {
			return new TypeValue(Double.parseDouble(ident.replaceAll("f$", "")) + "", TypeTree.getDefault(FLOAT_TYPE));
		} else {
			try {
				return new TypeValue(Integer.parseInt(ident) + "", TypeTree.getDefault(INT_TYPE));
			} catch (NumberFormatException ex) {
				return getIdent(ident, typeargs, expectedType);
			}
		}
	}
	
	/** arvaa tyyppiargumentit funktion argumenttien perusteella "type inference" 
	 * 
	 * @param signature funktion signatuuri
	 * @param expectedType funktion odotettu signatuuri ~ argumenttien tyypit
	 * @param f funktio
	 * @param typeargs lista, johon arvatut tyyppiargumentit kirjoitetaan
	 * 
	 * **/
	void infer(ArrayList<TypeTree> signature, ArrayList<TypeTree> expectedType, FunctionTree f, ArrayList<TypeTree> typeargs)  {
			
		if (signature.size() != expectedType.size()) {
			return;
		}
		
		for (int i = 0; i < signature.size(); i++) {
			if (f.typeargs.contains(signature.get(i).name) && typeargs.get(f.typeargs.indexOf(signature.get(i).name)).name.equals(TOP_TYPE)) {
				typeargs.set(f.typeargs.indexOf(signature.get(i).name), expectedType.get(i));
			} else {
				infer(signature.get(i).subtypes, expectedType.get(i).subtypes, f, typeargs);
			}
		}
			
	}
	
	
	/**
	 * 
	 * Etsii nimen (muuttuja, vakio, funktio, yms) ja palauttaa viittauksen siihen
	 * 
	 * @param ident etsittävä nimi
	 * @param typeargs annetut tyyppiargumentit
	 * @param expectedType oletettu tyyppi
	 * @return viittaus
	 * @throws CompilerError jos nimeä ei löydetä
	 */
	@SuppressWarnings({ "unchecked", "null" })
	public TypeValue getIdent(String ident, ArrayList<TypeTree> typeargs, TypeTree expectedType) throws CompilerError {
		typeargs = (ArrayList<TypeTree>) typeargs.clone();
		for (int i = 0; i < typeargs.size(); i++) {
			typeargs.set(i, checkTypeargs(typeargs.get(i)));
		}
		if (vars.containsKey(ident)) {
			return new TypeValue("var@" + ident, vars.get(ident));
		} else if (ident.equals("super")) {
			if (vars.containsKey("this")) {
				if (structures.containsKey(vars.get("this").name)) { // virtuaaliset funktiot
					return new TypeValue("var@this", structures.get(vars.get("this").name).superType);
				} else {
					err(vars.get("this") + " is not a valid structure type");
					return new TypeValue("lib@void", TypeTree.getDefault(TMP_TYPE));
				}
			}
			else {
				err(ident + " cannot be resolved");
				return new TypeValue("lib@void", TypeTree.getDefault(TMP_TYPE));
			}
		} else if (ident.equals("self") && vars.containsKey("nonlocal")) {
			ArrayList<TypeTree> subtypes = new ArrayList<>();
			subtypes.add(checkTypeargs(currFunc.returnType));
			for (int i = 1; i < currFunc.paramtypes.size(); i++) subtypes.add(checkTypeargs(currFunc.paramtypes.get(i)));
			
			return new TypeValue("var@nonlocal", TypeTree.getDefault(CLOSURE_TYPE, subtypes
					.toArray(new TypeTree[subtypes.size()])));
		} else if (statics.containsKey(ident)) {
			if (allowPrinting) markExterns.add(new TypeName("static@" + ident, typeFromTree(statics.get(ident))));
			return new TypeValue("static@" + ident, statics.get(ident));
		} else if (functions.containsKey(ident)) {  // Muuttujat ensin, sitten funktiot
			
			// Jos tyyppiargumentteja ei ole annettu, ne voidaan lukea funktion palautustyypistä ja argumentista
			if (typeargs == null || typeargs.size() == 0 && expectedType != null
					&& expectedType.name.equals(FUNC_TYPE) && expectedType.subtypes.size() != 0) {
				
				// Alusta typeargs
				typeargs = new ArrayList<>(functions.get(ident).typeargs.size());
				int j = functions.get(ident).typeargs.size();
				for (int i = 0; i < j; i++) typeargs.add(TypeTree.getDefault(TOP_TYPE));
				
				// Funktion tyyppi
				ArrayList<TypeTree> subtypes = new ArrayList<>();
				subtypes.add(functions.get(ident).returnType);
				for (TypeTree t2 : functions.get(ident).paramtypes) subtypes.add(t2);
				
				// Etsi tyyppiparametrit funktiosta ja etsi niiden vastaavat tyypit argumenttien tyypeistä
				// ja lisää löydetyt tyypit tyyppiargumentteihin
				infer(subtypes, expectedType.subtypes, functions.get(ident), typeargs);
			}
			
			TypeTree typeargs1 = TypeTree.getDefault("Typeargs", typeargs.toArray(new TypeTree[typeargs.size()]));
			
			ArrayList<TypeTree> subtypes = new ArrayList<>();
			subtypes.add(fixTypeargs(functions.get(ident).returnType, typeargs1, functions.get(ident)));
			for (TypeTree t2 : functions.get(ident).paramtypes) subtypes.add(fixTypeargs(t2, typeargs1, functions.get(ident)));
			
			if (useAttributes && functions.get(ident).flags.contains("deprecated")) {
				warn(W_NORMAL, "Function " + ident + " is deprecated");
			}
			
			if (useAttributes && (functions.get(ident).flags.contains("private") && !functions.get(ident).module.equals(currModule)
					|| functions.get(ident).flags.contains("restrict") && functions.get(ident).flags1.get("restrict").args.size() > 0
					&& currFunc.flags.contains(functions.get(ident).flags1.get("restrict").args.get(0)))) {
				err("Function " + ident + " is not visible");
			}
			
			if (!currModule.equals(functions.get(ident).module) && !imports.contains(functions.get(ident).module) && !ident.startsWith("method@")) {
				err(ident + " cannot be resolved (consider importing " + functions.get(ident).module + ")");
			}
			
			String name = "function@" + ident;
			
			if (functions.get(ident).alias != null) {
				name = "" + getIdent(functions.get(ident).alias, new ArrayList<TypeTree>(), expectedType).value;
			}
			
			if (functions.get(ident).template) {
				FunctionTree t1 = functions.get(ident);
				
				name = "templatefunction@" + t1.name;
				for (TypeTree ptype: subtypes.subList(1, subtypes.size())) name += "@f" + ptype.toBasic();
				
				if (!functions.containsKey(name))
				{ // lisätään metodi funktiolistaan
					FunctionTree t2 = t1.clonec();
					
					// funktio nimi
					t2.name = name;
					
					// lisätään lista tyyppiparametreista, jotka korvataa uusilla tyypeillä
					t2.template = false;
					t2.templateChanges = new HashMap<>();
					{
						for (int i = 0; i < t1.typeargs.size(); i++) {
							t2.templateChanges.put(t1.typeargs.get(i), typeargs.get(i));
						}
					}
					
					if (allowPrinting) funcs.add(t2);
					if (allowPrinting) functions.put(t2.name, t2);
				}
				
				name = "function@" + name;
			}
			
			TypeTree bonus = null;
			
			if (functions.get(ident).throwsEx) bonus = TypeTree.getDefault(EXCEPTION_TYPE);
			
			if (allowPrinting && !functions.get(ident).template) {
				
				markExterns.add(new TypeName(name, typeFromTree(TypeTree.getDefault(FUNC_TYPE, subtypes
						.toArray(new TypeTree[subtypes.size()])).setBonus(bonus)))); // merkataan funktio riippuvuudeksi
			}
			
			return new TypeValue(name, TypeTree.getDefault(FUNC_TYPE, subtypes
					.toArray(new TypeTree[subtypes.size()])).setBonus(bonus));
		} else if (Arrays.asList("==", "!=", "!", "<", ">", "<=", ">=", "+", "-", "*", "/", "%", "and", "or", "xor", "__at1__", "__at2__").contains(ident)) {
			final TypeTree btt = TypeTree.getDefault(FUNC_TYPE, BOOL_TYPE, TOP_TYPE, TOP_TYPE);
			final TypeTree bb = TypeTree.getDefault(FUNC_TYPE, BOOL_TYPE, BOOL_TYPE);
			final TypeTree bbb = TypeTree.getDefault(FUNC_TYPE, BOOL_TYPE, BOOL_TYPE, BOOL_TYPE);
			final TypeTree bii = TypeTree.getDefault(FUNC_TYPE, BOOL_TYPE, INT_TYPE, INT_TYPE);
			final TypeTree iii = TypeTree.getDefault(FUNC_TYPE, INT_TYPE, INT_TYPE, INT_TYPE);
			final TypeTree tt = TypeTree.getDefault(FUNC_TYPE, TOP_TYPE, TOP_TYPE);
			final TypeTree ttt = TypeTree.getDefault(FUNC_TYPE, TOP_TYPE, TOP_TYPE, TOP_TYPE);
			
			switch (ident) {
			case "==":
			case "__eq__":
				if (allowPrinting) markExterns.add(new TypeName("lib@eq", typeFromTree(btt)));
				return new TypeValue("lib@eq", btt);
			case "!=":
			case "__neq__":
				if (allowPrinting) markExterns.add(new TypeName("lib@neq", typeFromTree(btt)));
				return new TypeValue("lib@neq", btt);
			case "!":
			case "__not__":
				if (allowPrinting) markExterns.add(new TypeName("lib@not", typeFromTree(bb)));
				return new TypeValue("lib@not", bb);
			case "<":
			case "__lt__":
				if (allowPrinting) markExterns.add(new TypeName("lib@lt", typeFromTree(bii)));
				return new TypeValue("lib@lt", bii);
			case "<=":
			case "__leq__":
				if (allowPrinting) markExterns.add(new TypeName("lib@le", typeFromTree(bii)));
				return new TypeValue("lib@le", bii);
			case ">":
			case "__gt__":
				if (allowPrinting) markExterns.add(new TypeName("lib@gt", typeFromTree(bii)));
				return new TypeValue("lib@gt", bii);
			case ">=":
			case "__geq__":
				if (allowPrinting) markExterns.add(new TypeName("lib@ge", typeFromTree(bii)));
				return new TypeValue("lib@ge", bii);
				
			case "+":
			case "__add__":
				if (allowPrinting) markExterns.add(new TypeName("lib@add", typeFromTree(iii)));
				return new TypeValue("lib@add", iii);
			case "-":
			case "__sub__":
				if (allowPrinting) markExterns.add(new TypeName("lib@sub", typeFromTree(iii)));
				return new TypeValue("lib@sub", iii);
			case "*":
			case "__mul__":
				if (allowPrinting) markExterns.add(new TypeName("lib@mul", typeFromTree(iii)));
				return new TypeValue("lib@mul", iii);
			case "/":
			case "__div__":
				if (allowPrinting) markExterns.add(new TypeName("lib@div", typeFromTree(iii)));
				return new TypeValue("lib@div", iii);
			case "%":
			case "__mod__":
				if (allowPrinting) markExterns.add(new TypeName("lib@mod", typeFromTree(iii)));
				return new TypeValue("lib@mod", iii);
				
			case "and":
			case "__and__":
				if (allowPrinting) markExterns.add(new TypeName("lib@and", typeFromTree(bbb)));
				return new TypeValue("lib@and", bbb);
			case "or":
			case "__or__":
				if (allowPrinting) markExterns.add(new TypeName("lib@or", typeFromTree(bbb)));
				return new TypeValue("lib@or", bbb);
			case "xor":
			case "__xor__":
				if (allowPrinting) markExterns.add(new TypeName("lib@xor", typeFromTree(bbb)));
				return new TypeValue("lib@xor", bbb);
				
			case "__at1__":
				if (allowPrinting) markExterns.add(new TypeName("lib@at1", typeFromTree(tt)));
				return new TypeValue("lib@at1", tt);
			case "__at2__":
				if (allowPrinting) markExterns.add(new TypeName("lib@at2", typeFromTree(ttt)));
				return new TypeValue("lib@at2", ttt);
			default:
				err(ident + " not implemented!");
				return new TypeValue("lib@void", TypeTree.getDefault(TMP_TYPE));
			}
		} else if (externs.containsKey(ident)) {
			if (allowPrinting) markExterns.add(new TypeName(ident, typeFromTree(externs.get(ident))));
			return new TypeValue(ident, externs.get(ident));
		} else {
			if (currFunc != null && currFunc.nonlocal != null)
				for (int i = 0; i < currFunc.nonlocal.size(); i++) {
					NonLocal nl = currFunc.nonlocal.get(i);
					
					String name = "nlvar@" + ++ccounter;
					
					if (nl.name.equals(ident)) {
						{
							warn(W_STRICT, "Dereferenced a non-local variable " + ident + " which has not been declared. This can lead to an undefined behaviour.");
						}
						{
							out.call(new PointerType(typeFromTree(nl.type)), "odx", "get", "var@nonlocal", ""+(i*2+2));
							out.deref(typeFromTree(nl.type), name, "odx");
						}
						if (allowPrinting) markExterns.add(new TypeName("get", typeFromTree(nl.type)));
						return new TypeValue(name, nl.type);
					}
				}
			
			// TODO metodikutsut tyyppiä "(metodi)(argumentit)" eivät toimi!
			if (vars.containsKey("this")) {
				if (structures.containsKey(vars.get("this").name)) { // virtuaaliset funktiot
					if (functions.containsKey("method@" + vars.get("this").name + "." + ident)) {
						return compileMethodCall(new MethodCallTree(new ExpressionTree("this"), ident), null, expectedType, catchLabel != null);
					}
				}
			}
			
			err(ident + " cannot be resolved");
			return new TypeValue("lib@void", TypeTree.getDefault(TMP_TYPE));
		}
	}
	
	private TypeTree decltype(ExpressionTree t, TypeTree expectedType, boolean inTry) throws CompilerError {
		boolean tmp = allowPrinting;
		setAllowPrinting(false);
		TypeTree t1 = compileExpression(t, null, expectedType, inTry);
		setAllowPrinting(tmp);
		return t1;
	}
	private TypeTree decltype(ExpressionTree t, boolean inTry) throws CompilerError {
		return decltype(t, TypeTree.getDefault(TOP_TYPE), inTry);
	}
	
	private TypeTree compileExpression(ExpressionTree t, boolean inTry) throws CompilerError {
		return compileExpression(t, null, TypeTree.getDefault(TOP_TYPE), inTry);
	}

	private TypeValue compileMethodCall(MethodCallTree t, String name, TypeTree expectedType, boolean inTry) throws CompilerError {
		if (t.type == MethodCallTree.Type.METHOD) {
			int objectID = ++ccounter;
			TypeTree tt = null;
			boolean superMethod = false;
			if (t.expr.type == Type.VALUE && t.expr.var.equals("super")) {
				superMethod = true;
			}
			
			// lataa olion väliaikaismuuttujaan ja selvittää sen tyypin
			tt = compileExpression(t.expr, "_o"+objectID , TypeTree.getDefault(TOP_TYPE), inTry);
			
			if (structures.containsKey(tt.name)) {
				if (superMethod)
					superMethod = structures.get(tt.name).isClass;
				if (t.method.equals("new")) superMethod = false;
			}
			
			// Jos olio on rajapintatyyppinen, sillä voi olla metodeja...
			if (interfaces.containsKey(tt.name)) {
				if (functions.containsKey((superMethod?"v":"")+"method@" + tt.name + "." + t.method)) {
					FunctionTree t1 = functions.get((superMethod?"v":"")+"method@" + tt.name + "." + t.method);
					if (t1.owner == null) {
						err("(" + demangle(t.method) + ") Invalid method: " + t1.name + ": super type not found");
						return new TypeValue("lib@void", TypeTree.getDefault(TMP_TYPE));
					}
					while (structures.containsKey(tt.name) && !t1.owner.equals(tt.name)) {
						tt = fixTypeargs(structures.get(tt.name).superType, tt, structures.get(tt.name));
						//System.err.println(t1.toString());
					}
					if (!t1.owner.equals(tt.name)) {
						err("(" + demangle(t.method) + ") Invalid method: " + t1.name + ": super type not found");
						return new TypeValue("lib@void", TypeTree.getDefault(TMP_TYPE));
					}
					ArrayList<TypeTree> subtypes = new ArrayList<>();
					subtypes.add(t1.returnType);
					for (int i = 1; i < t1.paramtypes.size(); i++) subtypes.add(t1.paramtypes.get(i));
					
					ArrayList<TypeTree> typeargs = new ArrayList<>(t1.typeargs.size());
					int j = t1.typeargs.size();
					typeargs.addAll(tt.subtypes);
					for (int i = 0; i < j; i++) typeargs.add(TypeTree.getDefault(TOP_TYPE));
					
					infer(subtypes, expectedType.subtypes, t1, typeargs);
					
					TypeTree typeargs1 = TypeTree.getDefault("Typeargs", typeargs.toArray(new TypeTree[typeargs.size()]));
					
					subtypes = new ArrayList<>();
					subtypes.add(fixTypeargs(t1.returnType, typeargs1, t1));
					for (int i = 1; i < t1.paramtypes.size(); i++) subtypes.add(fixTypeargs(t1.paramtypes.get(i), typeargs1, t1));
					
					if (useAttributes && t1.flags.contains("deprecated")) {
						warn(W_NORMAL, "Method " + demangle(t.method)+ " is deprecated");
					}
					
					if (useAttributes && (t1.flags.contains("private") && !t1.module.equals(currModule)
							|| t1.flags.contains("restrict") && t1.flags1.get("restrict").args.size() > 0
							&& currFunc.flags.contains(t1.flags1.get("restrict").args.get(0)))) {
						err("Method " + tt.name + "." + demangle(t.method) + " is not visible");
						// Jatka virheiden etsintää
					}
					
					String fname = "function@"+(superMethod?"v":"")+"method@" + tt.name + "." + t.method;
					
					if (t1.alias != null) {
						fname = getIdent(t1.alias, new ArrayList<TypeTree>(), null).value;
					}
					
					if (t1.template) {
						fname = "templatemethod@" + t1.name;
						for (TypeTree ptype: typeargs) fname += "@f" + ptype.toBasic();
						
						if (!functions.containsKey(fname))
						{ // lisätään metodi funktiolistaan
							FunctionTree t2 = t1.clonec();
							
							t2.name = fname;
							
							// lisätään lista tyyppiparametreista, jotka korvataan uusilla tyypeillä
							t2.template = false;
							t2.templateChanges = new HashMap<>();
							{
								for (int i = 0; i < t1.typeargs.size(); i++) {
									t2.templateChanges.put(t1.typeargs.get(i), typeargs.get(i));
								}
							}
							
							if (allowPrinting) funcs.add(t2);
							if (allowPrinting) functions.put(t2.name, t2);
						}
						
						fname = "function@" + fname;
					}
					
					subtypes.add(1, TypeTree.getDefault(tt.name));
					
					// metodin nimi selvitetty! laitetaan se haluttuun muuttujaan
					if (name != null) out.set(typeFromTree(TypeTree.getDefault(FUNC_TYPE, subtypes
							.toArray(new TypeTree[subtypes.size()]))), name, fname);

					if (allowPrinting && !t1.template) {
						markExterns.add(new TypeName(fname, typeFromTree(TypeTree.getDefault(FUNC_TYPE, subtypes
								.toArray(new TypeTree[subtypes.size()]))))); // merkataan funktio riippuvuudeksi
					}
					
					subtypes.remove(1);
					
					// Jos metodi heittää virheen, sen tyyppiin lisätään "boonuksena" virhetyyppi (esim. @Method<;@Exception>)
					TypeTree bonus = null;
					if (t1.throwsEx) bonus = TypeTree.getDefault(EXCEPTION_TYPE);
					
					return new TypeValue("_o" + objectID, fname, TypeTree.getDefault(METHOD_TYPE, subtypes
							.toArray(new TypeTree[subtypes.size()])).setBonus(bonus));
					
					
				} else {
					err("Invalid method: " + tt + " does not have method " + demangle(t.method));
					return new TypeValue("lib@void", TypeTree.getDefault(TMP_TYPE));
				}
			} else {
				
				// Olio ei ollut rajapintatyyppinen! Sillä ei siis ole metodeja!
				if (!tt.name.equals(TMP_TYPE))
					err("Invalid method '" + demangle(t.method) + "': " + tt + " is not a valid interface type");
				return new TypeValue("lib@void", TypeTree.getDefault(TMP_TYPE));
			}
		} else {
			
			// Uudelleenohjataan compileExpressioniin
			// Palautusarvo on ohjattu muuttujaan "name",
			// Value on this_is_an_error, koska sitä ei ole tarkoitus käyttää mihinkään
			return new TypeValue("this_is_an_error", null, compileExpression(t.expr, name, expectedType, inTry));
		}
	}
	
	/**
	 * Demangles the name of a function or a method
	 * 
	 * @param method The name of the function or method
	 * @return The demangled name of the function or method
	 */
	private static String demangle(String method) {
		if (method.startsWith("operator@")) method = ProceedTree.demangleOperator(method);
		return demanglefunction(method);
		/*if (method.startsWith("function@")) method = method.substring("function@".length());
		if (method.startsWith("method@")) method = method.substring("method@".length());
		if (method.startsWith("vmethod@")) method = method.substring("vmethod@".length());
		if (method.startsWith("operator@")) method = ProceedTree.demangleOperator(method);
		return method;*/
	}
	
	private static String demanglefunction(String function) {
		if (!function.startsWith("function@")) function = "function@" + function;
		
		if (function.startsWith("function@method@") || function.startsWith("function@vmethod@")) {
			function = function.substring(function.indexOf("@")+1);
			function = function.substring(function.indexOf("@")+1);
			String clazz = function.split("\\.")[0];
			String method = function.split("\\.")[1];
			if (method.startsWith("operator@")) method = ProceedTree.demangleOperator(method).replace(":", "::");
			return clazz + "." + method;
		}
		
		String censored = org.kaivos.proceed.compiler.ProceedCompiler.censor2(function);
		if (censored.contains("_")) return censored.substring(censored.indexOf("_")+1);
		else return censored;
	}
	
	/**
	 * Demangles the name of a field
	 * 
	 * @param field The name of the field
	 * @return The demangled name of the field
	 */
	private static String demanglef(String field) {
		if (field.startsWith("method@")) field = field.substring("method@".length());
		//if (method.startsWith("operator@")) method = "operator_" + method.substring("operator@".length());
		if (field.startsWith("operator@")) field = ProceedTree.demangleOperator(field);
		return field;
	}

	/**
	 * Kääntää lausekkeen
	 * 
	 * @param t
	 *            Lausekepuu
	 * @param name
	 *            Muuttuja, johon lausekkeen arvo sijoitetaan
	 * @param expectedType
	 *            Lausekkeen odotettu tyyppi
	 * @param inTry
	 *            Ollaanko try-lohkossa
	 * @return Lausekkeen tyyppi
	 * @throws CompilerError
	 *             Jos lauseke on virheellinen
	 */
	@SuppressWarnings("unchecked")
	private TypeTree compileExpression(ExpressionTree t, String name, TypeTree expectedType, boolean inTry) throws CompilerError {
		
		// Tyyppimuunnos
		if (t.type == Type.TYPE_CAST) {
			TypeTree typeCast = changeTemplateTypes(checkTypeargs(t.typeCast), currFunc.templateChanges);
			
			if (useAttributes && typeCast.name.equals(TOP_TYPE)) {
				warn(W_NORMAL, "An unsafe cast to " + typeCast + "");
			}
			
			if (name != null) {
				TypeTree a = compileExpression(t.expr, name, typeCast, inTry); // TODO laskua ei tehdä jos ei tarvetta?
				if (interfaces.containsKey(a.name)) {
					String fname = findCast(a, typeCast, "manualcast");
					boolean found = false;
					if (fname != null && functions.containsKey(fname)) {
						found = true;
					} else {
						fname = findCast(a, typeCast, "autocast");
						if (functions.containsKey(fname)) {
							found = true;
						}
					}
					
					if (found) {
						TypeValue tv = getIdent(fname, a.subtypes, TypeTree.getDefault(FUNC_TYPE, typeCast, a));
						fname = tv.value;
						if (allowPrinting) markExterns.add(new TypeName(fname, typeFromTree(tv.type)));
						out.call(typeFromTree(typeCast), name, fname, name);
						return typeCast;
					}
				}
				if (!manuallyCastable(a, typeCast)) {
					err("Invalid type cast: can't cast from " + a + " to " + typeCast);
					return TypeTree.getDefault(TMP_TYPE);
				}
			}
			return typeCast;
		}
		
		// Muuttuja, vakio, numeroarvo
		if (t.type == Type.VALUE) {
			ArrayList<TypeTree> typeargs = new ArrayList<>();
			for (TypeTree tt1: t.typeargs) typeargs.add(changeTemplateTypes(tt1, currFunc.templateChanges));
			
			TypeValue tv = getAtomic(t.var, typeargs, expectedType);
			if (name != null) out.set(name.startsWith("_"), typeFromTree(tv.type), name, tv.value);
			return tv.type;
		}
		
		// Ei-paikallinen muuttuja
		if (t.type == Type.NONLOCAL) {
			if (currFunc.nonlocal != null)
				for (int i = 0; i < currFunc.nonlocal.size(); i++) {
					NonLocal nl = currFunc.nonlocal.get(i);
					if (nl.name.equals(t.var)) {
						if (name != null) {
							out.call(name.startsWith("_"), typeFromTree(nl.type), name, "get", "var@nonlocal", ""+(i*2+3));
						}
						if (allowPrinting) markExterns.add(new TypeName("get", new FunctionType(new PointerType(new VoidType()))));
						return nl.type;
					}
				}
			err("Invalid nonlocal variable: " + t.var + " not resolved");
			return TypeTree.getDefault(TMP_TYPE);
			
		}
		
		// Nimetön funktio
		if (t.type == Type.ANONYMOUS) {
			FunctionTree t1 = new FunctionTree();
			t1.lines = t.lines;
			t1.params = (ArrayList<String>) t.params.clone();
			t1.paramtypes = new ArrayList<>();
			for (TypeTree tt1 : t.paramtypes) {
				t1.paramtypes.add(changeTemplateTypes(checkTypeargs(tt1), currFunc.templateChanges));
			}
			
			// Jos odotustyypissä on tietoa parametrien tyypeistä, sitä verrataan nykyisiin parametrityyppeihin ja määrittelemättömät parametrit muutetaan oikeantyyppisiksi
			if (expectedType.name.equals(CLOSURE_TYPE) && expectedType.subtypes.size() != 0) {
				if (expectedType.subtypes.size()-1 == t1.paramtypes.size()) {
					for (int i = 1; i < expectedType.subtypes.size(); i++) {
						if (!doCasts(expectedType.subtypes.get(i), checkTypeargs(t1.paramtypes.get(i-1)), null)) {
							err("Invalid anonymous function parameter: can't cast from " + t1.paramtypes.get(i-1) + " to " + expectedType.subtypes.get(i));
						} else {
							if (t1.paramtypes.get(i-1).name.equals(TOP_TYPE)) {
								t1.paramtypes.set(i-1, expectedType.subtypes.get(i));
							}
						}
					}
				} else {
					err("Invalid anononymous function: " + expectedType + " requires " + (expectedType.subtypes.size()-1) + " arguments");
				}
			}
			
			// Ensimmäinen argumentti sisältää aina määrittely-ympäristön
			t1.params.add(0, "nonlocal");
			t1.paramtypes.add(0, TypeTree.getDefault(LIST_TYPE));
			
			t1.name = "lambda@" + ++lambdaCounter;
			
			t1.typeargs = (ArrayList<String>) currFunc.typeargs.clone();
			t1.template = false;
			t1.genericHandler = currFunc.genericHandler;
			t1.templateChanges = currFunc.templateChanges;
			
			t1.flags = t.flags;
			t1.flags1 = t.flags1;
			
			t1.nonlocal = new ArrayList<>();
			
			Entry<String, TypeTree>[] var = vars.entrySet().toArray(new Entry[vars.entrySet().size()]);
			
			// merkitään nykyiset nonlocalit funktion nonlocaleiksi (tätä tietoa hyödynnetään, kun funktio käännetään)
			if (currFunc != null && currFunc.nonlocal != null) {
				for (NonLocal nl : currFunc.nonlocal) {
					t1.nonlocal.add(nl);
				}
			}
			
			// merkitään paikalliset muuttujat nonlocaleiksi
			for (Entry<String, TypeTree> e : var) {
				t1.nonlocal.add(new NonLocal(e.getKey(), e.getValue()));
			}
			
			// palautusarvon tyyppi
			if (t.typeCast != null) {
				t1.returnType = changeTemplateTypes(checkTypeargs(t.typeCast), currFunc.templateChanges);
			}
			else if (expectedType.name.equals(CLOSURE_TYPE) && expectedType.subtypes.size() != 0) t1.returnType = expectedType.subtypes.get(0);
			else {
				// yrittää surkeasti etsiä return-lausetta...
				
				FunctionTree tmp = currFunc;
				HashMap<String, TypeTree> tmpv = vars;
				vars = new HashMap<>();
				
				currFunc = t1;
				for (int i = 0; i < t1.params.size(); i++) vars.put(t1.params.get(i), t1.paramtypes.get(i));
				
				for (LineTree t2 : t.lines) {
					if (t2.type == LineTree.Type.RETURN) {
						try {
							t1.returnType = decltype(t2.expr, false);
						} catch (CompilerError ex) {
							continue;
						}
					}
				}
				currFunc = tmp;
				vars = tmpv;
			}
			
			if (allowPrinting) funcs.add(t1);
			
			// allocia tarvitaan määrittely-ympäristön tallentamiseen
			if (allowPrinting) {
				markExterns.add(new TypeName("function@alloc", new FunctionType(new PointerType(new VoidType()), new IntegerType(Size.LONG))));
				markExterns.add(new TypeName("set", new FunctionType(new PointerType(new VoidType()))));
			}
			
			// kasaa tietotyypin
			ArrayList<TypeTree> subtypes = new ArrayList<>();
			subtypes.add(checkTypeargs(t1.returnType));
			for (int i = 0; i < t1.paramtypes.size(); i++) subtypes.add(checkTypeargs(t1.paramtypes.get(i)));
			
			TypeTree typeFunc = TypeTree.getDefault(FUNC_TYPE, subtypes
					.toArray(new TypeTree[subtypes.size()]));
			
			// ensimmäinen parametri on closure-olio itse (nonlocal). sitä ei oteta laskuihin @Closure-tyypissä
			subtypes = new ArrayList<>();
			subtypes.add(checkTypeargs(t1.returnType));
			for (int i = 1; i < t1.paramtypes.size(); i++) subtypes.add(checkTypeargs(t1.paramtypes.get(i)));
				
			TypeTree typeClosure = TypeTree.getDefault(CLOSURE_TYPE, subtypes
					.toArray(new TypeTree[subtypes.size()]));
			
			if (name != null && functions.containsKey("alloc"))  {
				
				// tallennetaan määrittely-ympäristö
				
				int size = 0;
				
				if (currFunc != null && currFunc.nonlocal != null) {
					size = currFunc.nonlocal.size()*2 + var.length*2 + 2;
				}
				else size = var.length*2 + 2;
				
				// luodaan closure-olio haluttuun muuttujaan tarvittavan kokoisena
				out.call(true, new IntegerType(Size.LONG), "_SV", "*", ""+size, "osize");
				out.call(new PointerType(new VoidType()), name, "function@alloc", "_SV");
				
				// tallennetaan viittaus funktioon
				out.setfield(name, new IntegerType(Size.LONG), "0", ""+size);
				out.setfield(name, typeFromTree(typeFunc), "1", "function@"+t1.name);
				
				int arrayIndex = 2;
				
				// tallennetaan nykyiset nonlocalit
				if (currFunc != null && currFunc.nonlocal != null) {
					for (int i = 0; i < currFunc.nonlocal.size(); i++) {
						// ensimmäisenä vuorossa on viittaus
						out.call(new PointerType(typeFromTree(currFunc.nonlocal.get(i).type)),
								"odx", "get", "var@nonlocal", ""+(i*2+2));
						out.setfield(name,
								new PointerType(typeFromTree(currFunc.nonlocal.get(i).type)),
								""+arrayIndex++, "odx");
						
						// sitten säilöttynä muuttujan alkuperäinen arvo
						out.call(typeFromTree(currFunc.nonlocal.get(i).type), "odx", "get", "var@nonlocal", ""+(i*2+3));
						out.setfield(name, typeFromTree(currFunc.nonlocal.get(i).type), ""+arrayIndex++, "odx");
					}
				}
				
				// tallennetaan paikalliset muuttujat
				for (int i = 0; i < var.length; i++) {
					// säilötään viittaus
					out.call(new PointerType(new VoidType()), "odx", "&", "var@" + var[i].getKey());
					out.setfield(name, new PointerType(typeFromTree(var[i].getValue())), ""+arrayIndex++, "odx");
					
					// säilötään nykyinen arvo
					out.setfield(name, typeFromTree(var[i].getValue()), ""+arrayIndex++, "var@" + var[i].getKey());
				}
				
				//println(")");
			} else {
				
				// näitä ei siis löytynyt, hakekaamme niitä ja meidän pitäisi saada virheitä
				getIdent("alloc", new ArrayList<TypeTree>(), null);
				getIdent("set", new ArrayList<TypeTree>(), null);
			}
			
			// palauttaa halutun tyypin
			return typeClosure;
		}
		
		// Typerä wrapperi metodikutsulle
		if (t.type == Type.METHOD_CHAIN) {
			TypeValue tv = compileMethodCall(t.function, name, expectedType, inTry);
			return tv.type;
		}
		
		// typeof
		if (t.type == Type.TYPEOF) {
			if (name != null) {
				out.set(new PointerType(new IntegerType(Size.I8)), name, getConstant(decltype(t.expr, inTry).toString()));
			}
			warn(W_STRICT, decltype(t.expr, inTry).toString());
			return TypeTree.getDefault(STR_TYPE);
		}
		
		// sizeof
		if (t.type == Type.SIZEOF) {
			int size = 0;
			if (structures.containsKey(changeTemplateTypes(checkTypeargs(t.typeCast), currFunc.templateChanges).name)) {
				size = structures.get(changeTemplateTypes(checkTypeargs(t.typeCast), currFunc.templateChanges).name).fields.size() * Registers.REGISTER_SIZE;
			} else size = Registers.REGISTER_SIZE;
			
			if (name != null) {
				out.set(new IntegerType(Size.getSize(Registers.REGISTER_SIZE_BIT)), name, ""+size);
			}
			
			return TypeTree.getDefault(INT_TYPE);
		}
		
		// alustaa uuden olion
		if (t.type == Type.NEW_CALL) {
			// alustettava rakenne
			TypeTree typeCast = changeTemplateTypes(checkTypeargs(t.typeCast), currFunc.templateChanges);
			
			int size = 0;
			
			// haetaan rakenteen koko
			if (structures.containsKey(typeCast.name)) {
				size = structures.get(typeCast.name).fields.size();
			} else {
				err(typeCast + " is not a valid structure type");
				return TypeTree.getDefault(TMP_TYPE);
			}
			
			if (name != null && functions.containsKey("alloc")) {
				markExterns.add(new TypeName("function@alloc", new FunctionType(new PointerType(new VoidType()), new IntegerType(Size.LONG))));
				
				// luodaan uusi olio
				out.call(true, new IntegerType(Size.LONG), "_SV", "*", ""+size, "osize");
				out.call(typeFromTree(typeCast), name, "function@alloc", "_SV");
				
				// kutsutaan newiä, jos sellainen on
				if (functions.containsKey("method@" + typeCast.name + ".new")) {
					if (allowPrinting) markExterns.add(new TypeName("function@method@" + typeCast.name + ".new",
							new FunctionType(new IntegerType(Size.I8), typeFromTree(typeCast))));
					
					out.proceed("function@method@"+typeCast.name+".new", name);
				}
			} else {
				
				// allocia ei löytynyt, hakekaamme se ja virheitä pitäisi tulla
				getIdent("alloc", new ArrayList<TypeTree>(), null);
			}
			
			return typeCast;
		}
		
		/* Lista */
		if (t.type == Type.LIST) {
			int size = t.args.size();
			
			if (size == 0) {
				err("Invalid list: list must not be empty");
				return TypeTree.getDefault(TMP_TYPE);
			}
			
			TypeTree type = null;
			if ((expectedType.name.equals(PTR_TYPE)
					|| expectedType.name.equals(LIST_TYPE))
					&& expectedType.subtypes.size() != 0) type = expectedType.subtypes.get(0);
			
			markExterns.add(new TypeName("function@alloc", new FunctionType(new PointerType(new VoidType()), new IntegerType(Size.LONG))));
			
			if (name != null) {
				out.call(true, new IntegerType(Size.LONG), "_SV", "*", ""+size, "osize");
				out.call(new PointerType(typeFromTree(type)), name, "function@alloc", "_SV");
			}
				
			int index = 0;
			
			/* Listatyypin ensimmäinen indeksi on koko */
			if (name != null && expectedType.name.equals(LIST_TYPE))
				out.setfield(name, new IntegerType(Size.LONG), ""+index++, ""+size);
			
			/* Aseta arvot listaan */
			
			for (int i = 0; i < size; i++) {
				
				String tmp_name = "_l" + ccounter++;
				
				TypeTree element_type = compileExpression(t.args.get(i), tmp_name, type==null?TypeTree.getDefault(TOP_TYPE):type, inTry);
				if (type == null) type = element_type;
				else if (!doCasts(element_type, type, tmp_name))
					err("Invalid list element " + i + ": can't cast from " + element_type + " to " + type);
				
				if (name != null)
					out.setfield(name, typeFromTree(type), ""+index++, tmp_name);
			}
			
			return expectedType.name.equals(LIST_TYPE) ? TypeTree.getDefault(LIST_TYPE, type) : TypeTree.getDefault(PTR_TYPE, type);
		}
		if (t.type == Type.EXPRESSION) {
			return compileExpression(t.expr, name, expectedType, inTry);
		}
		int funcID = ccounter;
		String funcName = t.var;
		
		TypeTree ftype;
		
		String object = "";
		
		if (t.type == Type.FUNCTION_CALL_LISP) {
			
			// Tee lista parametrien tyypeistä
			TypeTree[] types = new TypeTree[t.args.size()+1];
			types[0] = expectedType;
			for (int i = 0; i < t.args.size(); i++) {
				
				TypeTree expected = TypeTree.getDefault(TOP_TYPE);
				types[i+1] = decltype(t.args.get(i), expected, inTry);
			}
			
			if (t.function.type == MethodCallTree.Type.EXPRESSION && t.function.expr.type == Type.VALUE) {
				
				ArrayList<TypeTree> typeargs = new ArrayList<>();
				for (TypeTree tt1: t.function.expr.typeargs) typeargs.add(changeTemplateTypes(checkTypeargs(tt1), currFunc.templateChanges));
				
				TypeValue tv = getIdent(t.function.expr.var, typeargs, TypeTree.getDefault(FUNC_TYPE, types));
				ftype = tv.type;
				object = funcName = tv.value;
				if (tv.value2 != null) funcName = tv.value2;
				
			} else if (t.function.type == MethodCallTree.Type.METHOD) {
				TypeValue tv = compileMethodCall(t.function, null, TypeTree.getDefault(FUNC_TYPE, types), inTry);
				ftype = tv.type;
				object = tv.value;
				funcName = tv.value2;
			} else {
				TypeValue tv = compileMethodCall(t.function, "_f" + funcID, TypeTree.getDefault(FUNC_TYPE, types), inTry);
				ftype = tv.type;
				object = tv.value;
				funcName = "_f" + funcID;
				if (tv.value2 != null) funcName = tv.value2;
			}
		} else {
			ArrayList<TypeTree> typeargs = new ArrayList<>();
			for (TypeTree tt1: t.typeargs) typeargs.add(changeTemplateTypes(checkTypeargs(tt1), currFunc.templateChanges));
			
			TypeValue tv = getIdent(funcName, typeargs, null);
			funcName = tv.value;
			ftype = tv.type;
		}
		
		if (!ftype.name.equals(FUNC_TYPE) && !ftype.name.equals(EXT_FUNC_TYPE) && !ftype.name.equals(METHOD_TYPE) && !ftype.name.equals(CLOSURE_TYPE)) {
			if (!ftype.name.equals(TMP_TYPE))
				err(ftype + " is not a valid function type");
			return TypeTree.getDefault(TMP_TYPE);
		}
		
		if ((ftype.name.equals(FUNC_TYPE) || ftype.name.equals(METHOD_TYPE)) && !inTry) {
			if (ftype.bonusType != null) {
				if (!currFunc.throwsEx) {
					err("Invalid call: "+demangle(funcName)+" (" + ftype + ") throws an unhandled exception!");
					return TypeTree.getDefault(TMP_TYPE);
				}
			}
		}
		
		/*if (ftype.name.equals(FUNC_TYPE) && t.args.size() < (ftype.subtypes.size()-1) && ftype.subtypes.get(ftype.subtypes.size()-1).equals("...")) {
			ftype.subtypes.remove(ftype.subtypes.size()-1);
		}*/
		
		if (t.args.size() != ftype.subtypes.size()-1 && !ftype.name.equals(EXT_FUNC_TYPE)) {
			out.flush();
			
			for (int i = 0; i < t.args.size(); i++) {
				//System.err.println(decltype(t.args.get(i), inTry));
			}
			
			/*if (ftype.name.equals(FUNC_TYPE) && t.args.size() > (ftype.subtypes.size()-1) && ftype.subtypes.size() != 0 && ftype.subtypes.get(ftype.subtypes.size()-1).equals("...")) {
				for (int i = ftype.subtypes.size()-1; i < t.args.size()+1; i++) {
					ftype.subtypes.set(i, TypeTree.getDefault(TOP_TYPE));
				}
			}
			else*/ err("Invalid call: " + demangle(funcName) + " (" + ftype + ") requires " + (ftype.subtypes.size()-1) + 
					" arguments (not " + t.args.size() + ")");
			return TypeTree.getDefault(TMP_TYPE);
		}
		
		String[] arguments = new String[t.args.size()];
		TypeTree[] types = new TypeTree[t.args.size()];
		
		for (int i = 0; i < t.args.size(); i++) {
			
			/*if (t.args.get(i).type == Type.VALUE) {
				TypeValue tv = getAtomic(t.args.get(i).var, t.args.get(i).typeargs);
				if (!structures.containsKey(tv.type.name)) {
					types[i] = tv.type;
					arguments[i] = tv.value;
					continue;
				}
			}*/
			
			arguments[i] = "_t" + ccounter++;
			
			TypeTree excepted = TypeTree.getDefault(TOP_TYPE);
			if (ftype.subtypes.size() > t.args.size()) {
				excepted = ftype.subtypes.get(i+1);
			}
			
			types[i] = compileExpression(t.args.get(i), arguments[i], excepted, inTry);
		}

		if (types.length == ftype.subtypes.size()-1)
		for (int i = 0; i < types.length; i++) {
			if (!doCasts(types[i], ftype.subtypes.get(i+1), arguments[i])) 
				{
				err("(function=" + demangle(funcName) + " (" + ftype + ")"  + ") Invalid argument " + i + ": can't cast from " + types[i] + " to " + ftype.subtypes.get(i+1));
				return TypeTree.getDefault(TMP_TYPE);
				}
		}
		
		String func_hard_alias = funcName;
		
		switch (funcName) {
		case "lib@add":
			func_hard_alias = "+";
			break;
		case "lib@sub":
			func_hard_alias = "-";
			break;
		case "lib@mul":
			func_hard_alias = "*";
			break;
		case "lib@div":
			func_hard_alias = "/";
			break;
		case "lib@mod":
			func_hard_alias = "%";
			break;
		case "lib@and":
			func_hard_alias = "&";
			break;
		case "lib@or":
			func_hard_alias = "|";
			break;
		case "lib@xor":
			func_hard_alias = "^";
			break;
		case "lib@eq":
			func_hard_alias = "==";
			break;
		case "lib@neq":
			func_hard_alias = "!=";
			break;
		case "lib@lt":
			func_hard_alias = "<";
			break;
		case "lib@gt":
			func_hard_alias = ">";
			break;
		case "lib@le":
			func_hard_alias = "<=";
			break;
		case "lib@ge":
			func_hard_alias = ">=";
			break;
			
		case "lib@at1":
		case "lib@at2":
			func_hard_alias = "@";
			break;
		default:
			break;
		}
		if (ftype.name.equals(CLOSURE_TYPE)) {
			out.call(new FunctionType(new PointerType(new VoidType())), "_FV", "get", funcName, "1");
			func_hard_alias = "_FV";
		}
		
		String funcNameShort = funcName;
		if (funcName.startsWith("function@")) funcNameShort = funcName.substring("function@".length());
		
		if (functions.containsKey(funcNameShort)) {
			if (functions.get(funcNameShort).flags.contains(":getter:")) {
				func_hard_alias = "get";
				if (ftype.name.equals(METHOD_TYPE)) {
					assert arguments.length == 0;
					
					String[] new_args = new String[1];
					new_args[0] = ""+functions.get(funcNameShort).field;
					arguments = new_args;
				} else {
					assert arguments.length == 1;
					
					String[] new_args = new String[2];
					new_args[0] = arguments[0];
					new_args[1] = ""+functions.get(funcNameShort).field;
					arguments = new_args;
				}
			}
			else if (functions.get(funcNameShort).flags.contains(":setter:")) {
				func_hard_alias = "set";
				if (ftype.name.equals(METHOD_TYPE)) {
					assert arguments.length == 1;
					
					String[] new_args = new String[2];
					new_args[0] = ""+functions.get(funcNameShort).field;
					new_args[1] = arguments[0];
					arguments = new_args;
				} else {
					assert arguments.length == 2;
					
					String[] new_args = new String[3];
					new_args[0] = arguments[0];
					new_args[1] = ""+functions.get(funcNameShort).field;
					new_args[2] = arguments[1];
					arguments = new_args;
				}
			}
		}
		
		List<String> listedArguments = new ArrayList<>();
		if (svarargfuncs.contains(funcName)) listedArguments.add(""+arguments.length);
		if (ftype.name.equals(METHOD_TYPE)) listedArguments.add(object);
		if (ftype.name.equals(CLOSURE_TYPE)) listedArguments.add(funcName);
		listedArguments.addAll(Arrays.asList(arguments));
		
		if (name != null) {
			out.call(name.startsWith("_"), functions.containsKey(funcName) ? typeFromTree(functions.get(funcName).returnType)
					: ftype.subtypes.size() != 0 ? typeFromTree(ftype.subtypes.get(0)) : new PointerType(new VoidType()),
					name, func_hard_alias, listedArguments.toArray(new String[listedArguments.size()]));
		}
		else {
			out.proceed(func_hard_alias, listedArguments.toArray(new String[listedArguments.size()]));
		}
		
		if (ftype.bonusType != null) { // Jos funktio voi heittää virheen tarkista onko virhettä (BX)
			if (inTry) {
				// Jos sisällä try:ssä ja BX on 1, hyppää virheenkäsittelyyn
				out.goif(PILCondition.EQUALS, "obx", "1", catchLabel);
			} else {
				// Muulloin jos BX on 1 poistu funktiosta
				out.goif(PILCondition.EQUALS, "obx", "1", "__out_" + func);
			}
		}
		
		if (name == null && allowPrinting) ccounter = 0;
		
		if (functions.containsKey(funcName)) return functions.get(funcName).returnType;
		else {
			/*if (funcName.equals("list")) {
				TypeTree listType = types.length != 0 ? types[0] : TypeTree.getDefault(TOP_TYPE);
				for (TypeTree tt : types) {
					if (!castable(listType, tt)) {
						err("(" + funcName + ") Invalid argument: can't cast from " + tt + " to " + listType);
					}
				}
				return TypeTree.getDefault(LIST_TYPE, listType);
			}
			if (funcName.equals("get")) {
				TypeTree listType = types.length != 0 ? types[0] : null;
				if (listType != null) {
					if (listType.subtypes.size() != 0) return listType.subtypes.get(0);
				}
			}*/
			return ftype.subtypes.size() != 0 ? ftype.subtypes.get(0) : TypeTree.getDefault(TOP_TYPE);
		}
	}

	/**
	 * Muuntaa tyyppiparametrin tyyppiargumenttien mukaisiksi tyypeiksi
	 * 
	 * @param toBeFixed
	 *            Korjattava tyyppi, joka sisältää tyyppiparametrit
	 * @param type
	 *            Tyyppi, joka sisältää tyyppiargumentit
	 * @param interfaceTree
	 *            Tyyppiparametrisoitu rajapinta
	 * @param bs
	 *            DEBUG
	 * @return Korjattu tyyppi
	 * @throws CompilerError
	 *             Jos tyyppikorjaus on virheellinen, eli ei koskaan
	 */
	private TypeTree fixTypeargs(TypeTree toBeFixed, TypeTree type, GenericStruct interfaceTree, boolean...bs) throws CompilerError {
		
		toBeFixed = (TypeTree) toBeFixed.clone();
		
		//System.err.print("([" +type + ":"+interfaceTree.typeargs()+"]" + toBeFixed + " to ");
		for (int i = 0; i < interfaceTree.typeargs().size(); i++) {
			String arg = interfaceTree.typeargs().get(i);
			if (toBeFixed.name.equals(arg) && toBeFixed.subtypes.size()==0) {
				if (type.subtypes.size() == 0) toBeFixed = TypeTree.getDefault(TOP_TYPE);
				else toBeFixed = (TypeTree) type.subtypes.get(i).clone();
				
				//System.err.print(toBeFixed + ")" + (bs.length>0?"":"\n"));
				return toBeFixed;
			}
		}
		
		for (int j = 0; j < toBeFixed.subtypes.size(); j++) {
			//System.err.print("{");
			toBeFixed.subtypes.set(j, fixTypeargs(toBeFixed.subtypes.get(j), type, interfaceTree, false));
			//System.err.print("}");
		}
		
			//System.err.print(toBeFixed + ")" + (bs.length>0?"":"\n"));
		return toBeFixed;
	}

	@SuppressWarnings("unchecked")
	private TypeTree changeTemplateTypes(TypeTree toBeFixed, Map<String, TypeTree> changes) {
		if (changes == null) return toBeFixed;
		
		TypeTree newType = (TypeTree) toBeFixed.clone();
		
		boolean leftSubtypes = true;
		boolean leftBonusType = true;
		
		if (changes.containsKey(toBeFixed.name)) {
			leftSubtypes = (changes.get(toBeFixed.name).subtypes.size() == 0);
			leftBonusType = (changes.get(toBeFixed.name).bonusType == null);
			newType.name = changes.get(toBeFixed.name).name;
		}
		if (leftSubtypes) {
			newType.subtypes.clear();
			for (TypeTree tt: toBeFixed.subtypes) {
				newType.subtypes.add(changeTemplateTypes(tt, changes));
			}
		} else {
			newType.subtypes = (ArrayList<TypeTree>) changes.get(toBeFixed.name).subtypes.clone();
		}
		if (leftBonusType) {
			if (toBeFixed.bonusType != null) newType.bonusType = changeTemplateTypes(toBeFixed.bonusType, changes);
		} else {
			newType.bonusType = changes.get(toBeFixed.name).bonusType;
		}
		return newType;
	}
	
	private String findCast(TypeTree t1, TypeTree t2, String level) throws CompilerError {
		String fname = null;
		if (interfaces.containsKey(t1.name)) {
			int steps = Integer.MAX_VALUE;
			for (FunctionTree ft : interfaces.get(t1.name).functions) {
				int tmpSteps = -1;
				TypeTree fixed = fixTypeargs(ft.returnType, t1, interfaces.get(t1.name));
				if ((ft.name.startsWith("method@" + t1.name + "." + level + "@") || ft.name.startsWith(level + "@")) 	// TODO selvitä miksi struct-metodien nimet
																														// eivät ole manglattuja
						&& fixed.name.equals(t2.name)
						&& (tmpSteps=castSteps(fixed, t2)) != -1) {
					if (steps > tmpSteps) {
						fname = (!ft.name.startsWith("method@") ? "method@" + t1.name + ".":"") + ft.name;
						steps = tmpSteps;
					}
					if (steps == 1) break;
				}
			}
		}
		
		return fname;
	}
	
	/**
	 * Muunna tyyppiä t1 oleva arvo tyyppiin t2 ja sijoita lopputulos muuttujaan
	 * 
	 * @param t1 Tyyppi, josta muunnetaan
	 * @param t2 Tyyppi, johon muunnetaan
	 * @param name Muuttuja, johon lopputulos sijoitetaan
	 * @return <tt>true</tt>, jos tyyppimuunnos onnistui, muulloin <tt>false</tt>
	 * @throws CompilerError Jos tyyppimuunnos on virheellinen
	 */
	private boolean doCasts(TypeTree t1, TypeTree t2, String name) throws CompilerError {
		if (castable(t1, t2)) return true;
		if (interfaces.containsKey(t1.name)) {
			String fname = findCast(t1, t2, "autocast");
			TypeTree ftype = TypeTree.getDefault(FUNC_TYPE, t2, t1);
			if (functions.containsKey(fname)) {
				if (name != null) {
					if (functions.get(fname).alias != null) {
						TypeValue tv = getIdent(functions.get(fname).alias, new ArrayList<TypeTree>(), TypeTree.getDefault(FUNC_TYPE, t2, t1));
						fname = "" + tv.value;
						ftype = tv.type;
					} else {
						TypeValue tv = getIdent(fname, t1.subtypes, TypeTree.getDefault(FUNC_TYPE, t2, t1));
						fname = "" + tv.value;
						ftype = tv.type;
					}
					if (allowPrinting) markExterns.add(new TypeName("" + fname, typeFromTree(ftype)));
					
					out.call(typeFromTree(t2), name, fname, name);
				}
				return true;
			}
		}
		if (!plugins.isEmpty()) {
			for (CompilerPlugin plugin : plugins) {
				if (plugin.canDoTypeCast(t1, t2)) return plugin.doTypeCast(t1, t2, name);
			}
		}
		return false;
	}
	
	public String getPrimaryAlias(TypeTree type) {
		switch (type.name) {
		case INT_TYPE:
			return "int";
		case STR_TYPE:
			return "str";
		case BOOL_TYPE:
			return "bool";
		case FUNC_TYPE:
			return "func";
		case LIST_TYPE:
			return "list";
		case TOP_TYPE:
			return "any";
		case UNIT_TYPE:
			return "void";
		default:
			return "obj";
		}
	}
	
	private static int getWorth(TypeTree a) {
		switch (a.name) {
		case UNIT_TYPE:
		case METHOD_TYPE:
			return -1;
		case TOP_TYPE:
			return 0;
		default:
			return 1;
		}
	}
	
	enum TypeClass {
		INTEGER(0),
		FLOAT(1);
		
		int s;
		
		private TypeClass(int s) {
			this.s = s;
		}
	}
	
	private static TypeClass getTypeClass(TypeTree a) {
		switch (a.name) {
		case FLOAT_TYPE:
			return TypeClass.FLOAT;
		default:
			return TypeClass.INTEGER;
		}
	}
	
	public boolean manuallyCastable(TypeTree a, TypeTree b) {
		if (a == null) a = TypeTree.getDefault(TOP_TYPE);
		if (b == null) b = TypeTree.getDefault(TOP_TYPE);
		
		if (getWorth(a) == -1 || getWorth(b) == -1) return false;
		if (getTypeClass(a).s > getTypeClass(b).s) return false;
		if (getWorth(a) > getWorth(b) && getWorth(b) >= 0) return true;
		if (getWorth(a) == 0 || a.name.equals(NULL_TYPE)) return true;
		if (b.name.equals(NULL_TYPE)) return false;
		
		if (interfaces.containsKey(b.name)) {
			if (manuallyCastable(a, interfaces.get(b.name).data)) return true;
		}
		
		if (interfaces.containsKey(a.name)) {
			if (manuallyCastable(interfaces.get(a.name).data, b)) return true;
		}
		
		if (structures.containsKey(a.name) && !structures.get(a.name).superType.equals(TOP_TYPE)) {
			if (manuallyCastable((structures.get(a.name).superType), b)) return true;
		}
		
		if (structures.containsKey(b.name) && !structures.get(b.name).superType.equals(TOP_TYPE)) {
			if (manuallyCastable(a, (structures.get(b.name).superType))) return true;
		}
		
		if (a.toString().equals(b.toString())) return true;
		else if (a.name.equals(b.name)) {
			if (a.subtypes.size() != b.subtypes.size()) return a.subtypes.size() == 0 || b.subtypes.size() == 0;
			for (int i = 0; i < a.subtypes.size(); i++)
				if (!manuallyCastable(a.subtypes.get(i), b.subtypes.get(i))) return false;
			
			if (!manuallyCastable(a.bonusType, b.bonusType)) return false;
			return true;
		}
		return false;
		
	}
	
	public boolean castable(TypeTree a, TypeTree b) {
		if (a == null) a = TypeTree.getDefault(TOP_TYPE);
		if (b == null) b = TypeTree.getDefault(TOP_TYPE);
		
		if (getTypeClass(a).s > getTypeClass(b).s) return false;
		
		if (interfaces.containsKey(a.name) && interfaces.get(a.name).castable) {
			if (castable(interfaces.get(a.name).data, b)) return true;
		}
		if (interfaces.containsKey(b.name) && interfaces.get(b.name).castable) {
			if (castable(a, interfaces.get(b.name).data)) return true;
		}
		
		if (structures.containsKey(a.name) && !structures.get(a.name).superType.equals(TOP_TYPE)) {
			if (castable((structures.get(a.name).superType), b)) return true;
		}
		
		if (a.name.equals(NULL_TYPE)) return true;
		if (b.name.equals(NULL_TYPE)) return false;
		
		if (a.toString().equals(b.toString())) return true;
		else if (a.name.equals(b.name)) {
			if (a.subtypes.size() != b.subtypes.size()) return b.subtypes.size() == 0;
			for (int i = 0; i < a.subtypes.size(); i++)
				if (!castable(a.subtypes.get(i), b.subtypes.get(i))) {
					return false;
				}
			
			if ((a.bonusType != null || b.bonusType != null) && !castable(a.bonusType, b.bonusType)) return false;
			
			return true;
		}
		
		if (getWorth(a) == -1 || getWorth(b) == -1) return false;
		if (getWorth(a) > getWorth(b)) return true;
		
		return false;
	}
	
	/** Arvio, kuinka tarkka tyyppimuunnos on. Suurempi luku, epätarkempi muunnos. Esim. Integer -> Pointer -> String == 3 **/
	public int castSteps(TypeTree a, TypeTree b) {
		if (!castable(a, b)) return -1;
		
		if (interfaces.containsKey(a.name) && interfaces.get(a.name).castable) {
			int steps = castSteps(interfaces.get(a.name).data, b);
			if (steps > -1) return steps + 1;
		}
		if (interfaces.containsKey(b.name) && interfaces.get(b.name).castable) {
			int steps = castSteps(a, interfaces.get(b.name).data)+1;
			if (steps > -1) return steps + 1;
		}
		
		if (structures.containsKey(a.name) && !structures.get(a.name).superType.equals(TOP_TYPE)) {
			int steps = castSteps((structures.get(a.name).superType), b)+1;
			if (steps > -1) return steps + 1;
		}
		
		return 1;
	}

}
