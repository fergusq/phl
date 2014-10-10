package org.kaivos.proceedhl.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kaivos.parsertools.ParserTree;
import org.kaivos.proceedhl.compiler.ProceedCompiler;
import org.kaivos.sc.TokenScanner;
import org.kaivos.stg.error.SyntaxError;
import org.kaivos.stg.error.UnexpectedTokenSyntaxError;

public class ProceedTree extends ParserTree {
	
	public static abstract class TreeNode {

		public abstract void parse(TokenScanner s) throws SyntaxError;
		public abstract String generate(String a);
	}
	
	public static String nextI(TokenScanner s) throws SyntaxError {
		String s2 = s.next();
		
		if (!isValidIdentifier(s2)) {
			throw new UnexpectedTokenSyntaxError(s.file(), s.nextTokensLine()+1,s2, "<ident>", "'<ident>' expected, got '" + s2 + "'");
		}
		
		return s2;
	}
	
	public static boolean isValidIdentifier(String token) {
		if (!token.matches("[a-zA-Zöäåßü_\\+\\-\\*/%\\=\\!\\<\\>].*")) {
			return false;
		}
		
		switch (token) {
		case "interface":
		case "data":
		case "struct":
		case "class": // case "struct": // TODO Protokollat
		case "field":
		case "castable":
		case "extern":
		case "except":
		case "static":
		case "module":
		case "import":
		case "PIL":
		case "__special_varargs":
		case "var":
		case "if":
		case "else":
		case "while":
		case "for":
		case "do":
		case "break":
		case "new":
		case "alias":
		case "template":
		case "super":
		case "nonlocal":
			
		case "throws":
		case "throw":
		case "try":
		case "catch":
			
		case "operator":
		case "auto":
		case "manual":
			return false;
			
		default:
			return true;
		}
	}
	
	public static HashMap<String, StructTree> _structs = new HashMap<>();
	
	/**
	 * Start = {
	 * 		FUNCTION*
	 * }
	 */
	public static class StartTree extends TreeNode {
		
		public String module = "";
		
		public ArrayList<String> svarargfuncs = new ArrayList<>();
		
		public HashMap<String, TypeTree> excepts = new HashMap<>();
		
		public ArrayList<String> imports = new ArrayList<>();
		
		public ArrayList<String> externs = new ArrayList<>();
		public ArrayList<String> pil = new ArrayList<>();
		public ArrayList<FunctionTree> functions = new ArrayList<FunctionTree>();
		public ArrayList<InterfaceTree> interfaces = new ArrayList<InterfaceTree>();
		public ArrayList<StructTree> structs = new ArrayList<StructTree>();

		public HashMap<String,TypeTree> statics = new HashMap<>();
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			_structs.clear();
			{
				accept("module", s);
				String impor = (nextI(s));
				while (seek(s).equals("::")) {
					accept("::", s);
					impor += "::" + nextI(s);
				}
				module = impor;
				accept(";", s);
			}
			while (!seek(s).equals("<EOF>")) {
				if (seek(s).equals("doc")) {
					while (!seek(s).equals(";")) next(s);
					accept(";", s);
				} else if (seek(s).equals("extern")) {
					accept("extern", s);
					externs.add(next(s));
					accept(";", s);
				} else if (seek(s).equals("import")) {
					accept("import", s);
					String impor = (nextI(s));
					while (seek(s).equals("::")) {
						accept("::", s);
						impor += "::" + nextI(s);
					}
					imports.add(impor);
					accept(";", s);
				} else if (seek(s).equals("except") || seek(s).equals("expect")) {
					accept(new String[] {"except", "expect"}, s);
					TypeTree t = TypeTree.getDefault("Function", ProceedCompiler.TOP_TYPE);
					if (seek(s).equals("@")) {
						TypeTree type = new TypeTree();
						type.parse(s);
						t = type;
					} 
					excepts.put(nextI(s), t);
					accept(";", s);
				} else if (seek(s).equals("__special_varargs")) {
					accept("__special_varargs", s);
					svarargfuncs.add(nextI(s));
					accept(";", s);
				} else if (seek(s).equals("PIL")) {
					accept("PIL", s);
					pil.add(next(s));
					accept(";", s);
				} else if (seek(s).equals("interface")) {
					InterfaceTree t = new InterfaceTree();
					t.module = module;
					t.parse(s);
					interfaces.add(t);
					accept(";", s);
				} /*else if (seek(s).equals("protocol")) { // TODO Protokollat
					StructTree t = new StructTree();
					t.parse(s);
					structs.add(t);
					_structs.put(t.name, t);
					accept(";", s);
				} */else if (seek(s).equals("struct") || seek(s).equals("class")) {
					StructTree t = new StructTree();
					t.module = module;
					t.parse(s);
					structs.add(t);
					_structs.put(t.name, t);
					accept(";", s);
				} else if (seek(s).equals("static")) {
					accept("static", s);
					TypeTree t = TypeTree.getDefault(ProceedCompiler.TOP_TYPE);
					if (seek(s).equals("@")) {
						TypeTree type = new TypeTree();
						type.parse(s);
						t = type;
					}
					statics.put(nextI(s), t);
					accept(";", s);
				} else
					
				{
					FunctionTree t = new FunctionTree();
					t.module = module;
					t.parse(s);
					functions.add(t);
				}
				continue;
			}
			accept("<EOF>", s);
			
		}

		@Override
		public String generate(String a) {
			return null;
		}
		
	}
	
	/**
	 * Type = {
	 * 		"@" NAME ("<" TYPE* (";" TYPE)? ">")?
	 * }
	 */
	public static class TypeTree extends TreeNode implements Cloneable {
		
		public String name;
		
		public ArrayList<TypeTree> subtypes = new ArrayList<>();
		
		public TypeTree bonusType;
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			parse(s, false);
		}
		
		@SuppressWarnings("unchecked")
		public void parse(TokenScanner s, boolean methods) throws SyntaxError {
			accept("@", s);
			name = nextI(s);
			
			if (seek(s).equals("<")) {
				accept("<", s);
				TypeTree subtype;
				subtype = new TypeTree();
				subtype.parse(s);
				subtypes.add(subtype);
				if (seek(s).equals(",")) do {
					accept(",", s);
					TypeTree st = new TypeTree();
					st.parse(s);
					subtypes.add(st);
				} while (seek(s).equals(","));
				
				if (seek(s).equals(";")) {
					accept(";", s);
					bonusType = new TypeTree();
					bonusType.parse(s);
				}
				
				accept(">", s);
			}
			
			while (true)
				if (seek(s).equals("::") && methods) {
					accept("::", s);
					accept("array", s);
					TypeTree newt = new TypeTree();
					newt.name = this.name;
					newt.subtypes = (ArrayList<TypeTree>) this.subtypes.clone();
					newt.bonusType = this.bonusType;
					
					this.name = ProceedCompiler.ARRAY_TYPE;
					this.subtypes.clear();
					this.subtypes.add(newt);
					this.bonusType = null;
				}
				else if (seek(s).equals("[") && seek(s, 2).equals("]")) {
					accept("[", s);
					accept("]", s);
					TypeTree newt = new TypeTree();
					newt.name = this.name;
					newt.subtypes = (ArrayList<TypeTree>) this.subtypes.clone();
					newt.bonusType = this.bonusType;
					
					this.name = ProceedCompiler.ARRAY_TYPE;
					this.subtypes.clear();
					this.subtypes.add(newt);
					this.bonusType = null;
				}
				else if (seek(s).equals("*")) {
					accept("*", s);
					TypeTree newt = new TypeTree();
					newt.name = this.name;
					newt.subtypes = (ArrayList<TypeTree>) this.subtypes.clone();
					newt.bonusType = this.bonusType;
					
					this.name = ProceedCompiler.PTR_TYPE;
					this.subtypes.clear();
					this.subtypes.add(newt);
					this.bonusType = null;
				}
				else break;
		}

		@Override
		public String generate(String a) {
			return null;
		}
		
		public String toString() {
			String name = "@" + this.name;
			if (subtypes.size() > 0) {
				name += "<";
				for (int i = 0; i < subtypes.size(); i++) name += (i>0?", ":"") + subtypes.get(i).toString();
				if (bonusType != null) {
					name += "; " + bonusType.toString();
				}
				name += ">";
			}
			return name;
		}
		
		public String toBasic() {
			String name = "" + this.name;
			if (subtypes.size() > 0) {
				name += "@b";
				for (int i = 0; i < subtypes.size(); i++) name += (i>0?"@s":"") + subtypes.get(i).toBasic();
				if (bonusType != null) {
					name += "@x" + bonusType.toBasic();
				}
				name += "@e";
			}
			return name;
		}
		
		public static TypeTree getDefault(String name) {
			TypeTree t = new TypeTree();
			t.name = name;
			return t;
		}
		
		public static TypeTree getDefault(String name, String... subtype) {
			TypeTree t = new TypeTree();
			t.name = name;
			for (String s : subtype) t.subtypes.add(getDefault(s));
			return t;
		}
		
		public static TypeTree getDefault(String name, TypeTree... subtype) {
			TypeTree t = new TypeTree();
			t.name = name;
			for (TypeTree s : subtype) t.subtypes.add(s);
			return t;
		}
		
		public TypeTree setBonus(TypeTree t) {
			bonusType = t;
			return this;
		}
		
		@Override
		public Object clone() {
			TypeTree copy = new TypeTree();
			copy.name = name;
			for (TypeTree t : subtypes) {
				copy.subtypes.add((TypeTree) t.clone());
			}
			return copy;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof TypeTree)) return false;
			TypeTree b = (TypeTree) obj;
			if (b.name.equals(name) && b.subtypes.equals(subtypes) && (b.bonusType == null ? bonusType == null : b.bonusType.equals(bonusType))) return true;
			return super.equals(obj);
		}
		
		@Override
		public int hashCode() {
			int hash = name.hashCode();
			for (TypeTree t : subtypes) hash += t.hashCode();
			if (bonusType != null) hash += bonusType.hashCode();
			return hash;
		}
		
	}
	
	@CompilerInfo
	public interface GenericStruct {
		public ArrayList<String> typeargs();
	}
	
	@CompilerInfo
	public static class NonLocal {
		public String name;
		public TypeTree type;
		public NonLocal(String name, TypeTree type) {
			super();
			this.name = name;
			this.type = type;
		}
	}
	
	public static class Flag {
		public String name;
		public ArrayList<String> args = new ArrayList<>();
		public Flag() {}
	}
	
	public static void parseFlag(TokenScanner s, Set<String> flags, HashMap<String, Flag> flags1) throws SyntaxError {
		accept("#", s);
		Flag flag = new Flag();
		flag.name = nextI(s);
		if (seek(s).equals("(")) {
			accept("(", s);
			String s1 = (next(s));
			if (seek(s).equals("=")) {
				accept("=", s);
				String val = next(s);
				
				flag.args.add(s1 + "=" + val);
				
				while (seek(s).equals(",")) {
					accept(",", s);
					s1 = (next(s));
					accept("=", s);
					val = next(s);
					flag.args.add(s1 + "=" + val);
				}
			} else {
				flag.args.add(s1);
			}
			accept(")", s);
		}
		flags.add(flag.name);
		flags1.put(flag.name, flag);
	}
	
	/**
	 * Function = {
	 * 		NAME ("(" PARAMS ")")? "[" LINE* "]"
	 * }
	 */
	public static class FunctionTree extends TreeNode implements GenericStruct {
		
		@CompilerInfo public String module;
		
		@CompilerInfo public Set<String> genericHandler = new HashSet<String>();
		@CompilerInfo public String owner;
		@CompilerInfo public boolean typeargsAlreadySet = false;
		@CompilerInfo public List<NonLocal> nonlocal = null;
		
		@CompilerInfo public int field = 0;
		
		public ArrayList<String> typeargs = new ArrayList<>();
		
		public Map<String, TypeTree> templateChanges = null;
		
		public TypeTree returnType = TypeTree.getDefault(ProceedCompiler.TOP_TYPE);
		
		public String name;
		public ArrayList<String> params = new ArrayList<>();
		public ArrayList<TypeTree> paramtypes = new ArrayList<>();
		public ArrayList<LineTree> lines = new ArrayList<LineTree>();

		public Set<String> flags = new HashSet<>();
		public HashMap<String, Flag> flags1 = new HashMap<>();
		
		public String alias;
		
		public boolean template = false;
		public boolean isAbstract = false, isExtern = false;
		public boolean throwsEx = false;
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			while (seek(s).equals("#")) {
				parseFlag(s, flags, flags1);
				
			}
			
			if (seek(s).equals("template")) {
				accept("template", s);
				
				flags.add("template");
				flags1.put("template", new Flag());
				
				template = true;
			}
			
			if (seek(s).equals("<")) {
				accept("<", s);
				accept("@", s);
				typeargs.add(nextI(s));
				if (seek(s).equals(",")) do {
					accept(",", s);
					accept("@", s);
					typeargs.add(nextI(s));
				} while (seek(s).equals(","));
				
				accept(">", s);
			}
			if (seek(s).equals("@") && !seek(s, 3).equals(".")) {
				returnType = new TypeTree();
				returnType.parse(s);
			}
			
			name = "";
			
			/* Luokan ulkopuolella oleva metodi */
			if (seek(s).equals("@") && owner == null) {
				accept("@", s);
				String clazz = nextI(s);
				accept(".", s);
				name += "method@" + clazz + ".";
			}
			if (seek(s).equals("operator")) {
				accept("operator", s);
				String op = next(s);
				if (contains(MethodCallTree.OPERATORS_NPRIM, op)<0) {
					throw new SyntaxError(s.file(), s.line()+1, "Unknown operator " + op);
				}
				if (op.equals("[")) {
					op += "]";
					accept("]", s);
					if (seek(s).equals("=")) {
						op += "=";
						accept("=", s);
					}
				}
				name += "operator@" + getOperatorAlias(op);
			} else if (seek(s).equals("module")) {
				accept("module", s);
				accept(".", s);
				name += "modulef@" + mangle(module, "::") + "." + nextI(s);
			} else {
				name += nextI(s);
				if (seek(s).equals("=")) {
					name = "setfield@" + name;
					accept("=", s);
				}
			}
			if (seek(s).equals("(")) {
				
				accept("(", s);
				if (!seek(s).equals(")")) while (true) {
					if (seek(s).equals("@")) {
						TypeTree type = new TypeTree();
						type.parse(s);
						paramtypes.add(type);
					} else paramtypes.add(TypeTree.getDefault(ProceedCompiler.TOP_TYPE));
					params.add(nextI(s));
					if (accept(new String[]{",", ")"}, s).equals(")")) break;
				} else accept(")", s);
			
			}
			
			if (seek(s).equals("throws")) {
				accept("throws", s);
				accept("ex", s);
				throwsEx = true;
			}
			
			while (seek(s).equals("#")) {
				parseFlag(s, flags, flags1);
			}
			
			if (seek(s).equals("alias") && !template) {
				accept("alias", s);
				if (seek(s).equals("@")) {
					accept("@", s);
					this.alias = "method@" + nextI(s) + ".";
					accept(".", s);
					this.alias += nextI(s);
				}
				else this.alias = nextI(s);
				accept(";", s);
				return;
			}
			
			if (seek(s).equals("abstract") && !template) {
				accept("abstract", s);
				accept(";", s);
				flags.add("abstract");
				flags1.put("abstract", new Flag());
				isAbstract = true;
				return;
			} else if (seek(s).equals("extern") && !template) {
				accept("extern", s);
				accept(";", s);
				flags.add("extern");
				flags1.put("extern", new Flag());
				isExtern = true;
				return;
			}
			
			accept("[", s);
			while (!seek(s).equals("]")) {
				{
					LineTree t = new LineTree();
					t.parse(s);
					lines.add(t);
				}
			}
			accept("]", s);
			
		}

		@Override
		public String generate(String a) {
			return null;
		}

		@Override
		public ArrayList<String> typeargs() {
			return typeargs;
		}
		
		@CompilerInfo
		@Override
		public String toString() {
			String s = "<";
			for (int i = 0; i < typeargs.size(); i++) {
				if (i > 0) s += ", "; 
				s += typeargs.get(i);
			}
			s += "> ";
			
			s += returnType;
			s += " " + name;
			
			s += "(";
			for (int i = 0; i < params.size(); i++) {
				if (i > 0) s += ", "; 
				s += paramtypes.get(i) + " " + params.get(i);
			}
			s += ")";
			
			return s;
		}
		
	}
	
	/**
	 * Interface = {
	 * 		"interface" "@" NAME "{"
	 * 			FUNCTION*
	 * 		"}"
	 * }
	 */
	public static class InterfaceTree extends TreeNode implements GenericStruct {
		
		@CompilerInfo public String module;
		
		public ArrayList<String> typeargs = new ArrayList<>();
		
		public String name;
		
		public ArrayList<FunctionTree> functions = new ArrayList<FunctionTree>();
		
		public TypeTree data = TypeTree.getDefault(ProceedCompiler.UNIT_TYPE);
		public boolean castable = false;
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			accept("interface", s);
			accept("@", s);
			name = nextI(s);

			if (seek(s).equals("<")) {
				accept("<", s);
				accept("@", s);
				typeargs.add(nextI(s));
				if (seek(s).equals(",")) do {
					accept(",", s);
					accept("@", s);
					typeargs.add(nextI(s));
				} while (seek(s).equals(","));
				
				accept(">", s);
			}
			if (seek(s).equals("data")) {
				accept("data", s);
				if (seek(s).equals("castable")) {
					accept("castable", s);
					castable = true;
				}
				data = new TypeTree();
				data.parse(s);
			}
			accept("{", s);
			while (!seek(s).equals("}")) {
				if (seek(s).equals("doc")) {
					while (!seek(s).equals(";")) next(s);
					accept(";", s);
				} else if (seek(s).equals("manual") || seek(s).equals("auto") || (seek(s).equals("template") && (seek(s, 2).equals("auto") || seek(s, 2).equals("manual")))) {
					
					boolean template = false;
					if (template = seek(s).equals("template")) {
						accept("template", s);
					}
					
					String level = accept(new String[] {"manual", "auto"}, s);
					
					FunctionTree t = new FunctionTree();
					TypeTree ftype = new TypeTree();
					ftype.parse(s);
					t.template = template;
					t.returnType = ftype;
					t.name = level + "cast@" + ftype.toString().replaceAll(" ", "");
					
					while (seek(s).equals("#")) {
						parseFlag(s, t.flags, t.flags1);
					}
					
					if (seek(s).equals("alias")) {
						accept("alias", s);
						if (seek(s).equals("@")) {
							accept("@", s);
							t.alias = "method@" + nextI(s) + ".";
							accept(".", s);
							t.alias += nextI(s);
						}
						else t.alias = nextI(s);
						accept(";", s);
					} else {
					
						accept("[", s);
						while (!seek(s).equals("]")) {
							{
								LineTree t1 = new LineTree();
								t1.parse(s);
								t.lines.add(t1);
							}
						}
						accept("]", s);
					}
					t.module = module;
					t.owner = name;
					functions.add(t);
				} else {
					FunctionTree t = new FunctionTree();
					t.module = module;
					t.owner = name;
					t.parse(s);
					functions.add(t);
				}
				continue;
			}
			accept("}", s);
			
		}

		@Override
		public String generate(String a) {
			return null;
		}
		
		@Override
		public ArrayList<String> typeargs() {
			return typeargs;
		}
		
	}
	
	public static class FieldTree {
		public FieldTree(String name, TypeTree type, String getter,
				String setter) {
			super();
			this.name = name;
			this.type = type;
			this.getter = getter;
			this.setter = setter;
		}
		public FieldTree(String name, TypeTree type, String getter,
				String setter,
				Set<String> getter_flags, HashMap<String, Flag> getter_flags1, Set<String> setter_flags, HashMap<String, Flag> setter_flags1) {
			super();
			this.name = name;
			this.type = type;
			this.getter = getter;
			this.setter = setter;
			this.getter_flags = getter_flags;
			this.getter_flags1 = getter_flags1;
			this.setter_flags = setter_flags;
			this.setter_flags1 = setter_flags1;
		}
		public String name;
		public TypeTree type;
		public String getter;
		public String setter;
		
		public Set<String> getter_flags = new HashSet<>();
		public HashMap<String, Flag> getter_flags1 = new HashMap<>();
		public Set<String> setter_flags = new HashSet<>();
		public HashMap<String, Flag> setter_flags1 = new HashMap<>();
	}
	
	/**
	 * Struct = {
	 * 		"struct" "@" NAME : TYPE "{"
	 * 			FUNCTION*
	 * 		"}"
	 * }
	 */
	public static class StructTree extends TreeNode implements GenericStruct {
		
		@CompilerInfo public String module;
		
		public ArrayList<String> typeargs = new ArrayList<>();
		
		public String name;

		public TypeTree superType = TypeTree.getDefault(ProceedCompiler.OBJ_TYPE);
		public ArrayList<TypeTree> protocols = new ArrayList<>();
		
		public ArrayList<FunctionTree> functions = new ArrayList<FunctionTree>();
		
		@CompilerInfo
		public ArrayList<FunctionTree> functionsOrg = new ArrayList<FunctionTree>();
		
		public ArrayList<FieldTree> fields = new ArrayList<>();
		
		@CompilerInfo
		public ArrayList<FieldTree> fieldsOrg = new ArrayList<>();
		
		public boolean isClass = false, isProtocol = false;
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			switch (accept(new String[]{"struct", "class", "protocol"}, s)) {
			case "class":
				isClass = true; break;
			case "protocol":
				isProtocol = isClass = true; break;
			}
			accept("@", s);
			name = nextI(s);

			if (seek(s).equals("<")) {
				accept("<", s);
				accept("@", s);
				typeargs.add(nextI(s));
				if (seek(s).equals(",")) do {
					accept(",", s);
					accept("@", s);
					typeargs.add(nextI(s));
				} while (seek(s).equals(","));
				
				accept(">", s);
			}
			if (seek(s).equals(":") && !isProtocol) {
				accept(":", s);
				superType = new TypeTree();
				superType.parse(s);
			}
			
			if (seek(s).equals("(") && isClass) {
				accept("(", s);
				
				TypeTree tmp = new TypeTree();
				
				tmp.parse(s);
				protocols.add(tmp);
				
				while (seek(s).equals(",")) {
					accept(",", s);
					tmp = new TypeTree();
					tmp.parse(s);
					protocols.add(tmp);
				}
				
				accept(")", s);
			}
			
			accept("{", s);
			while (!seek(s).equals("}")) {
				if (seek(s).equals("doc")) {
					while (!seek(s).equals(";")) next(s);
					accept(";", s);
				} else if (seek(s).equals("field") && !isProtocol) {
					accept("field", s);
					TypeTree ftype = new TypeTree();
					ftype.parse(s);
					String name = nextI(s), getter = null, setter = null;
					
					FieldTree field = new FieldTree(null, null, null, null);
					
					if (!seek(s).equals("{")) {
						getter = "" + name;
						setter = "setfield@" + name;
					} else {
					
						accept("{", s);
						for (int i = 0; i < 2; i++) {
							if (seek(s).equals("get") && getter == null) {
								accept("get", s);
								accept(":", s);
								
								while (seek(s).equals("#")) {
									parseFlag(s, field.getter_flags, field.getter_flags1);
								}
								
								if (!seek(s).equals(",") && !seek(s).equals("}"))
									getter = nextI(s);
								else
									getter = name;
							} else if (seek(s).equals("set") && setter == null) {
								accept("set", s);
								accept(":", s);
								
								while (seek(s).equals("#")) {
									parseFlag(s, field.setter_flags, field.setter_flags1);
								}
								
								if (!seek(s).equals(",") && !seek(s).equals("}"))
								{
									setter = nextI(s);
									if (seek(s).equals("=")) {
										setter = "setfield@" + setter;
										accept("=", s);
									}
								}
								else
									setter = "setfield@" + name;
							}
							if (i == 0) accept(",", s);
						}
						accept("}", s);
					}
					accept(";", s);
					
					field.name = name;
					field.type = ftype;
					field.getter = getter;
					field.setter = setter;
					
					fields.add(field);
					fieldsOrg.add(field);
				} else if (seek(s).equals("new") && !isProtocol) {
					accept("new", s);
					FunctionTree t = new FunctionTree();
					t.alias = null;
					t.name = "new";
					t.returnType = TypeTree.getDefault(ProceedCompiler.UNIT_TYPE);
					accept("[", s);
					while (!seek(s).equals("]")) {
						{
							LineTree t1 = new LineTree();
							t1.parse(s);
							t.lines.add(t1);
						}
					}
					accept("]", s);
					t.owner = name;
					functions.add(t);
					functionsOrg.add(t);
				} else if ((seek(s).equals("manual") || seek(s).equals("auto") || (seek(s).equals("template") && (seek(s, 2).equals("auto") || seek(s, 2).equals("manual")))) && !isProtocol) {
					
					boolean template = false;
					if (template = seek(s).equals("template")) {
						accept("template", s);
					}
					
					String level = accept(new String[] {"manual", "auto"}, s);
					
					FunctionTree t = new FunctionTree();
					TypeTree ftype = new TypeTree();
					ftype.parse(s);
					t.template = template;
					t.returnType = ftype;
					t.name = level + "cast@" + ftype.toString().replaceAll(" ", "");
					
					while (seek(s).equals("#")) {
						parseFlag(s, t.flags, t.flags1);
					}
					
					if (seek(s).equals("alias")) {
						accept("alias", s);
						if (seek(s).equals("@")) {
							accept("@", s);
							t.alias = "method@" + nextI(s) + ".";
							accept(".", s);
							t.alias += nextI(s);
						}
						else t.alias = nextI(s);
						accept(";", s);
					} else {
					
						accept("[", s);
						while (!seek(s).equals("]")) {
							{
								LineTree t1 = new LineTree();
								t1.parse(s);
								t.lines.add(t1);
							}
						}
						accept("]", s);
					}
					t.module = module;
					t.owner = name;
					functions.add(t);
					functionsOrg.add(t);
				} else {
					FunctionTree t = new FunctionTree();
					t.module = module;
					t.owner = name;
					t.parse(s);
					
					if (isProtocol && !t.isAbstract) throw new SyntaxError(s.file(), s.line(), "Protocol can't contain non-abstract methods");
					
					functions.add(t);
					functionsOrg.add(t);
				}
				continue;
			}
			accept("}", s);
			
		}

		@Override
		public String generate(String a) {
			return null;
		}
		
		@Override
		public ArrayList<String> typeargs() {
			return typeargs;
		}
		
	}
	
	/**
	 * Line = {
	 * 		EXPRESSION ";"
	 * 		RETURN
	 * }
	 */
	public static class LineTree extends TreeNode {
		
		public ArrayList<String> args = new ArrayList<>();
		
		public boolean vardef = false;
		
		public String var;
		public ExpressionTree expr;
		
		public LineTree block2;
		
		public LineTree block;
		public LineTree elseBlock;
		
		public ArrayList<LineTree> lines = new ArrayList<LineTree>();
		
		public int line;
		
		public enum Type {
			IF,
			WHILE,
			DO_WHILE,
			FOR,
			BREAK,
			ASSIGN,
			EXPRESSION,
			RETURN,
			BLOCK,
			TRY_CATCH,
			THROW,
			NULL_STATEMENT,
			NONLOCAL
		}
		
		public Type type;
		
		public TypeTree typedef = null;//TypeTree.getDefault(ProceedCompiler.TOP_TYPE);
		
		public void parse(TokenScanner s) throws SyntaxError {
			parse_(s, true);
		}
		
		public void parse_(TokenScanner s, boolean requireSemicolon) throws SyntaxError {
			while (seek(s).equals("doc")) {
				while (!seek(s).equals(";")) next(s);
				accept(";", s);
				type = Type.NULL_STATEMENT;
				return;
			}
			if (seek(s).equals("if") || seek(s).equals("while")) {
				if (accept(new String[] {"if", "while"}, s).equals("if")) type = Type.IF;
				else type = Type.WHILE;
				accept("(", s);
				line = s.nextLine();
				expr = new ExpressionTree();
				expr.parse(s);
				accept(")", s);
				
				block = new LineTree();
				block.parse_(s, requireSemicolon);
				
				if (seek(s).equals("else") && type == Type.IF) {
					
					accept("else", s);
					elseBlock = new LineTree();
					elseBlock.parse_(s, requireSemicolon);
				} 
				
			} else if (seek(s).equals("for")) { // For
				accept("for", s);
				type = Type.FOR;
				accept("(", s);
				line = s.nextLine();
				
				block2 = new LineTree();
				block2.parse_(s, false);
				
				accept(";", s);
				
				expr = new ExpressionTree();
				expr.parse(s);
				
				accept(";", s);
				
				elseBlock = new LineTree();
				elseBlock.parse_(s, false);
				
				accept(")", s);
				
				block = new LineTree();
				block.parse_(s, requireSemicolon);
				
			} else if (seek(s).equals("do")) {
				type = Type.DO_WHILE;
				accept("do", s);
				line = s.nextLine();
				block = new LineTree();
				block.parse(s);
				accept("while", s);
				accept("(", s);
				line = s.nextLine();
				expr = new ExpressionTree();
				expr.parse(s);
				accept(")", s);
				if (requireSemicolon) accept(";", s);
				
			} else if (seek(s).equals("break")) {
				accept("break", s);
				line = s.nextLine();
				type = Type.BREAK;
				if (requireSemicolon) accept(";", s);
			} else if (seek(s).equals("try")) {
				accept("try", s);
				
				line = s.nextLine();
				
				block = new LineTree();
				block.parse(s);
				
				accept("catch", s);
				
				accept("(", s);		
				var = nextI(s);
				accept(")", s);
				
				elseBlock = new LineTree();
				elseBlock.parse_(s, requireSemicolon);
				
				type = Type.TRY_CATCH;
			} else if (seek(s).equals("return")) {
				accept("return", s);
				line = s.nextLine();
				expr = new ExpressionTree();
				expr.parse(s);
				type = Type.RETURN;
				if (requireSemicolon) accept(";", s);
			} else if (seek(s).equals("throw")) {
				accept("throw", s);
				line = s.nextLine();
				expr = new ExpressionTree();
				expr.parse(s);
				type = Type.THROW;
				if (requireSemicolon) accept(";", s);
			} else if (seek(s).equals("{")) {
				accept("{", s);
				while (!seek(s).equals("}")) {
					{
						LineTree t = new LineTree();
						t.parse(s);
						lines.add(t);
					}
					continue;
				}
				accept("}", s);
				type = Type.BLOCK;
			}
			else if (seek(s, 2).equals("=") || seek(s).equals("@") || seek(s).equals("var")) {
				if (seek(s).equals("var")) {
					accept("var", s);
					vardef = true;
				}
				else if (seek(s).equals("@")) {
					typedef = new TypeTree();
					typedef.parse(s);
					vardef = true;
				}
				var = nextI(s);
				line = s.nextLine();
				accept("=", s);
				expr = new ExpressionTree();
				expr.parse(s);
				type = Type.ASSIGN;
				if (requireSemicolon) accept(";", s);
			} else if (seek(s).equals(";")) {
				accept(";", s);
				type = Type.NULL_STATEMENT;
			} else if (seek(s).equals("nonlocal")) {
				accept("nonlocal", s);
				do {
					if (seek(s).equals("this")) args.add(next(s));
					else args.add(nextI(s));
					if (!seek(s).equals(",")) break;
					accept(",", s);
				} while (true);
				if (requireSemicolon) accept(";", s);
				type = Type.NONLOCAL;
			} else {
				line = s.nextLine();
				expr = new ExpressionTree();
				expr.parse(s);
				type = Type.EXPRESSION;
				if (requireSemicolon) accept(";", s);
			}
			
			while (seek(s).equals("doc")) {
				while (!seek(s).equals(";")) next(s);
				accept(";", s);
			}
			
			
		}

		@Override
		public String generate(String a) {
			return null;
		}
		
	}
	
	public static class MethodCallTree extends TreeNode {
		
		public final static String[] OPERATORS = {
			"::", ".", "(", "[",
			"**",
			"*", "/", "%",
			"+", "-",
			"..",
			"<<", ">>",
			"&",
			"^",
			"|",
			"<", ">", "<=", ">=",
			"==", "!=",
			"and", "&&",
			"or", "||",
			"->", "=>",
			"/[a-zäöå][a-zA-ZÄÖÅäöå0-9_]*/",
			":=", ":-", "?=", "~=",
			"|>", "<|",
			"/[A-ZÄÖÅ_][a-zA-ZÄÖÅäöå0-9_]*/"};
		
		public final static String[] OPERATORS_PRIM = {
			"::", ".", "(", "["
		};
		
		public final static String[] OPERATORS_NPRIM = {
			"[",
			"**",
			"*", "/", "%",
			"+", "-",
			"..",
			"<<", ">>",
			"&",
			"^",
			"|",
			"<", ">", "<=", ">=",
			"==", "!=",
			"and", "&&",
			"or", "||",
			"->", "=>",
			"/[a-zäöå][a-zA-ZÄÖÅäöå0-9_]*/",
			":=", ":-", "?=", "~=",
			"|>", "<|",
			"/[A-ZÄÖÅ_][a-zA-ZÄÖÅäöå0-9_]*/"};
		
		public final static String[][] OPERATORS_PREC = {
			{"/[A-ZÄÖÅ_][a-zA-ZÄÖÅäöå0-9_]*/"},
			{"|>", "<|"},
			{":=", ":-", "?=", "~="},
			{"/[a-zäöå][a-zA-ZÄÖÅäöå0-9_]*/"},
			{"->", "=>"},
			{"or", "||"},
			{"and", "&&"},
			{"==", "!="},
			{"<", ">", "<=", ">="},
			{"|"},
			{"^"},
			{"&"},
			{"<<", ">>"},
			{".."},
			{"+", "-"},
			{"*", "/", "%"},
			{"**"}};
		
		public String method;
		//public ArrayList<String> methods;
		
		public ExpressionTree expr;

		public enum Type {
			EXPRESSION,
			METHOD
		}
		
		public Type type;
		
		public MethodCallTree() {}
		
		public MethodCallTree(ExpressionTree expr, String method) {
			type = Type.METHOD;
			this.expr = expr;
			this.method = method;
		}
		
		private String nextI(TokenScanner s) throws SyntaxError {
			if (seek(s).equals("new")) return next(s);
			else return ProceedTree.nextI(s);
		}
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			parse(s, 0);
		}
		
		public void parse(TokenScanner s, int operatorC) throws SyntaxError {
			expr = new ExpressionTree();
			if (seek(s).equals("super") && (seek(s, 1).equals(".") || seek(s, 1).equals(":") || seek(s, 1).equals("::"))) {
				accept("super", s);
				expr.type = ExpressionTree.Type.VALUE;
				expr.var = "super";
			}
			else expr.parse(s, true, operatorC);
			type = Type.EXPRESSION;
			parse2(s, true, operatorC);
		}
		
		

		private void parse2(TokenScanner s, boolean methodsAllowed, int operatorC) throws SyntaxError {
			
			prim: {
				if (seek(s).equals(":") && methodsAllowed) {
					accept(":", s);
					if (seek(s).equals("operator")) {
						accept("operator", s);
						String op = next(s);
						if (contains(OPERATORS_NPRIM, op)<0) {
							throw new SyntaxError(s.file(), s.line()+1, "Unknown operator " + op);
						}
						if (op.equals("[")) {
							op += "]";
							accept("]", s);
							if (seek(s).equals("=")) {
								op += "=";
								accept("=", s);
							}
						}
						method = "operator@" + getOperatorAlias(op);
					}
					else method = nextI(s);
					type = Type.METHOD;
					return;
				} else if (seek(s).equals("::")) {
					accept("::", s);
					ExpressionTree e = new ExpressionTree();
					e.type = ExpressionTree.Type.FUNCTION_CALL_LISP;
					e.function = new MethodCallTree();
					e.function.expr = expr;
					
					if (seek(s).equals("operator")) {
						accept("operator", s);
						String op = next(s);
						if (contains(OPERATORS_NPRIM, op)<0) {
							throw new SyntaxError(s.file(), s.line()+1, "Unknown operator " + op);
						}
						if (op.equals("[")) {
							op += "]";
							accept("]", s);
							if (seek(s).equals("=")) {
								op += "=";
								accept("=", s);
							}
						}
						e.function.method = "operator@" + getOperatorAlias(op);
					}
					else e.function.method = nextI(s);
					e.function.type = Type.METHOD;
					
					if (seek(s).equals("=")) {
						accept("=", s);
						ExpressionTree e1 = new ExpressionTree();
						e1.parse(s);
						e.args.add(e1);
						e.function.method = "setfield@" + e.function.method;
					}
					
					type = Type.EXPRESSION;
					expr = e;
					
					parse2(s, methodsAllowed, -1);
					break prim;
				} else if (seek(s).equals(".")) {
					accept(".", s);
					ExpressionTree e = new ExpressionTree();
					e.type = ExpressionTree.Type.FUNCTION_CALL_LISP;
					e.function = new MethodCallTree();
					e.function.expr = expr;
					if (seek(s).equals("(")) {
						e.function.type = Type.EXPRESSION; 	// funktiota voi kutsua tyyliin f.(args)
					} else {
						if (seek(s).equals("operator")) {
							accept("operator", s);
							String op = next(s);
							if (contains(OPERATORS_NPRIM, op)<0) {
								throw new SyntaxError(s.file(), s.line()+1, "Unknown operator " + op);
							}
							if (op.equals("[")) {
								op += "]";
								accept("]", s);
								if (seek(s).equals("=")) {
									op += "=";
									accept("=", s);
								}
							}
							e.function.method = "operator@" + getOperatorAlias(op);
						}
						else e.function.method = nextI(s);	// metodia voi kutsua tyyliin f.m(args)
						e.function.type = Type.METHOD;
					}
					
					//if (seek(s).equals("(")) {
						accept("(", s);
						if (!seek(s).equals(")")) while (true) {
							ExpressionTree e1 = new ExpressionTree();
							e1.parse(s);
							e.args.add(e1);
							if (accept(new String[]{",", ")"}, s).equals(")")) break;
						} else accept(")", s);
					/*} else {
						if (seek(s).equals("=")) {
							accept("=", s);
							ExpressionTree e1 = new ExpressionTree();
							e1.parse(s);
							e.args.add(e1);
							e.function.method = "setfield@" + e.function.method;
						} else e.function.method = "getfield@" + e.function.method;
					}*/
					
						if (seek(s).equals("=")) {
							accept("=", s);
							ExpressionTree e1 = new ExpressionTree();
							e1.parse(s);
							e.args.add(e1);
							e.function.method = "setfield@" + e.function.method;
						}
						
					type = Type.EXPRESSION;
					expr = e;
					
					parse2(s, methodsAllowed, -1);
					break prim;
				} else if (seek(s).equals("(")) {
					ExpressionTree e = new ExpressionTree();
					e.type = ExpressionTree.Type.FUNCTION_CALL_LISP;
					e.function = new MethodCallTree();
					e.function.expr = expr;
					e.function.type = Type.EXPRESSION; 	// funktiota voi kutsua tyyliin f(args)
					
					accept("(", s);
					if (!seek(s).equals(")")) while (true) {
						ExpressionTree e1 = new ExpressionTree();
						e1.parse(s);
						e.args.add(e1);
						if (accept(new String[]{",", ")"}, s).equals(")")) break;
					} else accept(")", s);
					
					type = Type.EXPRESSION;
					expr = e;
					
					parse2(s, methodsAllowed, -1);
					break prim;
				} else if (seek(s).equals("[")) {
					ExpressionTree e = new ExpressionTree();
					e.type = ExpressionTree.Type.FUNCTION_CALL_LISP;
					e.function = new MethodCallTree();
					e.function.expr = expr;
					
					e.function.method = "operator@" + getOperatorAlias("[]");
	
					e.function.type = Type.METHOD;
					
					
					accept("[", s);
					ExpressionTree e1 = new ExpressionTree();
					e1.parse(s);
					e.args.add(e1);
					accept("]", s);
					
					if (seek(s).equals("=")) {
						accept("=", s);
						e1 = new ExpressionTree();
						e1.parse(s);
						e.args.add(e1);
						e.function.method = "operator@" + getOperatorAlias("[]=");
					}
					
					type = Type.EXPRESSION;
					expr = e;
					
					parse2(s, methodsAllowed, -1);
					break prim;
				}
			}
		
			if (operatorC == -1) return;
			parse3(s, operatorC);
		}

		private void parse3(TokenScanner s, int operatorC) throws SyntaxError {
			
			if (operatorC >= OPERATORS_PREC.length) return;
			parse3(s, operatorC+1);
			
			List<String> ops = new ArrayList<>();
			/*for (int i = operatorC; i < OPERATORS_PREC.length; i++) {
				ops.addAll(Arrays.asList(OPERATORS_PREC[i]));
			}*/
			ops.addAll(Arrays.asList(OPERATORS_PREC[operatorC]));
			while (contains(ops, seek(s)) >= 0) {
				String op = next(s);
				
				ExpressionTree e = new ExpressionTree();
				e.type = ExpressionTree.Type.FUNCTION_CALL_LISP;
				e.function = new MethodCallTree();
				e.function.expr = expr;
				e.function.method = "operator@" + getOperatorAlias(op);
				e.function.type = Type.METHOD;
				//System.err.println("(" + e.function.method + ")" + operatorC + "->"
				//		+ (operatorC+1));
				ExpressionTree e1 = new ExpressionTree();
				e1.parse(s, false, operatorC + 1);
				e.args.add(e1);

				type = Type.EXPRESSION;
				expr = e;
			}
			
		}

		@Override
		public String generate(String a) {
			return null;
		}
	}
	
	public static int getPrecedence(String op) {
		for (int i = 0; i < MethodCallTree.OPERATORS_PREC.length; i++) {
			if (contains(MethodCallTree.OPERATORS_PREC[i], op)>=0) return i;
		}
		return -1;
	}
	
	public static String mangle(String module, String string) {
		String[] name = module.split(string);
		String s = "";
		for (String s1 : name) s += "" + lengthChar(s1.length()+"") + s1.length() + s1;
		return s;
	}

	private static char lengthChar(String i) {
		return (char) (('a'-1)+i.length());
	}

	public static String getOperatorAlias(String op) {
		String ans = "";
		for (int i = 0; i < op.length(); i++) {
			if (i!=0) ans += "_";
			ans += Integer.toHexString(op.charAt(i));
		}
		return ans;
	}
	
	public static String demangleOperator(String func) {
		String a = "operator ";
		
		String op = func.substring(9);
		String[] chs = op.split("_");
		for (String s1 : chs) {
			a += ((char)NumberParser.parseHex(s1));
		}
		return a;
	}
	
	/**
	 * Expression = {
	 * 		VALUE | (name "(" list:Expression ")")
	 * }
	 */
	public static class ExpressionTree extends TreeNode {
		
		public ArrayList<String> params = new ArrayList<>();
		public ArrayList<TypeTree> paramtypes = new ArrayList<>();
		public List<TypeTree> typeargs = new ArrayList<>();
		
		public Set<String> flags = new HashSet<>();
		public HashMap<String, Flag> flags1 = new HashMap<>();
		
		public ArrayList<LineTree> lines = new ArrayList<>();
		
		public ArrayList<ExpressionTree> args = new ArrayList<>();
		
		public String var;
		
		public ExpressionTree expr;
		
		public MethodCallTree function;
		
		public TypeTree typeCast = null;
		
		public boolean sharevars = true;
		
		public int line = -1;
		
		public enum Type {
			FUNCTION_CALL, FUNCTION_CALL_LISP,
			VALUE,
			ANONYMOUS,
			TYPE_CAST,
			NEW_CALL,
			METHOD_CHAIN,
			SIZEOF,
			TYPEOF,
			EXPRESSION,
			NONLOCAL,
			LIST
		}
		
		public Type type;
		
		public ExpressionTree() {
			
		}
		
		@CompilerInfo
		public ExpressionTree(String var) {
			this.var = var;
			this.type = Type.VALUE;
		}
		
		@CompilerInfo
		public ExpressionTree(String var, ArrayList<TypeTree> typeargs) {
			this.var = var;
			this.typeargs = typeargs;
			this.type = Type.VALUE;
		}
		
		@CompilerInfo
		public ExpressionTree(String var, TypeTree... typeargs) {
			this.var = var;
			this.typeargs = Arrays.asList(typeargs);
			this.type = Type.VALUE;
		}
		
		@Override
		public void parse(TokenScanner s) throws SyntaxError {
			parse(s, false, 0);
		}
		
		public void parse(TokenScanner s, boolean lispOnly, int operatorC) throws SyntaxError {
			line = s.nextLine();
			
			if (seek(s).equals("@") && !seek(s, 3).equals(".")) {
				typeCast = new TypeTree();
				typeCast.parse(s);
				expr = new ExpressionTree();
				expr.parse(s, lispOnly, operatorC);
				type = Type.TYPE_CAST;
				return;
			}
			if (seek(s).equals("typeof")) {
				accept("typeof", s);
				expr = new ExpressionTree();
				expr.parse(s, lispOnly, operatorC);
				type = Type.TYPEOF;
				return;
			}
			/*if (seek(s).equals("-") || seek(s).equals("!")) {
				typeCast = new TypeTree();
				typeCast.parse(s);
				expr = new ExpressionTree();
				expr.parse(s, lispOnly, -1);
				type = Type.TYPE_CAST;
				return;
			} TODO */
			if (seek(s).equals("$") && !seek(s, 2).equals("(")) {
				type = Type.NONLOCAL;
				accept("$", s);
				var = nextI(s);
				if (contains(MethodCallTree.OPERATORS, seek(s))>= 0 && (!lispOnly || contains(MethodCallTree.OPERATORS_PRIM, seek(s))>= 0)) {
					type = Type.METHOD_CHAIN;
					this.function = new MethodCallTree();
					this.function.expr = new ExpressionTree();
					this.function.expr.type = Type.NONLOCAL;
					this.function.expr.var = var;
					this.function.parse2(s, false, operatorC);
					return;
				}
				return;
			}
			else if (seek(s).equals("$")) { // Lisp -tyyliset funktiokutsut VANHENTUNUT
				type = Type.FUNCTION_CALL_LISP;
				accept("$", s);
				accept("(", s);
				function = new MethodCallTree();
				function.parse(s, -1);
				if (!seek(s).equals(")")) while (true) {
					ExpressionTree e = new ExpressionTree();
					e.parse(s, true, -1);
					args.add(e);
					if (seek(s).equals(")")) break;
				}
				accept(")", s);
				if (contains(MethodCallTree.OPERATORS, seek(s))>= 0 && (!lispOnly || contains(MethodCallTree.OPERATORS_PRIM, seek(s))>= 0)) {
					type = Type.METHOD_CHAIN;
					ExpressionTree e = new ExpressionTree();
					e.function = new MethodCallTree();
					e.function.expr = new ExpressionTree();
					e.function.expr.type = Type.FUNCTION_CALL_LISP;
					e.function.expr.function = function;
					e.function.expr.typeargs = typeargs;
					e.function.expr.args = args;
					e.function.parse2(s, false, operatorC);
					this.function = e.function;
					return;
				}
				return;
			}
			if (seek(s).equals("sizeof")) {
				type = Type.SIZEOF;
				accept("sizeof", s);
				
				typeCast = new TypeTree();
				typeCast.parse(s);
				return;
			}
			if (seek(s).equals("new")) {
				type = Type.NEW_CALL;
				accept("new", s);
				
				typeCast = new TypeTree();
				typeCast.parse(s);
				
				if (contains(MethodCallTree.OPERATORS, seek(s))>= 0 && (!lispOnly || contains(MethodCallTree.OPERATORS_PRIM, seek(s))>= 0)) {
					type = Type.METHOD_CHAIN;
					ExpressionTree e = new ExpressionTree();
					e.function = new MethodCallTree();
					e.function.expr = new ExpressionTree();
					e.function.expr.type = Type.NEW_CALL;
					e.function.expr.typeCast = typeCast;
					e.function.parse2(s, false, operatorC);
					this.function = e.function;
					return;
				}
				return;
			}
			if (seek(s).equals("{")) {
				type = Type.LIST;
				accept("{", s);
				
				if (!seek(s).equals("}")) while (true) {
					ExpressionTree e1 = new ExpressionTree();
					e1.parse(s);
					args.add(e1);
					if (accept(new String[]{",", "}"}, s).equals("}")) break;
				} else accept("}", s);
				
				if (contains(MethodCallTree.OPERATORS, seek(s))>= 0 && (!lispOnly || contains(MethodCallTree.OPERATORS_PRIM, seek(s))>= 0)) {
					type = Type.METHOD_CHAIN;
					ExpressionTree e = new ExpressionTree();
					e.function = new MethodCallTree();
					e.function.expr = new ExpressionTree();
					e.function.expr.type = Type.LIST;
					e.function.expr.args = args;
					e.function.parse2(s, false, operatorC);
					this.function = e.function;
					return;
				}
				return;
			}
			boolean anon = false;
			if (seek(s).equals("#")) { // Nimettömät upotetut funktiot (lambda)
				anon = true;
				type = Type.ANONYMOUS;
				accept("#", s);
				
				if (seek(s).equals("(")) {
				
					accept("(", s);
					if (!seek(s).equals(")")) while (true) {
						if (seek(s).equals("@")) {
							TypeTree type = new TypeTree();
							type.parse(s);
							paramtypes.add(type);
						} else paramtypes.add(TypeTree.getDefault(ProceedCompiler.TOP_TYPE));
						params.add(nextI(s));
						if (accept(new String[]{",", ")"}, s).equals(")")) break;
					} else accept(")", s);
				
				}
				if (seek(s).equals("->")) {
					accept("->", s);
					typeCast = new TypeTree();
					typeCast.parse(s);
				}
				
				while (seek(s).equals("#") && isValidIdentifier(seek(s, 2))) {
					parseFlag(s, flags, flags1);
				}
				flags.add("lambda");
				flags1.put("lambda", new Flag());
				
				if (!seek(s).equals("[")) {
					LineTree l = new LineTree();
					l.type = LineTree.Type.RETURN;
					l.line = s.nextLine();
					l.expr = new ExpressionTree();
					l.expr.parse(s);
					
					lines.add(l);
					return;
				}
				
			}
			if (seek(s).equals("[") || anon) {
				type = Type.ANONYMOUS;
				accept("[", s);
				
				if (!seek(s).equals("]")) while (true) {
					if (seek(s).equals("]")) {
						accept("]", s);
						break;
					}
					LineTree e = new LineTree();
					e.parse(s);
					lines.add(e);
					//if (accept(new String[]{";", "]"}, s).equals("]")) break;
				}
				else accept("]", s);
				
				if (contains(MethodCallTree.OPERATORS, seek(s))>= 0 && (!lispOnly || contains(MethodCallTree.OPERATORS_PRIM, seek(s))>= 0)) {
					type = Type.METHOD_CHAIN;
					this.function = new MethodCallTree();
					this.function.expr = new ExpressionTree();
					this.function.expr.type = Type.ANONYMOUS;
					this.function.expr.sharevars = sharevars;
					this.function.expr.flags = flags;
					this.function.expr.flags1 = flags1;
					this.function.expr.params = params;
					this.function.expr.paramtypes = paramtypes;
					this.function.expr.lines = lines;
					this.function.expr.typeCast = typeCast;
					this.function.parse2(s, false, operatorC);
					return;
				}
				
				return;
			} else if (seek(s).equals("(")) {
				accept("(", s);
				expr = new ExpressionTree();
				expr.parse(s, false, 0);
				type = Type.EXPRESSION;
				accept(")", s);
				if (contains(MethodCallTree.OPERATORS, seek(s))>= 0 && (!lispOnly || contains(MethodCallTree.OPERATORS_PRIM, seek(s))>= 0)) {
					type = Type.METHOD_CHAIN;
					this.function = new MethodCallTree();
					this.function.expr = new ExpressionTree();
					this.function.expr.type = Type.EXPRESSION;
					this.function.expr.expr = expr;
					this.function.parse2(s, false, operatorC);
					return;
				}
				return;
			}
			boolean forceMethodCall = false;
			{
				if (seek(s).matches("[\\+\\-]?[0-9]*")) {
					var = next(s);
				} else if (seek(s).matches("0x[0-9a-fA-F]*")) {
					var = next(s);
				} else if (seek(s).matches("0b[0-1]*")) {
					var = next(s);
				} else if (seek(s).matches("[\\+\\-\\*/%\\<\\>\\=\\!]*")) {
					var = next(s);
				} else if (seek(s).startsWith("\"") || seek(s).startsWith("'")) {
					var = next(s);
				} else if (seek(s).equals("super")) {
					var = next(s);
					forceMethodCall = true;
				} else if (seek(s).equals("@")) {
					accept("@", s);
					var = "method@" + nextI(s);
					accept(".", s);
					var +="." + nextI(s);
				} else var = nextI(s);
			}
			if (seek(s).equals("~<")) {
				accept("~<", s);
				TypeTree subtype;
				subtype = new TypeTree();
				subtype.parse(s);
				typeargs.add(subtype);
				if (seek(s).equals(",")) do {
					accept(",", s);
					TypeTree st = new TypeTree();
					st.parse(s);
					typeargs.add(st);
				} while (seek(s).equals(","));
				
				accept(">", s);
			}
			if ((contains(MethodCallTree.OPERATORS, seek(s))>= 0 || forceMethodCall) && (!lispOnly || contains(MethodCallTree.OPERATORS_PRIM, seek(s))>= 0)) {
				type = Type.METHOD_CHAIN;
				this.function = new MethodCallTree();
				this.function.expr = new ExpressionTree();
				this.function.expr.type = Type.VALUE;
				this.function.expr.var = var;
				this.function.expr.typeargs = typeargs;
				this.function.parse2(s, false, operatorC);
				return;
			}
			{
				type = Type.VALUE;
				return;
			}
			/*if (!seek(s).equals("(")) {
				type = Type.VALUE;
				return;
			}
			
			type = Type.FUNCTION_CALL;
			accept("(", s);
			if (!seek(s).equals(")")) while (true) {
				ExpressionTree e = new ExpressionTree();
				e.parse(s);
				args.add(e);
				if (accept(new String[]{",", ")"}, s).equals(")")) break;
			} else accept(")", s);
			
			if (contains(MethodCallTree.OPERATORS, seek(s))>= 0) {
				type = Type.METHOD_CHAIN;
				this.function = new MethodCallTree();
				this.function.expr = new ExpressionTree();
				this.function.expr.type = Type.FUNCTION_CALL;
				this.function.expr.var = var;
				this.function.expr.typeargs = typeargs;
				this.function.expr.args = args;
				this.function.parse2(s, false, operatorC);
				return;
			}*/
			
		}

		@Override
		public String generate(String a) {
			return null;
		}
		
	}

	public static <T> int contains(T[] operators, T seek) {
		for (int i = 0; i < operators.length; i++) {
			if (equalsOrMatches(operators[i], seek)) return i;
		}
		return -1;
	} 
	
	public static <T> int contains(List<T> operators, T seek) {
		for (int i = 0; i < operators.size(); i++) {
			if (equalsOrMatches(operators.get(i), seek)) return i;
		}
		return -1;
	}
	
	public static <T> boolean equalsOrMatches(T a, T b) {
		if (a.getClass().getName().equals("java.lang.String")) {
			String sa = (String) a;
			String sb = (String) b;
			if (sa.startsWith("/") && sa.endsWith("/") && sa.length() > 2) {
				return sb.matches(sa.substring(1, sa.length()-1));
			}
			else return a.equals(b);
		} else return a.equals(b);
	}
	
}
