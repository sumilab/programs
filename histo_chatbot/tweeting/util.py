#!/usr/bin/env python
# -*- coding:utf-8 -*-

import json
import time
import MeCab
import mojimoji
import nltk
import requests
import datetime
import numpy as np
from bs4 import BeautifulSoup
from requests_oauthlib import OAuth1Session
import codecs# for logging

from keys import get_keys

from StopWord_en import StopWords
from mysql import get_obj

ms = get_obj("localhost", db="twitter_histo_chatbot")
langs = ["en", "ja", "es", "de", "fr", "zh", "pl"]
abs_path = ""## Set the absolute path of this file.
access_token = ''  # Set bitly's token used in shortining URL



def make_page_name(lang, m, d, y=None):
    page = ""
    mid = "_"
    if y is None:
        mstr = num2str_month(m, lang, True)
        dstr = num2str_day(d, lang)

        if lang == "es":
            page = dstr + mid + mstr

        if lang == "fr":
            page = dstr + mid + mstr

        if lang == "de":
            mid = "._"
            page = dstr + mid + mstr

        if lang == "ja" or lang == "zh":
            mid = ""
            page = mstr + mid + dstr

        if lang == "en":
            page = mstr + mid + dstr

        if lang == "zh":
            page = mstr + mid + dstr

        elif lang == "pl":
            page = dstr + mid + mstr

    else:
        ystr = str(y)
        page = ystr
        if lang == "ja" or lang == "zh":
            page += "年"

    return page


def num2str_month(m, lang, wiki_style):
    str_m = get_month_name(lang, wiki_style)
    return str_m[m]


def num2str_day(d, lang):
    if lang == "ja" or lang == "zh":
        return str(d) + "日"

    if lang == "fr" and int(d) == 1:
        return str(d) + "er"

    return d


def get_tweet_maxleng(lang):
    if lang == "en": maxleng = 254
    else: maxleng = 124
    return maxleng


def combine_date_text(y, m, d, text, lang):
    dtext = ""
    if lang == "ja" or lang == "zl":
        if y is not None:
            dtext += str(y) + u"年"
        if m is not None and d is not None:
            if len(dtext) > 0: dtext += str(m) + u"月" + str(d) + u"日. "
            else: dtext = str(m) + u"月" + str(d) + u"日. "

        if type(dtext) != type("hi"): dtext = dtext.encode("utf-8")
        if type(text) != type("hi"): text = text.encode("utf-8")
        if text.startswith("\r\n") or text.startswith("\r") or text.startswith("\n"): text = text[1:]

        dtext += text

    else:
        if m is not None and d is not None: dtext = str(m) + "/" + str(d)
        if y is not None:
            if len(dtext) > 0: dtext += "/"
            dtext += str(y) + ". "
        if text.startswith("\r\n") or text.startswith("\r") or text.startswith("\n"): text = text[1:]
        dtext += text

    return dtext
    

def get_lang_names(lang):
    if lang == "ja": return ["英語", "日本語", "ｽﾍﾟｲﾝ語", "ﾄﾞｲﾂ語", "ﾌﾗﾝｽ語", "中国語", "ﾎﾟｰﾗﾝﾄﾞ語"]
    return ["English", "Japanese", "Spanish", "German", "French", "Chinese", "Polish"]


def use_twitter_api(url, params, get=True):
    tmp = get_keys()
    api, sec, acc, ase = tmp[0], tmp[1], tmp[2], tmp[3]
    if get: req = OAuth1Session(api, sec, acc, ase).get(url, params=params)
    else: req = OAuth1Session(api, sec, acc, ase).post(url, params=params)
    data = json.loads(req.text)  
    if 'errors' in data and data['errors'][0]['message'] == 'Rate limit exceeded':
        print ("Error...", data)
        print ("Sleeping..")
        time.sleep(60*15)
        if get: req = OAuth1Session(api, sec, acc, ase).get(url, params=params)
        else: req = OAuth1Session(api, sec, acc, ase).get(url, params=params)
        data = json.loads(req.text)  

    return data


def is_simple_quiz(text):
    text = text.lower()
    if text.find("give me a quiz") >= 0: return True
    if text.find("show a quiz") >= 0: return True
    return False


def get_ymd(text, lang, timestamp="", infer=False):
    if text in ["otd", "onthisday", "on_this_day", "on this day", "today", "今日", "今日は何の日"]:
        if timestamp == "":
            time = datetime.date.today().strftime("%m/%d")
            tmp = time.split("/")
        else:
            time = timestamp.split("/")
            tmp = [time[1], time[2]]

        return ("", str(int(tmp[0])), str(int(tmp[1])))

    marks = [" ", "/", "-", "_"]
    for m in marks:
        tmp = text.split(m)
        if len(tmp) == 2 or len(tmp) == 3:
            return split_ymd(tmp, lang)

    if text.isdigit():
        return (year_text2num(text, lang), "", "")

    if lang == "ja" or lang == "zh":
        y, m, d = "", "", ""
        if text.find("年") > 0:
            tmp = text.split("年")
            if tmp[0].isdigit():
                y = year_text2num(tmp[0], lang)
                text = tmp[1]

        if text.find("月") > 0:
            tmp = text.split("月")
            if tmp[0].isdigit():
                m = str(int(tmp[0]))
                text = tmp[1]

        if text.find("日") > 0:
            tmp = text.split("日")
            if tmp[0].isdigit():
                d = str(int(tmp[0]))

        return (y, m, d)

    elif infer:
        tmp = detect_ymd(lang, text=text)
        if len(tmp) > 0: return tmp

    return None


def split_ymd(text, lang):
    mnames = get_month_name(lang, False)
    if len(text) == 2:
        m = text[0]
        d = text[1].split(" ")[0]
        y = ""

        for mn in mnames:
            if mn.find(text[1]) >= 0:
                d = m
                m = str(month_text2num(mn, lang))
                break

            if mn.find(m) >= 0:
                m = str(month_text2num(mn, lang))
                break

        if (not m.isdigit() and d.isdigit()) or (m.isdigit() and int(m) > 12):
            d = text[0].split(" ")[0]
            m = text[1]

    elif len(text) == 3:
        d = text[1]
        m = text[0]
        y = text[2].split(" ")[0]
        if text[0].isdigit() and int(text[0]) > 31:
            y = text[0]
            d = text[2].split(" ")[0]
        elif text[1].isdigit() and int(text[1]) > 12:#text:#8/30/1859
            m = text[0]
            d = text[1]
        y = year_text2num(y, lang)

    else:
        return None

    m = str(month_text2num(m, lang))
    d = day_text2num(d, lang)
    
    if int(m) < 0 or int(d) < 0 or (int(d) < 1 or int(d) > 31): return None
    m = str(int(m))
    d = str(int(d))
    return (y, m, d)


def get_month_name(lang, wiki_style):
    if lang == "es":
        if wiki_style: str_m = ["de_enero", "de_febrero", "de_marzo", "de_abril", "de_mayo", "de_junio", "de_julio", "de_agosto", "de_septiembre", "de_octubre", "de_noviembre", "de_diciembre"]
        else: str_m = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"]

    elif lang == "de":
        if wiki_style: str_m = ["Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"]
        else: str_m = ["januar", "februar", "märz", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "dezember"]

    elif lang == "fr":
        str_m = ["janvier", "février","mars", "avril", "mai", "juin", "juillet","août", "septembre", "octobre", "novembre", "décembre"]

    elif lang == "pl":
        str_m = ["stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia"]

    elif lang == "ja" or lang == "zh":
        str_m = [str(n) + "月" for n in range(1, 13)]

    else:
        if wiki_style: str_m = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
        else: str_m = ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"]

    return str_m


def month_text2num(m, lang):
    if m.isdigit(): return m
    m = m.replace(".", "")
    ms = get_month_name(lang, False)
    for i in range(12):
        str_m = ms[i]
        if str_m == m or str_m.startswith(m): return i+1
    return -1
    

def day_text2num(d, lang):
    if d.isdigit(): return d
    elif lang == "ja": return d.replace("日", "")
    elif lang == "fr": return d.replace("er", "")
    return - 1
    

def year_text2num(y, lang):
    if y.isdigit(): return y
    if lang == "ja" or lang == "zh":
        y = y.replace("年", "")
        if lang == "ja" and y.startswith("紀元前") >= 0 or lang == "zh" and y.startswith("前"):
            y = str(-1*int(y))
        return y
    return ""


def detect_ymd(lang, text="", tokens=[]):
    if len(tokens) < 1 and len(text) > 0:
        tmp =lang_split(text, lang)
    elif len(tokens) > 0:
        tmp = tokens
    else:
        return []

    ms = get_month_name(lang, False)
    tmp_result = ["", "", ""]

    for i in range(len(tmp)):
        t = tmp[i]
        if i+2 < len(tmp) and t.isdigit() and tmp[i+1].isdigit():
            if tmp[i].isdigit(): y = tmp[i]
            else: return []

            if tmp[i+1].isdigit(): d = tmp[i+1]
            else: return (y, "", "")

            if int(y) < 32 and int(d) > 31: y, d = tmp[i+1], tmp[i]
            return (y, month_text2num(tmp[i-1], lang), d)

        for j in range(12):
            m = ms[j]
            if t == m or t.startswith(m):
                if tmp[i+1].isdigit(): y = tmp[i+1]
                else: return []

                if tmp[i-1].isdigit(): d = tmp[i-1]
                else: return (y, "", "")

                if int(y) < 32 and int(d) > 31: y, d = tmp[i-1], tmp[i+1]
                return (y, month_text2num(t, lang), d)

        if lang_year_detect(i, tmp, lang): tmp_result[0] = t
        if lang_month_detect(i, tmp, lang): tmp_result[1] = month_text2num(t, lang)
        if lang_day_detect(i, tmp, lang): tmp_result[2] = t

    return tmp_result


def lang_year_detect(idx, tokens, lang):
    if not tokens[idx].isdigit(): return False
    if lang == "ja" or lang == "zh":
        return tokens[idx+1] == "年"
    return idx > 0 and tokens[idx-1] == "in"


def lang_month_detect(idx, tokens, lang):
    if not tokens[idx].isdigit(): return False
    if lang == "ja" or lang == "zh":
        return tokens[idx+1] == "月"

    else:
        ms = get_month_name(lang, False)
        return idx+1 < len(tokens) and tokens[idx+1] in ms


def lang_day_detect(idx, tokens, lang):
    if not tokens[idx].isdigit(): return False
    if lang == "ja" or lang == "zh":
        return tokens[idx+1] == "日"

    return False


def create_tokens(text, lang):
    tokens, nouns, verbs = [], [], []
    if lang == "en":
        lemmatizer = nltk.stem.WordNetLemmatizer()
        sw = StopWords()

        try:
            tokens = [lemmatizer.lemmatize(t) for t in text.lower().split() if not sw.is_stop_word(t) and len(t) > 1]
        except:
            tokens = [lemmatizer.lemmatize(t) for t in text.decode("utf-8").lower().split() if not sw.is_stop_word(t) and len(t) > 1]

    elif lang == "ja":
        sw = StopWords()
        mecab = MeCab.Tagger("-Ochasen")

        if type(text) == type("a"):
            text = text.decode("utf-8")

        text = mojimoji.zen_to_han(text)

        if type(text) != type("a"):
            text = text.encode("utf-8")

        node = mecab.parseToNode(text.lower())
        tokens = []
        while node:
            w = node.surface
            hinshi = node.feature.split(",")
            if (w.isdigit() or len(w) > 1) and not sw.is_stop_word(w): tokens.append(w)
            node = node.next
            
    else:
        tokens = [text]

    return [tokens, nouns, verbs]


def no_event_message(lang):
    if lang == "ja": return u"記録されているイベントはありません"
    else: return "No stored events"


def event_importance(event_ids):
    impl_scores = []
    for evt_id in event_ids:
        total = each_event_importance(evt_id)
        impl_scores.append(total)

    return impl_scores, np.argsort(impl_scores)[::-1]

def each_event_importance(event_id):
    sql = "select score from event_importance where event_id = " + str(event_id)
    data = ms.select(sql)
    if len(data) < 1 or len(data[0]) < 1: return 0
    return data[0][0]


def get_shortenURL(longUrl):
    url = 'https://api-ssl.bitly.com/v3/shorten'
    query = {
            'access_token': access_token,
            'longurl':longUrl
            }
    try:
        tmp = requests.get(url,params=query).json()
        return tmp['data']['url']
    except Exception:
        return None


def remove_citation(text):
    pos = text.find("[")
    while pos > 0:
        tmp = text[pos:]
        c = tmp[:tmp.find("]")+1]
        if c[1:-1].isdigit():
            text = text.replace(c, "")
        else:
            pos2 = text[pos+1:].find("[")
            if pos2 < 0: break
            pos = pos + pos2 + 1
    return text


def generate_output_text(elist, lang, allow_split_longtweet=False, num=1, choise="importance", minleng=10):
    if type(elist) == type([]) and str(elist[0]).isdigit():
        sql = "select text, url, event_id, year, month, day from wiki_event where event_id = " + str(elist[0])
        for i in range(1, len(elist)): sql += " or event_id = " + str(elist[i])
        data = [(get_texts(d[0]), d[1], d[2], d[3], d[4], d[5]) for d in ms.select(sql)]

    suitable = []
    toolong = []
    maxleng = get_tweet_maxleng(lang)
    for e in data:
        if len(e[0]) >= minleng and len(e[0]) <= maxleng: suitable.append(e)
        elif len(e[0]) > maxleng: toolong.append(e)

    if len(suitable) > 0:
        target = []
        for e in suitable:
            text = combine_date_text(e[3], e[4], e[5], e[0], lang)#str(e[3]) + "-" + str(e[4]) + "-" + str(e[5]) + ". " + e[0]
            target.append([text, e[1], e[2]])

    elif allow_split_longtweet:
        target = []
        for e in toolong:
            text = combine_date_text(e[3], e[4], e[5], e[0], lang)#str(e[3]) + "-" + str(e[4]) + "-" + str(e[5]) + ". " + e[0]
            target.append([text, e[1], e[2]])

    else:
        return []

    idxes = []
    if choise == "random":
        import random
        idx = random.randint(0, len(target)-1)
        idxes.append(idx)

    elif choise == "importance":
        _, indexes = event_importance([e[2] for e in target])
        while len(idxes) < num:
            idx = indexes[len(idxes)]
            idxes.append(idx)

    else:
        while len(idxes) < num:
            idxes.append(len(idxes))

    results = []
    for idx in idxes:
        text = target[idx][0]
        url = get_shortenURL(target[idx][1])
        results.append(text + " " + url)

    return results


def m2v(v, l):
    vmap = dict([(t[0], t[1]) for t in v])
    vec = []
    for i in range(l):
        if i in vmap: vec.append(float(vmap[i]))
        else: vec.append(0.0)
    return vec


def get_texts(wiki_text):
    soup = BeautifulSoup(wiki_text, "html.parser")
    return remove_citation(soup.text)


def lang_split(text, lang):
    if lang == "ja":
        import MeCab, mojimoji
        from StopWord import StopWords
        sw = StopWords()
        mecab = MeCab.Tagger("-Ochasen")
        text = mojimoji.zen_to_han(text.decode('utf-8')).encode('utf-8')
        node = mecab.parseToNode(text.lower())
        tokens = []
        while node:
            w = node.surface
            hinshi = node.feature.split(",")
            if not sw.is_stop_word(w): tokens.append(w)
            node = node.next
        return tokens

    return text.split(" ")


def logging(text):
    f = codecs.open(abs_path + "logs.txt", "a", "utf-8")
    #print ("@logging:", text, ":", type(text))
    try:
        f.write(text + "\n")
    except:
        f.write(text.decode("utf-8") + "\n")
    f.close()
