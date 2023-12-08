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

package pascal.taie.analysis.dataflow.inter;

import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.icfg.CallEdge;
import pascal.taie.analysis.graph.icfg.CallToReturnEdge;
import pascal.taie.analysis.graph.icfg.NormalEdge;
import pascal.taie.analysis.graph.icfg.ReturnEdge;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;

import java.util.List;

/**
 * Implementation of interprocedural constant propagation for int values.
 */
public class InterConstantPropagation extends
        AbstractInterDataflowAnalysis<JMethod, Stmt, CPFact> {

    public static final String ID = "inter-constprop";

    private final ConstantPropagation cp;

    public InterConstantPropagation(AnalysisConfig config) {
        super(config);
        cp = new ConstantPropagation(new AnalysisConfig(ConstantPropagation.ID));
    }

    @Override
    public boolean isForward() {
        return cp.isForward();
    }

    @Override
    public CPFact newBoundaryFact(Stmt boundary) {
        IR ir = icfg.getContainingMethodOf(boundary).getIR();
        return cp.newBoundaryFact(ir.getResult(CFGBuilder.ID));
    }

    @Override
    public CPFact newInitialFact() {
        return cp.newInitialFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        cp.meetInto(fact, target);
    }

    @Override
    protected boolean transferCallNode(Stmt stmt, CPFact in, CPFact out) {
        /* TODO - finish me */
        return out.copyFrom(in);
    }

    @Override
    protected boolean transferNonCallNode(Stmt stmt, CPFact in, CPFact out) {
        /* TODO - finish me */
        // 过程间非调用点，使用常量分析的transferNode
        return cp.transferNode(stmt, in, out);
    }

    @Override
    protected CPFact transferNormalEdge(NormalEdge<Stmt> edge, CPFact out) {
        /* TODO - finish me */
        // NormalEdge与过程间调用无关，经过transferNode后不发生改变
        return out;
    }

    @Override
    protected CPFact transferCallToReturnEdge(CallToReturnEdge<Stmt> edge, CPFact out) {
        // TODO - finish me
        // edge.getSource()返回的是这条边的源节点，即调用点
        Stmt callStmt = edge.getSource();
        // 如果调用点是方法调用语句(Invoke类型)
        if(callStmt instanceof Invoke callSite){
            Var var =  callSite.getLValue();
            // 删除掉调用点的左值，之后返回
            if(var != null){
                CPFact tmp = out.copy();
                tmp.remove(var);
                return tmp;
            }
        }
        // 调用点不是方法调用语句，直接等同于transferNormalEdge
        return out;
    }

    @Override
    protected CPFact transferCallEdge(CallEdge<Stmt> edge, CPFact callSiteOut) {
        /* TODO - finish me */
        // 这里如果不是方法调用语句，直接返回空值，即不起作用
        CPFact tmp = new CPFact();
        // 查边的源节点是否是一个方法调用语句
        if(edge.getSource() instanceof Invoke callSite){
            // 获取调用点的参数列表
            List<Var> args = callSite.getInvokeExp().getArgs();
            for(int i = 0; i < args.size(); i++){
                // 获取被调用方法的参数列表
                Var param = edge.getCallee().getIR().getParam(i);
                // 更新tmp中的参数信息
                tmp.update(param, callSiteOut.get(args.get(i)));
            }
        }
        return tmp;
    }

    @Override
    protected CPFact transferReturnEdge(ReturnEdge<Stmt> edge, CPFact returnOut) {
        // TODO - finish me
        // 这里如果不是方法调用语句，直接返回空值，即不起作用
        CPFact tmp = new CPFact();
        if(edge.getCallSite() instanceof Invoke callSite){
            // 获取调用点的左值
            Var var = callSite.getLValue();
            // 如果左值不为空，就将返回值加入到tmp中
            if(var != null){
                Value retval = Value.getUndef();
                // 获取被调用函数的返回值
                for(Var retvar: edge.getReturnVars()){
                    // 使用过程内的meetValue函数进行合并
                    retval = cp.meetValue(retval, returnOut.get(retvar));
                }
                tmp.update(var, retval);
            }
        }
        return tmp;
    }
}
