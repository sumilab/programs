# -*- coding:utf-8 -*-

import numpy as np
import nltk, codecs

from sklearn.tree import DecisionTreeClassifier
from gensim import corpora, models
from sklearn.externals import joblib

import clf_util as cu
from StopWord import StopWords

sw = StopWords()
lemmatizer = nltk.stem.WordNetLemmatizer()
na = 0.2
nb = 3

def application(text, f="tfidf", p="models/tweet_classify", nb=3):
    dct = corpora.Dictionary.load(p + "_" + str(nb) + ".dict")
    tfidf = models.TfidfModel.load(p + "_" + str(nb) + "_tfidf.model")
    tlist = [lemmatizer.lemmatize(w) for w in text.lower().split() if not sw.is_stop_word(w)]
    v = [cu.m2v(dct.doc2bow(tlist), len(dct))]
    clf = joblib.load('models/tree')

    y_pred = clf.predict(v)[0]
    #pp = clf.predict_proba(v)[0]
    #is_history = (y_pred == 1)
    #print ("is history?", is_history, ". ", y_pred, "(", pp, ")")

def invoke():
    history = cu.get_tweet_data("history_tweet", cportion=2000)
    nhistory = cu.get_tweet_data("trend", tportion=len(history))

    #fvecs = ["d2v", "lda", "lsi", "tfidf", "all"]
    fvecs = ["tfidf"]
    dnum = 300
    for f in fvecs:
        X, y = create_fvecs(history, nhistory, dnum, f)
        clf = train_classifier(X, y)

def train_classifier(X, y, knum=10):
    parameters = [{'kernel':('rbf'), 'C':np.logspace(-4, 4, 9), 'gamma':np.logspace(-4, 4, 9)}, {'kearnel':('rbf'), 'C':np.logspace(-4, 4, 9)}, {'kernel':('linear'), 'C':np.logspace(-4, 4, 9), 'gamma':np.logspace(-4, 4, 9)}, {'kearnel':('linear'), 'C':np.logspace(-4, 4, 9)}]
    clf_names = ["rf"]
    clfs = [DecisionTreeClassifier(random_state=0)]

    clf = clfs[0]
    clf.fit(X, y)
    joblib.dump(clf, 'models/tree') 


def create_fvecs(hdata, nhdata, tnum=1000, f="tfidf", topk=1000):
    p = "models/tweet_classify"
    dct = corpora.Dictionary.load(p + "_" + str(nb) + ".dict")

    if f == "d2v":
        hcorp, nhcorp = hdata, nhdata

    else:
        tfidf = models.TfidfModel.load(p + "_" + str(nb) + "_tfidf.model")
        h_token_list = [[lemmatizer.lemmatize(w) for w in hdata[tid]["text"].lower().split() if not sw.is_stop_word(w)] for tid in hdata]
        nh_token_list = [[lemmatizer.lemmatize(w) for w in nhdata[tid]["text"].lower().split() if not sw.is_stop_word(w)] for tid in nhdata]
        hcorp = [dct.doc2bow(tlist) for tlist in h_token_list]
        nhcorp = [dct.doc2bow(tlist) for tlist in nh_token_list]

    if f == "lda":
        lda =  models.LdaModel.load(p + '_lda_' + str(nb) + '_' + str(tnum) + '_topics.model')
        tmp = [lda[hcorp[i]] for i in range(len(hcorp))]
        hX = [cu.m2v(tmp[i], tnum) for i in range(len(hcorp))]
        nhX = [cu.m2v(lda[nhcorp[i]], tnum) for i in range(len(nhcorp))]

    elif f == "lsi":
        lsi =  models.LsiModel.load(p + '_lsi_' + str(nb) + '_' + str(tnum) + '_topics.model')
        hX = [cu.m2v(lsi[hcorp[i]], tnum) for i in range(len(hcorp))]
        nhX = [cu.m2v(lsi[nhcorp[i]], tnum) for i in range(len(nhcorp))]

    elif f == "d2v":
        data = cu.get_tweet_data("history_tweet")
        hids = [tid for tid in data]
        data = cu.get_tweet_data("trend")
        nhids = [tid for tid in data]
        d2v = models.Doc2Vec.load(p + '_doc2vec_' + str(nb) + "_" + str(tnum) + '.model')
        hX = [[v.item() for v in d2v.docvecs['d' + str(id)]] for id in hids]
        nhX = [[v.item() for v in d2v.docvecs['d' + str(id)]] for id in nhids]

    elif f == "all":
        # TF-IDF
        hX = [cu.m2v(hcorp[i], len(dct)) for i in range(len(hcorp))]
        nhX = [cu.m2v(nhcorp[i], len(dct)) for i in range(len(nhcorp))]

        # LDA
        lda =  models.LdaModel.load(p + '_lda_' + str(nb) + '_' + str(tnum) + '_topics.model')
        hX = each_extend(hX, [cu.m2v(lda[hcorp[i]], tnum) for i in range(len(hcorp))])
        nhX = each_extend(hX, [cu.m2v(lda[nhcorp[i]], tnum) for i in range(len(nhcorp))])

        # LSA
        lsi =  models.LsiModel.load(p + '_lsi_' + str(nb) + '_' + str(tnum) + '_topics.model')
        hX = each_extend(hX, [cu.m2v(lsi[hcorp[i]], tnum) for i in range(len(hcorp))])
        nhX = each_extend(hX, [cu.m2v(lsi[nhcorp[i]], tnum) for i in range(len(nhcorp))])

        # Doc2Vec
        data = cu.get_tweet_data("history_tweet")
        hids = [tid for tid in data]
        data = cu.get_tweet_data("trend")
        nhids = [tid for tid in data]
        d2v = models.Doc2Vec.load(p + '_doc2vec_' + str(nb) + "_" + str(tnum) + '.model')
        hX = each_extend(hX, [[v.item() for v in d2v.docvecs['d' + str(id)]] for id in hids])
        nhX = each_extend(hX, [[v.item() for v in d2v.docvecs['d' + str(id)]] for id in nhids])

        # Dim Reduct
        from sklearn.ensemble import RandomForestClassifier
        clf = RandomForestClassifier(n_estimators=200)
        X = np.r_[hX,nhX]
        y = np.r_[[1 for i in range(len(hX))], [0 for i in range(len(nhX))]]
        clf.fit(X, y)
        importances = clf.feature_importances_
        indices = np.argsort(importances)[::-1]

        fname = p + "_" + str(tnum) + "_rfs_base_fselection.csv"
        f = codecs.open(fname , "w", "utf-8")
        for i in indices:
            f.write(str(i) + "," + str(importances[i]) + "\n")
        f.close()

        tmp_hX = [[v[idx] for idx in indices[:topk]] for v in hX]
        tmp_nhX = [[v[idx] for idx in indices[:topk]] for v in nhX]
        hX = tmp_hX
        nhX = tmp_nhX

    else:
        hX = [cu.m2v(hcorp[i], len(dct)) for i in range(len(hcorp))]
        nhX = [cu.m2v(nhcorp[i], len(dct)) for i in range(len(nhcorp))]

    X = np.r_[hX, nhX]

    hy = [1 for i in range(len(hcorp))]
    nhy = [0 for i in range(len(nhcorp))]
    y = np.r_[hy, nhy]
    
    return [X, y]


def each_extend(hX, new_data):
    for i in range(len(hX)):
        hX[i].extend(new_data[i])
    return hX

if __name__ == "__main__":
    text = u"76 years ago, my grandad drove a tank onto Sword Beach. Like so many others, he risked his life to fight against fascism. #June6th #DDay #LestWeForget"
    application(text)
