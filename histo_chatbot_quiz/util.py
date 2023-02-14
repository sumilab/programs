#!/usr/bin/env python
# -*- coding:utf-8 -*-

import nltk
from StopWord_en import StopWords
import math
import time
import json
from keys import get_keys
from requests_oauthlib import OAuth1Session

def m2v(v, l):
    vmap = dict([(t[0], t[1]) for t in v])
    vec = []
    for i in range(l):
        if i in vmap: vec.append(float(vmap[i]))
        else: vec.append(0.0)
    return vec


def create_tokens(text, lang):
    tokens, nouns, verbs = [], [], []
    if lang == "en":
        lemmatizer = nltk.stem.WordNetLemmatizer()
        sw = StopWords()

        try:
            tokens = [lemmatizer.lemmatize(t) for t in text.lower().split() if not sw.is_stop_word(t) and len(t) > 1]
        except:
            tokens = [lemmatizer.lemmatize(t) for t in text.decode("utf-8").lower().split() if not sw.is_stop_word(t) and len(t) > 1]


    else:
        tokens = [text]

    return [tokens, nouns, verbs]


def jaccard(x, y):
    sx = set(x)
    sy = set(y)
    return len(sx & sy) / float(len((sx | sy)))


def cos_sim(v1, v2):
   num = len(v1)
   numerator = sum([v1[i] * v2[i] for i in range(num)])

   if numerator == 0: return 0.0

   sum1 = sum([v1[i]**2 for i in range(num)])
   sum2 = sum([v2[i]**2 for i in range(num)])
   denominator = math.sqrt(sum1) * math.sqrt(sum2)

   if not denominator: return 0.0
   else: return float(numerator) / denominator


def use_twitter_api(url, params, get=True):
    tmp = get_keys()
    api, sec, acc, ase = tmp[0], tmp[1], tmp[2], tmp[3]
    if get: req = OAuth1Session(api, sec, acc, ase).get(url, params=params)
    else: req = OAuth1Session(api, sec, acc, ase).post(url, params=params)
    data = json.loads(req.text)  
    if 'errors' in data and data['errors'][0]['message'] == 'Rate limit exceeded':
        #print "Error...", data
        #print "Sleeping.."
        time.sleep(60*15)
        if get: req = OAuth1Session(api, sec, acc, ase).get(url, params=params)
        else: req = OAuth1Session(api, sec, acc, ase).get(url, params=params)
        data = json.loads(req.text)  

    return data

