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

package pascal.taie.analysis.pta.cs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.PointerAnalysisResultImpl;
import pascal.taie.analysis.pta.core.cs.CSCallGraph;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.ArrayIndex;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.analysis.pta.core.cs.element.CSObj;
import pascal.taie.analysis.pta.core.cs.element.CSVar;
import pascal.taie.analysis.pta.core.cs.element.InstanceField;
import pascal.taie.analysis.pta.core.cs.element.MapBasedCSManager;
import pascal.taie.analysis.pta.core.cs.element.Pointer;
import pascal.taie.analysis.pta.core.cs.element.StaticField;
import pascal.taie.analysis.pta.core.cs.selector.ContextSelector;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.analysis.pta.pts.PointsToSetFactory;
import pascal.taie.config.AnalysisOptions;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.JField;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;

class Solver {

    private static final Logger logger = LogManager.getLogger(Solver.class);

    private final AnalysisOptions options;

    private final HeapModel heapModel;

    private final ContextSelector contextSelector;

    private CSManager csManager;

    private CSCallGraph callGraph;

    private PointerFlowGraph pointerFlowGraph;

    private WorkList workList;

    private PointerAnalysisResult result;

    Solver(AnalysisOptions options, HeapModel heapModel,
           ContextSelector contextSelector) {
        this.options = options;
        this.heapModel = heapModel;
        this.contextSelector = contextSelector;
    }

    void solve() {
        initialize();
        analyze();
    }

    private void initialize() {
        csManager = new MapBasedCSManager();
        callGraph = new CSCallGraph(csManager);
        pointerFlowGraph = new PointerFlowGraph();
        workList = new WorkList();
        // process program entry, i.e., main method
        Context defContext = contextSelector.getEmptyContext();
        JMethod main = World.get().getMainMethod();
        CSMethod csMethod = csManager.getCSMethod(defContext, main);
        callGraph.addEntryMethod(csMethod);
        addReachable(csMethod);
    }

    /**
     * Processes new reachable context-sensitive method.
     */
    private void addReachable(CSMethod csMethod) {
        /* TODO - finish me */
        if (callGraph.addReachableMethod(csMethod)){
            csMethod.getMethod().getIR().forEach(stmt -> {
                StmtProcessor stmtprocess = new StmtProcessor(csMethod);
                stmt.accept(stmtprocess);
            });
        }
    }

    /**
     * Processes the statements in context-sensitive new reachable methods.
     */
    private class StmtProcessor implements StmtVisitor<Void> {

        private final CSMethod csMethod;

        private final Context context;

        private StmtProcessor(CSMethod csMethod) {
            this.csMethod = csMethod;
            this.context = csMethod.getContext();
        }

        // TODO - if you choose to implement addReachable()
        //  via visitor pattern, then finish me
        // 1. New x = new T() -> add <c: x, {c: o_i}> to Wl
        @Override
        public Void visit(New stmt){
            // 1.1 get the c: x
            Pointer pointer = csManager.getCSVar(context, stmt.getLValue());
            // 1.2 get the c : o_i
            Obj newT = heapModel.getObj(stmt); // get o_i
            // These two c's are different, the c of c: o_i is k-1 -> 堆上下文不一定就使用方法的上下文,需要用 ContextSelector选择
            Context heapContext = contextSelector.selectHeapContext(csMethod, newT);
            // get c: o_i due to the heap context
            CSObj csObj = csManager.getCSObj(heapContext, newT);
            PointsToSet pointsToSet = PointsToSetFactory.make(csObj);
            workList.addEntry(pointer, pointsToSet);
            return StmtVisitor.super.visit(stmt);
        }
        // 2. Copy x = y
        @Override
        public Void visit(Copy stmt) {
            Pointer target = csManager.getCSVar(context, stmt.getLValue());
            Pointer source = csManager.getCSVar(context, stmt.getRValue());
            addPFGEdge(source, target);
            return StmtVisitor.super.visit(stmt);
        }
        // 3. x = y.f
        @Override
        public Void visit(LoadField stmt) {
            if(!stmt.isStatic()) return null;
            Var x = stmt.getLValue();
            JField f = stmt.getFieldRef().resolve();
            addPFGEdge(csManager.getStaticField(f),csManager.getCSVar(context, x));
            return StmtVisitor.super.visit(stmt);
        }
        // 4. x.f = y
        @Override
        public Void visit(StoreField stmt) {
            if (!stmt.isStatic()) return null;
            JField f = stmt.getFieldRef().resolve();
            Var y = stmt.getRValue();
            addPFGEdge(csManager.getCSVar(context,y), csManager.getStaticField(f));
            return StmtVisitor.super.visit(stmt);
        }
        // 5. y = x.m(...) 静态方法处理
        @Override
        public Void visit(Invoke stmt) {
            if (!stmt.isStatic()) return null;
            MethodRef methodRef = stmt.getMethodRef();
            JMethod method = methodRef.getDeclaringClass().getDeclaredMethod(methodRef.getSubsignature());
            CSCallSite csCallSite = csManager.getCSCallSite(context, stmt);
            Context c_t = contextSelector.selectContext(csCallSite, method);
            CSMethod ctMethod = csManager.getCSMethod(c_t, method);// 这里需要与csMethod区分，csMethod指向的是当前的c
            processEachCall(context, stmt, c_t, ctMethod);
            return StmtVisitor.super.visit(stmt);
        }
    }

    /**
     * Adds an edge "source -> target" to the PFG.
     */
    private void addPFGEdge(Pointer source, Pointer target) {
        /* TODO - finish me */
        // 1. if s -> t not belong PFG and add it
        if (pointerFlowGraph.addEdge(source, target)){
            // 2. if pt(s) is not empty, add <t, pt(s)> to Wl
            if (!source.getPointsToSet().isEmpty()) {
                workList.addEntry(target, source.getPointsToSet());
            }
        }
    }

    /**
     * Processes work-list entries until the work-list is empty.
     */
    private void analyze() {
        /* TODO - finish me */
        while (!workList.isEmpty()){
            // 1. remove <n,pts> from WL
            WorkList.Entry entry = workList.pollEntry();
            Pointer n = entry.pointer();
            PointsToSet pts = entry.pointsToSet();
            // 2. get the Δ
            PointsToSet delta = propagate(n, pts);
            // 3. if n represents a variable x then
            if (n instanceof CSVar csVar){
                // 3.1 foreach oi in Δ do
                Var x = csVar.getVar();
                Context c = csVar.getContext();
                if (delta.isEmpty()) continue;
                delta.getObjects().forEach(csObj -> {
                    // 3.1.1 foreach y = x.f in S do
                    x.getLoadFields().forEach(loadField -> {
                        Var y = loadField.getLValue();
                        JField f = loadField.getFieldRef().resolve();
                        addPFGEdge(csManager.getInstanceField(csObj, f), csManager.getCSVar(c, y));
                    });
                    // 3.1.2 foreach x.f = y in S do
                    x.getStoreFields().forEach(storeField -> {
                        JField f = storeField.getFieldRef().resolve();
                        Var y = storeField.getRValue();
                        addPFGEdge(csManager.getCSVar(c, y), csManager.getInstanceField(csObj, f));
                    });
                    // 3.1.3 foreach y = x[*] in S do
                    x.getLoadArrays().forEach(loadArray -> {
                        Var y = loadArray.getLValue();
                        addPFGEdge(csManager.getArrayIndex(csObj), csManager.getCSVar(c, y));
                    });
                    // 3.1.4 foreach x[*] = y in S do
                    x.getStoreArrays().forEach(storeArray -> {
                        Var y = storeArray.getRValue();
                        addPFGEdge(csManager.getCSVar(c, y), csManager.getArrayIndex(csObj));
                    });
                    // 3.1.5 foreach y = x.m(...) in S do
                    processCall(csManager.getCSVar(c, x), csObj);
                });
            }
        }
    }

    /**
     * Propagates pointsToSet to pt(pointer) and its PFG successors,
     * returns the difference set of pointsToSet and pt(pointer).
     */
    private PointsToSet propagate(Pointer pointer, PointsToSet pointsToSet) {
        /* TODO - finish me */
        // 1. pts is not empty
        PointsToSet delta = PointsToSetFactory.make();
        if (pointsToSet.isEmpty()) return delta;
        // 2. Δ = pts - pt(n) and pt(n) U= pts
        pointsToSet.forEach(csObj -> {
            if (pointer.getPointsToSet().addObject(csObj)){// Statement has been added with addObject
                delta.addObject(csObj);
//                if you use pointer.getPointsToSet().contains(csObj) to check whether the object is in the set,
//                you need add the following statement `pointer.getPointsToSet().addObject(csObj);`
            }
        });
        // 3. foreach m in succ(n) do
        if (delta.isEmpty()) return delta; // prevent point to empty set(delta)
        pointerFlowGraph.getSuccsOf(pointer).forEach(suc -> {
            workList.addEntry(suc, delta);
        });
        return delta;
    }
    private void processEachCall(Context c, Invoke x, Context c_t, CSMethod ctMethod){
        // 1. get the c: l and c_t: m
        CSCallSite callSite = csManager.getCSCallSite(c, x);
        // 2. if c: l -> c_t: m is not in CG then
        if (callGraph.addEdge(new Edge<>(CallGraphs.getCallKind(x), callSite, ctMethod))){
            // 3. add reachable(c_t: m)
            addReachable(ctMethod);
            // 4. foreach c: ai = c_t: pi ∈ S do
            for (int i = 0; i < x.getInvokeExp().getArgCount(); i ++){
                Var arg = x.getInvokeExp().getArg(i);
                Var param = ctMethod.getMethod().getIR().getParam(i);
                addPFGEdge(csManager.getCSVar(c, arg), csManager.getCSVar(c_t, param));
            }
            // 5. add the edge (c_t: m_ret, c: r) to PFG
            if (x.getLValue() != null){
                ctMethod.getMethod().getIR().getReturnVars().forEach(m_ret -> {
                    addPFGEdge(csManager.getCSVar(c_t, m_ret), csManager.getCSVar(c, x.getLValue()));
                });
            }
        }
    }
    /**
     * Processes instance calls when points-to set of the receiver variable changes.
     *
     * @param recv    the receiver variable
     * @param recvObj set of new discovered objects pointed by the variable.
     */
    private void processCall(CSVar recv, CSObj recvObj) {
        /* TODO - finish me */
        // processCall -> processCall(c: x, c_': o_i)
        // 1. foreach l: r = x.k(a1,…,an) ∈ S do
        Var var = recv.getVar();
        var.getInvokes().forEach(x -> {
            // 2. m = Dispatch(o_i, k)
            JMethod m = resolveCallee(recvObj, x);
            Var m_this = m.getIR().getThis();
            // 3. c_t = Select(c, l, c_':o_i)
            Context c = recv.getContext();
            CSCallSite csCallSite = csManager.getCSCallSite(c, x);
            Context c_t = contextSelector.selectContext(csCallSite, recvObj, m);
            // 3. add <c_t: m_this, {c_': o_i}> to WL
            workList.addEntry(csManager.getCSVar(c_t, m_this), PointsToSetFactory.make(recvObj));
            // 4. get the c_t: m and process the call
            CSMethod ctMethod = csManager.getCSMethod(c_t, m);
            processEachCall(c, x, c_t, ctMethod);
        });

    }

    /**
     * Resolves the callee of a call site with the receiver object.
     *
     * @param recv the receiver object of the method call. If the callSite
     *             is static, this parameter is ignored (i.e., can be null).
     * @param callSite the call site to be resolved.
     * @return the resolved callee.
     */
    private JMethod resolveCallee(CSObj recv, Invoke callSite) {
        Type type = recv != null ? recv.getObject().getType() : null;
        return CallGraphs.resolveCallee(type, callSite);
    }

    PointerAnalysisResult getResult() {
        if (result == null) {
            result = new PointerAnalysisResultImpl(csManager, callGraph);
        }
        return result;
    }
}
