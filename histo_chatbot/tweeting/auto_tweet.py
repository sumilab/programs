#!/usr/bin/env python
# -*- coding:utf-8 -*-

import twitter
from keys import get_keys
from get_past_events import get_event_texts

def setting():
    tmp = get_keys()
    api, sec, acc, ase = tmp[0], tmp[1], tmp[2], tmp[3]
    return twitter.Api(consumer_key=api, consumer_secret=sec, access_token_key=acc, access_token_secret=ase)


def invoke(mode, lang):
    if mode == "otd":
        e = get_event_texts(lang)
        if e != "":
            api = setting()
            status = api.PostUpdate(e)


if __name__ == "__main__":
    import sys
    lang = sys.argv[1]
    mode = "otd"
    invoke(mode, lang)

