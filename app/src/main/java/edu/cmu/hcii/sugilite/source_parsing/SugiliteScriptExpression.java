package edu.cmu.hcii.sugilite.source_parsing;

import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteLaunchAppOperation;
import edu.cmu.hcii.sugilite.model.variable.VariableHelper;
import edu.cmu.hcii.sugilite.model.variable.VariableValue;
import edu.cmu.hcii.sugilite.ontology.*;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import edu.cmu.hcii.sugilite.model.block.SugiliteBlock;
import edu.cmu.hcii.sugilite.model.block.booleanexp.SugiliteBooleanExpression;
import edu.cmu.hcii.sugilite.model.block.SugiliteConditionBlock;
import edu.cmu.hcii.sugilite.model.block.SugiliteStartingBlock;
import edu.cmu.hcii.sugilite.model.block.SugiliteOperationBlock;
import edu.cmu.hcii.sugilite.model.block.booleanexp.SugiliteBooleanExpressionNew;
import edu.cmu.hcii.sugilite.model.block.special_operation.SugiliteDelaySpecialOperationBlock;
import edu.cmu.hcii.sugilite.model.block.special_operation.SugiliteSubscriptSpecialOperationBlock;
import edu.cmu.hcii.sugilite.model.operation.SugiliteSpecialOperation;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteBinaryOperation;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteGetBoolExpOperation;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteGetOperation;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteGetProcedureOperation;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteGetValueOperation;
import edu.cmu.hcii.sugilite.model.operation.trinary.SugiliteLoadVariableOperation;
import edu.cmu.hcii.sugilite.model.operation.SugiliteOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteClickOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteLongClickOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteReadoutConstOperation;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteReadoutOperation;
import edu.cmu.hcii.sugilite.model.operation.binary.SugiliteSetTextOperation;
import edu.cmu.hcii.sugilite.model.operation.trinary.SugiliteTrinaryOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteResolveBoolExpOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteResolveProcedureOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteResolveValueQueryOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteSelectOperation;
import edu.cmu.hcii.sugilite.model.operation.unary.SugiliteUnaryOperation;
import edu.cmu.hcii.sugilite.model.value.SugiliteSimpleConstant;
import edu.cmu.hcii.sugilite.model.variable.Variable;
import edu.cmu.hcii.sugilite.ontology.description.OntologyDescriptionGenerator;
import edu.stanford.nlp.time.SUTime;

/**
 * @author toby
 * @date 3/18/18
 * @time 10:49 PM
 */

//represents a full standalone statement in the script
public class SugiliteScriptExpression<T> {
    private String operationName;
    //List<SugiliteScriptExpression> should always have size 1 unless used in ifBlock/elseBlock
    private List<List<SugiliteScriptExpression>> arguments = new ArrayList<>();

    //whether the expression is a constant
    private boolean isConstant;

    //value of the expression IF the expression is a constant
    private T constantValue;

    //the script content of the expression
    private String scriptContent;


    /**
     * construct a SugiliteScriptExpression from a SugiliteScriptNode
     *
     * @param node
     * @return
     */
    static public List<SugiliteScriptExpression> parse(SugiliteScriptNode node) {
        List<SugiliteScriptExpression> returnList = new ArrayList<>();
        if (node.getChildren().size() > 1 && node.getChildren().get(0).getValue() != null && node.getChildren().get(0).getValue().equals("call")) {
            //operation
            SugiliteScriptExpression<String> result = new SugiliteScriptExpression<>();
            result.setConstant(false);
            result.setOperationName(node.getChildren().get(1).getValue());
            //set the String content of the script
            result.setScriptContent(node.getScriptContent());
            for (int i = 2; i < node.getChildren().size(); i++) {
                //parse the args
                result.addArgument(parse(node.getChildren().get(i)));
            }
            returnList.add(result);
            return returnList;
        } else if (node.getChildren().size() == 2 && node.getChildren().get(0).getValue() != null && node.getChildren().get(0).getValue().equals("SUGILITE_START")) {
            //starting block
            SugiliteScriptExpression<String> result = new SugiliteScriptExpression<>();
            result.setConstant(false);
            result.setOperationName(node.getChildren().get(0).getValue());
            //set the String content of the script
            result.setScriptContent(node.getScriptContent());
            for (int i = 1; i < node.getChildren().size(); i++) {
                //parse the args
                result.addArgument(parse(node.getChildren().get(i)));
            }
            returnList.add(result);
            return returnList;
        } else if (node.getChildren().size() > 1) {
            //check if it's a typed constant?
            SugiliteScriptExpression result = null;

            if (node.getValue() != null) {
                //when the node already has a value --> simply use that value
                result = new SugiliteScriptExpression<SugiliteSimpleConstant<String>>();
                result.setConstantValue(new SugiliteSimpleConstant<>(node.getValue()));
            } else if (node.getChildren().size() == 2 && node.getChildren().get(0).getValue() != null && node.getChildren().get(1).getValue() != null && node.getChildren().get(0).getValue().equals("string")) {
                //string constant
                result = new SugiliteScriptExpression<SugiliteSimpleConstant<String>>();
                result.setConstantValue(new SugiliteSimpleConstant<>(node.getChildren().get(1).getValue()));
            } else if (node.getChildren().size() == 2 && node.getChildren().get(0).getValue() != null && node.getChildren().get(1).getValue() != null && node.getChildren().get(0).getValue().equals("time")) {
                //time constant
                result = new SugiliteScriptExpression<SugiliteSimpleConstant<String>>();
                String isoTimeString = node.getChildren().get(1).getValue();
                SUTime.Time timeSUTime = SUTime.parseDateTime(isoTimeString, true);
                Calendar cal = Calendar.getInstance();
                TimeZone tz = cal.getTimeZone();
                Date time = new Date();
                time.setTime(timeSUTime.getJodaTimeInstant().getMillis() - tz.getOffset(timeSUTime.getJodaTimeInstant().getMillis()));
                result.setConstantValue(new SugiliteSimpleConstant<Date>(time));
            }else if (node.getChildren().size() == 2 && node.getChildren().get(0).getValue() != null && node.getChildren().get(1).getValue() != null && node.getChildren().get(0).getValue().equals("number")) {
                //number constant without unit
                result = new SugiliteScriptExpression<SugiliteSimpleConstant<Number>>();
                result.setConstantValue(new SugiliteSimpleConstant<>(node.getChildren().get(1).getValue()));
            } else if (node.getChildren().size() == 3 && node.getChildren().get(0).getValue() != null && node.getChildren().get(1).getValue() != null && node.getChildren().get(2).getValue() != null && node.getChildren().get(0).getValue().equals("number")) {
                //number constant with unit
                result = new SugiliteScriptExpression<SugiliteSimpleConstant<Number>>();
                result.setConstantValue(new SugiliteSimpleConstant<String>(node.getChildren().get(1).getValue(), node.getChildren().get(2).getValue()));
            } else if (representMultipleSteps(node.getChildren())) {
                //multiple steps -> each child represents a single step;
                for (SugiliteScriptNode node1 : node.getChildren()) {
                    returnList.addAll(parse(node1));
                }
                return returnList;

            } else {
                //TODO: temp hack -- use the raw script content
                result = new SugiliteScriptExpression<SugiliteSimpleConstant<String>>();
                result.setConstantValue(new SugiliteSimpleConstant<>(node.getScriptContent()));
            }

            result.setScriptContent(node.getScriptContent());
            result.setConstant(true);
            returnList.add(result);
            return returnList;
        } else if (node.getChildren().size() == 1 && node.getChildren().get(0).getScriptContent().startsWith("(") && node.getChildren().get(0).getScriptContent().endsWith(")")) {
            //extra redundant parenthesis
            return parse(node.getChildren().get(0));
        } else {
            //is a simple constant
            SugiliteScriptExpression<SugiliteSimpleConstant<String>> result = new SugiliteScriptExpression<>();
            result.setConstant(true);
            if (node.getValue() != null) {
                result.setConstantValue(new SugiliteSimpleConstant<>(node.getValue()));
            } else if (node.getScriptContent() != null) {
                //TODO: temp hack
                result.setConstantValue(new SugiliteSimpleConstant<>(node.getScriptContent()));
            } else {
                throw new RuntimeException("empty node with no child");
            }
            result.setScriptContent(node.getScriptContent());
            returnList.add(result);
            return returnList;
        }

    }

    private static boolean representMultipleSteps(List<SugiliteScriptNode> nodes) {
        for (SugiliteScriptNode node : nodes) {
            if (!(node.getScriptContent().startsWith("(") && node.getScriptContent().endsWith(")"))) {
                return false;
            }
        }
        return true;
    }

    private static String stripQuote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        } else {
            return s;
        }
    }

    /**
     * create a constant SugiliteScriptExpression from a token in raw string
     *
     * @param token
     * @return
     */
    static public SugiliteScriptExpression parseConstant(String token) {
        //constant
        if (StringUtils.isNumeric(token)) {
            SugiliteScriptExpression<Double> result = new SugiliteScriptExpression<>();
            result.setConstant(true);
            result.setConstantValue(Double.valueOf(token));
            return result;
        } else {
            SugiliteScriptExpression<String> result = new SugiliteScriptExpression<>();
            result.setConstant(true);
            result.setConstantValue(token);
            return result;
        }
    }

    static public String addQuoteToTokenIfNeeded(String s) {
        if (s == null || s.length() == 0) {
            return "\"\"";
        }
        if (!((s.startsWith("(") && s.endsWith(")")) || (s.startsWith("\"") && s.endsWith("\"")))) {
            if (s.contains(" ")) {
                return "\"" + s + "\"";
            } else {
                return s;
            }
        }
        return s;
    }

    private static boolean isOperation(String token) {
        return true;
    }

    private void extractVariableFromQuery(OntologyQuery query, SugiliteStartingBlock startingBlock) {
        if (query instanceof LeafOntologyQuery) {
            LeafOntologyQuery loq = (LeafOntologyQuery)query;
            if (loq.getObjectSet() != null) {
                for (SugiliteEntity sugiliteEntity : loq.getObjectSet()) {
                    if (sugiliteEntity.getEntityValue() instanceof String) {
                        if (VariableHelper.isAVariable((String) sugiliteEntity.getEntityValue())) {
                            String variableName = VariableHelper.getVariableName((String) sugiliteEntity.getEntityValue());
                            if (!startingBlock.variableNameDefaultValueMap.containsKey(variableName)) {
                                //need to add to the variable map
                                startingBlock.variableNameDefaultValueMap.put(variableName, new VariableValue<>(variableName, ""));
                                startingBlock.variableNameVariableObjectMap.put(variableName, new Variable(Variable.USER_INPUT, variableName));
                            }
                        }
                    }
                }
            }
        }
        if (query instanceof CombinedOntologyQuery) {
            OntologyQueryWithSubQueries coq = (OntologyQueryWithSubQueries)query;
            if (coq.getSubQueries() != null) {
                for (OntologyQuery ontologyQuery : coq.getSubQueries()) {
                    if (ontologyQuery != null && startingBlock != null) {
                        extractVariableFromQuery(ontologyQuery, startingBlock);
                    }
                }
            }
        }
    }

    /**
     * convert an SugiliteScriptExpression to a SugiliteBlock
     *
     * @return
     */
    public SugiliteBlock toSugiliteBlock(SugiliteStartingBlock startingBlock, OntologyDescriptionGenerator descriptionGenerator) {
        if (operationName == null) {
            //not a valid operation
            return null;
        }

        if (SugiliteUnaryOperation.isUnaryOperation(operationName) && arguments.size() == 1) {
            //is a unary operation
            SugiliteUnaryOperation operation = null;
            SugiliteOperationBlock operationBlock = new SugiliteOperationBlock();
            String stringArg = arguments.get(0).get(0).getConstantValue().toString();
            if (arguments.get(0).get(0).getConstantValue() != null && arguments.get(0).get(0).getConstantValue() instanceof SugiliteSimpleConstant) {
                //TODO: handle other types of constants
                stringArg = ((SugiliteSimpleConstant) arguments.get(0).get(0).getConstantValue()).evaluate(null).toString();
            }
            switch (operationName) {
                case "click":
                    operation = new SugiliteClickOperation();
                    ((SugiliteClickOperation) operation).setQuery(OntologyQuery.deserialize(arguments.get(0).get(0).getScriptContent()));
                    break;
                case "long_click":
                    operation = new SugiliteLongClickOperation();
                    ((SugiliteLongClickOperation) operation).setQuery(OntologyQuery.deserialize(arguments.get(0).get(0).getScriptContent()));
                    break;
                case "select":
                    operation = new SugiliteSelectOperation();
                    ((SugiliteSelectOperation) operation).setQuery(OntologyQuery.deserialize(arguments.get(0).get(0).getScriptContent()));
                    break;
                case "readout_const":
                    operation = new SugiliteReadoutConstOperation();
                    ((SugiliteReadoutConstOperation) operation).setTextToReadout(stringArg);
                    break;
                case "resolve_boolExp":
                    operation = new SugiliteResolveBoolExpOperation();
                    ((SugiliteResolveBoolExpOperation) operation).setText(stringArg);
                    break;
                case "resolve_procedure":
                    operation = new SugiliteResolveProcedureOperation();
                    ((SugiliteResolveProcedureOperation) operation).setText(stringArg);
                    break;
                case "resolve_valueQuery":
                    operation = new SugiliteResolveValueQueryOperation();
                    ((SugiliteResolveValueQueryOperation) operation).setText(stringArg);
                    break;
                case "launch_app":
                    operation = new SugiliteLaunchAppOperation();
                    ((SugiliteLaunchAppOperation) operation).setAppPackageName(stringArg);
            }
            operationBlock.setOperation(operation);
            OntologyQuery query = OntologyQuery.deserialize(arguments.get(0).get(0).getScriptContent());

            if (startingBlock != null) {
                //extract variables from the query
                extractVariableFromQuery(query, startingBlock);
            }


            //set the description
            if (descriptionGenerator != null) {
//                operationBlock.setDescription(descriptionGenerator.getSpannedDescriptionForOperation(operation, operationBlock.getOperation().getDataDescriptionQueryIfAvailable()));
            } else {
                operationBlock.setDescription(operationBlock.toString());
            }
            if (operationBlock.getDescription() == null) {
                operationBlock.setDescription(operationBlock.toString());
            }
            return operationBlock;
        } else if (SugiliteBinaryOperation.isBinaryOperation(operationName) && (arguments.size() == 2 || operationName.equals("get"))) {
            //is a binary operation -> NOTE: "get" operaitons may have additional arguments
            SugiliteBinaryOperation operation = null;
            SugiliteOperationBlock operationBlock = new SugiliteOperationBlock();
            //full binary operation with a query included
            switch (operationName) {
                case "read_out":
                    operation = new SugiliteReadoutOperation();
                    String parameter0 = arguments.get(0).get(0).getConstantValue().toString();
                    if (arguments.get(0).get(0).getConstantValue() instanceof SugiliteSimpleConstant) {
                        parameter0 = stripQuote(((SugiliteSimpleConstant) arguments.get(0).get(0).getConstantValue()).evaluate(null).toString());
                    }
                    operation.setParameter0(parameter0);
                    ((SugiliteReadoutOperation) operation).setQuery(OntologyQuery.deserialize(arguments.get(1).get(0).getScriptContent()));
                    break;
                case "set_text":
                    operation = new SugiliteSetTextOperation();
                    String text = stripQuote(arguments.get(0).get(0).getConstantValue().toString());
                    if (arguments.get(0).get(0).getConstantValue() instanceof SugiliteSimpleConstant) {
                        text = stripQuote(((SugiliteSimpleConstant) arguments.get(0).get(0).getConstantValue()).evaluate(null).toString());
                    }
                    operation.setParameter0(text);
                    ((SugiliteSetTextOperation) operation).setQuery(OntologyQuery.deserialize(arguments.get(1).get(0).getScriptContent()));
                    if (VariableHelper.isAVariable(text)) {
                        //is a variable
                        String variableName = VariableHelper.getVariableName(text);
                        if (!startingBlock.variableNameDefaultValueMap.containsKey(variableName)) {
                            //need to add to the variable map
                            startingBlock.variableNameDefaultValueMap.put(variableName, new VariableValue<>(variableName, ""));
                            startingBlock.variableNameVariableObjectMap.put(variableName, new Variable(Variable.USER_INPUT, variableName));
                        }
                    }
                    break;
                case "get":
                    String parameter11 = arguments.get(1).get(0).getConstantValue().toString();
                    if (arguments.get(1).get(0).getConstantValue() instanceof SugiliteSimpleConstant) {
                        parameter11 = stripQuote(((SugiliteSimpleConstant) arguments.get(1).get(0).getConstantValue()).evaluate(null).toString());
                    }

                    if (parameter11.equals(SugiliteGetOperation.PROCEDURE_NAME)) {
                        operation = new SugiliteGetProcedureOperation();
                        List<List<SugiliteScriptExpression>> procedureParameterArguments = arguments.subList(2, arguments.size());
                        for (List<SugiliteScriptExpression> procedureParameterArgument : procedureParameterArguments) {
                            SugiliteScriptExpression expression = procedureParameterArgument.get(0);
                            if (expression != null && expression.getOperationName().equals("set_param")) {
                                List<List<SugiliteScriptExpression>> setParamArgs = expression.getArguments();
                                assert setParamArgs.size() == 2;
                                String paramName = ((SugiliteSimpleConstant<String>)setParamArgs.get(0).get(0).constantValue).evaluate(null);
                                String valueName = ((SugiliteSimpleConstant<String>)setParamArgs.get(1).get(0).constantValue).evaluate(null);
                                ((SugiliteGetProcedureOperation) operation).getVariableValues().add(
                                        new VariableValue<>(paramName, valueName));
                            } else {
                                throw new RuntimeException("Failed to parse the parameters in SugiliteGetProcedureOperation");
                            }
                        }

                    } else if (parameter11.equals(SugiliteGetOperation.BOOL_FUNCTION_NAME)) {
                        operation = new SugiliteGetBoolExpOperation();
                    } else if (parameter11.equals(SugiliteGetOperation.VALUE_QUERY_NAME)) {
                        operation = new SugiliteGetValueOperation();
                    } else {
                        throw new RuntimeException("Unknown get operation type");
                    }

                    String parameter00 = arguments.get(0).get(0).getConstantValue().toString();
                    if (arguments.get(0).get(0).getConstantValue() instanceof SugiliteSimpleConstant) {
                        parameter00 = stripQuote(((SugiliteSimpleConstant) arguments.get(0).get(0).getConstantValue()).evaluate(null).toString());
                    }

                    operation.setParameter0(parameter00);
                    operation.setParameter1(parameter11);

                    break;
            }
            operationBlock.setOperation(operation);

            //set the description
            if (descriptionGenerator != null && operationBlock.getOperation().getDataDescriptionQueryIfAvailable() != null) {
//                operationBlock.setDescription(descriptionGenerator.getSpannedDescriptionForOperation(operation, operationBlock.getOperation().getDataDescriptionQueryIfAvailable()));
            } else {
                operationBlock.setDescription(operationBlock.toString());
            }
            if (operationBlock.getDescription() == null) {
                operationBlock.setDescription(operationBlock.toString());
            }

            return operationBlock;
        } else if (SugiliteTrinaryOperation.isTrinaryOperation(operationName) && arguments.size() == 3) {
            //is a trinary operation
            SugiliteTrinaryOperation operation = null;
            SugiliteOperationBlock operationBlock = new SugiliteOperationBlock();
            switch (operationName) {
                case "load_as_variable":
                    operation = new SugiliteLoadVariableOperation();
                    String parameter0 = arguments.get(0).get(0).getConstantValue().toString();
                    String parameter1 = arguments.get(1).get(0).getConstantValue().toString();
                    if (arguments.get(0).get(0).getConstantValue() instanceof SugiliteSimpleConstant) {
                        parameter0 = stripQuote(((SugiliteSimpleConstant) arguments.get(0).get(0).getConstantValue()).evaluate(null).toString());
                    }
                    if (arguments.get(1).get(0).getConstantValue() instanceof SugiliteSimpleConstant) {
                        parameter1 = stripQuote(((SugiliteSimpleConstant) arguments.get(1).get(0).getConstantValue()).evaluate(null).toString());
                    }
                    operation.setParameter0(parameter0);
                    operation.setParameter1(parameter1);
                    //the query is the parameter 2
                    ((SugiliteLoadVariableOperation) operation).setQuery(OntologyQuery.deserialize(arguments.get(2).get(0).getScriptContent()));
                    //add the variable to the variable map in the starting block
                    startingBlock.variableNameVariableObjectMap.put(parameter0, new Variable(Variable.LOAD_RUNTIME, parameter0));
                    startingBlock.variableNameDefaultValueMap.put(parameter0, new VariableValue<>(parameter0, ""));
                    break;
            }
            operationBlock.setOperation(operation);

            //set the description
            if (descriptionGenerator != null) {
//                operationBlock.setDescription(descriptionGenerator.getSpannedDescriptionForOperation(operation, operationBlock.getOperation().getDataDescriptionQueryIfAvailable()));
            } else {
                operationBlock.setDescription(operationBlock.toString());
            }
            if (operationBlock.getDescription() == null) {
                operationBlock.setDescription(operationBlock.toString());
            }

            return operationBlock;
        } else if (operationName.contentEquals("SUGILITE_START") && arguments.size() == 1) {
            SugiliteStartingBlock newStartingBlock = new SugiliteStartingBlock();
            newStartingBlock.setScriptName(stripQuote(arguments.get(0).get(0).getConstantValue().toString()));
            return newStartingBlock;
        } else if (operationName.contentEquals("special_go_home") && arguments.size() == 0) {
            SugiliteOperationBlock operationBlock = new SugiliteOperationBlock();
            operationBlock.setOperation(new SugiliteSpecialOperation(SugiliteOperation.SPECIAL_GO_HOME));
            operationBlock.setDescription(operationBlock.toString());
            return operationBlock;
        } else if (operationName.contentEquals("run_script") && arguments.size() == 1) {
            SugiliteSubscriptSpecialOperationBlock operationBlock = new SugiliteSubscriptSpecialOperationBlock();
            operationBlock.setSubscriptName(stripQuote(arguments.get(0).get(0).getConstantValue().toString()));
            operationBlock.setDescription(operationBlock.toString());
            return operationBlock;
        } else if (operationName.contentEquals("delay") && arguments.size() == 1) {
            int delayTime = Double.valueOf(arguments.get(0).get(0).getConstantValue().toString()).intValue();
            SugiliteDelaySpecialOperationBlock operationBlock = new SugiliteDelaySpecialOperationBlock(delayTime);
            operationBlock.setDescription(operationBlock.toString());
            return operationBlock;
        } else if (operationName.contentEquals("if") && arguments.size() == 2) {
            SugiliteBlock ifBlock = arguments.get(1).get(0).toSugiliteBlock(startingBlock, descriptionGenerator);
            if (arguments.get(1).size() > 1) {
                SugiliteBlock previousBlock = ifBlock;
                for (int i = 1; i < arguments.get(1).size(); i++) {
                    SugiliteBlock currentBlock = arguments.get(1).get(i).toSugiliteBlock(startingBlock, descriptionGenerator);
                    currentBlock.setPreviousBlock(previousBlock);
                    previousBlock.setNextBlock(currentBlock);
                    previousBlock = currentBlock;
                }
            }

            SugiliteBooleanExpression booleanExpression = new SugiliteBooleanExpression(arguments.get(0).get(0));
            SugiliteBooleanExpressionNew booleanExpression2 = new SugiliteBooleanExpressionNew(arguments.get(0).get(0));

            SugiliteConditionBlock conditionBlock = new SugiliteConditionBlock(ifBlock, null, booleanExpression);
            //test purpose
            conditionBlock.setSugiliteBooleanExpressionNew(booleanExpression2);
            return conditionBlock;
        } else if (operationName.contentEquals("if") && arguments.size() == 3) {
            SugiliteBlock ifBlock = arguments.get(1).get(0).toSugiliteBlock(startingBlock, descriptionGenerator);
            if (arguments.get(1).size() > 1) {
                SugiliteBlock previousBlock = ifBlock;
                for (int i = 1; i < arguments.get(1).size(); i++) {
                    SugiliteBlock currentBlock = arguments.get(1).get(i).toSugiliteBlock(startingBlock, descriptionGenerator);
                    currentBlock.setPreviousBlock(previousBlock);
                    previousBlock.setNextBlock(currentBlock);
                    previousBlock = currentBlock;
                }
            }


            SugiliteBlock elseBlock = arguments.get(2).get(0).toSugiliteBlock(startingBlock, descriptionGenerator);
            if (arguments.get(2).size() > 1) {
                SugiliteBlock previousBlock = elseBlock;
                for (int i = 1; i < arguments.get(2).size(); i++) {
                    SugiliteBlock currentBlock = arguments.get(2).get(i).toSugiliteBlock(startingBlock, descriptionGenerator);
                    currentBlock.setPreviousBlock(previousBlock);
                    previousBlock.setNextBlock(currentBlock);
                    previousBlock = currentBlock;
                }
            }

            SugiliteBooleanExpression booleanExpression = new SugiliteBooleanExpression(arguments.get(0).get(0));
            SugiliteBooleanExpressionNew booleanExpression2 = new SugiliteBooleanExpressionNew(arguments.get(0).get(0));

            SugiliteConditionBlock conditionBlock = new SugiliteConditionBlock(ifBlock, elseBlock, booleanExpression);
            //test purpose
            conditionBlock.setSugiliteBooleanExpressionNew(booleanExpression2);
            return conditionBlock;
        }

        return null;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public List<List<SugiliteScriptExpression>> getArguments() {
        return arguments;
    }

    public void setArguments(List<List<SugiliteScriptExpression>> arguments) {
        this.arguments = arguments;
    }

    public void addArgument(List<SugiliteScriptExpression> argument) {
        this.arguments.add(argument);
    }

    public T getConstantValue() {
        return constantValue;
    }

    public void setConstantValue(T constantValue) {
        this.constantValue = constantValue;
    }

    public boolean isConstant() {
        return isConstant;
    }

    public void setConstant(boolean constant) {
        isConstant = constant;
    }

    public String getScriptContent() {
        return scriptContent;
    }

    public void setScriptContent(String scriptContent) {
        this.scriptContent = scriptContent;
    }
}
