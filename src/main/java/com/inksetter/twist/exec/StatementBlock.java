package com.inksetter.twist.exec;

import com.inksetter.twist.Script;
import com.inksetter.twist.TwistException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StatementBlock implements Script, Serializable {
    private final List<Statement> statements = new ArrayList<>();
    private static final long serialVersionUID = 6843629792467515246L;

    public List<Statement> getStatements() {
        return statements;
    }
    
    public void addStatement(Statement statement) {
        statements.add(statement);
    }

    @Override
    public Object execute(ScriptContext exec) throws TwistException {
        return execute(exec, false).getValue();
    }
    
    public StatementResult execute(ScriptContext exec, boolean newStack) throws TwistException {
        if (newStack) exec.pushStack(false);
        StatementResult lastValue = null;
        try {
            for (Statement statement : statements) {
                lastValue = statement.execute(exec);
                if (lastValue != null && lastValue.getType() == StatementResult.Type.RETURN) {
                    return lastValue;
                }
            }
        }
        finally {
            if (newStack) exec.popStack();
        }

        return lastValue == null ? StatementResult.valueResult(null) : lastValue;
    }

    // @see java.lang.Object#toString()
    @Override
    public String toString() {
        StringBuilder tmp = new StringBuilder("{");
        for (Iterator<Statement> i = statements.iterator(); i.hasNext();) {
            Statement stream = i.next();
            tmp.append(stream);
            if (i.hasNext()) tmp.append("; ");
        }
        tmp.append('}');
        return tmp.toString();
    }

}
