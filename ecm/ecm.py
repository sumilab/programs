# -*- coding:utf-8 -*-

import math

def cos(v1, v2):
    num = len(v1)
    numerator = sum([v1[i] * v2[i] for i in range(num)])
    if numerator == 0: return 0.0
    sum1 = sum([v1[i]**2 for i in range(num)])
    sum2 = sum([v2[i]**2 for i in range(num)])
    denominator = math.sqrt(sum1) * math.sqrt(sum2)
    if not denominator: return 0.0
    return float(numerator) / denominator

def paraph_sim(e1, e2, t1=[], t2=[], mode="cos"):
	"""
	e1: feature vectors of a causal relationship
	e2: feature vectors of a causal relationship
	"""
    with open('test.csv', 'w', newline='', encoding='utf8') as f:
        sim_tbl = []
        for i in range(len(e1)):
            p1 = e1[i]
            vals = []
            for j in range(len(e2)):
                p2 = e2[j]
                sval = cos(p1, p2)
                if j == 0: f.write(str(sval))
                else: f.write("," + str(sval))
            if i+1 < len(e1):f.write("\n")

    # Exec. ecm.cpp
    elist = cpp_exec()


def cpp_exec(cmd="./a.out"):
    elist = subprocess.check_output(cmd.split()).decode("utf-8").split("\t\t")
    if elist[0] == "": return []
    eset = []
    for e in elist:
        tmp = e.split("\t")
        eset.append([int(tmp[0])-1, int(tmp[1])-1])
    return eset
