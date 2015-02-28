package org.kaivos.proceedhl.documentgen;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.kaivos.proceedhl.compiler.ProceedCompiler;
import org.kaivos.proceedhl.parser.NumberParser;
import org.kaivos.proceedhl.parser.ProceedTree.FieldTree;
import org.kaivos.proceedhl.parser.ProceedTree.FunctionTree;
import org.kaivos.proceedhl.parser.ProceedTree.InterfaceTree;
import org.kaivos.proceedhl.parser.ProceedTree.StructTree;
import org.kaivos.proceedhl.parser.ProceedTree.TypeTree;

public class PHLHeaderCreator {
	
	private PrintWriter out;
	private PrintWriter out_html;

	public PHLHeaderCreator(PrintWriter docout, PrintWriter docout_html) {
		super();
		this.out = docout;
		this.out_html = docout_html;
	}
	
	@SuppressWarnings("unused")
	private static String demangle(String s) {
		return null;
	}

	public void startDocument(String name) {
		out_html.println("<html><head>" +
				"<title>Module "+name+"</title>" +
				"<link rel=\"stylesheet\" href=\"http://kaivos.org/tyyli.css\" type=\"text/css\" /></head><body>" +
				"<div class=\"pohja\">" +
				"<div class=\"linkit\"><span class=\"sivuotsikko\">Module "+name+"</span></div>" +
				"<div class=\"sis\">");
	}
	
	public void startStatics() {
		out_html.println("<h1>Static variables</h1><table class=\"wtable\" width=\"100%\"><tr><th width=\"33%\">Type</th><th>Name</th></tr>");
	}
	
	public void docStatic(String name, TypeTree type) {
		htmlStatic(name, type);
	}
	
	private void htmlStatic(String name, TypeTree type) {
		out_html.println("<tr><td><tt>" + type + "</tt></td><td><tt>" + name);
		out_html.println("</tt></td></tr>");
	}
	
	public void endStatics() {
		out_html.println("</table>");
	}
	
	public void startInterfaces() {
		out_html.println("<h1>Interfaces</h1>");
	}
	
	public void docInterface(InterfaceTree t) {
		out.println(headerInterface(t));
		
		out_html.println("<h2><a id=\"interface_"+t.name+"\"><tt>@" + t.name + "</tt></a></h2>");
		out_html.println("<h3>Methods</h3><table class=\"wtable\" width=\"100%\"><tr><th width=\"33%\">Return type</th><th>Name</th></tr>");
		
		for (FunctionTree t1 : t.functions) {
			htmlFunc(t1);
		}
		
		out_html.println("</table>");
		
		htmlDetailedFunction();
		
		
	}
	
	private String headerInterface(InterfaceTree t) {
		String s = "";
		
		s += ("interface @" + t.name);
		if (t.typeargs.size() != 0) {
			s += ("<");
			for (int i = 0; i < t.typeargs.size(); i++) {
				if (i != 0) out.print(", ");
				s += ("@" + t.typeargs.get(i));
			}
			s += (">");
		}
		s += (" data");
		if (t.castable) s += (" castable");
		s += (" " + t.data);
		s += (" {\n");
		
		Collections.sort(t.functions, new Comparator<FunctionTree>() {
			@Override
			public int compare(FunctionTree o1, FunctionTree o2) {
				return o1.name.compareTo(o2.name);
			}
		});
		
		for (FunctionTree t1 : t.functions) {
			s += ("\t");
			s += headerFunc(t1);
		}
		s += ("};\n");
		
		return s;
	}
	
	public void endInterfaces() {
		//
	}
	
	public void startStructs() {
		out_html.println("<h1>Structs And Classes</h1>");
		
		out_html.println("<h2>Tree</h2>");
		htmlInheritanceTree(ProceedCompiler.structures.get(ProceedCompiler.OBJ_TYPE), null);
	}

	public void docStruct(StructTree t) {
		out.println(headerStruct(t));
		
		out_html.println("<h2><a id=\"interface_"+t.name+"\"><tt>@" + t.name + "</tt></a></h2>");
		
		/* Tarkka puu kaikista luokista ja niiden vanhemmista ja lapsista
		StructTree t2 = t;
		while (ProceedCompiler.structures.get(t2.superType.name) != null) t2 = ProceedCompiler.structures.get(t2.superType.name);
		
		htmlInheritanceTree(t2, t.name);
		*/
		
		// Yksinkertainen puu vanhemmista
		htmlSimpleInheritanceTree(t, true);
		
		out_html.println("<h3>Fields</h3><table class=\"wtable\" width=\"100%\">" +
				"<tr><th width=\"33%\">Type</th><th>Name</th></tr>");
		
		for (FieldTree t1 : t.fields) {
			out_html.println("<tr><td><tt>" + t1.type + "</tt></td><td><tt>" + t1.name + "</tt></td></tr>");
		}
		
		out_html.println("</table>");
		
		out_html.println("<h3>Getters and Setters</h3><table class=\"wtable\" width=\"100%\">" +
				"<tr><th width=\"33%\">Return type</th><th>Name</th></tr>");
		
		for (FieldTree t1 : t.fields) {
			out_html.println("<tr></tr>");
			out_html.println("<tr><td><tt>" + t1.type + "</tt></td><td><tt>" + t1.getter + "</tt></td></tr>");
			out_html.println("<tr><td><tt>@" + ProceedCompiler.UNIT_TYPE + "</tt></td><td><tt>" + (t1.setter.startsWith("setfield@")?t1.setter.substring(9)+"=":t1.setter) + "(" + t1.type + " value)</tt></td></tr>");
		}
		
		out_html.println("</table>");
		
		out_html.println("<h3>Methods</h3><table class=\"wtable\" width=\"100%\"><tr><th width=\"33%\">Return type</th><th>Name</th></tr>");
		
		Collections.sort(t.functions, new Comparator<FunctionTree>() {
			@Override
			public int compare(FunctionTree o1, FunctionTree o2) {
				return o1.name.compareTo(o2.name);
			}
		});
		
		for (FunctionTree t1 : t.functions) {
			htmlFunc(t1);
		}
		
		out_html.println("</table>");
		
		htmlDetailedFunction();
	}

	private void htmlInheritanceTree(StructTree t, String bold) {
		
		if (t.name.equals(bold)) {
			out_html.println("<b><tt>@" + t.name + "</tt></b>");
		} else {
			out_html.println("<a href=\""+t.module.replace("::", "_")+".html#interface_"+t.name+"\"><tt>@" + t.name + "</tt></a>");
		}
		out_html.println(" <ul>");
		
		List<String> children = new ArrayList<>();
		for (StructTree tree : ProceedCompiler.structures.values()) {
			if (tree.superType.name.equals(t.name)) {
				children.add(tree.name);
			}
		}
		Collections.sort(children);

		for (String str : children) {
			StructTree tree = ProceedCompiler.structures.get(str);
			out_html.println("<li>");
			htmlInheritanceTree(tree, bold);
			out_html.println("</li>");
		}

		out_html.println("</ul>");
	}
	
	private void htmlSimpleInheritanceTree(StructTree t, boolean bold) {
		
		if (ProceedCompiler.structures.get(t.superType.name) != null) {
			htmlSimpleInheritanceTree(ProceedCompiler.structures.get(t.superType.name), false);
			out_html.println("<ul><li>");
		}
		
		if (bold) {
			out_html.println("<b><tt>@" + t.name + "</tt></b>");
		} else {
			out_html.println("<a href=\""+t.module.replace("::", "_")+".html#interface_"+t.name+"\"><tt>@" + t.name + "</tt></a>");
		}
		
		while ((t = ProceedCompiler.structures.get(t.superType.name)) != null && bold) {
			out_html.println("</li></ul>");
		}
	}

	private String headerStruct(StructTree t) {
		String s = "";
		
		s += ((t.isClass ? "class":"struct")+" @" + t.name);
		if (t.typeargs.size() != 0) {
			s += ("<");
			for (int i = 0; i < t.typeargs.size(); i++) {
				if (i != 0) out.print(", ");
				s += ("@" + t.typeargs.get(i));
			}
			s += (">");
		}
		s += (" : " + t.superType);
		s += (" {\n");
		for (FieldTree t1 : t.fields) {
			//s += ("\t" + t1.type + " " + t1.getter + " []");
			//s += ("\t@Void " + t1.setter + "(" + t1.type + ") []");
			s += ("\tfield " + t1.type + " " + t1.name + " {\n");
			s += ("\t\tget:" + t1.getter + ",\n");
			s += ("\t\tset:" + (t1.setter.startsWith("setfield@")?t1.setter.substring(9)+"=":t1.setter) + "\n");
			s += ("\t};\n");
		}
		for (FunctionTree t1 : t.functions) {
			s += ("\t");
			if (t1.name.equals("new")) {
				s += ("new extern;\n");
			}
			else s += headerFunc(t1);
		}
		s += ("};\n");
		
		return s;
	}

	public void endStructs() {
		//
	}
	
	public void startFuncs() {
		out_html.println("<h1>Functions</h1><table class=\"wtable\" width=\"100%\"><tr><th width=\"33%\">Return type</th><th>Name</th></tr>");
	}
	
	public void docFunc(FunctionTree t) {
		out.println(headerFunc(t));
		
		htmlFunc(t);
	}
	
	private String headerFunc(FunctionTree t) {
		String s = "";
		
		/*for (String flag : t.flags) {
			if (t.flags1.containsKey(flag)) {
				s += "#" + flag + "(" + t.flags1.get(flag).args.stream().collect(Collectors.joining(",")) + ")<br/>";
			}
			else s += "#" + flag + "<br/>";
		}*/
		
		if (t.typeargs.size() != 0) {
			s += ("<");
			for (int i = 0; i < t.typeargs.size(); i++) {
				if (i != 0) out.print(", ");
				s += ("@" + t.typeargs.get(i));
			}
			s += ("> ");
		}
		
		if (t.name.startsWith("operator@")) {		// operaattorimetodi
			s += ("" + t.returnType + " ");
			s += ("operator ");
			String op = t.name.substring(9);
			String[] chs = op.split("_");
			for (String s1 : chs) {
				s += ((char)NumberParser.parseHex(s1));
			}
		} else if (t.name.startsWith("autocast@")) {	// autocast
			s += ("auto " + t.name.substring(9) + "");
		} else if (t.name.startsWith("manualcast@")) {	// manualcast
			s += ("manual " + t.name.substring(11) + "");
		} else if (t.name.startsWith("modulef@")) {	// module method
			s += ("module" + t.name.substring(t.name.indexOf(".")) + "");
		} else if (t.name.startsWith("setfield@")) {	// setfield
			s += (t.name.substring(9) + "=");
		} else {	// normaali funktio/metodi
			s += ("" + t.returnType + " ");
			s += (t.name + "");
		}
		
		if (t.params.size() != 0) {
			s += ("(");
			for (int i = 0; i < t.params.size(); i++) {
				if (i != 0) s += (", ");
				s += ("" + t.paramtypes.get(i) + "");
				s += (" " + t.params.get(i) + "");
			}
			s += (")");
		}
		s += (" extern;\n");
		
		return s;
	}

	private static List<FunctionTree> detailedFunctions = new ArrayList<>();
	
	private void htmlFunc(FunctionTree t1) {
		detailedFunctions.add(t1);
		
		out_html.print("<tr><td><tt>" + t1.returnType + "</tt></td><td><tt>");
		
		out_html.print("<a href=\"#function_" + (t1.owner == null?"":t1.owner+".") + t1.name + "\">");
		
		if (t1.flags.contains("deprecated")) out_html.print("<s>");
		
		if (t1.name.startsWith("operator@")) {		// operaattorimetodi
			out_html.print("operator ");
			String op = t1.name.substring(9);
			String[] chs = op.split("_");
			for (String s1 : chs) {
				out_html.print((char)NumberParser.parseHex(s1));
			}
		} else if (t1.name.startsWith("autocast@")) {	// autocast
			out_html.print("auto " + t1.name.substring(9));
		} else if (t1.name.startsWith("manualcast@")) {	// manualcast
			out_html.print("manual " + t1.name.substring(11));
		} else if (t1.name.startsWith("modulef@")) {	// module method
			out_html.print("module" + t1.name.substring(t1.name.indexOf(".")) + "");
		} else if (t1.name.startsWith("setfield@")) {	// setfield
			out_html.print(t1.name.substring(9) + "=");
		} else {	// normaali funktio/metodi
			out_html.print(t1.name);
		}
		
		if (t1.flags.contains("deprecated")) out_html.print("</s>");
		
		out_html.print("</a>");
		
		out_html.print("(");
		
		for (int i = 0; i < t1.params.size(); i++) {
			if (i != 0) out_html.print(", ");
			out_html.print("" + t1.paramtypes.get(i));
			out_html.print(" " + t1.params.get(i));
		}
		out_html.println(")</tt></td></tr>");
	}
	
	public void endFuncs() {
		out_html.print("</table>");
		
		htmlDetailedFunction();
		
	}
	
	private void htmlDetailedFunction() {
		Collections.sort(detailedFunctions, new Comparator<FunctionTree>() {
			@Override
			public int compare(FunctionTree o1, FunctionTree o2) {
				return o1.name.compareTo(o2.name);
			}
		});
		
		for (FunctionTree t : detailedFunctions) {
			
			
			String name = "";
			
			if (t.name.startsWith("operator@")) {		// operaattorimetodi
				name += ("operator ");
				String op = t.name.substring(9);
				String[] chs = op.split("_");
				for (String s1 : chs) {
					name += ((char)NumberParser.parseHex(s1));
				}
			} else if (t.name.startsWith("autocast@")) {	// autocast
				name += ("auto " + t.name.substring(9) + "");
			} else if (t.name.startsWith("manualcast@")) {	// manualcast
				name += ("manual " + t.name.substring(11) + "");
			} else if (t.name.startsWith("modulef@")) {	// module method
				name += ("module" + t.name.substring(t.name.indexOf(".")) + "");
			} else if (t.name.startsWith("setfield@")) {	// setfield
				name += (t.name.substring(9) + "=");
			} else {	// normaali funktio/metodi
				name += (t.name + "");
			}
			
			if (t.flags.contains("deprecated")) name = "<s>" + name + "</s>";
			
			out_html.println("<h2><a id=\"function_"+(t.owner == null?"":t.owner+".")+t.name+"\">" + name + "</a></h2>");
			out_html.println("<div class=\"upotus\">");
			out_html.println("<p><tt>" + headerFunc(t) + "</tt></p>");
			
			if (t.name.equals("new")) out_html.println("<p><b>This method is a constructor.</b></p>");
			
			if (t.flags.contains("abstract")) out_html.println("<p><b>This function is abstract.</b></p>");
			
			if (t.flags.contains("deprecated")) {
				if (t.flags1.get("deprecated").args.size() > 0) {
					String desc = t.flags1.get("deprecated").args.get(0);
					if (desc.startsWith("\"")) desc = desc.substring(1, desc.length()-1);
					out_html.println("<p><b>Deprecated.</b> <i>" + desc + "</i></p>");
				}
				else out_html.println("<p><b>This function is deprecated.</b></p>");
			}
			if (t.flags.contains("private")) out_html.println("<p><b>This function is private.</b></p>");
			
			if (t.flags.contains("modifies_this")) out_html.println("<p><b>This method modifies the <tt>this</tt> object.</b></p>");
			if (t.flags.contains("unittest")) out_html.println("<p><b>This function is a unit test method.</b></p>");
			
			if (t.flags.contains("info") && t.flags1.get("info").args.size() > 0) {
				String tmp = t.flags1.get("info").args.get(0);
				tmp = tmp.substring(1, tmp.length()-1);
				tmp = tmp.replace("\n", " ").replace("\t", " ");
				tmp = tmp.trim().replaceAll("  ", " ");
				String[] info = tmp.split("@");
				
				String description = info[0];
				out_html.println("" + description + "");
				
				boolean params = false;
				
				for (int i = 1; i < info.length; i++) {
					String[] cmd = info[i].split(" ");
					if (info[i].startsWith("param") && cmd.length > 2) {
						params = true;
						break;
					}
						
				}
				
				if (params) out_html.println("<dl><dt><b>Parameters:</b></dt><dd><table>");
				
				for (int i = 1; i < info.length; i++) {
					String[] cmd = info[i].split(" ");
					if (info[i].startsWith("param") && cmd.length > 2) {
						out_html.println("<tr><td><i>"+cmd[1]+"</i> - ");
						out_html.println(info[i].substring(info[i].indexOf(" ", info[i].indexOf(" ")+1)+1));
						out_html.println("</td></tr>");
					}
				}
				
				if (params) out_html.println("</table></dd></dl>");
				
				for (int i = 1; i < info.length; i++) {
					if (info[i].startsWith("returns")) {
						out_html.println("<dl><dt><b>Returns:</b></dt><dd>");
						if (info[i].contains(" ")) out_html.println(info[i].substring(info[i].indexOf(" ")+1));
						out_html.println("</dd></dl>");
						break;
					}
				}
				
				for (int i = 1; i < info.length; i++) {
					if (info[i].startsWith("throws")) {
						out_html.println("<dl><dt><b>Throws:</b></dt><dd>");
						if (info[i].contains(" ")) out_html.println(info[i].substring(info[i].indexOf(" ")+1));
						out_html.println("</dd></dl>");
						break;
					}
				}
				
				boolean sees = false;
				
				for (int i = 1; i < info.length; i++) {
					String[] cmd = info[i].split(" ");
					if (info[i].startsWith("see") && cmd.length == 2) {
						sees = true;
						break;
					}
						
				}
				
				if (sees) out_html.println("<dl><dt><b>See also:</b></dt><dd><table>");
				
				for (int i = 1; i < info.length; i++) {
					String[] cmd = info[i].split(" ");
					if (info[i].startsWith("see") && cmd.length == 2) {
						out_html.println("<tr><td><a href=\"#function_"+cmd[1]+"\">" + cmd[1] + "</a></td></tr>");
					}
				}
				
				if (sees) out_html.println("</table></dd></dl>");
				
				for (int i = 1; i < info.length; i++) {
					if (info[i].startsWith("since")) {
						out_html.println("<dl><dt><b>Since:</b></dt><dd>");
						if (info[i].contains(" ")) out_html.println(info[i].substring(info[i].indexOf(" ")+1));
						out_html.println("</dd></dl>");
					}
				}
				
				
			} else {
				if (t.params.size() > 0) {
					out_html.println("<dl><dt><b>Parameters:</b></dt><dd><table>");
					
					for (String str : t.params) out_html.println("<tr><td><i>" + str + "</i></td></tr>");
					
					out_html.println("</table></dd></dl>");
				}
				out_html.println("<dl><dt><b>Returns:</b></dt><dd>");
				out_html.println("A <tt>" + t.returnType + "</tt> object");
				out_html.println("</dd></dl>");
					
				
			}
			
			out_html.println("</div>");
		}
		detailedFunctions.clear();
	}

	public void endDocument() {
		out_html.print("</div></div></body></html>");
	}
	
	public void flush() {
		out.flush();
		out_html.flush();
	}
	
}
