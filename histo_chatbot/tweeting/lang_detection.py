#!/usr/bin/env python
# -*- coding:utf-8 -*-

from util import get_month_name
from util import langs
from util import get_lang_names

def detect_text_lang(lang):
    if lang in langs: return lang
    lang_names = get_lang_names(lang)
    for i in range(len(lang_names)):
        ln = lang_names[i].lower()
        if lang.lower() == ln: return langs[i]
    return "en"

def get_output_lang(text, lang):
    lang_names = get_lang_names(lang)

    txt = text.lower()
    if type(txt) != type("str"): txt = txt.encode("utf-8")
    for i in range(len(lang_names)):
        t = get_lang_fix(lang, lang_names[i])
        if txt.find(t) > 0: return langs[i], text.replace(t, "")
    return lang, text.replace(".", "")

def get_lang_fix(lang, lang_name):
    if lang == "en": return " in " + lang_name.lower()
    return " " + lang_name

