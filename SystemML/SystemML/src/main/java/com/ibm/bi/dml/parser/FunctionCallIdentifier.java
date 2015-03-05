/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2015
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.IOException;


public class FunctionCallIdentifier extends DataIdentifier 
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2015\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	private ArrayList<ParameterExpression> _paramExprs;
	private FunctCallOp _opcode;	// stores whether internal or external
	private String _namespace;		// namespace of the function being called (null if current namespace is to be used)

	/**
	 * setFunctionName: sets the function namespace (if specified) and name
	 * @param functionName the (optional) namespace information and name of function.  If both namespace and name are specified, they are concatinated with "::"
	 * @throws ParseException 
	 */
	public void setFunctionName(String functionName) throws ParseException{
		_name = functionName;
	}
	
	public void setFunctionNamespace(String passed) throws ParseException{
		_namespace 	= passed;
	}
	
	public String getNamespace(){
		return _namespace;
	}
	
	public ArrayList<ParameterExpression> getParamExprs(){
		return _paramExprs;
	}
	
	public Expression rewriteExpression(String prefix) throws LanguageException {
			
		ArrayList<ParameterExpression> newParameterExpressions = new ArrayList<ParameterExpression>();
		for (ParameterExpression paramExpr : _paramExprs)
			newParameterExpressions.add(new ParameterExpression(paramExpr.getName(), paramExpr.getExpr().rewriteExpression(prefix)));
		
		// rewrite each output expression
		FunctionCallIdentifier fci = new FunctionCallIdentifier(newParameterExpressions);
		
		fci.setBeginLine(this.getBeginLine());
		fci.setBeginColumn(this.getBeginColumn());
		fci.setEndLine(this.getEndLine());
		fci.setEndColumn(this.getEndColumn());
			
		fci._name = this._name;
		fci._namespace = this._namespace;
		fci._opcode = this._opcode;
		fci._kind = Kind.FunctionCallOp;	 
		
		return fci;
	}
	
	
	
	public FunctionCallIdentifier(){}
	
	public FunctionCallIdentifier(ArrayList<ParameterExpression> paramExpressions) {
		
		_paramExprs = paramExpressions;
		_opcode = null;
		_kind = Kind.FunctionCallOp;	 
	}
	
	
	
	public FunctCallOp getOpCode() {
		return _opcode;
	}
	
	/**
	 * Validate parse tree : Process ExtBuiltinFunction Expression is an
	 * assignment statement
	 * 
	 * NOTE: this does not override the normal validateExpression because it needs to pass dmlp!
	 * 
	 * @throws LanguageException
	 */
	public void validateExpression(DMLProgram dmlp, HashMap<String, DataIdentifier> ids, HashMap<String, ConstIdentifier> constVars, boolean conditional) 
		throws LanguageException, IOException
	{
		
		// check the namespace exists, and that function is defined in the namespace
		if (dmlp.getNamespaces().get(_namespace) == null){
			raiseValidateError("namespace " + _namespace + " is not defined ", conditional);
		}
		FunctionStatementBlock fblock = dmlp.getFunctionStatementBlock(_namespace, _name);
		if (fblock == null){
			raiseValidateError("function " + _name + " is undefined in namespace " + _namespace, conditional);
		}
		// set opcode (whether internal or external function) -- based on whether FunctionStatement
		// in FunctionStatementBlock is ExternalFunctionStatement or FunctionStatement
		if (fblock.getStatement(0) instanceof ExternalFunctionStatement)
			_opcode = Expression.FunctCallOp.EXTERNAL;
		else
			_opcode = Expression.FunctCallOp.INTERNAL;
		
		// force all parameters to be either unnammed or named for functions
		boolean hasNamed = false, hasUnnamed = false;
		for (ParameterExpression paramExpr : _paramExprs){
			if (paramExpr.getName() == null)
				hasUnnamed = true;
			else
				hasNamed = true;
		}
		
		if (hasNamed && hasUnnamed){
			raiseValidateError(" In DML, functions can only have named parameters " +
					"(e.g., name1=value1, name2=value2) or unnamed parameters (e.g, value1, value2). " + 
					_name + " has both parameter types.", conditional);
		}
		// validate expressions for each passed parameter
		for (ParameterExpression paramExpr : _paramExprs) {
			
			if (paramExpr.getExpr() instanceof FunctionCallIdentifier){
				raiseValidateError("UDF function call not supported as parameter to function call", false);
			}
			
			paramExpr.getExpr().validateExpression(ids, constVars, conditional);
		}
	
		FunctionStatement fstmt = (FunctionStatement)fblock.getStatement(0);
		
		// TODO: DRB: FIX THIS
		// check correctness of number of arguments and their types 
		if (fstmt.getInputParams().size() < _paramExprs.size()){ 
			raiseValidateError("function " + _name 
					+ " has incorrect number of parameters. Function requires " 
					+ fstmt.getInputParams().size() + " but was called with " + _paramExprs.size(), conditional);
		}
		
		/*
		// check the types of the input to see they match OR has default values
		for (int i = 0; i < fstmt.getInputParams().size(); i++) {
					
			if (i >= _paramExprs.size()){
				// check a default value is provided for this variable
				if (fstmt.getInputParams().get(i).getDefaultValue() == null){
					LOG.error(this.printErrorLocation() + "parameter " + fstmt.getInputParams().get(i) + " must have default value");
					throw new LanguageException(this.printErrorLocation() + "parameter " + fstmt.getInputParams().get(i) + " must have default value");
				}
			}
			
			else {
				Expression param = fstmt.getInputParams().get(i);
				boolean sameDataType = param.getOutput().getDataType().equals(fstmt.getInputParams().get(i).getDataType());
				if (!sameDataType){
					LOG.error(this.printErrorLocation() + "parameter " + param.toString() + " does not have correct dataType");
					throw new LanguageException(this.printErrorLocation() + "parameter " + param.toString() + " does not have correct dataType");
				}
				boolean sameValueType = param.getOutput().getValueType().equals(fstmt.getInputParams().get(i).getValueType());
				if (!sameValueType){
					LOG.error(this.printErrorLocation() + "parameter " + param.toString() + " does not have correct valueType");
					throw new LanguageException(this.printErrorLocation() + "parameter " + param.toString() + " does not have correct valueType");
				}
			}
		}
		*/
		
		
		// set the outputs for the function
		_outputs = new Identifier[fstmt.getOutputParams().size()];
		for(int i=0; i < fstmt.getOutputParams().size(); i++) {
			_outputs[i] = new DataIdentifier(fstmt.getOutputParams().get(i));
		}
		
		return;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (_namespace != null && _namespace.length() > 0 && !_namespace.equals(".defaultNS")) 
			sb.append(_namespace + "::"); 
		sb.append(_name);
		sb.append(" ( ");		
				
		for (int i = 0; i < _paramExprs.size(); i++){
			sb.append(_paramExprs.get(i).toString());
			if (i<_paramExprs.size() - 1) 
				sb.append(",");
		}
		sb.append(" )");
		return sb.toString();
	}

	@Override
	public VariableSet variablesRead() {
		VariableSet result = new VariableSet();
		for (int i = 0; i < _paramExprs.size(); i++)
			result.addVariables(_paramExprs.get(i).getExpr().variablesRead());
		return result;
	}

	@Override
	public VariableSet variablesUpdated() {
		VariableSet result = new VariableSet();
		for (int i=0; i< _outputs.length; i++)
			result.addVariable( ((DataIdentifier)_outputs[i]).getName(), (DataIdentifier)_outputs[i] );
		return result;
	}

	@Override
	public boolean multipleReturns() {
		return true;
	}
}

