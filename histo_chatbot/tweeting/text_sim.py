#!/usr/bin/env python
# -*- coding:utf-8 -*-

import math
import datetime
import numpy as np

from util import get_shortenURL
from util import remove_citation
from util import get_tweet_maxleng

from mysql import get_obj
ms = get_obj("localhost", db="twitter_histo_chatbot")


def rank(tvec, y="", m="", d="", lang="", target_event_ids=[], alpha=0.95):
    data = get_wikipedia_event_texts(y, m, d, lang, target_event_ids)
    if len(data) < 1: return [""]

    scores = []
    total = len(data)
    impl_max = np.max([d[9] for d in data])

    for i in range(total):
        d = data[i]
        wvec, impl_score = d[1], d[9]
        text_sim = cos(tvec, wvec)
        sim = alpha * text_sim + (1-alpha) * (impl_score/impl_max)
        scores.append(sim)

    idx = np.argmax(scores)
    url = data[idx][7]
    surl = get_shortenURL(url)

    if surl is None:
        surl = ""
    else:
        surl = surl.encode("utf-8")

    wtext = remove_citation(data[idx][0])

    return [wtext + " " + surl]


def get_wikipedia_event_texts(y, m, d, lang, target_event_ids=[]):
    cond = " where"
    if y != "": cond += " year = " + str(y)
    if len(target_event_ids) > 0:
        tmp = " (event_id = " + str(target_event_ids[0])
        for i in range(1, len(target_event_ids)):
            tmp += " or event_id = " + str(target_event_ids[i])
        if cond != " where": cond += " or "
        cond += tmp + ")"

    if m != "":
        if cond != " where": cond += " and "
        cond += " month = " + str(m)

    if d != "":
        if cond != " where": cond += " and "
        cond += " day = " + str(d)

    if lang != "":
        if cond != " where": cond += " and "
        cond += " lang = '" + lang + "'"

    date = (datetime.date.today().strftime("%Y/%m/%d")).split("/")
    ty, tm, td = date[0], date[1], date[2]
    if y == "" and m == "" and d == "":
        y, m, d = ty, tm, td
    else:
        if y == "" or y < 0: y = ty
        if m == "" or m < 0 or m > 12: m = tm
        if d == "" or d < 0 or d > 31: d = td

    date_tweet = datetime.date(int(y), int(m), int(d))
    maxleng = get_tweet_maxleng(lang)

    if cond != " where": cond += " and text_leng < " + str(maxleng)
    else: cond += " text_leng < " + str(maxleng)
    sql = "select event_id, text, tokens, url, year, month, day, importance from text_sim_data" + cond + " LIMIT 50"

    tmp_data = ms.select(sql)
    data = []
    for dt in tmp_data:
        eid = dt[0]
        wtext = dt[1]
        if type(wtext) != type("str"): wtext = wtext.encode("utf-8")
        v = dt[2].split("\t")
        url = dt[3]
        wy, wm, wd = dt[4], dt[5], dt[6]
        impl_score = dt[7]

        if y != "" and wy is None:
            wy = int(y)

        if m != "" and (wm is None or int(wm) > 12):
            wm = int(m)

        if d != "" and (wd is None or int(wd) > 31):
            wd = int(d)
            if int(wy) % 4 == 0 and int(wm) == 2:
                if int(wd) > 29: continue
            else:
                if int(wd) > 28: continue

        diff = (date_tweet - datetime.date(wy, wm, wd)).total_seconds() / 360
        if diff < 0: diff = diff * -1

        data.append((wtext, v, diff, str(wy), str(wm), str(wd), lang, url, eid, impl_score))

    return data


def cos(wv1, wv2):
    wlist = [w for w in  wv1]
    wlist.extend(wv2)
    wset = list(set(wlist))
    v1 = [wv1.count(w) for w in wset]
    v2 = [wv2.count(w) for w in wset]
    return cos_calc(v1, v2)


def cos_calc(v1, v2):
    num = len(v1)
    numerator = sum([v1[i] * v2[i] for i in range(num)])
    if numerator == 0: return 0.0
    sum1 = sum([v1[i]**2 for i in range(num)])
    sum2 = sum([v2[i]**2 for i in range(num)])
    denominator = math.sqrt(sum1) * math.sqrt(sum2)
    if not denominator: return 0.0
    return float(numerator) / denominator
