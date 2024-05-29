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

import com.fasterxml.jackson.databind.ser.std.ObjectArraySerializer;
import jas.CP;
import pascal.taie.World;
import pascal.taie.analysis.dataflow.analysis.constprop.CPFact;
import pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation;
import pascal.taie.analysis.dataflow.analysis.constprop.Value;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.graph.icfg.CallEdge;
import pascal.taie.analysis.graph.icfg.CallToReturnEdge;
import pascal.taie.analysis.graph.icfg.NormalEdge;
import pascal.taie.analysis.graph.icfg.ReturnEdge;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.core.cs.element.InstanceField;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.classes.JField;

import java.util.*;

import static pascal.taie.analysis.dataflow.analysis.constprop.ConstantPropagation.canHoldInt;

/**
 * Implementation of interprocedural constant propagation for int values.
 */
public class InterConstantPropagation extends
        AbstractInterDataflowAnalysis<JMethod, Stmt, CPFact> {

    public static final String ID = "inter-constprop";

    private final ConstantPropagation cp;

    // 用于保存每个变量的别名，base -> (var1, vat2...)
    private Map<Var, HashSet<Var>> allVarAliases;
    // 用于保存静态的 Store 和 Load
    private Map<JField, HashSet<StoreField>> staticFieldStore;
    private Map<JField, HashSet<LoadField>> staticFieldLoad;

    public InterConstantPropagation(AnalysisConfig config) {
        super(config);
        cp = new ConstantPropagation(new AnalysisConfig(ConstantPropagation.ID));
    }

    @Override
    protected void initialize() {
        String ptaId = getOptions().getString("pta");
        PointerAnalysisResult pta = World.get().getResult(ptaId);
        // You can do initialization work here
        // 这里存储每个变量的别名信息，base -> (var1, vat2...)
        allVarAliases = new HashMap<>();
        for (Var base : pta.getVars()) {
            for (Var otherVar : pta.getVars()) {  // 遍历所有的 var，查看 PointsToSet 存在交集的情况设置别名
                Set<Obj> basePointsToSet = pta.getPointsToSet(base);
                if (basePointsToSet.isEmpty()) continue;
                basePointsToSet.forEach(baseObj -> {
                    // 对于 basePointsToSet，若是其中的 baseObj 被 otherVar 的 PointsToSet 所包含
                    // 即二者 PointsToSet存在交集，则认为 otherVar 为 base 的一个别名
                    if (pta.getPointsToSet(otherVar).contains(baseObj)) {
                        HashSet<Var> baseAliases = allVarAliases.getOrDefault(base, new HashSet<>());
                        baseAliases.add(otherVar);
                        allVarAliases.put(base, baseAliases);
                    }
                });
            }
        }

        // 这里存储静态字段的别名信息，由于它只存在 T.f 一种
        staticFieldStore = new HashMap<>();
        staticFieldLoad = new HashMap<>();
        icfg.forEach(stmt -> {
            // 这里静态字段的 jField 都是相同的，所以这里遍历 icfg 中的相同的静态调用作为别名
            if (stmt instanceof StoreField storeField && storeField.isStatic()) {
                JField jField = storeField.getFieldRef().resolve();
                HashSet<StoreField> storeFields = staticFieldStore.getOrDefault(jField, new HashSet<>());
                storeFields.add(storeField);
                staticFieldStore.put(jField, storeFields);
            }
            if (stmt instanceof LoadField loadField && loadField.isStatic()) {
                JField jField = loadField.getFieldRef().resolve();
                HashSet<LoadField> loadFields = staticFieldLoad.getOrDefault(jField, new HashSet<>());
                loadFields.add(loadField);
                staticFieldLoad.put(jField, loadFields);
            }
        });
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
        // TODO - finish me
        return out.copyFrom(in);
    }

    private boolean isArrayIndexAlias(Value indexI, Value indexJ) {
        boolean isAlias = false;
        if ((indexI.isConstant() && indexJ.isConstant()) && indexI.getConstant() == indexJ.getConstant()) {
            isAlias = true;
        } else if (indexI.isNAC() && (indexJ.isNAC() || indexJ.isConstant())) {
            isAlias = true;
        } else if (indexJ.isNAC() && (indexI.isNAC() || indexI.isConstant())) {
            isAlias = true;
        }
        return isAlias;
    }

    @Override
    protected boolean transferNonCallNode(Stmt stmt, CPFact in, CPFact out) {
        // TODO - finish me
        CPFact copyIn = in.copy();
        if (stmt instanceof AssignStmt<?, ?> assignStmt) {
            LValue lValue = assignStmt.getLValue();
            DataflowResult<Stmt, CPFact> dataflowResult = solver.getResult();
            // 这里设置为 Undef，可以在 meetValue 中直接赋值为第二个 Value 的值
            Value resValue = Value.getUndef();
            // 不是需要处理的语句，直接跳过
            if (!(lValue instanceof Var lVar && ConstantPropagation.canHoldInt(lVar))) {
                return out.copyFrom(copyIn);
            }
            if (stmt instanceof LoadField loadField) {
                CPFact gen = new CPFact();
                // 处理 loadField 语句，把对应的 store 的 rhs 用 meet 计算出来，赋给 load 的 lhs
                JField jField = loadField.getFieldRef().resolve();
                if (loadField.isStatic()) {   // 静态字段的load，直接在 staticFieldStore 获取别名然后进行 meetValue 操作
                    HashSet<StoreField> storeFields = staticFieldStore.getOrDefault(jField, new HashSet<>());
                    for (StoreField storeField : storeFields) {
                        resValue = cp.meetValue(resValue, dataflowResult.getOutFact(storeField).get(storeField.getRValue()));
                    }
                } else {   // 实例字段的load，则是根据对象 base 来在 allVarAliases 寻找别名，将对该对象的 store 操作进行 meetValue
                    Var base = ((InstanceFieldAccess) loadField.getFieldAccess()).getBase();
                    for (Var var : allVarAliases.getOrDefault(base, new HashSet<>())) {
                        for (StoreField storeField : var.getStoreFields()) {
                            if (jField == storeField.getFieldRef().resolve()) {
                                resValue = cp.meetValue(resValue, dataflowResult.getOutFact(storeField).get(storeField.getRValue()));
                            }
                        }
                    }
                }
                // 对 copyIn 进行更新，以便下面与 out 比较判断是否改动
                copyIn.update(lVar, resValue);
                return out.copyFrom(copyIn);
            } else if (stmt instanceof StoreField storeField) {
                // 判断 y.f 的值是否有变化，有变化就把对应的 load 加入到 WorkList 中，其余按照过程内常量传播处理
                boolean isChange = cp.transferNode(stmt, in, out);
                if (isChange) {
                    if (storeField.isStatic()) {
                        JField jField = storeField.getFieldRef().resolve();
                        // 提取 staticFieldLoad 中 jField 的别名的 loadField 操作，然后加入 solver 的 WorkList
                        HashSet<LoadField> loadFields = staticFieldLoad.getOrDefault(jField, new HashSet<>());
                        loadFields.forEach(loadField -> {
                            solver.addWorkList(loadField);
                        });
                    } else {
                        Var base = ((InstanceFieldAccess) storeField.getFieldAccess()).getBase();
                        for (Var var : allVarAliases.getOrDefault(base, new HashSet<>())) {
                            var.getLoadFields().forEach(loadField -> {
                                solver.addWorkList(loadField);
                            });
                        }
                    }
                }
            } else if (stmt instanceof LoadArray loadArray) {
                // load 的操作一样，寻找别名的 store 操作，然后把 store 的 rhs meet 计算，赋值给 load 的 lhs
                CPFact gen = new CPFact();
                Var base = loadArray.getArrayAccess().getBase();
                Value loadIndexValue = in.get(loadArray.getArrayAccess().getIndex());
                for (Var var : allVarAliases.getOrDefault(base, new HashSet<>())) {
                    for (StoreArray storeArray : var.getStoreArrays()) {
                        Value storeIndexValue = dataflowResult.getInFact(storeArray).get(storeArray.getArrayAccess().getIndex());
                        if (isArrayIndexAlias(loadIndexValue, storeIndexValue)) {
                            resValue = cp.meetValue(resValue, dataflowResult.getOutFact(storeArray).get(storeArray.getRValue()));
                        }
                    }
                }
                copyIn.update(lVar, resValue);
                return out.copyFrom(copyIn);
            } else if (stmt instanceof StoreArray storeArray) {
                boolean isChange = cp.transferNode(stmt, in, out);
                if (isChange) {
                    Var base = storeArray.getArrayAccess().getBase();
                    for (Var var : allVarAliases.getOrDefault(base, new HashSet<>())) {
                        var.getLoadArrays().forEach(loadArray -> {
                            solver.addWorkList(loadArray);
                        });
                    }
                }
            }
        }
        // 不涉及别名的语句，或者涉及的为 store 语句，采用过程内常量分析
        return cp.transferNode(stmt, in, out);
    }

    @Override
    protected CPFact transferNormalEdge(NormalEdge<Stmt> edge, CPFact out) {
        // TODO - finish me
        return out;
    }

    @Override
    protected CPFact transferCallToReturnEdge(CallToReturnEdge<Stmt> edge, CPFact out) {
        // TODO - finish me
        // edge.getSource()返回的是这条边的源节点，即调用点
        Stmt callStmt = edge.getSource();
        // 如果调用点是方法调用语句(Invoke类型)
        if (callStmt instanceof Invoke callSite) {
            Var var = callSite.getLValue();
            // 删除掉调用点的左值，之后返回
            if (var != null) {
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
        // TODO - finish me
        // 这里如果不是方法调用语句，直接返回空值，即不起作用
        CPFact tmp = new CPFact();
        // 查边的源节点是否是一个方法调用语句
        if (edge.getSource() instanceof Invoke callSite) {
            // 获取调用点的参数列表
            List<Var> args = callSite.getInvokeExp().getArgs();
            for (int i = 0; i < args.size(); i++) {
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
        if (edge.getCallSite() instanceof Invoke callSite) {
            // 获取调用点的左值
            Var var = callSite.getLValue();
            // 如果左值不为空，就将返回值加入到tmp中
            if (var != null) {
                Value retval = Value.getUndef();
                // 获取被调用函数的返回值
                for (Var retvar : edge.getReturnVars()) {//获取返回变量的名称，例如y
                    // 使用过程内的meetValue函数进行合并
                    retval = cp.meetValue(retval, returnOut.get(retvar));
                    tmp.update(var, retval);
                }
            }
        }
        return tmp;
    }
}
