/**
 * Copyright (c) 2020 devolo AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.jpa.rsql;

import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.OrNode;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;
import org.eclipse.hawkbit.repository.jpa.TargetFieldData;

import java.util.List;

/**
 *  Implementation of {@link RSQLVisitor}
 */
public class RsqlVisitor implements RSQLVisitor<Boolean, TargetFieldData> {

    @Override
    public Boolean visit(AndNode node, TargetFieldData fieldData) {
        for(Node child: node.getChildren()){
            if(!child.accept(this, fieldData)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Boolean visit(OrNode node, TargetFieldData fieldData) {
        for(Node child: node.getChildren()){
            if(child.accept(this, fieldData)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Boolean visit(ComparisonNode node, TargetFieldData fieldData) {
        String key = node.getSelector();
        List<String> values = node.getArguments();
        ComparisonOperator operator = node.getOperator();

        return fieldData.request(key, operator.getSymbol(), values);
    }
}
