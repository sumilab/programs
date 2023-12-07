This program is a part of implementation of [HistoChatbot](https://mobile.twitter.com/HistoChatbot). 
The main programs are `auto_tweet.py` and `auto_reply.py`.

# Python version
* All python programs were implemented with Python 2.7. If you run these programs with Python 3, it may need to rewrite handling Japanese texts, e.g., removing encoding and decoding, and to use up-to-date libraries including `requests_oauthlib`.


# Organization
## Main Programs
* `auto_tweet.py`: posting a tweet
* `auto_reply.py`: posting a replying tweet
* `get_past_events.py`: loading past event data
* `keys.py`: setting the access token keys of Twitter Developer API
* `mysql.py`: wrapper functions for mysql operation


## History content data
* `wiki_event.sql`: history-related quiz data collected from Wikipedia



# Using Libraries
1. Numpy
2. MySQL
3. NLTK
4. twitter
5. BeautifulSoup

For Japanese mode
1. mojimoji
2. MeCab


# Preparation
## Installing libraries

1. Installing MySQL
2. Creating a database in MySQL
3. Applying Twitter Developer API

## Parameter setting
This guide presents procedures for ease using quiz post. We assume that parameters are set as follows:
* `path` in `auto_reply.py`
* `botname` in `auto_reply.py`
* `self.user` in `mysql.py`
* `self.passwd` in `mysql.py`
* `abs_path` in `util`
* `access_token` in `util`


# Publication
1.　澄川 靖信, ヤトフト アダム,[デジタルヒストリーとの対話を促すTwitterチャットボット](https://search.ieice.org/bin/summary.php?id=j104-d_5_486), 電子情報通信学会論文誌, Vol. J104-D, No. 5, pp. 486–497 (2021).

