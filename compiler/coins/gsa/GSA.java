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

import coins.backend.Data;
import coins.backend.Function;
import coins.backend.LocalTransformer;
import coins.backend.Op;
import coins.backend.Type;
import coins.backend.ana.DFST;
import coins.backend.cfg.BasicBlk;
import coins.backend.lir.LirNode;
import coins.backend.sym.Symbol;
import coins.backend.util.BiLink;
import coins.backend.util.ImList;

public class GSA implements LocalTransformer {
    public boolean doIt(Data data, ImList args) { return true; }
    public String name() { return "GSA"; }
    public String subject() {
    	return "Optimizatin with efficient question propagation.";
    }
    private String tmpSymName="_gsa";
    
    public static final int THR=SsaEnvironment.OptThr;
    /** The threshold of debug print **/
    public static final int THR2=SsaEnvironment.AllThr;
    private SsaEnvironment env;
    private SsaSymTab sstab;
    private Function f;
    private DFST dfst;
    /*    private Hashtable memArgs;*/
	public BasicBlk[] bVecInOrderOfRPost;
    
    int idBound;
    
    // for PDE
	boolean[] nIsSame;
	boolean[] xIsSame;
	boolean[] nUsed;
	boolean[] xUsed;
	boolean[] nMayMod;
	boolean[] xMayMod;
	boolean[] nMustMod;
	boolean[] xMustMod;
	boolean[] nBlock;
	boolean[] xBlock;
	boolean[] xCand;
	boolean[] nDead;
	boolean[] xDead;
	boolean[] nDelayed;
	boolean[] xDelayed;
	boolean[] nInsert;
	boolean[] xInsert;
	
	// for GSA
	boolean[] nIsStore;
	boolean[] xIsStore;
	boolean[] nSameAddr;
	boolean[] xSameAddr;
	boolean[] nModidx;
	boolean[] xModidx;
	boolean[] nCont;
	boolean[] xCont;
	boolean[] nInCont;
	boolean[] xInCont;
	boolean[] nBlockAddr;
	boolean[] xBlockAddr;
	
	boolean pde = false;
	
    /**
     * Constructor
     * @param e The environment of the SSA module
     * @param tab The symbol tabel of the SSA module
     * @param function The current function
     * @param m The current mode
     **/
    public GSA(SsaEnvironment env, SsaSymTab sstab) {
		this.env = env;
		this.sstab = sstab;
	}
    
    public GSA(SsaEnvironment env, SsaSymTab sstab, Function f) {
		this.env = env;
		this.sstab = sstab;
		this.f = f;
	}
    
    /**
     * Do optimize using Efficient Question Propagation.
     * @param f The current function
     **/
    
   void displayBasicBlk() {
	   for(BiLink p =f.flowGraph().basicBlkList.first();!p.atEnd();p=p.next()){
		   BasicBlk v=(BasicBlk)p.elem();
		   System.out.println("");
		   System.out.println("-------------------------------------------");
		   System.out.println(v.label()+". id:"+v.id);
		   for(BiLink bl=v.instrList().first();!bl.atEnd();bl=bl.next()){
			   LirNode node=(LirNode)bl.elem();
			   System.out.println(node);
		   }
	   }
    }
   
   void displayBasicBlk(BasicBlk v) {
	   System.out.println("");
	   System.out.println("-------------------------------------------");
	   System.out.println(v.label()+". id:"+v.id);
	   for(BiLink bl=v.instrList().first();!bl.atEnd();bl=bl.next()){
		   LirNode node=(LirNode)bl.elem();
		   System.out.println(node);
	   }
    }
   
   void printProperties() {
	   for(BiLink p =f.flowGraph().basicBlkList.first();!p.atEnd();p=p.next()){
		   BasicBlk v=(BasicBlk)p.elem();
		   System.out.println("");
		   System.out.println("-------------------------------------------");
		   System.out.println("Blk:"+v.label()+", ID:"+v.id);
		   printBlkProperties(v.id);
	   }
   }
   
   void printBlkProperties(int vid) {
	   System.out.println("");
	   System.out.println("nUsed:"+nUsed[vid]);
	   System.out.println("xUsed:"+xUsed[vid]);
	   System.out.println("nMayMod:"+nMayMod[vid]);
	   System.out.println("xMayMod:"+xMayMod[vid]);
	   System.out.println("nMustMod:"+nMustMod[vid]);
	   System.out.println("xMustMod:"+xMustMod[vid]);
	   System.out.println("nModIdx:"+nModidx[vid]);
	   System.out.println("xModIdx:"+xModidx[vid]);
	   System.out.println("nIsSame:"+nIsSame[vid]);
	   System.out.println("xIsSame:"+xIsSame[vid]);
	   System.out.println("nIsStore:"+nIsStore[vid]);
	   System.out.println("xIsStore:"+xIsStore[vid]);
	   System.out.println("nSameAddr:"+nSameAddr[vid]);
	   System.out.println("xSameAddr:"+xSameAddr[vid]);
	   System.out.println("nBlock:"+nBlock[vid]);
	   System.out.println("xBlock:"+xBlock[vid]);
	   System.out.println("nBlockAddr:"+nBlockAddr[vid]);
	   System.out.println("xBlockAddr:"+xBlockAddr[vid]);
	   System.out.println("xCand:"+xCand[vid]);
	   System.out.println("nDead:"+nDead[vid]);
	   System.out.println("xDead:"+xDead[vid]);
	   System.out.println("nDelayed:"+nDelayed[vid]);
	   System.out.println("xDelayed:"+xDelayed[vid]);
	   System.out.println("nCont:"+nCont[vid]);
	   System.out.println("xCont:"+xCont[vid]);
	   System.out.println("nInCont:"+nInCont[vid]);
	   System.out.println("xInCont:"+xInCont[vid]);
	   System.out.println("nInsert:"+nInsert[vid]);
	   System.out.println("xInsert:"+xInsert[vid]);
   }
    
    public void collectVars(ArrayList<LirNode> vars, LirNode exp){
		for(int i=0;i<exp.nKids();i++){
			if(exp.kid(i).opCode==Op.REG) vars.add(exp.kid(i).makeCopy(env.lir));
			else if(exp.kid(i).nKids()>0) collectVars(vars,exp.kid(i));
		}
	}
   
    boolean isUse(LirNode n1, LirNode n1addr, ArrayList<LirNode> vars, LirNode n2) {
    	if(n2.opCode==Op.CALL) {
			for(int i=0;i<n2.kid(1).nKids();i++) {
				LirNode v = n2.kid(1).kid(i);
				if(v.equals(n1addr) || v.opCode==Op.REG) {
					return true;
				}
			}
		}
		
		if(isLoad(n2)) {
			ArrayList<LirNode> nvars = new ArrayList<LirNode>();
			collectVars(nvars,n2.kid(1));
			if(isSame(n2.kid(1), n1.kid(0), vars, nvars)) {
				return true;
			}
		}
    	return false;
    }
    
    public boolean isSame(LirNode n1, LirNode n2){
    	if(n1.opCode!=Op.SET || n1.kid(0).opCode!=Op.MEM) return false;
    	if(n2.opCode!=Op.SET || n2.kid(0).opCode!=Op.MEM) return false;
    	return (n1.kid(0).kid(0).equals(n2.kid(0).kid(0)));
    }
    
    public boolean isLoad(LirNode node){
    	return (node.opCode==Op.SET && node.kid(1).opCode==Op.MEM);
    }
    
    public boolean isStore(LirNode node) {
    	return(node.opCode==Op.SET && node.kid(0).opCode==Op.MEM);
    }
    
    /*
     * May indicates that the store or call statements may change the value of analyzing array.
     * This implementation does not perform alias analysis. If alias analysis is usable, the MayMode is not necessary.
     */
    public boolean isMayMod(LirNode expr, LirNode addr1, LirNode node, ArrayList<LirNode> vars, ArrayList<LirNode> nvars, BasicBlk blk, BiLink p){
    	if(isMustMod(expr,node,vars,nvars,blk,p)) return true;
    	
		if(node.opCode==Op.CALL)return true;
		
		// check store
		if(isStore(node)) {
			LirNode addr2 = getAddr(node);
			if(addr2.opCode != Op.FRAME) {
				return true;
			}
			else if(!addr1.equals(addr2)) {
				return false;
			}
			else if(vars.size() > 0 || nvars.size() > 0) {
				return true;
			}
			else {
				return false;
			}
		}
		return false;
	}
    
    public boolean isMustMod(LirNode expr, LirNode node, ArrayList<LirNode> vars, ArrayList<LirNode> nvars, BasicBlk blk, BiLink p){
		// check store
		if(isStore(node)) {
			LirNode addr1 = getAddr(expr);
			LirNode addr2 = getAddr(node);
			if(!addr1.equals(addr2)) {
				return false;
			}
			
			if(expr.kid(0).equals(node.kid(0).kid(0))) {
				return true;
			}
			else {
				return false;
			}
		}
		return false;
	}
    
    
    boolean isModIdx(LirNode node, ArrayList<LirNode> vars) {
    	if(node.opCode!=Op.SET || node.kid(0).opCode!=Op.REG) return false;
		
		return vars.contains(node.kid(0));
    }
    
    
    LirNode getAddr(LirNode exp){
    	if(exp.nKids()==0) return exp;
		if(exp.kid(0).nKids()==0) return exp.kid(0);
		else return getAddr(exp.kid(0));
	}
    
    
  	public boolean sameAddr(LirNode node, LirNode addr){
  		if(isLoad(node)) return (addr.equals(getAddr(node.kid(1))));
  		if(isStore(node)) return (addr.equals(getAddr(node.kid(0))));
  		return false;
  	}
    
  	
	void compLocalProperty(LirNode node, LirNode addr, ArrayList<LirNode> vars){
		// PDE
		nIsSame = new boolean[idBound];
		xIsSame = new boolean[idBound];
		nUsed = new boolean[idBound];
		xUsed = new boolean[idBound];
		nBlock = new boolean[idBound];
		xBlock = new boolean[idBound];

		nMayMod = new boolean[idBound];
		xMayMod = new boolean[idBound];
		nMustMod = new boolean[idBound];
		xMustMod = new boolean[idBound];
		
		// GSA
		nSameAddr = new boolean[idBound];
		xSameAddr = new boolean[idBound];
		nIsStore = new boolean[idBound];
		xIsStore = new boolean[idBound];
		nModidx = new boolean[idBound];
		xModidx = new boolean[idBound];
		nCont = new boolean[idBound];
		xCont = new boolean[idBound];
		nInCont = new boolean[idBound];
		xInCont = new boolean[idBound];
		nBlockAddr = new boolean[idBound];
		xBlockAddr = new boolean[idBound];
		
		Arrays.fill(nMayMod, false);
		Arrays.fill(xMayMod, false);
		Arrays.fill(nMustMod, false);
		Arrays.fill(xMustMod, false);
		
		// GSA
		for(int i=1;i<bVecInOrderOfRPost.length; i++) {
			BasicBlk blk = bVecInOrderOfRPost[i];
			
			// PDE
			nIsSame[blk.id] = compNIsSame(node,addr,vars,blk);
			xIsSame[blk.id] = compXIsSame(node,addr,vars,blk);
			

			nSameAddr[blk.id] = compNSameAddr(addr,blk);
			xSameAddr[blk.id] = compXSameAddr(addr,blk);
			
			nUsed[blk.id] = compNUsed(node,addr,vars,blk);
			xUsed[blk.id] = compXUsed(node,addr,vars,blk);
			compNMod(node,addr,vars,blk);
			compXMod(node,addr,vars,blk);
			
			nBlock[blk.id] = nUsed[blk.id] || nMayMod[blk.id];
			xBlock[blk.id] = xUsed[blk.id] || xMayMod[blk.id];
			
			// GSA
			nModidx[blk.id] = compNModIdx(vars, blk);
			xModidx[blk.id] = compXModIdx(vars, blk);
			nIsStore[blk.id] = compNIsStore(node, vars, blk);
			xIsStore[blk.id] = compXIsStore(node, vars, blk);
			nBlockAddr[blk.id] = nIsStore[blk.id] && nMustMod[blk.id];
			xBlockAddr[blk.id] = xIsStore[blk.id] && xMustMod[blk.id];
		}
	}
	
	public boolean compNSameAddr(LirNode addr, BasicBlk blk) {
		for(BiLink p=blk.instrList().first();!p.atEnd();p=p.next()){
			LirNode n2 = (LirNode)p.elem();
			if(sameAddr(n2, addr)) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean compXSameAddr(LirNode addr, BasicBlk blk) {
		for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()){
			LirNode n2 = (LirNode)p.elem();
			if(sameAddr(n2, addr)) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean compNIsSame(LirNode n1, LirNode addr, ArrayList<LirNode> vars, BasicBlk blk) {
		for(BiLink p=blk.instrList().first();!p.atEnd();p=p.next()){
			LirNode n2 = (LirNode)p.elem();
			
			ArrayList<LirNode> nvars = new ArrayList<LirNode>();
			collectVars(nvars,n2);
			
			if(isMayMod(n1.kid(0),addr,n2,vars,nvars,blk,p)) {
				break;
			}
			
			if(isSame(n1, n2)) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean compXIsSame(LirNode n1, LirNode addr, ArrayList<LirNode> vars, BasicBlk blk) {
		for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()){
			LirNode n2 = (LirNode)p.elem();
			
			ArrayList<LirNode> nvars = new ArrayList<LirNode>();
			collectVars(nvars,n2);
			
			if(isMayMod(n1.kid(0),addr,n2,vars,nvars,blk,p)) {
				break;
			}
			
			if(isSame(n1, n2)) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean compNModIdx(ArrayList<LirNode> vars, BasicBlk blk) {
		for(BiLink p=blk.instrList().first();!p.atEnd();p=p.next()){
			LirNode n = (LirNode)p.elem();
			if(isModIdx(n, vars)) return true;
		}
		
		return false;
	}
	
	public boolean compXModIdx(ArrayList<LirNode> vars, BasicBlk blk) {
		for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()){
			LirNode n = (LirNode)p.elem();
			if(isModIdx(n, vars)) return true;
		}
		
		return false;
	}
	
	public boolean compNIsStore(LirNode n1, ArrayList<LirNode> vars, BasicBlk blk){
		for(BiLink p=blk.instrList().first();!p.atEnd();p=p.next()){
			LirNode n2 = (LirNode)p.elem();
			
			if(n2.opCode==Op.CALL) {
				break;
			}

			if(isStore(n2)) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean compXIsStore(LirNode n1, ArrayList<LirNode> vars, BasicBlk blk){
		for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()){
			LirNode n2 = (LirNode)p.elem();
			
			if(n2.opCode==Op.CALL) {
				break;
			}

			if(isStore(n2)) {
				return true;
			}
		}
		
		return false;
	}
	
	public void compNMod(LirNode n1, LirNode addr, ArrayList<LirNode> vars, BasicBlk blk){
		for(BiLink p=blk.instrList().first();!p.atEnd();p=p.next()){
			LirNode n2 = (LirNode)p.elem();
			
			ArrayList<LirNode> nvars = new ArrayList<LirNode>();
			collectVars(nvars,n2);

			if(isMustMod(n1.kid(0),n2,vars,nvars,blk,p)) {
				nMustMod[blk.id] = true;
			}
			
			if(isMayMod(n1.kid(0),addr,n2,vars,nvars,blk,p)) {
				nMayMod[blk.id] = true;
			}
			
		}
	}
	
	public void compXMod(LirNode n1, LirNode addr, ArrayList<LirNode> vars, BasicBlk blk){
		for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()){
			LirNode n2 = (LirNode)p.elem();
			
			ArrayList<LirNode> nvars = new ArrayList<LirNode>();
			collectVars(nvars,n2);
			
			if(isMustMod(n1.kid(0),n2,vars,nvars,blk,p)) {
				xMustMod[blk.id] = true;
			}
			
			if(isMayMod(n1.kid(0),addr,n2,vars,nvars,blk,p)) {
				xMayMod[blk.id] = true;
			}
			
		}
	}
	
	public boolean compNUsed(LirNode n1, LirNode addr, ArrayList<LirNode> vars, BasicBlk blk){
		for(BiLink p=blk.instrList().first();!p.atEnd();p=p.next()){
			LirNode n2 = (LirNode)p.elem();
			
			if(isUse(n1, addr, vars, n2)) {
				return true;
			}
			
			if(isStore(n2)) {
				LirNode n2addr = getAddr(n2.kid(0));
				if(n2addr.equals(addr)) {
					return false;
				}
			}
			
			if(isModIdx(n2, vars)) {
				return false;
			}
			
		}
		return false;
	}
	
	public boolean compXUsed(LirNode n1, LirNode addr, ArrayList<LirNode> vars, BasicBlk blk){
		for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()){
			LirNode n2 = (LirNode)p.elem();
			
			if(isUse(n1, addr, vars, n2)) {
				return true;
			}
			
			if(isStore(n2)) {
				LirNode n2addr = getAddr(n2.kid(0));
				if(n2addr.equals(addr)) {
					return false;
				}
			}

			if(isModIdx(n2, vars)) {
				return false;
			}
			
		}
		
		return false;
	}
	
	
	public void compCand(LirNode n1, LirNode addr, BasicBlk blk, BiLink q, ArrayList<LirNode> vars) {
		xCand = new boolean[idBound];
		Arrays.fill(xCand, false);
		
		if(!xModidx[blk.id]) {
			xCand[blk.id] = true;
			
			for(BiLink p=q;!p.atEnd();p=p.next()) {
				LirNode node = (LirNode)p.elem();
				
				if(node.opCode==Op.CALL){
					xCand[blk.id] = false;
					break;
				}

				ArrayList<LirNode> nvars = new ArrayList<LirNode>();
				collectVars(nvars,node);
				
				if(isMustMod(n1.kid(0),node,vars,nvars,blk,p) || isMayMod(n1.kid(0),addr,node,vars,nvars,blk,p)){
					xCand[blk.id] = false;
					break;
				}
				
				if(isUse(n1, addr, vars, node)) {
					xCand[blk.id] = false;
					break;
				}
			}
		}
	}
	
	public void compDead() {
		nDead = new boolean[idBound];
		xDead = new boolean[idBound];
		Arrays.fill(nDead, true);
		Arrays.fill(xDead, true);
		boolean change = true;
		while(change){
			change = false;
			for(BiLink p=f.flowGraph().basicBlkList.last();!p.atEnd();p=p.prev()){
				BasicBlk blk = (BasicBlk)p.elem();
				boolean x = false;
				if(xIsSame[blk.id]) {
					x = true;
				}
				else if(xCand[blk.id] || !xMustMod[blk.id]){
					x = true;
					for(BiLink q=blk.succList().first();!q.atEnd();q=q.next()){
						BasicBlk succ = (BasicBlk)q.elem();
						if(!nDead[succ.id]){
							x = false;
							break;
						}
					}
				}

				boolean n = !nUsed[blk.id] && (nMustMod[blk.id] || nIsSame[blk.id] || x);
				if(nDead[blk.id]!=n || xDead[blk.id]!=x) change = true;
				nDead[blk.id] = n;
				xDead[blk.id] = x;
			}
		}
	}
	
	
	public void compCont() {
		nCont = new boolean[idBound];
		xCont = new boolean[idBound];
		Arrays.fill(nCont, false);
		Arrays.fill(xCont, false);
		boolean change = true;
		while(change){
			change = false;
			for(BiLink p=f.flowGraph().basicBlkList.last();!p.atEnd();p=p.prev()){
				BasicBlk blk = (BasicBlk)p.elem();
				boolean x = false;
				
				if(blk!=f.flowGraph().entryBlk()){
					if(nIsStore[blk.id] && nSameAddr[blk.id]) {
						x = true;
					}else{
						for(BiLink q=blk.succList().first();!q.atEnd();q=q.next()){
							BasicBlk succ = (BasicBlk)q.elem();
							if(nCont[succ.id]){
								x = true;
								break;
							}
						}
					}
				}
				
				boolean n = nIsStore[blk.id] && nSameAddr[blk.id] || x;
				if(nCont[blk.id]!=n || xCont[blk.id]!=x) change = true;
				nCont[blk.id] = n;
				xCont[blk.id] = x;
			}
		}
	}
	
	
	public void compInCont() {
		nInCont = new boolean[idBound];
		xInCont = new boolean[idBound];
		Arrays.fill(nInCont, false);
		Arrays.fill(xInCont, false);
		boolean change = true;
		while(change){
			change = false;
			for(BiLink p=f.flowGraph().basicBlkList.first();!p.atEnd();p=p.next()){
				BasicBlk blk = (BasicBlk)p.elem();
				boolean n = false;
				
				if(blk!=f.flowGraph().exitBlk()){
					if(nIsStore[blk.id] && !nSameAddr[blk.id]) {
						n = true;
					}
					else {
						for(BiLink q=blk.predList().first();!q.atEnd();q=q.next()){
							BasicBlk pred = (BasicBlk)q.elem();
							if(xInCont[pred.id]){
								n = true;
								break;
							}
						}
					}
				}
				
				boolean x = xIsStore[blk.id] && !xSameAddr[blk.id] || n;
				if(nInCont[blk.id]!=n || xInCont[blk.id]!=x) change = true;
				nInCont[blk.id] = n;
				xInCont[blk.id] = x;
			}
		}
	}
	
	
	public void compDelayed() {
		nDelayed = new boolean[idBound];
		xDelayed = new boolean[idBound];
		Arrays.fill(nDelayed, false);
		Arrays.fill(xDelayed, false);
		boolean change = true;
		while(change){
			change = false;
			for(BiLink p=f.flowGraph().basicBlkList.first();!p.atEnd();p=p.next()){
				BasicBlk blk = (BasicBlk)p.elem();
				boolean n = false;

				if(blk!=f.flowGraph().entryBlk()){
					n = true;
					for(BiLink q=blk.predList().first();!q.atEnd();q=q.next()){
						BasicBlk pred = (BasicBlk)q.elem();
						if(!xDelayed[pred.id]){
							n = false;
							break;
						}
						else if(!pde && xInCont[pred.id]) {
							if(!nCont[blk.id]) {
								n = false;
								break;
							}
						}
					}
				}
				
				boolean x = xCand[blk.id] || (n && !nBlock[blk.id] && !xBlock[blk.id] && !nModidx[blk.id] && !xModidx[blk.id]);
				if(!pde && x && nInCont[blk.id]) {
					if(!xCont[blk.id]) {
						x = false;
					}
				}
				
				if(nDelayed[blk.id]!=n || xDelayed[blk.id]!=x) change = true;
				nDelayed[blk.id] = n;
				xDelayed[blk.id] = x;
			}
		}
	}
	
	
	public void compInsert() {
		nInsert = new boolean[idBound];
		xInsert = new boolean[idBound];
		Arrays.fill(nInsert, false);
		Arrays.fill(xInsert, false);
		boolean change = true;
		while(change){
			change = false;
			for(BiLink p=f.flowGraph().basicBlkList.last();!p.atEnd();p=p.prev()){
				BasicBlk blk = (BasicBlk)p.elem();
				boolean x = false;
				if(xDelayed[blk.id] && !xDead[blk.id]) {
					if(blk!=f.flowGraph().exitBlk()){
						for(BiLink q=blk.succList().first();!q.atEnd();q=q.next()){
							BasicBlk succ = (BasicBlk)q.elem();
							if(!nDelayed[succ.id]){
								x = true;
								break;
							}
						}
					}
				}
				
				boolean n = nDelayed[blk.id] && !xDelayed[blk.id] && !nDead[blk.id];
				if(nInsert[blk.id]!=n || xInsert[blk.id]!=x) change = true;
				nInsert[blk.id] = n;
				xInsert[blk.id] = x;
			}
		}
	}


	public boolean checkType(LirNode exp){
		char type=Type.toString(exp.type).charAt(0);
		return (type=='I' || type=='F');
	}
	
	
	public void localCodeMotion(){
		for(int i=bVecInOrderOfRPost.length-1;i>0; i--) {
			BasicBlk blk = bVecInOrderOfRPost[i];
			ArrayList<ArrayList<LirNode>> localStore = new ArrayList<ArrayList<LirNode>>();
			ArrayList<LirNode> localAddr = new ArrayList<LirNode>();
			for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()){
				LirNode node = (LirNode)p.elem();
				
				// Checking mod statements
				if(node.opCode==Op.CALL){
					localStore = new ArrayList<ArrayList<LirNode>>();
					localAddr = new ArrayList<LirNode>();
				}
				
				// Checking target statements
				if(!isStore(node))continue;
				
				
				// Preparations
				LirNode addr = getAddr(node.kid(0));
				if(!checkType(addr)) continue;
				
				if(addr.opCode != Op.FRAME) {
					localStore = new ArrayList<ArrayList<LirNode>>();
					localAddr = new ArrayList<LirNode>();
					continue;
				}
				
				ArrayList<LirNode> vars = new ArrayList<LirNode>();
				collectVars(vars,node.kid(0));
				
				LirNode idx = getIndex(node.kid(0));

				// Do code motion
				if(checkLocal(node,addr,idx,localStore,localAddr)) {
					localCM(node,addr,vars,blk,p);
				}
				
				// For following store statement code motion
				ArrayList<LirNode> mem = new ArrayList<LirNode>();
				mem.add(addr);
				mem.add(idx);
				
				localStore.add(mem);
				localAddr.add(addr.makeCopy(env.lir));
			}
		}
	}
	
	
	boolean checkLocal(LirNode node, LirNode addr, LirNode idx, ArrayList<ArrayList<LirNode>> localStore, ArrayList<LirNode> localAddr){
		for(int i=0; i<localStore.size();i++) {
			ArrayList<LirNode> mem = (ArrayList<LirNode>)localStore.get(i);
			LirNode maddr = (LirNode)mem.get(0);
			LirNode midx = (LirNode)mem.get(1);
			if(maddr.equals(addr) && midx.equals(idx)) {
				return true;
			}
		}
		
		if(localAddr.contains(addr)){
			int pos = localAddr.indexOf(addr);
			for(int i=pos+1;i<localAddr.size();i++){
				LirNode la = (LirNode)localAddr.get(i);
				if(!la.equals(addr)){
					return true;
				}
			}
		}
		return false;
	}
	
	boolean isSame(LirNode ar1, LirNode ar2, ArrayList<LirNode> vars1, ArrayList<LirNode> vars2) {
		
		if(ar1.equals(ar2)) {
			return true;
		}
		
		LirNode addr1 = getAddr(ar1);
		LirNode addr2 = getAddr(ar2);
		
		if(!addr1.equals(addr2)) {
			return false;
		}
		
		if(addr1.opCode==Op.REG || addr2.opCode==Op.REG) {
			return true;
		}
		
		if(vars1.size() > 0 || vars2.size() > 0) {
			return true;
		}

		LirNode idx1 = getIndex(ar1);
		LirNode idx2 = getIndex(ar2);
		
		if(idx1==null || idx2==null) {
			return true;
		}
		
		return idx1.equals(idx2);
	}
	
	LirNode getIndex(LirNode mem) {
		if(mem.kid(0).opCode!=Op.ADD) {
			return null;
		}
		return mem.kid(0).kid(1);
	}
	
	boolean localCM(LirNode expr, LirNode addr, ArrayList<LirNode> vars, BasicBlk blk, BiLink p){
		BiLink latest = null;
		for(BiLink q=p.next();!q.atEnd();q=q.next()){
			LirNode node = (LirNode)q.elem();
			if(!checkType(node)) continue;
			
			ArrayList<LirNode> nvars = new ArrayList<LirNode>();
			collectVars(nvars,node);
			if(nvars.contains(expr.kid(0))) {
				return false;
			}
			
			/*
			 *  Checking local mod statements
			 */
			if(isMustMod(expr.kid(0),node,vars,nvars,blk,p)) {
				if(node.kid(0).kid(0).equals(expr.kid(0).kid(0))) {
					System.out.println("Local Dead!!!");
					p.unlink();
					return true;
				}
				return false;
			}
			
			/*
			 *  Checking local used statements
			 */
			if(isLoad(node) && isSame(node.kid(1), expr.kid(0), vars, nvars)) {
				return false;
			}
			
			/*
			 *  Checking same array referencing store statement
			 */
			if(!isStore(node)) {
				if(node.opCode==Op.SET && node.kid(0).opCode==Op.REG && vars.contains(node.kid(0))) {
					return false;
				}
				continue;
			}
			
			if(!pde) {
				LirNode node_addr = getAddr(node.kid(0));
				if(node_addr.equals(addr)){
					// This store statement refers to the same array with expr
					if(latest!=null){
						System.out.println("Local Code Motion!");
						q.addBefore(expr.makeCopy(env.lir));
						p.unlink();
						return true;
					}
				}else{
					latest = q;
				}
			}
		}
		return false;
	}
	
	
	
	private void globalCodeMotion() {
		ArrayList<LirNode> insertNode = new ArrayList<LirNode>();
		for(int i=bVecInOrderOfRPost.length-1;i>0;i--) {
			BasicBlk blk = bVecInOrderOfRPost[i];
			
			for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()) {
				LirNode node = (LirNode)p.elem();
				
				/*
				 *  Checking out of targets
				 */
				if(!isStore(node) || insertNode.contains(node.kid(0)) || !checkType(node))continue;

				/*
				 *  Preparation
				 */
				insertNode.add(node.kid(0).makeCopy(env.lir));
				LirNode addr = getAddr(node.kid(0));
				if(addr.opCode != Op.FRAME || !checkType(addr)) {
					continue;
				}

				ArrayList<LirNode> vars = new ArrayList<LirNode>();
				collectVars(vars,node.kid(0));
				
				compLocalProperty(node,addr,vars);
				compCand(node,addr,blk, p.next(), vars);
				compDead();
				
				if(dce(blk)) {
					System.out.println("Dead!");
					p.unlink();
					continue;
				}
				
				compCont();
				compInCont();
				compDelayed();
				compInsert();
				
				if(sinking(blk)) {
					System.out.println("Sinking!");
					LirNode newNode = createNewStatement(node);
					LirNode newStore = node.makeCopy(env.lir);
					newStore.setKid(1, newNode.kid(0));
					insert(newStore,addr,vars);
					node.setKid(0, newNode.kid(0).makeCopy(env.lir));
				}
			}
		}
	}
	
	LirNode createNewStatement(LirNode statement) {
    	LirNode newSt = statement.makeCopy(env.lir);
    	Symbol dstSym = sstab.newSsaSymbol(tmpSymName, newSt.type);
    	LirNode nn = env.lir.symRef(Op.REG, dstSym.type, dstSym, ImList.Empty);
    	LirNode newNode = env.lir.operator(Op.SET, newSt.type, nn, newSt.kid(1),ImList.Empty).makeCopy(env.lir);
    	return newNode;
    }
	
	void insert(LirNode node, LirNode addr, ArrayList<LirNode> vars) {
		for(int i=bVecInOrderOfRPost.length-1;i>0;i--) {
			BasicBlk blk = bVecInOrderOfRPost[i];
			if(nInsert[blk.id]) {
				boolean did = false;
				if(nIsStore[blk.id] && !nSameAddr[blk.id]) {
					for(BiLink p=blk.instrList().first();!p.atEnd();p=p.next()) {
						LirNode n2 = (LirNode)p.elem();
						
						if(isStore(n2)) {
							LirNode addr2 = getAddr(n2.kid(0));
							if(addr.equals(addr2)) {
								p.addBefore(node.makeCopy(env.lir));
								did = true;
								break;
							}
						}
						
						ArrayList<LirNode> nvars = new ArrayList<LirNode>();
						collectVars(nvars,n2);
						
						if(isMayMod(node.kid(0),addr,n2,vars,nvars,blk,p)) {
							p.addBefore(node.makeCopy(env.lir));
							did = true;
							break;
						}
					}
				}
				
				if(!did) {
					blk.instrList().first().addBefore(node.makeCopy(env.lir));
				}
			}

			if(xInsert[blk.id]) {
				boolean did = false;
				if(xIsStore[blk.id] && !xSameAddr[blk.id]) {
					
					for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()) {
						LirNode n2 = (LirNode)p.elem();
						
						if(isStore(n2)) {
							LirNode addr2 = getAddr(n2.kid(0));
							if(addr.equals(addr2)) {
								p.addAfter(node.makeCopy(env.lir));
								did = true;
								break;
							}
						}
						
						ArrayList<LirNode> nvars = new ArrayList<LirNode>();
						collectVars(nvars,n2);
						
						if(isMayMod(node.kid(0),addr,n2,vars,nvars,blk,p)) {
							p.addAfter(node.makeCopy(env.lir));
							did = true;
							break;
						}
					}
				}
				
				if(!did) {
					blk.instrList().last().addBefore(node.makeCopy(env.lir));
				}
			}
		}
	}
	
	public boolean sinking(BasicBlk blk) {
		if(!xDelayed[blk.id] || xInsert[blk.id]) return false;
		
		for(int i=bVecInOrderOfRPost.length-1;i>0;i--) {
			BasicBlk v = bVecInOrderOfRPost[i];
			if(v.id == blk.id) continue;
			if(nInsert[v.id] || xInsert[v.id]) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean dce(BasicBlk blk) {
		if(xDead[blk.id] && !xUsed[blk.id]) {
			return true;
		}
		return false;
	}

	
	void init() {
		dfst=(DFST)f.require(DFST.analyzer);
		idBound = f.flowGraph().idBound();
		bVecInOrderOfRPost = dfst.blkVectorByRPost();
	}
	
	void gsa() {
		init();
		localCodeMotion();
		globalCodeMotion();
	}
         
    public boolean doIt(Function function,ImList args) {
    	
      //
//      long startTime = System.currentTimeMillis();
      //
      f = function;
      env.println("****************** doing GSA to "+
    		  f.symbol.name,SsaEnvironment.MinThr);

      gsa();

      f.flowGraph().touch();
      return(true);
    }
 }
