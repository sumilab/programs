/* ---------------------------------------------------------------------
%   Copyright (C) 2007 Association for the COINS Compiler Infrastructure
%       (Read COPYING for detailed information.)
--------------------------------------------------------------------- */
/*
the case where a query reaches uneffective traces of propagated queries is problem.
*/


package coins.ssa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

//import java.util.Enumeration;
//import java.util.Hashtable;
//import java.util.Stack;
//import java.util.Vector;

import coins.backend.Data;
import coins.backend.Function;
import coins.backend.LocalTransformer;
import coins.backend.Op;
import coins.backend.ana.DFST;
import coins.backend.ana.Dominators;
import coins.backend.cfg.BasicBlk;
import coins.backend.lir.LirLabelRef;
import coins.backend.lir.LirNode;
import coins.backend.lir.LirSymRef;
import coins.backend.sym.Label;
import coins.backend.sym.Symbol;
import coins.backend.util.BiLink;
import coins.backend.util.ImList;


public class LDPRE implements LocalTransformer {
    public boolean doIt(Data data, ImList args) { return true; }
    public String name() { return "LDPRE"; }
    public String subject() {
	return "Optimizatin with efficient question propagation.";
    }
    private String tmpSymName="_ldpre";
    
    public static final int THR=SsaEnvironment.OptThr;
    /** The threshold of debug print **/
    public static final int THR2=SsaEnvironment.AllThr;
    private SsaEnvironment env;
    private SsaSymTab sstab;
    private Util util;
    private Function f;
    // Copy Propagation
    private DDCPYP cpy;
    private DFST dfst;
    public Dominators dom;
    Lattice lattice = new Lattice();
    HashMap<LirNode, boolean[]> visitedPREQPExp;
    HashMap<LirNode, boolean[]> preqp_Answer;
    HashMap<LirNode, int[]> visitedExp;
    HashMap<LirNode, int[]> dfvisitedExp;
    HashMap<LirNode, boolean[]> dfPREQPvisitedExp;
    boolean[] insertPhiBlk;
    LirNode[] sameExpVars;
    LirNode[] sameExpVarFlow;
    int[] insertedBlk;
    ArrayList<LirNode> insertedNewNodes;
    HashMap<Integer, HashSet<LirNode>> kills;
    HashMap<Integer, HashMap<LirNode,LirNode>> exps;
    HashMap<Integer, HashMap<LirNode,LirNode>> mems;
    HashMap<Integer, HashMap<LirNode,HashMap<Label,LirNode>>> pcc;
    HashSet<Integer> stores;
    
    boolean printRemoving = false;
    
    /**
     * Constructor
     * @param e The environment of the SSA module
     * @param tab The symbol tabel of the SSA module
     * @param function The current function
     * @param m The current mode
     **/
    public LDPRE(SsaEnvironment env, SsaSymTab sstab) {
		this.env = env;
		this.sstab = sstab;
	}
   
    void collectLocalInfo() {
        for(BiLink pp=f.flowGraph().basicBlkList.first();!pp.atEnd();pp=pp.next()){
            BasicBlk v=(BasicBlk)pp.elem();
            
            HashMap<LirNode,HashMap<Label,LirNode>> var2pmap = new HashMap<LirNode,HashMap<Label,LirNode>>();
            pcc.put(v.id, var2pmap);
            
            HashSet<LirNode> blkKills = new HashSet<LirNode>();
            kills.put(v.id, blkKills);
            
            HashMap<LirNode,LirNode> blkExps = new HashMap<LirNode,LirNode>();
            exps.put(v.id, blkExps);

            HashMap<LirNode,LirNode> blkMems = new HashMap<LirNode,LirNode>();
            mems.put(v.id, blkMems);
            
            for(BiLink p=v.instrList().first();!p.atEnd();p=p.next()){
                LirNode node=(LirNode)p.elem();

                // Collecting PCC 
                if(node.opCode==Op.PHI) {
                    LirNode pdst = node.kid(0);
                    
                    HashMap<Label,LirNode> plabel2var = new HashMap<Label,LirNode>();
                    for(int pos=1;pos<node.nKids();pos++) {
                        Label l = ((LirLabelRef)node.kid(pos).kid(1)).label;
                        LirNode psrc = node.kid(pos).kid(0);
                        plabel2var.put(l, psrc);
                    }
                    var2pmap.put(pdst, plabel2var);
                }
                
                
                
                // Collecting kill
                if(node.opCode == Op.SET) {
                    if(node.kid(0).opCode == Op.MEM) {
                        stores.add(v.id);
                        blkMems = new HashMap<LirNode,LirNode>();
                        blkMems.put(node.kid(0), node.kid(1));
                        mems.put(v.id, blkMems);
                    }
                    else {
                        blkKills.add(node.kid(0));
                    }
                }
                
                
                if(node.opCode == Op.CALL){
                    stores.add(v.id);
                    if(node.kid(2).nKids() > 0) {
                        blkKills.add(node.kid(2).kid(0));
                    }
                    blkMems = new HashMap<LirNode,LirNode>();
                    mems.put(v.id, blkMems);
                }
                
                
                
                // Collecting exps and removing local redundancy
                if(node.opCode == Op.SET && node.kid(0).opCode != Op.MEM) {
                    LirNode exp = node.kid(1).makeCopy(env.lir);
                    if(exp.opCode == Op.MEM) {
                        blkMems.put(exp.makeCopy(env.lir),node.kid(0));
                    }
                    else if(exp.nKids() > 0){
                        if(exp.opCode==Op.ADD || exp.opCode==Op.MUL) {
                            LirNode tmp = exp.makeCopy(env.lir);
                            tmp.setKid(0, exp.kid(1).makeCopy(env.lir));
                            tmp.setKid(1, exp.kid(0).makeCopy(env.lir));
                            blkExps.put(tmp.makeCopy(env.lir),node.kid(0).makeCopy(env.lir));
                            blkExps.put(exp.makeCopy(env.lir),node.kid(0).makeCopy(env.lir));
                        }
                        else {
                            blkExps.put(exp.makeCopy(env.lir),node.kid(0).makeCopy(env.lir));
                        }
//                        blkExps.put(exp.makeCopy(env.lir),node.kid(0).makeCopy(env.lir));
                    }
                }
                    
            }
        }
    }
    
    
    boolean isKillBlk(BasicBlk blk, LirNode node) {
    	HashSet<LirNode> blkKills = kills.get(blk.id);
    	if(blkKills.contains(node.kid(1))) {
			return true;
		}
    	
    	if(node.kid(1).opCode == Op.MEM && stores.contains(blk.id)) {
    		return true;
    	}
    	
    	if(node.kid(1).nKids() == 1) {
    		if(blkKills.contains(node.kid(1).kid(0))) {
    			return true;
    		}
    	}
    	else {
    		for(int i=0;i<node.kid(1).nKids();i++) {
    			if(blkKills.contains(node.kid(1).kid(i))) {
        			return true;
        		}
    		}
//    		System.out.println("");
//    		System.out.println(node.kid(1).kid(0)+":"+blkKills.contains(node.kid(1).kid(0)));
//    		System.out.println(node.kid(1).kid(1)+":"+blkKills.contains(node.kid(1).kid(1)));
//    		if(blkKills.contains(node.kid(1).kid(0)) || blkKills.contains(node.kid(1).kid(1))) {
//    			return true;
//    		}
    	}
		
		return false;
    }
    
    
    LirNode createNewStatement(LirNode statement) {
    	LirNode newSt = statement.makeCopy(env.lir);
    	//System.out.println("Checking newSt");
    	//System.out.println(newSt);
    	Symbol dstSym = sstab.newSsaSymbol(tmpSymName, newSt.type);
    	LirNode nn = env.lir.symRef(Op.REG, dstSym.type, dstSym, ImList.Empty);
    	LirNode newNode = env.lir.operator(Op.SET, newSt.type, nn, newSt.kid(1),ImList.Empty).makeCopy(env.lir);
    	//System.out.println("new node:"+newNode);
    	insertedNewNodes.add(newNode);
    	return newNode;
    }
    
    LirNode updateSSAExp(LirNode node, BasicBlk v, BasicBlk p, boolean backward) {
    	LirNode newNode = node.makeCopy(env.lir);
    	for(int i=0;i<node.kid(1).nKids();i++) {
    		LirNode newT = getPCCVar(v, p, node.kid(1).kid(i), backward);
    		newNode.kid(1).setKid(i, newT.makeCopy(env.lir));
    	}
    	return newNode;
    }
    
    LirNode getPCCVar(BasicBlk blk, BasicBlk pred, LirNode var, boolean backward) {
    	HashMap<LirNode,HashMap<Label,LirNode>> var2pmap = pcc.get(blk.id);
    	if(backward) {
    		if(!var2pmap.containsKey(var)) return var;
        	HashMap<Label,LirNode> plabel2var = var2pmap.get(var);
        	return plabel2var.get(pred.label());
    	}
    	else {
        	for(Map.Entry<LirNode,HashMap<Label,LirNode>> entry : var2pmap.entrySet()){
        		if(entry.getValue().containsValue(var)) {
        			return entry.getKey();
        		}
    		}
        	
    		return var;
    	}
    }
    
    LirNode insertPhi(LirNode node, BasicBlk blk, HashMap<Integer, LirNode> phiArgMap) {
    	HashMap<LirNode,HashMap<Label,LirNode>> var2pmap = pcc.get(blk.id);
    	HashMap<Label,LirNode> plabel2var = new HashMap<Label,LirNode>();
    	
    	LirNode phi = newPhi(node, blk, tmpSymName);
//    	sameExpVars[blk.id] = phi.kid(0);
    	LirNode pdst = phi.kid(0);
    	LirNode args = null;
    	boolean allSame = true;
    	
    	for(int j=1;j<phi.nKids();j++){
			BasicBlk pred = (((LirLabelRef) phi.kid(j).kid(1)).label).basicBlk();
			LirNode predVar = phiArgMap.get(pred.id);
			
			if(predVar == null){
				BasicBlk dblk = pred;
				predVar = phi.kid(0);
				while(dblk != null){
					if(sameExpVars[dblk.id] != null) {
						predVar = sameExpVars[dblk.id];
						break;
					}
					else if(sameExpVarFlow[dblk.id] != null) {
						predVar = sameExpVarFlow[dblk.id];
						break;
					}
					dblk = dom.immDominator(dblk);
				}
				
			}
			
			if(args==null) {
				args = predVar;
			}
			else if(!args.equals(predVar)) {
				allSame = false;
			}
			
			phi.kid(j).setKid(0, predVar);
			
			// recording PCC info
			Label l = ((LirLabelRef)phi.kid(j).kid(1)).label;
    		plabel2var.put(l, predVar);
		}
    	
    	if(allSame) return null;
//    	printBBs();
//    	System.out.println("new phi:"+phi);
    	phi = phi.makeCopy(env.lir);
    	BiLink pl = blk.instrList().first();
		pl.addBefore(phi);
    	
		var2pmap.put(pdst, plabel2var);
		insertPhiBlk[blk.id] = true;
		sameExpVars[blk.id] = phi.kid(0);
    	return phi;
    }
    
    public LirNode newPhi(LirNode expr, BasicBlk blk, String tmpSymName){
		Symbol sym = sstab.newSsaSymbol(tmpSymName, expr.type);
		return util.makePhiInst(sym, blk);
	}
    
    void insert(LirNode node, BasicBlk blk, ArrayList<BasicBlk> trues, ArrayList<BasicBlk> falses, ArrayList<BasicBlk> tops, HashMap<BasicBlk, LirNode> p2n, HashMap<Integer, LirNode> phiArgMap) {
    	for(int i=0;i<falses.size();i++) {
    		BasicBlk p = falses.get(i);
    		LirNode newNode = createNewStatement(p2n.get(p));
    		BiLink pl = p.instrList().last();
    		pl.addBefore(newNode);
    		sameExpVars[p.id] = newNode.kid(0);
    		HashMap<LirNode,LirNode> blkExps = exps.get(p.id);
    		blkExps.put(newNode.kid(1), newNode.kid(0));
    		
    		phiArgMap.put(p.id, sameExpVars[p.id]);
    	}
    	
    	for(int i=0;i<tops.size();i++) {
    		BasicBlk p = tops.get(i);
    		LirNode newNode = createNewStatement(p2n.get(p));
    		BiLink pl = p.instrList().last();
    		pl.addBefore(newNode);
    		sameExpVars[p.id] = newNode.kid(0);
    		HashMap<LirNode,LirNode> blkExps = exps.get(p.id);
    		blkExps.put(newNode.kid(1), newNode.kid(0));
    		
    		phiArgMap.put(p.id, sameExpVars[p.id]);
    	}
    	
		if(blk.predList().length() > 1) {
//			printBBs();
//			System.out.println("phiArgMap:"+phiArgMap+", @"+blk.label());
			insertPhi(node, blk, phiArgMap);
		}
    }
    
    int NAvail(LirNode node, BasicBlk v) {
//    	System.out.println("NAvail @ " + v.label() + ". node:" + node + ". pred:"+v.predList().length());

    	int[] visited = getVisited(node.kid(1));
    	if(v.predList().length()==0) {
    		visited[v.id] = lattice.False;
    		return visited[v.id];
    	}
    	
//    	HashMap<Integer,LirNode> p2n = new HashMap<Integer,LirNode>();
    	
    	int answers = 0;
    	ArrayList<BasicBlk> trues = new ArrayList<BasicBlk>();
    	ArrayList<BasicBlk> falses = new ArrayList<BasicBlk>();
    	ArrayList<BasicBlk> tops = new ArrayList<BasicBlk>();
    	HashMap<BasicBlk, LirNode> p2n = new HashMap<BasicBlk,LirNode>();
    	HashMap<Integer, LirNode> phiArgMap = new HashMap<Integer,LirNode>();
    	
    	for(BiLink pp=v.predList().first();!pp.atEnd();pp=pp.next()) {
    		BasicBlk p = (BasicBlk)pp.elem();
    		
    		// prepare for query propagation
    		LirNode newNode = updateSSAExp(node, v, p, true);
    		p2n.put(p, newNode);
    		
    		// query propagation
    		int pans = XAvail(newNode, p);
    		LirNode predSameExpVar = sameExpVars[p.id];
    		sameExpVarFlow[v.id] = sameExpVars[p.id];
    		if(sameExpVarFlow[v.id] == null) sameExpVarFlow[v.id] = sameExpVarFlow[p.id];
    		if(predSameExpVar != null) {
    			phiArgMap.put(p.id, predSameExpVar.makeCopy(env.lir));
    		}
    		else if(sameExpVarFlow[v.id] != null) {
    			phiArgMap.put(p.id, sameExpVarFlow[v.id].makeCopy(env.lir));
    		}
    		
    		// recording answers
    		if(pans == lattice.True) trues.add(p);
        	if(pans == lattice.False) falses.add(p);
        	if(pans == lattice.Top) {
//        		if(dom.dominates(v, p)) {
//        			if(trues.size() > 0) {
//        				trues.add(p);
//        			}
//        			else if(falses.size() > 0) {
//        				falses.add(p);
//        			}
//        		}
//        		else {
//        			tops.add(p);
//        		}
        		tops.add(p);
        	}
        	
//        	System.out.println("");
//    		System.out.println("v:"+v.label());
//    		System.out.println("pred:"+p.label());
//    		System.out.println("newNode:"+newNode);
//    		System.out.println("pred answer:"+pans);
//    		System.out.println("phiArgMap:"+phiArgMap);
//    		System.out.println("sameExpVarFlow[v.id]:"+sameExpVarFlow[v.id]);
//    		System.out.println("predSameExpVar:"+predSameExpVar);
//    		System.out.println("before current answer:"+answers);
        	
    		if(answers == 0) {
        		answers = pans;
        	}
        	else {
//        		answers = lattice.l_or(answers, pans);
        		answers = lattice.l_and(answers, pans);
        	}
    		
//    		System.out.println("after current answer:"+answers+"@"+v.label());
    	}
    	
    	// Insert
    	if(trues.size() > 0) {
//    		if(falses.size() > 0 || tops.size() > 0) {
    		if(falses.size() > 0 || tops.size() > 0 && !allTopDomied(v,tops)) {
    			boolean dsafe = true;
        		for(int i=0;i<falses.size();i++) {
        			BasicBlk p = falses.get(i);
        			LirNode newNode = p2n.get(p);
//        			System.out.println("");
//        			System.out.println("Check DSafe..:" + p.label());
//        			System.out.println("Result of DSafe "+XDSafe(p,newNode));
//        			if(XDSafe(p,newNode) != lattice.True) {
//        				dsafe = false;
//        				break;
//        			}
//        			if(XDSafe(p,newNode) != lattice.True) {
        			if(!preqp_XDSafe(p,newNode)) {
        				dsafe = false;
        				break;
        			}
        		}
        		
        		for(int i=0;i<tops.size();i++) {
        			BasicBlk p = tops.get(i);
        			LirNode newNode = p2n.get(p);
//        			System.out.println("");
//        			System.out.println("Check DSafe..:" + p.label());
//        			System.out.println("Result of DSafe "+XDSafe(p,newNode));
//        			if(XDSafe(p,newNode) != lattice.True) {
//        				dsafe = false;
//        				break;
//        			}
//        			if(XDSafe(p,newNode) != lattice.True) {
        			if(!preqp_XDSafe(p,newNode)) {
        				dsafe = false;
        				break;
        			}
        		}
        		
        		if(dsafe) {
//        			System.out.println("");
//        			System.out.println("");
//        			System.out.println("* node *");
//        			System.out.println("phiArgMap:"+phiArgMap);
//        			System.out.println("trues:"+trues);
//        			System.out.println("falses:"+falses);
//        			System.out.println("tops:"+tops);
        			insert(node, v, trues, falses, tops, p2n, phiArgMap);
            		answers = lattice.True;
        		}
        		else {
        			answers = lattice.False;
        		}
    		}
    		else {
    			answers = lattice.True;
    			if(trues.size() > 1 && trues.size() == v.predList().length()) {
//        			System.out.println("");
//        			System.out.println("");
//        			System.out.println("*** node ***");
//        			System.out.println("phiArgMap:"+phiArgMap);
//        			System.out.println("trues:"+trues);
//        			System.out.println("falses:"+falses);
//        			System.out.println("tops:"+tops);
    	    		insertPhi(node, v, phiArgMap);
//    	    		answers = lattice.True;
    	    	}
    		}
    	}
//    	else if(falses.size() > 0 && tops.size() > 0) {
//    		answers = lattice.False;
//    	}
//    	if(trues.size() > 0 && falses.size() > 0) {
//    		boolean dsafe = true;
//    		for(int i=0;i<falses.size();i++) {
//    			BasicBlk p = falses.get(i);
//    			LirNode newNode = p2n.get(p);
////    			System.out.println("");
////    			System.out.println("Check DSafe..:" + p.label());
////    			System.out.println("Result of DSafe "+XDSafe(p,newNode));
////    			if(XDSafe(p,newNode) != lattice.True) {
////    				dsafe = false;
////    				break;
////    			}
//    			if(!preqp_XDSafe(p,newNode)) {
//    				dsafe = false;
//    				break;
//    			}
//    		}
//    		
//    		if(dsafe) {
//    			insert(node, v, trues, falses, tops, p2n, phiArgMap);
//        		answers = lattice.True;
//    		}
//    		else {
//    			answers = lattice.False;
//    		}
//    	}
//    	else if(trues.size() > 1 && trues.size() == v.predList().length() && answers == lattice.True) {
//    		insertPhi(node, v, phiArgMap);
//    	}
//    	else if(trues.size() > 0 && trues.size() == v.predList().length()) {
//    		answers = lattice.True;
//    	}
//    	else if(falses.size() > 0 && falses.size() == v.predList().length()) {
//    		answers = lattice.False;
//    	}
//    	else if(tops.size() > 0 && tops.size() == v.predList().length()) {
//    		answers = lattice.Top;
//    	}
//    		if(trues.size() > 0 && falses.size() > 0) {
//        		insert(node, v, trues, falses, tops, p2n, phiArgMap);
//        		answers = lattice.True;
//        	}
//        	else if(trues.size() > 1 && trues.size() == v.predList().length() && answers == lattice.True) {
//        		insertPhi(node, v, phiArgMap);
//        	}
//    	if(answers == 0) {
//    		answers = lattice.False;
//    	}
    	
    	visited[v.id] = answers;
    	
//    	System.out.println("result@"+v.label()+". "+visited[v.id]);
    	
 	   	return visited[v.id];
    }
    
    boolean allTopDomied(BasicBlk v, ArrayList<BasicBlk> tops) {
    	for(int i=0;i<tops.size();i++) {
    		BasicBlk p = tops.get(i);
    		if(!dom.dominates(v, p)) {
    			return false;
    		}
    	}
    	return true;
    }
    
    int XAvail(LirNode node, BasicBlk v) {
    	int[] visited = getVisited(node.kid(1));
//    	System.out.println("XAvail @ " + v.label() + ". node:" + node + ". visited:" + visited[v.id]);
    	
    	if(visited[v.id] == lattice.Bot) {
    		visited[v.id] = lattice.Top;
    		return visited[v.id];
//			return lattice.Top;
    	}
		else if(visited[v.id] == lattice.True || visited[v.id] == lattice.False || visited[v.id] == lattice.Top) {
			return visited[v.id];
		}
    	
    	visited[v.id] = lattice.Bot;
    	
    	LirNode sameExpVar = checkLocalRedundant(node.kid(1), v);
    	if(sameExpVar != null) {
    		sameExpVars[v.id] = sameExpVar;
    		visited[v.id] = lattice.True;
    		return lattice.True;
    	}
    	
    	if(isKillBlk(v, node)) {
    		visited[v.id] = lattice.False;
    		return lattice.False;
    	}
    	
    	visited[v.id] = NAvail(node, v);
    	return visited[v.id];
// 	   	return NAvail(node, v);
    }
    
    int[] getVisited(LirNode exp) {
    	if(visitedExp.containsKey(exp)) {
    		return visitedExp.get(exp);
    	}
    	else {
    		int[] visited = new int[f.flowGraph().idBound()];
    		visitedExp.put(exp, visited);
    		return visited;
    	}
    }
    
    boolean[] getPREQPVisited(LirNode exp) {
    	if(visitedPREQPExp.containsKey(exp)) {
    		return visitedPREQPExp.get(exp);
    	}
    	else {
    		boolean[] visited = new boolean[f.flowGraph().idBound()];
    		Arrays.fill(visited, false);
    		visitedPREQPExp.put(exp, visited);
    		return visited;
    	}
    }
    
    int[] getDSafeVisited(LirNode exp) {
    	if(dfvisitedExp.containsKey(exp)) {
    		return dfvisitedExp.get(exp);
    	}
    	else {
    		int[] visited = new int[f.flowGraph().idBound()];
    		dfvisitedExp.put(exp, visited);
    		return visited;
    	}
    }
    
    boolean[] getPREQPDSafeVisited(LirNode exp) {
    	if(dfPREQPvisitedExp.containsKey(exp)) {
    		return dfPREQPvisitedExp.get(exp);
    	}
    	else {
    		boolean[] visited = new boolean[f.flowGraph().idBound()];
    		Arrays.fill(visited, false);
    		dfPREQPvisitedExp.put(exp, visited);
    		return visited;
    	}
    }
    
    int NDSafe(BasicBlk v, LirNode node) {
    	int[] visited = getDSafeVisited(node.kid(1));
    	
    	if(visited[v.id] == lattice.Bot) {
			return lattice.Top;
    	}
		else if(visited[v.id] > 0) {
			return visited[v.id];
		}
    	
    	visited[v.id] = lattice.Bot;
    	
    	LirNode sameExpVar = checkLocalRedundant(node.kid(1), v);
    	if(sameExpVar != null) {
    		visited[v.id] = lattice.True;
//    		sameExpVars[v.id] = sameExpVar;
    		return visited[v.id];
    	}
    	
//    	if(isKillBlk(v, node)) {
//    		visited[v.id] = lattice.False;
//    		return visited[v.id];
//    	}
    	
 	   	return XDSafe(v, node);
    }
    
    boolean preqp_NDSafe(BasicBlk v, LirNode node) {
    	boolean[] visited = getPREQPVisited(node.kid(1));
    	boolean[] answers = pre_qp_GetAnswer(node.kid(1));
    	
    	if(visited[v.id]) {
			return answers[v.id];
    	}
    	
    	visited[v.id] = true;
    	
    	LirNode sameExpVar = checkLocalRedundant(node.kid(1), v);
    	if(sameExpVar != null) {
    		answers[v.id] = true;
//    		sameExpVars[v.id] = sameExpVar;
    		return answers[v.id];
    	}
    	
    	if(isKillBlk(v, node)) {
    		answers[v.id] = true;
    		return answers[v.id];
    	}
    	
    	boolean ans = preqp_XDSafe(v, node);
    	answers[v.id] = ans;
 	   	return answers[v.id];
    }

    
    int XDSafe(BasicBlk v, LirNode node) {
//    	System.out.println("@XDSafe "+v.label());
    	if(v.succList().length()==0) {
    		return lattice.False;
    	}
    	
    	int answers = 0;
    	for(BiLink pp=v.succList().first();!pp.atEnd();pp=pp.next()) {
    		BasicBlk s = (BasicBlk)pp.elem();
    		
    		LirNode newNode = updateSSAExp(node, s, v, false);
//    		System.out.println("@XDSafe " + node + " -> " + newNode + " for "+s.label());
    		int sans = NDSafe(s, newNode);
    		
    		if(sans == lattice.False) return lattice.False;
    		
    		if(answers == 0) {
        		answers = sans;
        	}
        	else {
        		answers = lattice.l_or(answers, sans);
        	}
    	}
    	return answers;
    }
    
    boolean preqp_XDSafe(BasicBlk v, LirNode node) {
//    	System.out.println("@XDSafe "+v.label());
    	if(v.succList().length()==0) {
    		return false;
    	}
    	
    	for(BiLink pp=v.succList().first();!pp.atEnd();pp=pp.next()) {
    		BasicBlk s = (BasicBlk)pp.elem();
    		
    		LirNode newNode = updateSSAExp(node, s, v, false);
//    		System.out.println("@XDSafe " + node + " -> " + newNode + " for "+s.label());
    		boolean sans = preqp_NDSafe(s, newNode);
    		
    		if(!sans) {
    			return false;
    		}
    	}
    	return true;
    }
    
    LirNode checkLocalRedundant(LirNode exp, BasicBlk blk) {
		if(exp.opCode == Op.MEM) {
			HashMap<LirNode,LirNode> blkMems = mems.get(blk.id);
			if(blkMems.containsKey(exp)) {
				return blkMems.get(exp);
			}
		}
		else {
			HashMap<LirNode,LirNode> blkExps = exps.get(blk.id);
			if(blkExps.containsKey(exp)) {
				return blkExps.get(exp);
			}
		}
		return null;
    }
    
    
   boolean kill(LirNode s1, LirNode s2) {
	if(s1.opCode==Op.SET) {
		if(s2.kid(1).opCode==Op.MEM && s1.kid(0).opCode==Op.MEM) {
			return true;
		}
		if(s2.kid(1).nKids() < 2) {
			if(s1.kid(0).equals(s2.kid(1))) {
				return true;
			}
			return false;
		}
		else {
			if(s1.kid(0).equals(s2.kid(1).kid(0))) {
				return true;
			}  
			else if(s1.kid(0).equals(s2.kid(1).kid(1))) {
				return true;
			}
		}
	}
	else if(s1.opCode == Op.CALL){
		if(s2.opCode == Op.SET && s2.kid(1).opCode==Op.MEM) {
			return true;
		}
		else if (s1.kid(2).nKids() > 0){
			if(s2.kid(1).nKids() < 2) {
				if(s1.kid(2).kid(0).equals(s2.kid(1))) {
					return true;
				}
			}
			else {
				if(s1.kid(2).kid(0).equals(s2.kid(1).kid(0))) {
					return true;
				}
				else if(s1.kid(2).kid(0).equals(s2.kid(1).kid(1))) {
					return true;
				}
			}
		}
	}
	   
	return false;
   }
   
   void printBB(BasicBlk v) {
	   for(BiLink p=v.instrList().first();!p.atEnd();p=p.next()){
       	LirNode node=(LirNode)p.elem();
       	System.out.println(node);
	   }
   }
   
   void printBBs() {
	   for(BiLink pp=f.flowGraph().basicBlkList.first();!pp.atEnd();pp=pp.next()){
	        BasicBlk v=(BasicBlk)pp.elem();
	        
	        System.out.println("");
	        System.out.println("");
	        System.out.println("***");
	        System.out.println("BB:"+v.label() + ". BBID:"+v.id);
	        for(BiLink p=v.instrList().first();!p.atEnd();p=p.next()){
	        	LirNode node=(LirNode)p.elem();
	        	System.out.println(node);
	        }
	   }
   }
   
   void invoke() {
       long startTime = System.currentTimeMillis();
       
       // Initialization
       exps = new HashMap<Integer, HashMap<LirNode,LirNode>>();
       mems = new HashMap<Integer, HashMap<LirNode,LirNode>>();
       kills = new HashMap<Integer, HashSet<LirNode>>();
       stores = new HashSet<Integer>();
       pcc = new HashMap<Integer, HashMap<LirNode,HashMap<Label,LirNode>>>();
       util = new Util(env, f);
       dom = (Dominators) f.require(Dominators.analyzer);
       dfst=(DFST)f.require(DFST.analyzer);
       insertedNewNodes = new ArrayList<LirNode>();
       
       // Collecting local info and removing local redundancy
       collectLocalInfo();
//     printBBs();
       
       // Global analysis
       BasicBlk[] bVecInOrderOfRPost = dfst.blkVectorByRPost();
//     for(BiLink pp=f.flowGraph().basicBlkList.first();!pp.atEnd();pp=pp.next()){
       for (int i = 1; i <  bVecInOrderOfRPost.length; i++) {
            BasicBlk v = bVecInOrderOfRPost[i];
//          BasicBlk v=(BasicBlk)pp.elem();
//          if(v.id < 15 || v.id > 16) continue;
//          if(v.id != 16) continue;
//          if(bVecInOrderOfRPost.length > 140) continue;
            
            HashMap<LirNode,LirNode> blkExps = new HashMap<LirNode,LirNode>();
            HashMap<LirNode,LirNode> blkMems = new HashMap<LirNode,LirNode>();
            
            HashMap<LirNode, LirNode> valueMap = new HashMap<LirNode, LirNode>();
            int pos = 0;
            for(BiLink p=v.instrList().first();!p.atEnd();p=p.next()){
                LirNode node=(LirNode)p.elem();
                 pos++;
                 
                 if(node.opCode == Op.SET) {
                     if(node.kid(0).opCode == Op.MEM) {
                         blkMems = new HashMap<LirNode,LirNode>();
                         blkMems.put(node.kid(0), node.kid(1));
                     }
                 }
                    
                 if(node.opCode == Op.CALL){
                     blkMems = new HashMap<LirNode,LirNode>();
                 }
                    
                 if(node.opCode!=Op.SET) continue;
                 if(insertedNewNodes.contains(node)) continue;
                 
                 LirNode exp = node.kid(1).makeCopy(env.lir);
                 if(exp.opCode == Op.MEM) {
                     if(blkMems.containsKey(exp)) {
                    	 LirNode newExp = blkMems.get(exp).makeCopy(env.lir);
                         node.setKid(1, newExp);
                         if(printRemoving) System.out.println("removing redundant exp");
                         cpy(v,p.next(),node.kid(0),node.kid(1));
                         valueMap.put(node.kid(0), node.kid(1));
                     }
                     blkMems.put(exp,node.kid(0));
                 }
                 else if(node.kid(0).opCode != Op.MEM){
                	 // check redundancy
                	 if(blkExps.containsKey(exp)) {
                		 LirNode newExp = blkExps.get(exp).makeCopy(env.lir);
                		 
                		 node.setKid(1, newExp.makeCopy(env.lir));
                		 if(printRemoving) System.out.println("removing redundant exp");
                		 cpy(v,p.next(),node.kid(0),newExp);
                             
                		 valueMap.put(node.kid(0), node.kid(1));
                		 blkExps.put(exp.makeCopy(env.lir),node.kid(0).makeCopy(env.lir));
                		 if(exp.opCode==Op.ADD || exp.opCode==Op.MUL) {
                			 LirNode tmp = exp.makeCopy(env.lir);
                			 tmp.setKid(0, exp.kid(1));
                			 tmp.setKid(1, exp.kid(0));
                			 blkExps.put(tmp.makeCopy(env.lir),node.kid(0));
                		 }
                	 }
                	 else if(exp.opCode==Op.ADD || exp.opCode==Op.MUL) {
                		 LirNode tmp = exp.makeCopy(env.lir);
                		 tmp.setKid(0, exp.kid(1).makeCopy(env.lir));
                		 tmp.setKid(1, exp.kid(0).makeCopy(env.lir));
                		 if(blkExps.containsKey(tmp)) {
                			 LirNode newExp = blkExps.get(tmp);
                			 node.setKid(1, newExp.makeCopy(env.lir));
                			 if(printRemoving) System.out.println("removing redundant exp");
                			 cpy(v,p.next(),node.kid(0),node.kid(1));
                			 valueMap.put(node.kid(0), node.kid(1));
                		 }
                		 blkExps.put(tmp,node.kid(0));
                		 blkExps.put(exp.makeCopy(env.lir),node.kid(0).makeCopy(env.lir));
                	 }
                	 else if(exp.nKids() > 0){
                		 blkExps.put(exp.makeCopy(env.lir),node.kid(0).makeCopy(env.lir));
                	 }
                 }
                 
                 if(node.kid(1).opCode == Op.INTCONST || node.kid(1).opCode == Op.FLOATCONST || node.kid(1).opCode == Op.REG) continue;
//               if(node.kid(1).opCode != Op.MEM && (node.kid(1).nKids() < 2 || node.kid(1).kid(0).opCode != Op.REG && node.kid(1).kid(1).opCode != Op.REG)) continue;
                 if(isKillBlk(v,node)) continue;
                 
//               if(pos != 3) continue;
//               if(pos == 5 || pos == 9 || pos > 12)continue;
//               if(node.kid(1).opCode != Op.MUL)continue;
//               if(node.kid(1).kid(0).opCode != Op.REG && node.kid(1).kid(1).opCode != Op.REG)continue;
//               if(node.kid(1).kid(0).opCode == Op.REG)continue;
//               if(node.kid(1).nKids()<2)continue;
//               if(v.id != 5) continue;
//               if(v.instrList().length() == 2) continue;
//               if(v.instrList().length() < 60) continue;
//               printBBs();
//               System.out.println("");
//               System.out.println("");
//               System.out.println("=== start query propagation ===");
//               System.out.println("original node:"+node+". pos:"+pos);
//               System.out.println("v. label:"+v.label());
//               System.out.println("v. id:"+v.id);
//               System.out.println("v.instrList().length():"+v.instrList().length());
//               System.out.println("bVecInOrderOfRPost.length:"+bVecInOrderOfRPost.length);
                 
                 // LDPRE
                 //propagate(node, v, p.next());
                 
                 // PREQP
                 preqp_propagate(node, v, p.next());
                 
//               printBBs();
                 
            }
            
        }
       
       long stopTime = System.currentTimeMillis();
       System.out.println("Analyzing time:"+(stopTime-startTime));
//     System.out.println("exp_num:"+exp_num);
       
//       printBBs();
   }
   
   void propagate(LirNode node, BasicBlk v, BiLink pp) {
	   insertedBlk = new int[f.flowGraph().idBound()];
	   visitedExp = new HashMap<LirNode, int[]>();
	   dfvisitedExp = new HashMap<LirNode, int[]>();
	   sameExpVarFlow = new LirNode[f.flowGraph().idBound()];
	   sameExpVars = new LirNode[f.flowGraph().idBound()];
	   insertPhiBlk = new boolean[f.flowGraph().idBound()];
	   visitedPREQPExp = new HashMap<LirNode, boolean[]>();
	   preqp_Answer = new HashMap<LirNode, boolean[]>();
	   
	   int answer = NAvail(node,v);
	   if(answer == lattice.True) {
		   BasicBlk dblk;
		   if(insertPhiBlk[v.id]) {
			   dblk = v;
		   }
		   else {
			   dblk = dom.immDominator(v);
		   }
		   
		   LirNode p = null;
		   while(p == null) {
			   while(dblk != null && sameExpVars[dblk.id] == null){
				   dblk = dom.immDominator(dblk);
			   }
			   
			   if(dblk == null) {
//				   printBBs();
//				   System.out.println("original node:"+node);
//				   
				   dblk = dom.immDominator(v);
				   while(dblk != null && sameExpVars[dblk.id] == null){
					   System.out.println("dblk:"+dblk.label()+", sameExpVars[dblk.id]:"+sameExpVars[dblk.id]);
					   dblk = dom.immDominator(dblk);
				   }
				   printBBs();
				   System.out.println("node--"+node);
				   System.out.println("v:"+v.label());
				   System.out.println("ERROR?");
				   System.exit(2);
			   }
			   
//			   System.out.println("dblk:"+dblk.label());
//			   if(sameExpVars[dblk.id] == null) {
//				   System.out.println("ERROR?");
//			   }
			   
			   if(!sameExpVars[dblk.id].equals(node.kid(0))) {
				   p = sameExpVars[dblk.id];
			   }
			   else {
				   dblk = dom.immDominator(dblk);
			   }
		   }
		   replace(node, p);
//		   cpy.cpyp(v,node.kid(0),node.kid(1),1);
		   cpy(v, pp, node.kid(0), node.kid(1));
	   }
//	   else if(answer == lattice.Top) {
//		   printBBs();
//		   System.out.println("TOP!!! "+node+" @ "+v.label());
//		   System.exit(2);
//	   }
   }
   
   void replace(LirNode node, LirNode t) {
//	   printBBs();
//	   System.out.println("before:" + node);
	   node.setKid(1, t.makeCopy(env.lir));
	   if(printRemoving) System.out.println("removing redundant exp");
//	   System.out.println("after:"+node);
//	   System.exit(2);
   }
   
   void cpy(BasicBlk blk, BiLink p, LirNode from, LirNode to) {
//     System.out.println("");
//     System.out.println("===CPY===");
//     System.out.println(from+"->"+to+"@"+blk.label());
       localCPYP(blk,p,from,to);
       
       for(BiLink pp=dom.kids[blk.id].first();!pp.atEnd();pp=pp.next()){
           BasicBlk kid = (BasicBlk)pp.elem();
           cpy(kid, kid.instrList().first(), from, to);
       }
        
   }
   
   void localCPYP(BasicBlk blk, BiLink p, LirNode from, LirNode to) {
       HashMap<LirNode,LirNode> blkMems = mems.get(blk.id);
       HashMap<LirNode,LirNode> blkExps = exps.get(blk.id);
       
       boolean is_target = updateTable(blkMems, from, to);
       boolean is_target2 = updateTable(blkExps, from, to);
       
       if(is_target || is_target2) {
           for(BiLink pp=p;!pp.atEnd();pp=pp.next()){
                LirNode node=(LirNode)pp.elem();
                if(node.opCode==Op.SET) {
                    if(node.kid(0).opCode==Op.MEM) {
                        for(int i=0;i<node.kid(0).nKids();i++) {
                            if(node.kid(0).kid(i).equals(from)) {
                                node.kid(0).setKid(i, to.makeCopy(env.lir));
                            }
                        }
                    }
                    else if(node.kid(1).equals(from)) {
                        node.setKid(1, to.makeCopy(env.lir));
                    }
                    else {
                        for(int i=0;i<node.kid(1).nKids();i++) {
                            if(node.kid(1).kid(i).equals(from)) {
                                node.kid(1).setKid(i, to.makeCopy(env.lir));
                            }
                        }
                    }
                }
           }
       }
       
   }
   
   boolean updateTable(HashMap<LirNode,LirNode> blkMap, LirNode from, LirNode to) {
	   boolean is_target = false;
	   ArrayList<ArrayList<LirNode>> newComb = new ArrayList<ArrayList<LirNode>>();
	   for (LirNode key : blkMap.keySet()) {
		   boolean is_target_exp = false;
		   LirNode newExp = key.makeCopy(env.lir);
		   for(int i=0;i<key.nKids();i++) {
			   if(key.kid(i).equals(from)) {
				   is_target = true;
				   is_target_exp = true;
				   newExp.setKid(i, to);
			   }
		   }
		   if(is_target_exp) {
			   ArrayList<LirNode> comb = new ArrayList<LirNode>();
			   LirNode tgt = blkMap.get(key);
			   comb.add(newExp);
			   comb.add(tgt);
			   newComb.add(comb);
		   }
	   }
	   
	   for(int i=0;i<newComb.size();i++) {
		   ArrayList<LirNode> comb = newComb.get(i);
		   LirNode f = comb.get(0);
		   LirNode t = comb.get(1);
		   blkMap.put(f, t);
	   }
	   return is_target;
   }
   
   void preqp_propagate(LirNode node, BasicBlk v, BiLink pp) {
	   insertedBlk = new int[f.flowGraph().idBound()];
	   visitedPREQPExp = new HashMap<LirNode, boolean[]>();
	   preqp_Answer = new HashMap<LirNode, boolean[]>();
	   dfvisitedExp = new HashMap<LirNode, int[]>();
	   sameExpVarFlow = new LirNode[f.flowGraph().idBound()];
	   sameExpVars = new LirNode[f.flowGraph().idBound()];
	   insertPhiBlk = new boolean[f.flowGraph().idBound()];
	   
	   boolean answer = preqp_NAvail(node,v);
	   if(answer) {
		   BasicBlk dblk;
		   if(insertPhiBlk[v.id]) {
			   dblk = v;
		   }
		   else {
			   dblk = dom.immDominator(v);
		   }
		   
		   LirNode p = null;
		   while(p == null) {
			   while(dblk != null && sameExpVars[dblk.id] == null){
				   dblk = dom.immDominator(dblk);
			   }
			   
			   if(dblk == null) {
//				   printBBs();
//				   System.out.println("original node:"+node);
//				   
				   dblk = dom.immDominator(v);
				   while(dblk != null && sameExpVars[dblk.id] == null){
//					   System.out.println("dblk:"+dblk.label()+", sameExpVars[dblk.id]:"+sameExpVars[dblk.id]);
					   dblk = dom.immDominator(dblk);
				   }
//				   System.out.println("ERROR?");
//				   System.exit(2);
			   }
			   
			   if(dblk == null) {
				   return;
			   }
			   
//			   System.out.println("dblk:"+dblk.label());
//			   if(sameExpVars[dblk.id] == null) {
//				   System.out.println("ERROR?");
//			   }
			   
			   if(!sameExpVars[dblk.id].equals(node.kid(0))) {
				   p = sameExpVars[dblk.id];
			   }
			   else {
				   dblk = dom.immDominator(dblk);
			   }
		   }
		   replace(node, p);
//		   cpy.cpyp(v,node.kid(0),node.kid(1),1);
		   cpy(v, pp, node.kid(0), node.kid(1));
	   }
   }
   
   boolean preqp_NAvail(LirNode node, BasicBlk v) {
	   if(v.predList().length()==0) {
		   return false;
	   }
   	
	   ArrayList<BasicBlk> trues = new ArrayList<BasicBlk>();
	   ArrayList<BasicBlk> falses = new ArrayList<BasicBlk>();
	   HashMap<BasicBlk, LirNode> p2n = new HashMap<BasicBlk,LirNode>();
	   HashMap<Integer, LirNode> phiArgMap = new HashMap<Integer,LirNode>();
	   
	   for(BiLink pp=v.predList().first();!pp.atEnd();pp=pp.next()) {
		   BasicBlk p = (BasicBlk)pp.elem();
   		
		   // prepare for query propagation
		   LirNode newNode = updateSSAExp(node, v, p, true);
		   p2n.put(p, newNode);
   		
		   // query propagation
		   boolean pans = preqp_XAvail(newNode, p);
		   LirNode predSameExpVar = sameExpVars[p.id];
		   sameExpVarFlow[v.id] = sameExpVars[p.id];
		   if(sameExpVarFlow[v.id] == null) sameExpVarFlow[v.id] = sameExpVarFlow[p.id];
		   if(predSameExpVar != null) {
			   phiArgMap.put(p.id, predSameExpVar);
		   }
		   
		   // recording answers
		   if(pans) trues.add(p);
		   else falses.add(p);
       	
//       	System.out.println("");
//   		System.out.println("v:"+v.label());
//   		System.out.println("pred:"+p.label());
//   		System.out.println("newNode:"+newNode);
//   		System.out.println("pred answer:"+pans);
//   		System.out.println("predSameExpVar:"+predSameExpVar);
//   		System.out.println("before current answer:"+answers);
       	
	   }
	   
	   boolean answer = false;
   	
	   // Insert
	   if(trues.size() > 0) {
		   if(falses.size() > 0) {
			   boolean dsafe = true;
			   for(int i=0;i<falses.size();i++) {
				   BasicBlk p = falses.get(i);
				   LirNode newNode = p2n.get(p);
				   if(!preqp_XDSafe(p,newNode)) {
					   dsafe = false;
					   break;
				   }
			   }
	   		
			   if(dsafe) {
				   insert(node, v, trues, falses, new ArrayList<BasicBlk>(), p2n, phiArgMap);
				   answer = true;
			   }
			   else {
				   answer = false;
			   }
		   }
		   
		   if(trues.size() == v.predList().length()) {
			   answer = true;
			   if(trues.size() > 1) {
				   insertPhi(node, v, phiArgMap);
			   }
		   }
	   }
//	   if(trues.size() > 0 && falses.size() > 0) {
//		   boolean dsafe = true;
//		   for(int i=0;i<falses.size();i++) {
//			   BasicBlk p = falses.get(i);
//			   LirNode newNode = p2n.get(p);
//// 	  			System.out.println("");
////   			System.out.println("Check DSafe..:" + p.label());
////   			System.out.println("Result of DSafe "+XDSafe(p,newNode));
//			   if(!preqp_XDSafe(p,newNode)) {
//				   dsafe = false;
//				   break;
//			   }
//		   }
//		   
////		   System.out.println("DSafe:"+dsafe);
//   		
//		   if(dsafe) {
//			   insert(node, v, trues, falses, new ArrayList<BasicBlk>(), p2n, phiArgMap);
//			   answer = true;
//		   }
//		   else {
//			   answer = false;
//		   }
//	   }
//	   else if(trues.size() > 1 && trues.size() == v.predList().length()) {
//		   insertPhi(node, v, phiArgMap);
//		   answer = true;
//	   }
//	   else if(trues.size() > 0 && trues.size() == v.predList().length()) {
//		   answer = true;
//	   }
   	
	   boolean[] visited = getPREQPVisited(node.kid(1));
       visited[v.id] = true;
       
       boolean[] answers = pre_qp_GetAnswer(node.kid(1));
       answers[v.id] = answer;
   	
//   		System.out.println("result@"+v.label()+". "+answers[v.id]+". trues:"+trues.size()+", v.preds:"+v.predList().length());
   	
	   return answers[v.id];
   }
   
   
   boolean preqp_XAvail(LirNode node, BasicBlk v) {
	   boolean[] visited = getPREQPVisited(node.kid(1));
//   	System.out.println("XAvail @ " + v.label() + ". node:" + node + ". visited:" + visited[v.id]);
   	
	   if(visited[v.id]) {
		   boolean[] answers = pre_qp_GetAnswer(node.kid(1));
		   return answers[v.id];
	   }
   	
	   visited[v.id] = true;
	   boolean[] answers = pre_qp_GetAnswer(node.kid(1));
	   
	   LirNode sameExpVar = checkLocalRedundant(node.kid(1), v);
	   if(sameExpVar != null) {
		   sameExpVars[v.id] = sameExpVar;
		   answers[v.id] = true;
		   return answers[v.id];
	   }
   	
	   if(isKillBlk(v, node)) {
		   visited[v.id] = true;
		   answers[v.id] = false;
		   return answers[v.id];
	   }
   	
	   boolean ans = preqp_NAvail(node, v);
	   answers[v.id] = ans;
	   return answers[v.id];
   }
   
   boolean[] pre_qp_GetAnswer(LirNode exp) {
	   if(preqp_Answer.containsKey(exp)) {
		   return preqp_Answer.get(exp);
	   }
       else {
           boolean[] answers = new boolean[f.flowGraph().idBound()];
           preqp_Answer.put(exp, answers);
           return answers;
       }
  }
   
   public boolean doIt(Function function,ImList args) {
    	
      //
//      long startTime = System.currentTimeMillis();
      //
    f = function;
    this.dom = (Dominators) f.require(Dominators.analyzer);
    env.println("****************** doing LDPRE to "+
    f.symbol.name,SsaEnvironment.MinThr);

    invoke();

    f.flowGraph().touch();
    return(true);
  }
 }


class Lattice {
    int Top = 1;
    int Bot = 2;
    int True = 3;
    int False = 4;
    
    Lattice(){
//        System.out.println(l_and(True,False));
//        System.out.println(l_or(Top,True));
    }
    
    String getType(int t) {
    	if(t == 1) {
    		return "Top";
    	}
    	else if(t == 2) {
    		return "Bot";
    	}
    	else if(t == 3) {
    		return "True";
    	}
    	else if(t == 4) {
    		return "False";
    	}
    	else {
    		return "ERROR";
    	}
    }
    
    public int l_and(int op1,int op2){
        if(op1==Top){
            if(op2==True){
                return True;
            } else if(op2==False){
                return False;
            } else if(op2==Bot){
                return Bot;
            } else {
                return Top;
            }
        } 
        else if(op1==Bot){
            if(op2==True){
                return Bot;
            } else if(op2==False) {
                return Bot;
            } else if(op2==Top) {
                return Bot;
            } else {
                return Bot;
            }
        } 
        else if(op1==True){
            if(op2==False){
                return Bot;
            } else if(op2==Top){
                return True;
            } else if(op2==Bot){
                return Bot;
            } else {
                return True;
            }
        } 
        else if(op1==False){
            if(op2==True){
                return Bot;
            } else if(op2==Top){
                return False;
            } else if(op2==Bot){
                return Bot;
            } else {
                return False;
            }
        }
        else{
            return -1;
        }
    }
    public int l_or(int op1, int op2){
        if(op1==Top){
            if(op2==True){
                return Top;
            } else if(op2==False) {
                return Top;
            } else if(op2==Bot) {
                return Top;
            } else {
                return Top;
            }
        } 
        else if (op1==Bot) {
            if(op2==True){
                return True;
            } else if(op2==False){
                return False;
            } else if(op2==Top){
                return Top;
            } else {
                return Bot;
            }
        }
        else if(op1==True){
            if(op2==False){
                return Top;
            } else if(op2==Top){
                return Top;
            } else if(op2==Bot){
                return True;
            }  else {
                return True;
            }
        }
        else if(op1==False){
            if(op2==True){
                return Top;
            } else if(op2==Top){
                return Top;
            } else if(op2==Bot){
                return False;
            } else {
                return False;
            }
        }
        else {
            return -1;
        }
    }
}