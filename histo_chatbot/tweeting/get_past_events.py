#!/usr/bin/env python
# -*- coding:utf-8 -*-

import codecs
import random
import pickle
import datetime

from util import ms
from util import abs_path
from util import make_page_name
from util import get_texts
from util import get_tweet_maxleng
from util import combine_date_text


def get_event_texts(lang, replying=False):
    today = str(datetime.date.today()).split("-")
    y = ""
    m = str(int(today[1]))
    d = str(int(today[2]))

    tmp = get_event_cands(lang, y, m, d)

    stored_events, too_long_event_texts = tmp[0], tmp[1]
    not_targets = get_tweeted_events(m, d)

    targets = []
    for se in stored_events:
        if se[0] in not_targets: continue
        targets.append(se)

    is_store = False
    if len(targets) > 0:
        e = targets[random.randint(0, len(targets)-1)]
        is_store = True

    elif len(too_long_event_texts) > 0:
        e = too_long_text_message(lang)

    else:
        if replying: return "No recorded events"
        else: return ""

    if is_store:
        store = not_targets
        store.append(e[0])
        store_tweeted_events(m, d, store)

    text = e[0]
    return gen_otd_hashtag(text, lang)


def get_event_cands(lang, y, m, d, minleng=10, maxleng=225, tbl="wiki_event", filtered_target_event_ids=[]):
    maxleng = get_tweet_maxleng(lang)
    evt_list = ""
    if len(filtered_target_event_ids) > 0:
        evt_list = "event_id = " + str(filtered_target_event_ids[0])
        for i in range(1, len(filtered_target_event_ids)):
            evt_list += " or event_id = " + str(filtered_target_event_ids[i])

    if lang == "en" or lang == "ja":
        sql = "select text, url, event_id, year, month, day from " + tbl
        cond = " where "
        if len(y) > 0:
            cond += " year = " + str(y)

        if len(m) > 0:
            if cond != " where ": cond += " and "
            cond += " month = " + str(m)

        if len(d) > 0:
            if cond != " where ": cond += " and "
            cond += " day = " + str(d)

        if len(lang) > 0:
            if cond != " where ": cond += " and "
            cond += " lang = '" + lang + "'"

        if evt_list != "":
            if cond != " where ": cond += " and (" + evt_list + ")"
            else: cond += evt_list

        if cond != " where ": sql = sql + cond

        data = [(get_texts(de[0]), de[1], de[2], de[3], de[4], de[5]) for de in ms.select(sql)]

    else:
        url = get_citation_url(lang, y, m, d)
        fname = abs_path + "events/" + lang + "/" + m + "_" + d + ".html"
        data = [(get_texts(l.rstrip()), url, y, m, d) for l in codecs.open(fname, "r", "utf-8")]

    if len(data) < 1 and len(filtered_target_event_ids) > 0:
        sql = "select text, url, event_id, year, month, day from " + tbl + " where "
        evt_list = "event_id = " + str(filtered_target_event_ids[0])
        for i in range(1, len(filtered_target_event_ids)):
            evt_list += " or event_id = " + str(filtered_target_event_ids[i])
        data = [(get_texts(de[0]), de[1], de[2], de[3], de[4], de[5]) for de in ms.select(sql+evt_list)]


    suitable = []
    toolong = []
    for e in data:
        if len(e) > 5: text = combine_date_text(e[3], e[4], e[5], e[0], lang)
        else: text = e[0]
        if len(text) >= minleng and len(text) <= maxleng: suitable.append(e)
        elif len(text) > maxleng: toolong.append(e)

    return [suitable, toolong]


def get_tweeted_events(m, d):
    try:
        data = pickle.load(open("tweeted_events.pkl", "r"))
        if data["today"] != m + "/" + d: return []
        else: return data["events"]
    except:
        return []


def too_long_text_message(lang):
    if lang == "ja": return u"140字以上で記述された出来事しかないので次のリンクをご参照ください。"
    return "see the following link as the description is too long for tweeting."


def store_tweeted_events(m, d, events):
    data = {}
    data["today"] = m + "/" + d
    data["events"] = events
    pickle.dump(data, open("tweeted_events.pkl", 'w'), protocol=2)


def gen_otd_hashtag(text, lang):
    base = "#otd"
    h2 = " #onthisday"
    jh = u" #今日は何の日"

    if len(text+base+h2) < 140: text += base + h2
    elif len(text+base) < 140: text += base

    if lang == "ja" and len(text+jh) < 140: text += jh
    return text


def get_citation_url(lang, y, m, d):
    if len(y) > 0: page = make_page_name(lang, "", "", y=y)
    else: page = make_page_name(lang, int(m)-1, d)
    url = "https://" + lang + ".wikipedia.org/wiki/" + page
    return url

