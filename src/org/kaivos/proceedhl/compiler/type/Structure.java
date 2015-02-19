package org.kaivos.proceedhl.compiler.type;

import java.util.List;

public class Structure {
	private String name;
	private TypeName[] fields;
	
	public Structure(String name, TypeName[] fields) {
		super();
		this.name = name;
		this.fields = fields;
	}
	
	public Structure(String name, List<TypeName> fields) {
		this(name, fields.toArray(new TypeName[fields.size()]));
	}
	
	public String getName() {
		return name;
	}
	
	public TypeName[] getFields() {
		return fields;
	}
}
