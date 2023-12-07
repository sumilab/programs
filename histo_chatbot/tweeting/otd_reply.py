#!/usr/bin/env python
# -*- coding:utf-8 -*-

import random

from util import event_importance
from util import no_event_message
from util import combine_date_text
from util import get_tweet_maxleng
from util import get_shortenURL

from get_past_events import get_event_cands


def otd_reply(text, date, lang, output_lang, num, filtered_target_event_ids=[]):
    elist = get_events(text, date, lang, output_lang, num, filtered_target_event_ids)
    if len(elist) < 1: return [no_event_message(output_lang)]

    ## Filtering
    elist = select_event(elist, num, output_lang, choise="importance")

    return elist


def get_events(text, date, lang, oplang, num, filtered_target_event_ids=[]):
    y, m, d = date[0], date[1], date[2]
    tmp = get_event_cands(oplang, y, m, d, filtered_target_event_ids=filtered_target_event_ids)
    events, too_long_event_texts = tmp[0], tmp[1]

    if y != "":
        mid = get_mid_y_text(oplang)
        targets = []
        for e in events:
            if not (e[0].startswith(y+mid) or e[1].find(y)>0): continue
            text = combine_date_text(e[3], e[4], e[5], e[0], oplang)
            targets.append([text, e[1], e[2], e[3], e[4], e[5]])

        if len(targets) < 1 and len(events) > 0:
            targets = events

    else:
        targets = []
        for e in events:
            text = combine_date_text(e[3], e[4], e[5], e[0].encode("utf-8"), oplang)
            targets.append([text, e[1], e[2], e[3], e[4], e[5]])

    if len(targets) < num:
        too_long = [e for e in too_long_event_texts if e[0].startswith(y+mid)]
        for e in too_long:
            if not (e[0].startswith(y+mid) or e[1].find(y)>0): continue
            text = combine_date_text(e[3], e[4], e[5], e[0], oplang)
            targets.append([text, e[1], e[2], e[3], e[4], e[5]])

    return targets


def get_mid_y_text(lang):
    if lang == "ja" or lang == "zh": return u"年"
    elif lang == "de": return u": "
    elif lang == "es": return u": "
    elif lang == "fr": return u" : "
    elif lang == "pl": return u" – "
    return u" "


def select_event(elist, num, lang, choise="random", allow_spliting_longtweet=False):
    target = []

    if len(elist) > num:
        if choise == "leng_order":
            e2leng = dict([(i, len(elist[i][0])) for i in range(len(elist))])
            for k, v in sorted(e2leng.items(), key=lambda x:x[1], reverse=False):
                if len(target) >= num: break
                target.append(elist[k])

        elif choise == "head":
            target.append(elist[0])

        elif choise == "importance":
            _, indexes = event_importance([e[2] for e in elist])
            while len(target) < num:
                idx = indexes[len(target)]
                target.append(elist[idx])

        else:
            checked_idx = []
            while len(target) < num:
                idx = random.randint(0, len(elist)-1)
                if idx in checked_idx: continue
                checked_idx.append(idx)
                target.append(elist[idx])

    elif len(elist) == 1: target = elist
    else:
        if choise == "random":
            checked_idx = []
            while len(target) < num:
                idx = random.randint(0, len(elist)-1)
                if idx in checked_idx: continue
                checked_idx.append(idx)
                target.append(elist[idx])

    final_target = []
    maxleng = get_tweet_maxleng(lang)
    for t in target:
        if len(t[0]) <= maxleng:
            surl = get_shortenURL(t[1]).encode("utf-8")
            date = ""
            txt = date + t[0] + " " + surl + " "
            final_target.append(txt)

        elif not allow_spliting_longtweet: continue
        else:
            start = 100
            end = 200
            final_target.append(t[0][:100])
            tmp = t[0]
            while True:
                if end > len(tmp): end = len(tmp)
                sub = get_continue_word(lang) + tmp[start:end]
                tmp = tmp[end:]
                start = end
                end = start + 100
                if start < len(tmp):
                    final_target.append(sub)
                else:
                    final_target.append(sub)
                    break
            final_target.append(get_shortenURL(t[1]))

    return final_target


def get_continue_word(lang):
    if lang == "ja": return u"（続き）"
    return "(continue) "


def ask_text(lang):
    if lang == "ja":
        text = "いつ？ （月/日/年の順にお問い合わせください）"

    elif lang == "es":
        text = "¿Cuando? (por favor pregunte en el orden de mes / día / año)"

    elif lang == "de":
        text = "Wann? (bitte in der Reihenfolge Monat / Tag nachfragen / Jahr)"

    elif lang == "fr":
        text = "Quand? (veuillez demander dans l'ordre du mois / jour / année)"

    elif lang == "zh":
        text = "什么时候？ （请按月/日的顺序询问/年）"

    elif lang == "pl":
        text = "Gdy? (proszę pytać w kolejności miesiąc / dzień / rok)"

    else:
        text = "When? (please ask in the order of month/day/year)"

    return text
