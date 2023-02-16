# -*- coding: utf-8 -*-
#!/usr/bin/python

import pickle
import random
import datetime
import numpy as np

from gensim import corpora, models

from util import create_tokens
from util import cos_sim, jaccard
from util import m2v
from util import use_twitter_api
from mysql import get_obj
ms = get_obj("localhost")

path = "." # Set the path where this program is located.
# We assume that this path has a directory `models` to make feature vectors.

list_id = int("") # Set the link of Twitter list including Twitter users.
current_news_user_list = "https://api.twitter.com/1.1/lists/members.json"

def post_current_news_quiz(time, lang):
    fname = "the_last_news_account_pos.pkl"
    pos = load_pkl_file(fname) + 1
    texts = get_current_news(pos=pos)
    store_pkl_file(pos, fname)

    tokens = [create_tokens(txt, lang)[0] for txt in texts]
    nfvecs = make_fvec(tokens)

    ## extract quiz
    qfvdata = load_quiz_fvecs(filtering=tokens[0])
    qids, qfvecs = qfvdata[0], qfvdata[1]

    post_quiz(nfvecs, qfvecs, qids, "Current-News-related ")


def load_quiz_fvecs(p=path+"/models/quiz", mode="word", filtering=[]):
    if len(filtering) > 0:
        qtokens = pickle.load(open(p + "_quiz_token_fvecs.pkl", "rb"))

        ftokens = set(filtering)

        if mode != "word": fvecs = pickle.load(open(p + "_" + mode + "_quiz_fvecs.pkl", "rb"))
        else: fvecs = qtokens

        filtered_ids = []
        filtered_fvecs = []

        for i in range(len(qtokens[1])):
            qt = qtokens[1][i]
            if len(ftokens & set(qt)) < 1: continue
            filtered_ids.append(fvecs[0][i])
            filtered_fvecs.append(fvecs[1][i])

            sql = "select quiz_text from quiz where quiz_id = " + str(fvecs[0][i])

        if len(filtered_fvecs) > 0:
            return [filtered_ids, filtered_fvecs]


    if mode == "lda":
        p += "_lda"
        return pickle.load(open(p + "_quiz_fvecs.pkl", "rb"))

    if mode == "lsi":
        p += "_lsi"
        return pickle.load(open(p + "_quiz_fvecs.pkl", "rb"))

    if mode == "tfidf":
        p += "_tfidf"
        return pickle.load(open(p + "_quiz_fvecs.pkl", "rb"))

    return pickle.load(open(p + "_quiz_token_fvecs.pkl", "rb"))


def post_quiz(ipts, qfvecs, qids, p, mode="tokens", qmode="importance"):
    for ipt in ipts:
        sval = []
        targets = []
        alpha = 1.0
        if mode == "both": alpha = 0.5
        if mode == "lda": alpha = 0.0
        for i in range(len(qfvecs)):
            qf = qfvecs[i]
            val = 0.0

            if mode == "tokens" or mode == "both":
                tval = jaccard(ipt, qf)
                val += alpha * tval

            if mode == "lda" or mode == "both":
                tval = cos_sim(ipt, qf)
                val += (1-alpha) * tval

            if val > 0.0:
                sval.append(val)
                targets.append(qids[i])

        if len(targets) > 0:
            idx = np.argmax(sval)
            qid = targets[idx]

            qsql = "select quiz_text, quiz_id from quiz where quiz_id = " + str(qid)

            if qmode == "importance":
                sql = "select quiz_text, score from event_importance as e inner join (" + qsql + ") as q on e.event_id = q.quiz_id"
                data = ms.select(sql)

                scores = [d[1] for d in data]
                idx = np.argmax(scores) # One good method is to record the selections made for each day so that they can be displayed one by one, starting with those with the highest scores.

            else:
                data = ms.select(qsql)
                idx = random.randrange(len(data))
            
            qtext = data[idx][0]
            post_tweet(qtext, p=p)

            
def post_replying_simple_quiz(time, lang, tokens):
    sql = "select quiz_text from quiz"
    data = ms.select(sql)
    idx = random.randrange(len(data))
    return [data[idx][0]]


def post_entity_quiz(time, lang, text, mode="importance"):
    pos = text.find("about")
    if pos >= 0: text = text[pos+len("about"):]
    else:
        pos = text.find("related to")
        if pos >= 0: text = text[pos+len("related to"):]

    entities = text.split()

    sql = "select quiz.quiz_id from quiz_original_data inner join quiz on quiz.quiz_id = quiz_original_data.quiz_id"
    for i in range(len(entities)):
        e = entities[i]
        if i == 0: sql += " where text like '%" + e + "%'"
        else: sql += " AND text like '%" + e + "%'"
    data = ms.select(sql)

    if len(data) < 1:
        sql = "select quiz.quiz_id from quiz_original_data inner join quiz on quiz.quiz_id = quiz_original_data.quiz_id"
        for i in range(len(entities)):
            e = entities[i]
            if i == 0: sql += " where text like '%" + e + "%'"
            else: sql += " OR text like '%" + e + "%'"
        data = ms.select(sql)

    if len(data) < 1:
        return ["No suitable quiz stored in the current DB."]
    
    qsql = "select quiz_text from quiz where quiz_id = " + str(data[idx][0])

    if mode == "importance":
        sql = "select quiz_text, score from event_importance as e inner join (" + qsql + ") as q on e.event_id = q.quiz_id"
        data = ms.select(sql)

        scores = [d[1] for d in data]
        idx = np.argmax(scores)

    else:
        data = ms.select(qsql)
        idx = random.randrange(len(data))
        
    qtext = data[idx][0]
    return [qtext]


def get_current_news(pos=-1):
    users = collect_users(list_id)
    if pos >= len(users): pos = pos % len(users)
    if pos > -1: users = [users[pos]]

    last_tweet_fname = "news_account_the_last_tweet_id.pkl"
    u2t = get_last_tweets(last_tweet_fname)
    texts = []
    
    for uid in users:
        data = collect_users_timeline(uid, u2t)
        if len(data) < 1: continue

        for d in data:
            texts.append(d[1])

        if len(data[0]) > 1:
            update_latest_tweet_id(uid, data[0][0], u2t, last_tweet_fname)
    return texts


def get_last_tweets(fname):
    try:
        return pickle.load(open(fname, 'rb'))
    except:
        return {}


def collect_users(list_id, url=current_news_user_list):
    params = {"list_id":list_id, "count":200}
    tweets = use_twitter_api(url, params)
    ids = [users["id"] for users in tweets['users']]
    return ids


def collect_users_timeline(user_id, u2t, url="https://api.twitter.com/1.1/statuses/user_timeline.json", num=1):
    params = {'user_id':user_id, 'count':num, 'trim_user':'true', 'exclude_replies': 'false', 'include_rts':'true', "tweet_mode" : "extended", 'since_id': None}
    since_id = get_latest_tweet_id(user_id, u2t)
    if since_id is not None: params['since_id'] = since_id
    tweets = use_twitter_api(url, params)
    data = []

    for t in tweets:
        tweet_id = t['id']
        text = t['full_text']
        urls = []

        if len(t['entities']['urls']) > 0: urls = [t['entities']['urls'][i]['expanded_url'] for i in range(len(t['entities']['urls']))]
        data.append([tweet_id, text, urls])

    return data


def get_latest_tweet_id(uid, u2t):
    if uid not in u2t: return None
    return u2t[uid]


def update_latest_tweet_id(uid, tid, u2t, fname):
    u2t[uid] = tid
    pickle.dump(u2t, open(fname, 'wb'), protocol=2)

    
def post_trending_quiz(time, lang, mode="lda"):
    places = []

    fname = "the_last_trending_place_pos.pkl"
    pos = load_pkl_file(fname) + 1
    twords, pos = get_trending_words(pos=pos)
    store_pkl_file(pos, fname)

    ## extract quiz
    qfvdata = load_quiz_fvecs(mode=mode, filtering=[w for wlist in twords for w in wlist])
    qids, qfvecs = qfvdata[0], qfvdata[1]

    ## trend word analysis
    tfvecs = make_fvec(twords, mode=mode)

    post_quiz(tfvecs, qfvecs, qids, "Trending-related ", mode="both")


def make_fvec(tlists, p=path+"/models/quiz", nb=3, na=0.3, tnum=100, mode="word"):
    if mode == "lda":
        dct = corpora.Dictionary.load(p + "_" + str(nb) + "_" + str(na) + ".dict")
        tfidf = models.TfidfModel.load(p + "_" + str(nb) + "_" + str(na) + "_tfidf.model")

        lda = models.LdaModel.load(p + '_lda_' + str(nb) + "_" + str(na) + "_" + str(tnum) + '_topics.model')
        vs = [m2v(lda[dct.doc2bow(tlist)], tnum) for tlist in tlists]
        return vs

    if mode == "lsi":
        dct = corpora.Dictionary.load(p + "_" + str(nb) + "_" + str(na) + ".dict")
        tfidf = models.TfidfModel.load(p + "_" + str(nb) + "_" + str(na) + "_tfidf.model")

        lsi = models.LsiModel.load(p + '_lsi_' + str(nb) + "_" + str(na) + "_" + str(tnum) + '_topics.model')
        vs = [m2v(lsi[dct.doc2bow(tlist)], tnum) for tlist in tlists]
        return vs

    if mode == "tfidf":
        dct = corpora.Dictionary.load(p + "_" + str(nb) + "_" + str(na) + ".dict")
        tfidf = models.TfidfModel.load(p + "_" + str(nb) + "_" + str(na) + "_tfidf.model")
        vs = [m2v(dct.doc2bow(tlist), len(dct)) for tlist in tlists]
        return vs

    return tlists


def pickup_text_similar_quiz(time, lang, tokens=[], topics=[]):
    """
    tokens: using tokens to load quiz texts from database with AND/OR operators
    topics: using as a feature vector to measure similarity from quiz texts 
    """
    y, m, d = time[0], time[1], time[2]
    sql = "select quiz_text, year, month, day from quiz"
    cond = " where lang = '" + lang + "'"
    if len(tokens) > 0:
        for t in tokens:
            cond += " AND quiz_text like %" + t + "%"
        print(sql + cond)


def post_calendar_quiz(time, lang, mode="importance"):
    y, m, d = time[0], time[1], time[2]
    qsql = "select quiz_text, quiz_id from quiz where lang = '" + lang + "' AND ((year=" + str(y) + ") OR (month=" + str(m) + " AND day=" + str(d) + "))"
    sql = "select quiz_text, score from event_importance as e inner join (" + qsql + ") as q on e.event_id = q.quiz_id"
    data = ms.select(sql)
    
    if mode == "importance":
        scores = [d[1] for d in data]
        idx = np.argmax(scores)

    else:
        # This mode is useful if the importance mode selects the same text. This is because Twitter does not show the same text in the short time.
        idx = random.randrange(len(data))

    post_tweet(data[idx][0], p="Calendar-based ")

    
def post_tweet(otxt, url="https://api.twitter.com/1.1/statuses/update.json", p=""):
    txt = p + "History Quiz: " + otxt
    params = {"status":txt}
    use_twitter_api(url, params, get=False)

def invoke(mode, lang):
    time = get_current_time()

    # Calender-based activation
    if mode == "calendar":
        post_calendar_quiz(time, lang)

    # Trending-based activation
    elif mode == "trending":
        post_trending_quiz(time, lang)

    elif mode == "current_news":
        post_current_news_quiz(time, lang)

        
def get_trending_target_place_ids(url="https://api.twitter.com/1.1/trends/available.json"):
    data = use_twitter_api(url, {})
    targets = []
    country_targets = ['Canada', 'United States', 'Australia', 'India', 'New Zealand', 'Singapore', 'United Kingdom', 'South Africa', 'Ireland']
    c2ids = {}
    for d in data:
        if d['name'] == 'Winnipeg' or d['country'] in country_targets:
            if d['country'] not in c2ids: c2ids[d['country']] = []
            if len(c2ids[d['country']]) > 3: continue
            c2ids[d['country']].append(d['woeid'])
            targets.append(d['woeid'])
    return targets


def load_pkl_file(fname):
    try:
        return pickle.load(open(fname, 'rb'))
    except:
        return 0

    
def store_pkl_file(pos, fname):
    pickle.dump(pos, open(fname, 'wb'), protocol=2)

def get_trending_words(url="https://api.twitter.com/1.1/trends/place.json", pos=-1):
    pids = [2972, 3369, 3444, 3534, 12723, 12903, 13383, 13911, 560472, 560743, 560912, 1062617, 1098081, 1099805, 1100661, 1100968, 1580913, 1582504, 1586614, 1586638, 2282863, 2295377, 2295378, 2295381, 2348079, 2352824, 2357024, 2357536, 2358820, 23424803, 23424916, 23424948]

    if pos >= len(pids): pos = pos % len(pids)
    if pos > -1: pids = [pids[pos]]
    twords = []
    for pid in pids:
        params = {'id': pid}
        data = use_twitter_api(url, params)
        for d in data:
            words = [t['name'].replace("#", "").replace("(", "").replace(")", "").lower() for t in d["trends"] if t['promoted_content'] is None]

            twords.append(words)

    return twords, pos


def get_current_time():
    dt_now = datetime.datetime.now()
    y, m, d = dt_now.year, dt_now.month, dt_now.day
    return [y, m, d]


if __name__ == "__main__":
    modes = ["calendar", "trending", "current_news"]
    lang = "en"
    for m in modes:
        invoke(m, lang)
