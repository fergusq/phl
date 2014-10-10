package org.kaivos.proceedhl.compiler;

public class CompilerError extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1823941955397309451L;
	public String msg;

	public CompilerError(String msg) {
		this.msg = msg;
	}
	
	@Override
	public String getMessage() {
		return msg;
	}
	
}
