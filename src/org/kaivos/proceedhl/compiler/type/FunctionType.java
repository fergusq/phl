package org.kaivos.proceedhl.compiler.type;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionType extends Type {

	private Type returnType;
	private Type[] parameterTypes;
	
	public FunctionType(Type returnType, Type... parameterTypes) {
		super(Class.FUNCTION);
		this.returnType = returnType;
		this.parameterTypes = parameterTypes;
	}
	
	public FunctionType(Type returnType, List<Type> parameterTypes) {
		this(returnType, parameterTypes.toArray(new Type[parameterTypes.size()]));
	}

	public Type getReturnType() {
		return returnType;
	}
	
	public Type[] getParameterTypes() {
		return parameterTypes;
	}
	
	@Override
	public String toCString() {
		return returnType.toCStringWithVariable("(*)(" + Arrays.asList(parameterTypes).stream().map(Type::toCString).collect(Collectors.joining(", ")) + ")");
	}
	
	@Override
	public String toCStringWithVariable(String var) {
		return returnType.toCStringWithVariable("(*" + var + ")(" + Arrays.asList(parameterTypes).stream().map(Type::toCString).collect(Collectors.joining(", ")) + ")");
	}
}
