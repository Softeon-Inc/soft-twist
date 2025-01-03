package com.inksetter.twist.exec;

import com.inksetter.twist.Expression;
import com.inksetter.twist.TwistException;

public class ExpressionStatement implements Statement {
    private final Expression expression;

    public ExpressionStatement(Expression expression) {
        this.expression = expression;
    }

    @Override
    public StatementResult execute(ScriptContext exec) throws TwistException {
        return StatementResult.valueResult(expression.evaluate(exec));
    }

    public Expression getExpression() {
        return expression;
    }
}
