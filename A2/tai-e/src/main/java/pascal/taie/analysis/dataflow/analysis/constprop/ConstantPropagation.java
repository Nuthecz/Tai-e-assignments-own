/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.dataflow.analysis.constprop;

import pascal.taie.analysis.dataflow.analysis.AbstractDataflowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.util.AnalysisException;

public class ConstantPropagation extends
        AbstractDataflowAnalysis<Stmt, CPFact> {

    public static final String ID = "constprop";

    public ConstantPropagation(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public CPFact newBoundaryFact(CFG<Stmt> cfg) {
        /* TODO - finish me */
        // 初始化每一个OUT节点向量为NAC类型
        CPFact fact = new CPFact();
        // 获得 IR(每个被处理方法) 的参数, 我理解这里是边界初始化, 所以都是简单赋值, 而不是其他语句一样进行计算
        for (Var var : cfg.getIR().getParams()) {
            //判断是否是Int类型, 其余类型最终都可以归于Int类型
            if (canHoldInt(var)) {
                fact.update(var, Value.getNAC());
            }
        }
        return fact;
    }

    @Override
    public CPFact newInitialFact() {
        /* TODO - finish me */
        // 初始化边界节点向量, OUT[entry] = 空
        return new CPFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        /* TODO - finish me */
        // 利用格上的 meet 方法进行实现, 提供了不同分支传递同一个变量的赋值的交汇处理
        // 这里 keySet 就是 val(x) 中的 x, 这里就是对于x变量赋值的交汇
        for (Var var : fact.keySet()) {
            target.update(var, meetValue(fact.get(var), target.get(var)));
        }
    }

    /**
     * Meets two Values.
     */
    public Value meetValue(Value v1, Value v2) {
        /* TODO - finish me */
        // 基于给出的格的案例进行编写, 这里我的理解 meetValue 就是对于上面传下来的对同一个变量的赋值的交汇
        // 首先判断存在 NAC 的情况
        if (v1.isNAC() || v2.isNAC()) {
            return Value.getNAC();
        }
        // 除去 NAC, 最坏就是 Undef了, 如果都是 Undef, 那么返回的也是 Undef
        if (v1.isUndef() || v2.isUndef()) {
            return v1.isUndef() ? v2 : v1;
        }
        // 余下就都是 constant 的情况了
        if (v1.equals(v2)) {
            return Value.makeConstant(v1.getConstant());
        }
        // 两个常量不同的情况, 返回 NAC
        return Value.getNAC();
    }

    @Override
    public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
        /* TODO - finish me */
        // 这里实现的是对于 statement 的处理, 即 BB 内部的数据流的处理
        if (stmt instanceof DefinitionStmt<?, ?>) {
            LValue lv = ((DefinitionStmt<?, ?>) stmt).getLValue();
            RValue rv = ((DefinitionStmt<?, ?>) stmt).getRValue();
            // 判断左侧是否是变量, 特别判断是否是 Int 类型的变量(其它类型也包含在 Int 类型中)
            if (lv instanceof Var && canHoldInt((Var) lv)) {
                CPFact tmp = in.copy();
                // evaluate 用来处理右侧变量与这个 BB 的 IN 的交汇
                tmp.update((Var) lv, evaluate(rv, in));
                return out.copyFrom(tmp);
            }
        }
        return out.copyFrom(in);
    }

    /**
     * @return true if the given variable can hold integer value, otherwise false.
     */
    public static boolean canHoldInt(Var var) {
        Type type = var.getType();
        if (type instanceof PrimitiveType) {
            switch ((PrimitiveType) type) {
                case BYTE:
                case SHORT:
                case INT:
                case CHAR:
                case BOOLEAN:
                    return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the {@link Value} of given expression.
     *
     * @param exp the expression to be evaluated
     * @param in  IN fact of the statement
     * @return the resulting {@link Value}
     */
    public static Value evaluate(Exp exp, CPFact in) {
        /* TODO - finish me */
        // 这里是 transfer function 的右侧表达式的计算
        // 定义最终的返回值
        Value result = Value.getNAC();
        // 根据 Exp 接口子类的类型来进行处理
        if (exp instanceof IntLiteral) {
            return Value.makeConstant(((IntLiteral) exp).getValue());
        } else if (exp instanceof Var) {
            return in.get((Var) exp);
        } else if (exp instanceof InvokeExp){// 这里通过给的最后一个案例， 需要判断不是给定范围的情况
            return Value.getNAC();
        } else if (exp instanceof BinaryExp) {
            // 从二元表达式中获取两个操作数---> 获取 x
            Var op1 = ((BinaryExp) exp).getOperand1();
            Var op2 = ((BinaryExp) exp).getOperand2();
            // 从数据流中依据操作数在控制流上的定义来获取准确的操作数的值---> 获取 val(x)
            Value op1_val = in.get(op1);
            Value op2_val = in.get(op2);
            // 定义两个操作数之间的操作符
            BinaryExp.Op op = ((BinaryExp) exp).getOperator();
            // 根据操作数是否是常量进行划分
            if (op1_val.isConstant() && op2_val.isConstant()) {
                // 根据表达式的类型进行划分 -- f(y,z) = val(y) op val(z)
                if (exp instanceof ArithmeticExp) {
                    if (op == ArithmeticExp.Op.ADD) {
                        result = Value.makeConstant(op1_val.getConstant() + op2_val.getConstant());
                    } else if (op == ArithmeticExp.Op.SUB) {
                        result = Value.makeConstant(op1_val.getConstant() - op2_val.getConstant());
                    } else if (op == ArithmeticExp.Op.MUL) {
                        result = Value.makeConstant(op1_val.getConstant() * op2_val.getConstant());
                    } else if (op == ArithmeticExp.Op.DIV) {
                        result = op2_val.getConstant()==0 ? Value.getUndef(): Value.makeConstant(op1_val.getConstant() / op2_val.getConstant());
                    } else if (op == ArithmeticExp.Op.REM) {
                        result = op2_val.getConstant()==0 ? Value.getUndef(): Value.makeConstant(op1_val.getConstant() % op2_val.getConstant());
                    }
                } else if (exp instanceof BitwiseExp) {
                    if (op == BitwiseExp.Op.OR) {
                        result = Value.makeConstant(op1_val.getConstant() | op2_val.getConstant());
                    } else if (op == BitwiseExp.Op.AND) {
                        result = Value.makeConstant(op1_val.getConstant() & op2_val.getConstant());
                    } else if (op == BitwiseExp.Op.XOR) {
                        result = Value.makeConstant(op1_val.getConstant() ^ op2_val.getConstant());
                    }
                } else if (exp instanceof ShiftExp) {
                    if (op == ShiftExp.Op.SHL) {
                        result = Value.makeConstant(op1_val.getConstant() << op2_val.getConstant());
                    } else if (op == ShiftExp.Op.SHR) {
                        result = Value.makeConstant(op1_val.getConstant() >> op2_val.getConstant());
                    } else if (op == ShiftExp.Op.USHR) {
                        result = Value.makeConstant(op1_val.getConstant() >>> op2_val.getConstant());
                    }
                } else if (exp instanceof ConditionExp) {
                    if (op == ConditionExp.Op.EQ) {
                        result = Value.makeConstant(op1_val.getConstant() == op2_val.getConstant() ? 1 : 0);
                    } else if (op == ConditionExp.Op.NE) {
                        result = Value.makeConstant(op1_val.getConstant() != op2_val.getConstant() ? 1 : 0);
                    } else if (op == ConditionExp.Op.GE) {
                        result = Value.makeConstant(op1_val.getConstant() >= op2_val.getConstant() ? 1 : 0);
                    } else if (op == ConditionExp.Op.LE) {
                        result = Value.makeConstant(op1_val.getConstant() <= op2_val.getConstant() ? 1 : 0);
                    } else if (op == ConditionExp.Op.GT) {
                        result = Value.makeConstant(op1_val.getConstant() > op2_val.getConstant() ? 1 : 0);
                    } else if (op == ConditionExp.Op.LT) {
                        result = Value.makeConstant(op1_val.getConstant() < op2_val.getConstant() ? 1 : 0);
                    }
                }
            }// f(y,z) = NAC
            else if (op1_val.isNAC() || op2_val.isNAC()) {
                if (exp instanceof ArithmeticExp && (op == ArithmeticExp.Op.DIV || op == ArithmeticExp.Op.REM)) {
                    result = (op2_val.isConstant() && op2_val.getConstant() == 0) ? Value.getUndef(): Value.getNAC();
                }
            }// f(y,z) = UNDEF
            else {
                result = Value.getUndef();
            }
        }
        return result;
    }
}
