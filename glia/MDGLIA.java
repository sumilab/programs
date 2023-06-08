/* ---------------------------------------------------------------------
%   Copyright (C) 2007 Association for the COINS Compiler Infrastructure
%       (Read COPYING for detailed information.)
--------------------------------------------------------------------- */
package coins.ssa;

import java.util.ArrayList;
import java.util.Arrays;

import coins.backend.Data;
import coins.backend.Function;
import coins.backend.LocalTransformer;
import coins.backend.Op;
import coins.backend.cfg.BasicBlk;
import coins.backend.lir.LirNode;
import coins.backend.util.BiLink;
import coins.backend.util.ImList;

/**
 *   Multi-dimensional GLIA.
 **/
public class MDGLIA implements LocalTransformer {
	public static final int THR = SsaEnvironment.OptThr;
	/** The threshold of debug print **/
	public static final int THR2 = SsaEnvironment.AllThr;
	private SsaEnvironment env;
	private SsaSymTab sstab;
	private Function f;
	private NGLIA nglia;
	
	int exprIndexNum;
	int idBound;
	int[] minIndex;
	int[] nlocalIndex;
	int[] xlocalIndex;
	int[] nIndexFlow;
	boolean[] nLocalKeepDim;
	boolean[] xLocalKeepDim;
	boolean[] nKeepDim;
	boolean[] xKeepDim;
	
	/**
	 * Constructor.
	 * 
	 * @param env The environment of the SSA module
	 * @param sstab The current symbol table on SSA form
	 **/
	public MDGLIA(SsaEnvironment env, SsaSymTab sstab) {
		this.env = env;
		this.sstab = sstab;
	}
	
	
	/**
	 * Do Multi-dimensional GLIA.
	 * 
	 * @param func The current function
	 * @param args The list of options
	 **/
	public boolean doIt(Function func, ImList args) {
		f = func;
		env.println("****************** doing MDGLIA. to " + f.symbol.name, SsaEnvironment.MinThr);
		nglia = new NGLIA(env,sstab,f);
		nglia.init();
		idBound = f.flowGraph().idBound();
		invoke();
		f.flowGraph().touch();
		return (true);
	}
	
	
	boolean localCM(LirNode node, LirNode addr, ArrayList vars, BasicBlk blk, BiLink q){
		int indexNum = getIndexNum(node.kid(1));
		int same_index_num = 0;
		BiLink insertCand = null;
		BiLink latest = null;
		for(BiLink p=q.prev();!p.atEnd();p=p.prev()){
			LirNode n = (LirNode)p.elem();
			if(nglia.isKill(node.kid(1),n,vars,blk,p))return false;
			ArrayList nvars = new ArrayList();
			nglia.collectVars(nvars,n);
			if(nvars.contains(node.kid(0)))return false;
			if(!(nglia.isLoad(n) && nglia.sameAddr(n,addr) && indexNum==getIndexNum(n.kid(1))))continue;
			int same = checkIndexNum(node.kid(1),n.kid(1),indexNum);
			if(same<same_index_num){
				latest.addBefore(node.makeCopy(env.lir));
				return true;
			}else{
				if(same==0) insertCand = p;
				else if(latest!=null) insertCand = latest;
				same_index_num = same;
			}
			latest = p;
		}
		if(insertCand!=null && same_index_num>0){
			insertCand.addBefore(node.makeCopy(env.lir));
			return true;
		}
		return false;
	}
	
	
	void localCodeMotion(){
		for(int i=1;i<nglia.bVecInOrderOfRPost.length; i++) {
			BasicBlk blk = nglia.bVecInOrderOfRPost[i];
			for(BiLink p=blk.instrList().first();!p.atEnd();p=p.next()){
				LirNode node = (LirNode)p.elem();
				if(!nglia.isLoad(node))continue;
				LirNode addr = nglia.getAddr(node.kid(1));
				ArrayList vars = new ArrayList();
				nglia.collectVars(vars,node.kid(1));
				if(p!=blk.instrList().first()){
					if(localCM(node,addr,vars,blk,p)) p.unlink();
				}
			}
		}
	}
	
	
	private void invoke(){
		localCodeMotion();
		globalCodeMotion();
	}
	
	
	void globalCodeMotion(){
		ArrayList insertNode = new ArrayList();
		for(int i=1;i<nglia.bVecInOrderOfRPost.length; i++) {
			BasicBlk blk = nglia.bVecInOrderOfRPost[i];
			for(BiLink p=blk.instrList().first();!p.atEnd();p=p.next()){
				LirNode node = (LirNode)p.elem();
				if(!nglia.isLoad(node) || insertNode.contains(node.kid(1)) || !nglia.checkType(node))continue;
				insertNode.add(node.kid(1).makeCopy(env.lir));
				exprIndexNum = getIndexNum(node.kid(1)); 
				LirNode addr = nglia.getAddr(node.kid(1));
				ArrayList vars = new ArrayList();
				nglia.collectVars(vars,node.kid(1));
				gliacda(node,addr,vars);
				LirNode newNode = insertNewNode(node.makeCopy(env.lir),addr,vars);
				if(newNode!=null) nglia.replace(newNode);
			}
		}
	}
	
	
	public void gliacda(LirNode node, LirNode addr, ArrayList vars){
		nglia.compLocalProperty(node.kid(1),addr,vars);
		compLocalProperty(node,addr,vars);
		nglia.compUSafe();
		nglia.compDSafe();
		nglia.compPartialSafe();
		nglia.compEarliest();
		nglia.compKeepOrder();
		compIndexFlow(node,addr);
		compKeepDim();
		compDelayed();
		nglia.compLatest();
		nglia.compIsolated();
		nglia.compInsert();
		nglia.compReplace();
	}
	
	
	private void compLocalProperty(LirNode expr, LirNode addr, ArrayList vars){
		minIndex = new int[idBound];
		nlocalIndex = new int[idBound];
		xlocalIndex = new int[idBound];
		nLocalKeepDim = new boolean[idBound];
		xLocalKeepDim = new boolean[idBound];
		Arrays.fill(minIndex, -1);
		Arrays.fill(nlocalIndex, 0);
		Arrays.fill(xlocalIndex, 0);
		Arrays.fill(nLocalKeepDim, true);
		Arrays.fill(xLocalKeepDim, true);
		for(int i=1;i<nglia.bVecInOrderOfRPost.length; i++) {
			BasicBlk blk = nglia.bVecInOrderOfRPost[i];
			nlocalIndex[blk.id] = nLocalIndex(expr,addr,blk,vars);
			xlocalIndex[blk.id] = xLocalIndex(expr,addr,blk,vars);
		}
	}
	
	
	int nLocalIndex(LirNode expr, LirNode addr, BasicBlk blk, ArrayList vars){
		int maxIndex = 0;
		for(BiLink p=blk.instrList().first();!p.atEnd();p=p.next()){
			LirNode node = (LirNode)p.elem();
			if(nglia.isKill(expr.kid(1),node,vars,blk,p))break;
			if(!nglia.isLoad(node) || !nglia.sameAddr(node,addr) || !sameIndexNum(node.kid(1)))continue;
			int index = checkIndexNum(expr,node,exprIndexNum);
			if(index < maxIndex) nLocalKeepDim[blk.id] = false;
			else{
				if(minIndex[blk.id]==-1) minIndex[blk.id] = index;
				maxIndex = index;
			}
		}
		return maxIndex;
	}
	
	
	int xLocalIndex(LirNode expr, LirNode addr, BasicBlk blk, ArrayList vars){
		int maxIndex = -1;
		for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()){
			LirNode node = (LirNode)p.elem();
			if(nglia.isKill(expr.kid(1),node,vars,blk,p))break;
			if(!nglia.isLoad(node) || !nglia.sameAddr(node,addr) || !sameIndexNum(node.kid(1)))continue;
			int index = checkIndexNum(expr,node,exprIndexNum);
			if(maxIndex!=-1 && index > maxIndex) xLocalKeepDim[blk.id] = false;
			else maxIndex = index;
		}
		if(maxIndex==-1) maxIndex=0;
		return maxIndex;
	}
	
	
	private void compIndexFlow(LirNode expr, LirNode addr) {
		nIndexFlow = new int[idBound];
		Arrays.fill(nIndexFlow, 0);
		for (BiLink p=f.flowGraph().basicBlkList.first();!p.atEnd();p=p.next()) {
			BasicBlk blk = (BasicBlk) p.elem();
			int index = 0;
			for(BiLink q=blk.predList().first();!q.atEnd();q=q.next()){
				BasicBlk pred = (BasicBlk)q.elem();
				int predIndex = xlocalIndex[pred.id];
				if(index < predIndex) index = predIndex;
			}
			nIndexFlow[blk.id] = index;
		}
	}
	
	
	private void compKeepDim(){
		nKeepDim = new boolean[idBound];
		xKeepDim = new boolean[idBound];
		for(BiLink p=f.flowGraph().basicBlkList.first();!p.atEnd();p=p.next()){
			BasicBlk blk = (BasicBlk)p.elem();
			nKeepDim[blk.id] = nglia.nKeepOrder[blk.id] && (!nglia.nSameAddr[blk.id] || minIndex[blk.id]!=-1 && nLocalKeepDim[blk.id] && nIndexFlow[blk.id]<nlocalIndex[blk.id]);
			xKeepDim[blk.id] = nglia.xKeepOrder[blk.id] && xLocalKeepDim[blk.id];
		}
	}
	
	
	private void compDelayed() {
		nglia.nDelayed = new boolean[idBound];
		nglia.xDelayed = new boolean[idBound];
		Arrays.fill(nglia.nDelayed, true);
		Arrays.fill(nglia.xDelayed, true);
		boolean change = true;
		while(change){
			change = false;
			for(BiLink p=f.flowGraph().basicBlkList.first();!p.atEnd();p=p.next()){
				BasicBlk blk = (BasicBlk)p.elem();
				boolean n = false;
				if(nglia.nEarliest[blk.id]) n = true;
				else if(blk!=f.flowGraph().entryBlk()){
					n = true;
					for(BiLink q=blk.predList().first();!q.atEnd();q=q.next()){
						BasicBlk pred = (BasicBlk)q.elem();
						if(!nglia.xDelayed[pred.id] || !xKeepDim[pred.id] || !nglia.xKeepOrder[pred.id] || nglia.xIsSame[pred.id]){
							n = false;
							break;
						}
					}
				}
				boolean x = nglia.xEarliest[blk.id] || (n && !nglia.nIsSame[blk.id] && nKeepDim[blk.id]);
				if(nglia.nDelayed[blk.id]!=n || nglia.xDelayed[blk.id]!=x) change = true;
				nglia.nDelayed[blk.id] = n;
				nglia.xDelayed[blk.id] = x;
			}
		}
	}
	
	
	int getIndexNum(LirNode exp){
		int num = 0;
		LirNode kid = exp.kid(0);
		while(kid.opCode==Op.ADD){
			num++;
			kid = kid.kid(0);
		}
		return num;
	}
	
	
	boolean sameIndexNum(LirNode exp){
		int indexNum = getIndexNum(exp);
		return (indexNum==exprIndexNum);
	}
	
	
	int checkIndexNum(LirNode exp1, LirNode exp2, int index){
		if(exp2.opCode==Op.SET) return checkIndexNum(exp1.kid(1).kid(0),exp2.kid(1).kid(0),index);
		else if(exp2.opCode==Op.MEM) return checkIndexNum(exp1.kid(0),exp2.kid(0),index);
		LirNode expKid = exp1;
		LirNode exprKid = exp2;
		while(expKid.opCode==Op.ADD){
			if(exprKid.equals(expKid))break;
			expKid = expKid.kid(0);
			exprKid = exprKid.kid(0);
			index--;
		}
		return index;
	}
	
	
	LirNode insertNewNode(LirNode expr, LirNode addr, ArrayList vars){
		LirNode newNode = null;
		for(int i=1;i<nglia.bVecInOrderOfRPost.length; i++) {
			BasicBlk blk = nglia.bVecInOrderOfRPost[i];
			if(nglia.nInsert[blk.id]){
				boolean same_addr = false;
				boolean insert = false;
				int index = nIndexFlow[blk.id];
				BiLink start = blk.instrList().first();
				for(BiLink p=start;!p.atEnd();p=p.next()){
					LirNode node = (LirNode)p.elem();
					if(nglia.isKill(expr.kid(1),node,vars,blk,p))break;
					if(!nglia.isLoad(node))continue;
					if(node.kid(1).equals(expr.kid(1))){
						newNode = nglia.getNewNode(newNode,expr);
						p.addBefore(newNode);
						nglia.replace(node,newNode,blk,p);
						insert = true;
						break;
					}
					if(nglia.sameAddr(node,addr)){
						same_addr = true;
						if(sameIndexNum(node.kid(1))){
							int node_index = checkIndexNum(expr,node,exprIndexNum);
							if(index>node_index){
								insert = true;
								newNode = nglia.getNewNode(newNode,expr);
								p.addBefore(newNode);
								break;
							}else if(index<node_index){
								index = node_index;
							}
						}
					}else if(same_addr){
						insert = true;
						newNode = nglia.getNewNode(newNode,expr);
						p.addBefore(newNode);
						break;
					}
				}
				if(!insert){
					newNode = nglia.getNewNode(newNode,expr);
					for(BiLink p=start;!p.atEnd();p=p.next()){
						LirNode node = (LirNode)p.elem();
						if(node.opCode!=Op.PROLOGUE){
							p.addBefore(newNode);
							break;
						}
					}
					
				}
			}
			if(nglia.xInsert[blk.id]){
				boolean insert = false;
				if(nglia.xSameAddr[blk.id]){
					BiLink latest = null;
					BiLink sameAddrLast = null;
					int index = -1;
					for(BiLink p=blk.instrList().last();!p.atEnd();p=p.prev()){
						LirNode node = (LirNode)p.elem();
						if(nglia.isKill(expr.kid(1),node,vars,blk,p))break;
						if(!nglia.isLoad(node))continue;
						if(node.equals(newNode)){
							insert = true;
							break;
						}
						if(node.kid(1).equals(expr.kid(1))){
							newNode = nglia.getNewNode(newNode,expr);
							p.addBefore(newNode);
							nglia.replace(node,newNode,blk,p);
							insert = true;
							break;
						}
						if(nglia.sameAddr(node,addr)){
							if(sameIndexNum(node.kid(1))){
								int node_index = checkIndexNum(expr,node,exprIndexNum);
								if(index!=-1 && node_index>index){
									insert = true;
									newNode = nglia.getNewNode(newNode,expr);
									sameAddrLast.addBefore(newNode);
									break;
								}else if(index<node_index){
									index = node_index;
									sameAddrLast = p;
								}
							}
							if(latest!=null){
								newNode = nglia.getNewNode(newNode,expr);
								latest.addBefore(newNode);
								insert = true;
								break;
							}
						}else{
							latest = p;
						}
					}
				}
				if(!insert){
					insert = true;
					newNode = nglia.getNewNode(newNode,expr);
					blk.instrList().last().addBefore(newNode);
				}
			}
		}
		return newNode;
	}
	
	
	public void printProp(LirNode expr) {
		System.out.println("----------------------------------------------------");
		for (BiLink p = f.flowGraph().basicBlkList.first(); !p.atEnd(); p = p.next()) {
			BasicBlk blk = (BasicBlk) p.elem();
			System.out.println("blk: " + blk.label());
			System.out.println("NlocalIndex: " + nlocalIndex[blk.id]+", XlocalIndex:"+xlocalIndex[blk.id]);
			System.out.println("NIndexFlow: " + nIndexFlow[blk.id]);
			System.out.println("NLocalKeepDim: " + nLocalKeepDim[blk.id]+", XLocalKeepDim:"+xLocalKeepDim[blk.id]);
			System.out.println("NKeepDimension: " + nKeepDim[blk.id] + ", XKeepDimension: "+ xKeepDim[blk.id]);
			System.out.println("----------------------------------------------------");
		}
		System.out.println("target -- " + expr);
		System.out.println("");
	}
	
	
	/**
	 * @param data Data to be processes.
	 * @param args List of optional arguments.
	 **/
	public boolean doIt(Data data, ImList args) {return true;}
	/**
	 * Return the name of this optimizer.
	 **/
	public String name() {return "MDGLIA";}
	/**
	 * Return brief descriptions of this optimizer.
	 **/
	public String subject() {return "MDGLIA";}
}
