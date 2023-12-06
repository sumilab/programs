# -*- coding:utf-8 -*-


import mojimoji
import pickle
import json
import datetime
from requests_oauthlib import OAuth1Session

from keys import get_keys
from mysql import get_obj

from util import get_ymd
from util import create_tokens
from util import no_event_message
from util import detect_ymd
from util import generate_output_text

from greeting import greet

from lang_detection import get_output_lang
from lang_detection import detect_text_lang

from otd_reply import otd_reply
from otd_reply import ask_text

from text_sim import rank

path = ""  # Set a path recording pickle files
botname = "HistoChatbot"  # Set the name of the chatbot
ms = get_obj("localhost", db="twitter_histo_chatbot")


def invoke():
    replying_for_public_tweets()
    replying_for_protected_users()


def replying_for_public_tweets():
    # Preprocess for replying
    last_tweet_ids = get_last_replied_tweet()

    last_tid = None
    if type(last_tweet_ids) == type(2): last_tid = last_tweet_ids
    elif 'public' in last_tweet_ids: last_tid = last_tweet_ids['public']
    tweets = search_tweets(last_tid)

    tmp = get_keys()

    # Replying
    for r in tweets:
        rtext = get_reply_text(r, tmp[0], tmp[1], tmp[2], tmp[3])
        reply(tmp, r['id'], rtext)
        #retweet_history_tweet(tmp, r['id'], r['full_text'].lower().encode("utf-8"), r['lang'].encode("utf-8"))

    if len(tweets) > 0:
        last_tweet_ids['public'] = tweets[0]['id'] 
        pickle.dump(last_tweet_ids, open(path+"the_last_replied_tweet_id.pkl", 'w'), protocol=2)


def get_last_replied_tweet(public=True):
    try:
        if public:
            return pickle.load(open(path+"the_last_replied_tweet_id.pkl", 'r'))
        else:
            return pickle.load(open(path+"the_last_replied_protected_tweet_id.pkl", 'r'))
    except Exception:
        return None


def search_tweets(last_id, count=100):
    user_id = "@@" + botname + " exclude:retweets"
    url = "https://api.twitter.com/1.1/search/tweets.json"
    params = {'q': user_id, 'count':count, "tweet_mode" : "extended"}
    if not last_id is None: params['since_id'] = last_id

    tmp = get_keys()
    api, sec, acc, ase = tmp[0], tmp[1], tmp[2], tmp[3]
    req = OAuth1Session(api, sec, acc, ase).get(url, params=params)
    tweets = json.loads(req.text)

    return tweets['statuses']


def get_reply_text(r, api, sec, acc, ase, num=1):
    text = r['full_text'].lower()
    timestamp = timestamp_formatting(r['created_at'])

    lang = r['lang'].encode("utf-8")
    tmp_htags = get_htag_texts(r['entities']['hashtags'])
    htags = []
    for ht in tmp_htags:
        if type(ht) != type("str"): htags.append(ht.encode("utf-8"))
        else: htags.append(ht)

    if lang == "ja": text = mojimoji.zen_to_han(text).encode("utf-8")
    elif lang == "und": lang = "en"
    else: lang = detect_text_lang(lang)

    text = get_analyzing_text(text, htags)

    urls = get_url_texts(r['entities']['urls'])

    if type(text) != type("str"): text = text.encode("utf-8")

    return gen_reply_text(text, timestamp, lang, htags, urls, num)


def timestamp_formatting(s):
    return datetime.datetime.strftime(datetime.datetime.strptime(s, '%a %b %d %H:%M:%S +0000 %Y'), '%Y/%m/%d')


def get_htag_texts(htags):
    texts = []
    for h in htags:
        texts.append(h["text"])
    return texts


def get_analyzing_text(text, htags):
    ## remove "@account name"
    while text.startswith("@"):
        text = text[text.find(" ")+1:]

    if type(text) != type("str"): text = text.encode("utf-8")

    ## remove hashtag texts
    for h in htags:
        if type(h) != type("str"): text = (text.replace(u"#"+h.encode("utf-8"), ""))
        else: text = text.replace("#"+h, "")

    while text.startswith(" "): text = text[1:]
    while text.endswith(" "): text = text[:-1]

    return text


def get_url_texts(urls):
    return [url["expanded_url"] for url in urls]


def gen_reply_text(text, timestamp, lang, htags, url, num, otd_mode=True):
    ## Preprocess
    output_lang, cont = get_output_lang(text, lang)
    cont = cont.replace("\r\n", " ").replace("\n", " ").replace("\r", " ").replace("(", "").replace(")", "").replace(",", " ").replace(".", " ")

    while cont.startswith(" "):
        cont = cont[1:]

    ## Check reply mode
    greet_text = greet(cont, lang, output_lang)
    if len(greet_text) > 0: return [greet_text]

    target_event_ids, cont = hashtag_filters(htags, cont)

    if type(cont) != type("str"): cont = cont.encode("utf-8")
    ymd = get_ymd(cont, lang, timestamp=timestamp)

    token_data = create_tokens(cont, lang)
    tokens = token_data[0]

    if ymd is not None and is_simple_commemoration(lang, ymd, tokens):
        return otd_reply(cont, ymd, lang, output_lang, num, target_event_ids)

    if lang == "en" and len(tokens) > 1 or lang == "ja" and len(tokens) > 1:
        return sentence_commemoration(tokens, lang, output_lang, target_event_ids=target_event_ids)

    elif len(target_event_ids) > 0:
        return generate_output_text(target_event_ids, output_lang)

    elif len(htags) > 0:
        return [no_event_message(output_lang)]

    elif otd_mode:
        return [ask_text(output_lang)]

    return ["hi"]


def hashtag_filters(htags, cont):
    if len(htags) < 1: return [], cont
    esets = []

    ## Check events including all the hashtag texts by intersection 
    sql = "select entity_id, entity from entity"
    cond = " where source = 'wikipedia'"
    if len(htags) == 1:
        cond += " and (entity like '%" + htags[0].lower() + "%' or category like '%" + htags[0].lower() + "%')"
        cont = cont.replace("#"+htags[0].lower(), "")

    else:
        cond += " and ((entity like '%" + htags[0].lower() + "%' or category like '%" + htags[0].lower() + "%')"
        cont = cont.replace("#"+htags[0].lower(), "")
        for i in range(1, len(htags)):
            ht = htags[i].lower()
            cond += " and (entity like '%" + ht + "%' or category like '%" + ht + "%')"
            cont = cont.replace("#"+ht.lower(), "")
        cond += ")"

    entity_ids = [d[0] for d in ms.select(sql + cond)]

    ## Check events including all the hashtag texts by intersection 
    if len(entity_ids) < 0:
        sql = "select entity_id, entity from entity"
        cond = " where source = 'wikipedia'"
        cond += " and (entity like '%" + htags[0].lower() + "%' or category like '%" + htags[0].lower() + "%'"
        for i in range(1, len(htags)):
            ht = htags[i].lower()
            cond += " or entity like '%" + ht + "%' or category like '%" + ht + "%'"
            cont = cont.replace("#"+ht.lower(), "")
        cond += ")"

    entity_ids = [d[0] for d in ms.select(sql + cond)]
    if len(entity_ids) < 1: return [], cont

    sql = "select event_id from event_entity"
    cond = " where eid_list like '%*" + str(entity_ids[0]) + "*%'"
    for i in range(1, len(entity_ids)):
        cond += " or eid_list like '%*" + str(entity_ids[i]) + "*%'"

    esets = [d[0] for d in ms.select(sql+cond)]
    if len(esets) < 1: return [], cont

    while cont.endswith(" "): cont = cont[:-1]

    return esets, cont


def is_simple_commemoration(lang, ymd, tokens):
    if ymd is None: return False
    if lang == "ja" or lang == "zl":
        if "年" in tokens: tokens.remove("年")
        if "月" in tokens: tokens.remove("月")
        if "日" in tokens: tokens.remove("日")

    if lang == "en" or lang == "ja":
        if len(tokens) < 2 and (ymd[0].isdigit() or ymd[1].isdigit() or ymd[2].isdigit()):
            return True
        
        if len(tokens) == 2 and (ymd[0].isdigit() or ymd[1].isdigit() and ymd[2].isdigit()):
            return True

        if len(tokens) == 3 and (ymd[0].isdigit() and ymd[1].isdigit() and ymd[2].isdigit()):
            return True

        else:
            return False        
    else:
        return (ymd is not None) and (ymd[0].isdigit() or ymd[1].isdigit() or ymd[2].isdigit())

 
def sentence_commemoration(tokens, lang, output_lang, target_event_ids=[], nouns=[], verbs=[]):
    ymd = detect_ymd(output_lang, tokens=tokens)
    y, m, d = "", "", ""

    if len(ymd) > 0: y, m, d = ymd[0], ymd[1], ymd[2]
    else:
        ymd = detect_ymd(lang, tokens=tokens)
        if len(ymd) > 0: y, m, d = ymd[0], ymd[1], ymd[2]

    return rank(tokens, y, m, d, output_lang, target_event_ids=target_event_ids)


def reply(keys, tweet_id, rtext, url="https://api.twitter.com/1.1/statuses/update.json"):
    api, sec, acc, ase = keys[0], keys[1], keys[2], keys[3]
    try:
        for rep_text in rtext:
            params = {'in_reply_to_status_id': tweet_id, 'status':rep_text, 'auto_populate_reply_metadata':True}
            OAuth1Session(api, sec, acc, ase).post(url, params=params)

    except Exception:
        print("ERROR @ reply")


def replying_for_protected_users(count=100):
    # Preprocess for replying
    ids = pickle.load(open("protected_tweet_ids.pkl", 'r'))
    text = "@HistoChatbot "
    url = "https://api.twitter.com/1.1/statuses/user_timeline.json"
    params = {'count':count, "tweet_mode" : "extended"}#'truncated':False}
    tmp = get_keys()
    api, sec, acc, ase = tmp[0], tmp[1], tmp[2], tmp[3]
    last_tweet_ids = get_last_replied_tweet(public=False)

    for id in ids:
        # Collect tweets
        params['user_id'] = id
        last_id = None
        if last_tweet_ids is not None and id in last_tweet_ids: last_id = last_tweet_ids[id]
        if not last_id is None: params['since_id'] = last_id
        req = OAuth1Session(api, sec, acc, ase).get(url, params=params)
        tweets = json.loads(req.text)

        # Replying
        for r in tweets:
            if "full_text" not in r: continue
            ttext = r['full_text']
            if not ttext.startswith(text): continue
            rtext = get_reply_text(r, tmp[0], tmp[1], tmp[2], tmp[3])
            reply(tmp, r['id'], rtext)
            #retweet_history_tweet(tmp, r['id'], ttext.lower().encode("utf-8"), r['lang'].encode("utf-8"))

        if len(tweets) > 0 and not 'error' in tweets:
            print("tweets:", tweets)
            last_tweet_ids[id] = tweets[0]["id"]
        else:
            last_tweet_ids[id] = last_id

    try:
        pickle.dump(last_tweet_ids, open("the_last_replied_protected_tweet_id.pkl", 'w'), protocol=2)
    except Exception:
        print("error @ storing the last reply for protected users")


if __name__ == "__main__":
    invoke()
