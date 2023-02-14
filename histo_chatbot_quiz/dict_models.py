#-*- coding: utf-8 -*-
#!/usr/bin/python

import nltk
import pickle
import numpy as np
from gensim import corpora, models

from util import m2v

from StopWord_en import StopWords
from mysql import get_obj
ms = get_obj("localhost")
sw = StopWords()
lemmatizer = nltk.stem.WordNetLemmatizer()


def invoke():
    p = "models/quiz"
    id_list, tlists = load_data_for_models(p)

    nb = 3
    na = 0.3
    tnum = 100

    make_models(p, tlists, 3, 0.3, 100)
    pickle.dump([id_list, tlists], open(p + "_quiz_token_fvecs.pkl", 'wb'), protocol=2)
    make_quiz_fvecs(id_list, tlists, 3, 0.3, 100)


def make_models(p, token_list, nb, na, tnum):
    pickle.dump(token_list, open(p + "_tokens.pkl", 'wb'), protocol=2)

    dct = corpora.Dictionary(token_list)
    if nb > 0 and na > 0:
        dct.filter_extremes(no_below=nb, no_above=na)
    dct.save(p + "_" + str(nb) + "_" + str(na) + '.dict')

    corp = [dct.doc2bow(tlist) for tlist in token_list]


    fname = p + "_" + str(nb) + "_" + str(na) + '_corp.pkl'
    pickle.dump(corp, open(fname, 'wb'), protocol=2)

    tfidf = models.TfidfModel(corp)
    tfidf.save(p + "_" + str(nb) + "_" + str(na) + '_tfidf.model')
    corp_tfidf = tfidf[corp]

    lsi = models.LsiModel(corpus=corp, id2word=dct, num_topics=tnum)
    lsi.save(p + '_lsi_' + str(nb) + "_" + str(na) + "_" + str(tnum) + '_topics.model')

    lda = models.LdaModel(corpus=corp, id2word=dct, num_topics=tnum)
    lda.save(p + '_lda_' + str(nb) + "_" + str(na) + "_" + str(tnum) + '_topics.model')

    
def load_data_for_models(p):
    stop_words = nltk.corpus.stopwords.words('english')

    token_list = []

    sql = "select quiz.quiz_id, text, quiz_text from quiz inner join quiz_original_data on quiz.quiz_id = quiz_original_data.quiz_id"
    data = ms.select(sql)
    id_list = []
    for d in data:
        id_list.append(d[0])
        text = d[1].rstrip().replace("\r\n", "").replace("\n", "").replace("\r", "").replace(u'–', "").replace(".", "")
        tlist = [lemmatizer.lemmatize(w) for w in text.lower().split() if not sw.is_stop_word(w) and w not in stop_words]
        token_list.append(tlist)

    return id_list, token_list

def make_quiz_fvecs(id_list, tlists, nb, na, tnum, p="models/quiz"):
    dct = corpora.Dictionary.load(p + "_" + str(nb) + "_" + str(na) + ".dict")
    lda = models.LdaModel.load(p + '_lda_' + str(nb) + "_" + str(na) + "_" + str(tnum) + '_topics.model')
    vs = [m2v(lda[dct.doc2bow(tlist)], tnum) for tlist in tlists]
    pickle.dump([id_list, vs], open(p + "_lda_quiz_fvecs.pkl", 'wb'), protocol=2)

if __name__ == "__main__":
    invoke()
