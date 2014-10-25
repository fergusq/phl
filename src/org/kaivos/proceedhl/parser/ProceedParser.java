package org.kaivos.proceedhl.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.kaivos.lib.ArgumentParser;
import org.kaivos.proceed.compiler.Registers;
import org.kaivos.proceedhl.parser.ProceedTree;
import org.kaivos.proceedhl.plugins.CompilerPlugin;
import org.kaivos.proceedhl.compiler.CompilerError;
import org.kaivos.proceedhl.compiler.ProceedCompiler;
import org.kaivos.sc.TokenScanner;
import org.kaivos.stg.error.SyntaxError;
import org.kaivos.stg.error.UnexpectedTokenSyntaxError;

public class ProceedParser 
{
	
	public static int errors = 0;
	
	private static boolean createDocs;
	private static boolean compilePIL;
	
	private static Map<String, Integer> argtypes = new HashMap<>();
	static {
		argtypes.put("h", 0);
		argtypes.put("help", 0);
		argtypes.put("v", 0);
		argtypes.put("version", 0);
		
		argtypes.put("i", 0);
		argtypes.put("d", 0);
		argtypes.put("a", 0);
		argtypes.put("t", 0);
		argtypes.put("noattr", 0);
		argtypes.put("nostd", 0);
		argtypes.put("path", 1);
		argtypes.put("out", 1);
		argtypes.put("o", 1);
		
		argtypes.put("plugins", 1);
		
		argtypes.put("arch", 1);
		
		argtypes.put("W", 1);
	}
	
	private static Set<String> plugins = new HashSet<>();
	
	public static final String PHL_VERSION = "1.2.7";
	
	public static final String COMPILER_NAME = "Proceed High Language Compiler";
	
	public static final String COMPILER_MINOR = "a";
	public static final String COMPILER_VERSION = "#phlc" + createVersion(PHL_VERSION) + COMPILER_MINOR;
	
	private static String createVersion(String phlversion) {
		String[] numbers = phlversion.split("\\.");
		String a = "";
		for (int i = 0; i < numbers.length; i++) {
			a += (numbers[i].length()<3?mul(3-numbers[i].length(), "0")+numbers[i]:numbers[i]);
		}
		
		return a;
	}
	
	private static String mul(int i, String string) {
		String a = "";
		for (int j = 0; j < i; j++) a += string;
		return a;
	}

	public static void main(String[] args) {
		ArgumentParser a = new ArgumentParser(argtypes, args);
		
		if (a.getFlag("v") != null || a.getFlag("version") != null)
		{
			System.out.println(COMPILER_NAME + " " + PHL_VERSION + COMPILER_MINOR);
			System.out.println(COMPILER_VERSION);
			return;
		}
		
		if (a.getFlag("h") != null || a.getFlag("help") != null)
		{
			System.out.println("Usage: java -cp phl.jar org.kaivos.proceedhl.parser.ProceedParser [OPTIONS] file\n| java -cp phl.jar org.kaivos.proceedhl.parser.ProceedParse [OPTIONS] -i");
			System.out.println("Options:");
			System.out.println("-i          read the source code from stdin instead of a file");
			System.out.println("-d          print generated function lists files in 'docgen' dir");
			System.out.println("--noattr    ignore function attributes in source code");
			System.out.println("-W level    set warning level, can be all, strict, normal or critical");
			System.out.println("--path p    search modules folder 'p'. multiple modules are separated with ':'");
			System.out.println("--nostd     does not load the standard library");
			System.out.println("-t          output only the main input file and compile and assemble it");
			System.out.println("-o p        use 'p' as output folder");
			System.out.println("--out p     use 'p' as output folder");
			System.out.println("--arch t    set the arch. possibilities are: x86, amd64.");
			System.out.println("-a          compile and assemble pil-files too");
			System.out.println("--plugins p load plugin 'p'. multiple plugins are separated with ':'");
			System.out.println("-v          print the version number");
			System.out.println("--version   print the version number");
			System.out.println("-h          print this text");
			System.out.println("--help      print this text");
			return;
		}
		
		BufferedReader in = null;
		
		if (a.getFlag("i") != null) {
			in = new BufferedReader(new InputStreamReader(System.in));
		} else {
			try {
				in = new BufferedReader(new FileReader(new File(a.lastText())));
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
				return;
			}
		}
		
		if (a.getFlag("d") != null) {
			createDocs = true;
		}
		if (a.getFlag("noattr") != null) {
			ProceedCompiler.useAttributes = false;
		}
		if (a.getFlag("nostd") != null) {
			ProceedCompiler.useStdLib = false;
		}
		
		if (a.getFlag("W") != null) {
			String level = a.getFlag("W").getFlagArgument();
			ProceedCompiler.w_level = (
					level.equals("all") ? ProceedCompiler.W_STRICT :
					level.equals("strict") ? ProceedCompiler.W_STRICT :
					level.equals("normal") ? ProceedCompiler.W_NORMAL :
					level.equals("critical") ? ProceedCompiler.W_CRITICAL :
					ProceedCompiler.W_NORMAL
					);
		}
		
		if (a.getFlag("a") != null) {
			compilePIL = true;
		}
		
		if (a.getFlag("t") != null) {
			compilePIL = true;
			ProceedCompiler.nullOut = true;
		}
		
		if (a.getFlag("path") != null) {
			for (String s : a.getFlag("path").getFlagArgument().split(":")) {
				if (s.length() == 0) continue;
				if (!s.startsWith("/") && !s.startsWith(".")) s = "./" + s;
				if (!s.endsWith("/")) s += "/";
				ProceedCompiler.importPath.add(s);
			}
		}
		out: if (a.getFlag("out") != null) {
			String s = a.getFlag("out").getFlagArgument();
			if (s.length() == 0) break out;
			if (!s.startsWith("/") && !s.startsWith(".")) s = "./" + s;
			if (!s.endsWith("/")) s += "/";
			ProceedCompiler.outputPath = s;
		}
		out: if (a.getFlag("o") != null) {
			String s = a.getFlag("o").getFlagArgument();
			if (s.length() == 0) break out;
			if (!s.startsWith("/") && !s.startsWith(".")) s = "./" + s;
			if (!s.endsWith("/")) s += "/";
			ProceedCompiler.outputPath = s;
		}
		
		if (a.getFlag("arch") != null) {
			switch (a.getFlag("arch").getFlagArgument().toLowerCase()) {
			case "x86":
				Registers.toX86();
				break;
			case "amd64":
				Registers.toAmd64();
				break;

			default:
				break;
			}
		}
		
		if (a.getFlag("plugins") != null) {
			for (String s : a.getFlag("plugins").getFlagArgument().split(":")) {
				if (s.length() == 0) continue;
				plugins.add(s);
			}
		}
		
		parseStream(in, a.lastText());
		
		if (errors > 0) System.exit(errors);
	}
	
	public static void parseFile(String file) {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(new File(file)));
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			return;
		}
		
		parseStream(in, file);
	}

	private static void parseStream(BufferedReader in, String file) {
		String textIn = "";
		try {
			while (in.ready())
				textIn += in.readLine() + "\n";
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		}

		TokenScanner s = new TokenScanner();
		s.setSpecialTokens(new char[] { ';', '<', '>', '(', ')', ',', ':', '+',
				'-', '*', '/', '%', '=', '&', '|', '{', '}', '!', '[',
				']', '$', '@', '#', '.', '?', '~', '^' });
		s.setBSpecialTokens(new String[] { "~<", "->", "=>", "==", "!=", "&&", "||", "..",
				"<=", ">=", "++", "--", "/*", "*/", "::", "<<", ">>", "~=", "?=", "**", ":=", ":-", "|>", "<|"});
		s.setComments(new String[][] {{"doc", ";"}, {"/*", "*/"}});
		s.setComments(false);
		s.setPrep(true);
		s.setFile(file);
		s.init(textIn);

		
		// System.out.println(s.getTokenList());
		ProceedTree.StartTree tree = new org.kaivos.proceedhl.parser.ProceedTree.StartTree();

		try {
			tree.parse(s);
			try {
				in.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			ProceedCompiler compiler = new ProceedCompiler("", createDocs);
			
			/* Lataa pluginit */
			{
				for (String pluginName : plugins) {
					try {
						compiler.plugins.add((CompilerPlugin) Class.forName(pluginName).getConstructor(ProceedCompiler.class).newInstance(compiler));
					} catch (InstantiationException | IllegalAccessException
							| IllegalArgumentException | InvocationTargetException
							| NoSuchMethodException | SecurityException
							| ClassNotFoundException e) {
						e.printStackTrace();
					}
				}
			}
			
			try {
				// käännä tmp-tiedoston kautta oliotiedostoksi
				if (compilePIL) {
					File tmp = File.createTempFile("phlc", ".pil");
					tmp.deleteOnExit();
					compiler.compile(tree, file, new PrintWriter(tmp));
					
					org.kaivos.proceed.parser.ProceedParser.main(new String[] {"-a", "--out", ProceedCompiler.outputPath + tree.module.replace("::", "_") + ".o", "--arch", Registers.ARCH, tmp.getAbsolutePath()});
				}
				else compiler.compile(tree, file, null); // käännä pil-tiedostoksi
			} catch (CompilerError e) {
				System.err.println("; E: [" + compiler.currModuleFile + ":" + (compiler.currLine+1) + "] (in function "+compiler.func+") "+e.msg);
				//e.printStackTrace();
			} catch (Exception e) {
				System.err.println("; E: [" + compiler.currModuleFile + ":" + (compiler.currLine+1) + "] (in function "+compiler.func+") Internal Compiler Exception");
				e.printStackTrace();
			}
		} catch (UnexpectedTokenSyntaxError e) {

			if (e.getExceptedArray() == null) {
				System.err.println("; E: [" + e.getFile() + ":" + e.getLine()
						+ "] Syntax error on token '" + e.getToken()
						+ "', expected '" + e.getExcepted() + "'");
				 //e.printStackTrace();
			} else {
				System.err.println("; E: [" + e.getFile() + ":" + e.getLine()
						+ "] Syntax error on token '" + e.getToken()
						+ "', expected one of:");
				for (String token : e.getExceptedArray()) {
					System.err.println(";    [Line " + e.getLine() + "] \t\t'"
							+ token + "'");
				}
				//e.printStackTrace();
			}

			System.err.println(";    [Line " + e.getLine() + "] Line: '"
					+ s.getLine(e.getLine() - 1).trim() + "'");
			
			errors++;
		} catch (SyntaxError e) {

			System.err.println("; E: [" + e.getFile() + ":" + e.getLine() + "] " + e.getMessage());
			System.err.println(";    [" + e.getFile() + ":" + e.getLine() + "] Line: \t"
					+ s.getLine(e.getLine() - 1).trim());
			
			errors++;
		} catch (StackOverflowError e) {
			System.err.println(";E: Internal Compiler Exception: Stack overflow exception");
			e.printStackTrace();
			
			errors++;
		}

		{
			// SveCodeGenerator.CGStartTree gen = new
			// SveCodeGenerator.CGStartTree(tree);
			// System.out.println(gen.generate(""));
		}
		
	}
	
}
