# -*- coding:utf-8 -*-

def greet(text, lang, output_lang):
    text = text[text.find(" ")+1:]
    way = analyze_way(text, lang)
    return output_text(way, output_lang)


def output_text(way, lang):
    if way == 0:
        if lang == "en": return "hi"

    if way == 1:
        if lang == "en": return "good morning"
        if lang == "ja": return u"おはようございます"

    if way == 2:
        if lang == "en": return "good evening"
        if lang == "ja": return u"こんにちは"

    if way == 3:
        if lang == "en": return "good evening"
        if lang == "ja": return u"こんばんは"

    if way == 4:
        if lang == "en": return "you are welcome"
        if lang == "ja": return u"どういたしまして"

    if way == 5:
        if lang == "en": return "bye"
        if lang == "ja": return u"またお話ししましょう"

    if way == -1:
        return ""


def analyze_way(text, lang):
    if lang == "en":
        text = text.lower()
        if text in ["hi", "hello"]:
            return 0

        if text == "good morning":
            return 1

        if text == "good evening":
            return 2

        if text in ["thank", "thank you", "thanks"]:
            return 4

        if text in ["bye", "good bye", "have a nice day", "see you"]:
            return 5

    if lang == "ja":
        if text in ["おはようございます", "おはよう"]:
            return 1

        if text == "こんにちは":
            return 2

        if text in ["こんばんわ", "こんばんは"]:
            return 3

        if text in ["ありがとう", "ありがとうございます", "サンキュー"]:
            return 4

        if text in ["バイバイ", "ばいばい", "またね", "じゃあ"]:
            return 5

    return -1
