# -*- coding:utf-8 -*-

import pickle
import json
from mysql import get_obj
from MySQLdb import escape_string
from keys import get_keys
from requests_oauthlib import OAuth1Session
from history_hashtags import get_tags
chtags = get_tags()
cnames = chtags[-1]
ms = get_obj("localhost", db="twitter_histo_chatbot")

def m2v(v, l):
    vmap = dict([(t[0], t[1]) for t in v])
    vec = []
    for i in range(l):
        if i in vmap: vec.append(float(vmap[i]))
        else: vec.append(0.0)
    return vec

def get_tweet_data(tbl, cportion=-1, tportion=-1):
    sql = "select tweet_id, tweet_text, hashtag_texts, urls from " + tbl
    data = {}
    ddata = ms.select(sql)
    c2n = {}
    if tportion < 0: tportion = len(ddata)
    for d in ddata[:tportion]:
        tid = d[0]
        text = d[1].rstrip().replace("\r\n", "").replace("\n", "").replace("\r", "")#.replace("#", "# ")
        htags = [t.lower() for t in d[2].split("\t")]
        cats = [cnames[i] for i in range(len(chtags[:-1])) for c in chtags[i] for ht in htags if ht == c]

        target = (cportion < 0)
        for c in cats:
            if c not in c2n: c2n[c] = 0
            c2n[c] += 1
            if cportion > 0 and c2n[c] < cportion: target = True
        if not target: continue

        url = [u for u in d[3].split("\t")]
        data[tid] = {}
        data[tid]["text"] = text
        data[tid]["htags"] = htags
        data[tid]["cats"] = cats
        data[tid]["url"] = url
    return data


