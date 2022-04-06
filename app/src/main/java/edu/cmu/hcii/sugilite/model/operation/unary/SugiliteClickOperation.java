package edu.cmu.hcii.sugilite.model.operation.unary;

import edu.cmu.hcii.sugilite.ontology.OntologyQuery;

import static edu.cmu.hcii.sugilite.source_parsing.SugiliteScriptExpression.addQuoteToTokenIfNeeded;

/**
 * @author toby
 * @date 11/13/18
 * @time 11:46 PM
 */
public class SugiliteClickOperation extends SugiliteUnaryOperation<OntologyQuery> {
    private OntologyQuery targetUIElementDataDescriptionQuery;
    private OntologyQuery alternativeTargetUIElementDataDescriptionQuery;
    private OntologyQuery alternativeTargetUIElementDataDescriptionQuery2;
    public SugiliteClickOperation(){
        super();
        this.setOperationType(CLICK);
    }

    public void setQuery(OntologyQuery targetUIElementDataDescriptionQuery) {
        setParameter0(targetUIElementDataDescriptionQuery);
    }

    public void setAlternativeTargetUIElementDataDescriptionQuery(OntologyQuery alternativeTargetUIElementDataDescriptionQuery) {
        this.alternativeTargetUIElementDataDescriptionQuery = alternativeTargetUIElementDataDescriptionQuery;
    }

    public OntologyQuery getAlternativeTargetUIElementDataDescriptionQuery() {
        return alternativeTargetUIElementDataDescriptionQuery;
    }

    @Override
    public OntologyQuery getParameter0() {
        return targetUIElementDataDescriptionQuery;
    }

    @Override
    public void setParameter0(OntologyQuery value) {
        this.targetUIElementDataDescriptionQuery = value;
    }

    @Override
    public boolean containsDataDescriptionQuery() {
        return true;
    }

    @Override
    public OntologyQuery getDataDescriptionQueryIfAvailable() {
        return targetUIElementDataDescriptionQuery;
    }

    @Override
    public String toString() {
        return "(" + "call click " + addQuoteToTokenIfNeeded(getParameter0().toString()) +addQuoteToTokenIfNeeded(getAlternativeTargetUIElementDataDescriptionQuery().toString())+ addQuoteToTokenIfNeeded(getAlternativeTargetUIElementDataDescriptionQuery2().toString())+")";
    }

    @Override
    public String getPumiceUserReadableDecription() {
        return String.format("click on %s", targetUIElementDataDescriptionQuery);
    }

    public OntologyQuery getAlternativeTargetUIElementDataDescriptionQuery2() {
        if(alternativeTargetUIElementDataDescriptionQuery2!=null) {
            return alternativeTargetUIElementDataDescriptionQuery2;
        }
        return alternativeTargetUIElementDataDescriptionQuery;
    }

    public void setAlternativeTargetUIElementDataDescriptionQuery2(OntologyQuery alternativeTargetUIElementDataDescriptionQuery2) {
        this.alternativeTargetUIElementDataDescriptionQuery2 = alternativeTargetUIElementDataDescriptionQuery2;
    }
}
