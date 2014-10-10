package org.kaivos.proceedhl.plugins;

import org.kaivos.proceedhl.compiler.ProceedCompiler;
import org.kaivos.proceedhl.parser.ProceedTree;

public abstract class CompilerPlugin {

	protected ProceedCompiler compiler;
	
	public CompilerPlugin(ProceedCompiler compiler) {
		this.compiler = compiler;
	}
	
	public abstract String name();
	public abstract String version();
	
	public void pre_ast(ProceedTree.StartTree tree) {}
	
	public void post_interface(ProceedTree.InterfaceTree tree) {}
	public void post_struct(ProceedTree.StructTree tree) {}
	public void post_function(ProceedTree.FunctionTree tree) {}
	
	public boolean canDoTypeCast(ProceedTree.TypeTree from, ProceedTree.TypeTree to) {
		return false;
	}
	
	public boolean doTypeCast(ProceedTree.TypeTree from, ProceedTree.TypeTree to, String target) {
		return false;
	}
	
}
