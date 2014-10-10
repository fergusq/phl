package org.kaivos.proceedhl.plugins;

import org.kaivos.proceedhl.compiler.ProceedCompiler;
import org.kaivos.proceedhl.parser.ProceedTree.FunctionTree;
import org.kaivos.proceedhl.parser.ProceedTree.InterfaceTree;
import org.kaivos.proceedhl.parser.ProceedTree.StructTree;

/**
 * Esimerkkiplugini -- tarkistaa että luokkien nimet on kirjoitettu isolla ja
 * funktioiden nimet pienellä
 * 
 * @author Iikka Hauhio
 *
 */
public class NameCheckPlugin extends CompilerPlugin {

	public NameCheckPlugin(ProceedCompiler compiler) {
		super(compiler);
	}

	@Override
	public String name() {
		return "NameCheck";
	}

	@Override
	public String version() {
		return "0.1";
	}
	
	@Override
	public void post_interface(InterfaceTree tree) {
		if (!Character.isUpperCase(tree.name.charAt(0)))
			compiler.warn(ProceedCompiler.W_NORMAL, "'" + tree.name + "' starts with a lower case letter! (interface names shall start with an upper case letter)");
	}
	
	@Override
	public void post_struct(StructTree tree) {
		if (!Character.isUpperCase(tree.name.charAt(0)))
			compiler.warn(ProceedCompiler.W_NORMAL, "'" + tree.name + "' starts with a lower case letter! (struct names shall start with an upper case letter)");
	}
	
	@Override
	public void post_function(FunctionTree tree) {
		if (Character.isUpperCase(tree.name.charAt(0)))
			compiler.warn(ProceedCompiler.W_NORMAL, "'" + tree.name + "' starts with an upper case letter! (function names shall start with a lower case letter)");
	}

}
