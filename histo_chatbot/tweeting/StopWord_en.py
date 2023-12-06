#!/usr/bin/env python
# -*- coding:utf-8 -*- 

__author__ = 'sumi'

import re
from nltk.corpus import stopwords

class StopWords:
    def __init__(self):
        self.cite_pattern = r"[0-9]"
        self.noise_marks = [
            u"〓",
            u"—",
            u"一",
            u"・",
            u"%",
            u"▼",
            u"｡",
            u"、",
            u"＠",
            u"#",
            u"$",
            u"&",
            u"'",
            u"\"",
            u"!",
            u"?",
            u"<",
            u">",
            u"\\",
            u"[",
            u"]",
            u"{",
            u"}",
            u"(",
            u")",
            u"「",
            u"」",
            u"『",
            u"』",
            u"-",
            u"=",
            u"-",
            u"+",
            u"*",
            u"|",
            u"~",
            u"`",
            u".",
            u",",
            u"【", 
            u"】",
            u";",
            u"▽",
            u"◇",
            u"/",
            u"〒",
            u":",
            u"–",
            u"–"
        ]

        self.swlist = set("! \" # $ % & ' ( ) - = ^ ~ \\ | @ ` [ ] { } ; + : * , . < > / ? _ a about above after again against all am an and any are aren't as at be because been before being below between both but by can't cannot could couldn't did didn't do does doesn't doing don't down during each few for from further had hadn't has hasn't have haven't having he he'd he'll he's her here here's hers herself him himself his how how's i i'd i'll i'm  i've if in into is isn't it it's its itself let's me more most mustn't my myself no nor not of  off on once only or other ought our ours ourselves out over own same shan't she she'd she'll she's should shouldn't so some such than that that's the their theirs them themselves then there there's these they they'd they'll they're they've this those through to too under until up very was wasn't we we'd we'll we're we've were weren't what what's when when's where where's which while who who's whom why why's with won't would wouldn't you you'd you'll you're you've your yours yourself yourselves a'sable about above according accordingly across actually after afterwards again against ain't  all allow allows almost alone along already also although always am among amongst an and another any anybody anyhow anyone anything anyway anyways anywhere apart appear appreciate appropriate are aren't around as aside ask asking associated at available away awfully be became because become becomes becoming been before beforehand behind being believe below beside besides best better between beyond both brief but by c'mon c's came can can't cannot cant cause causes certain certainly changes clearly co com come comes concerning consequently consider considering contain containing contains corresponding could couldn't course currently definitely described despite did didn't different do does doesn't doing don't done down downwards during each eg eight either else elsewhere enough entirely especially et etc even ever every everybody everyone everything everywhere ex exactly example except far few fifth first five followed following follows for former formerly forth four from further furthermore get gets getting given gives go goes going  gone got gotten greetings had hadn't happens hardly has hasn't have haven't having he he's hello help hence her here here's hereafter hereby herein hereupon hers herself hi him himself his hither hopefully how howbeit however i'd i'll i'm i've ie if ignored immediate in inasmuch inc indeed indicate indicated indicates inner insofar instead into inward is isn't it it'd it'll it's its itself just keep keeps kept know known knows last lately later latter latterly least less lest let let's like liked likely little look looking looks ltd mainly many maybe me mean meanwhile merely might more moreover most mostly much must my myself name namely nd near nearly necessary need needs neither never nevertheless new next nine no nobody non none noone nor normally not nothing novel ow nowhere obviously of off often oh ok okay old on once one ones only onto or other others otherwise ought our ours ourselves out outside over overall own particular particularly per perhaps placed please plus possible presumably probably provides que quite qv rather rd re really reasonably regarding regardless regards relatively respectively right said same saw say saying says second secondly see seeing seem seemed seeming seems seen self selves sensible sent serious seriously seven several shall she should shouldn't since six so some somebody somehow someone something sometime sometimes somewhat somewhere soon sorry specified specify specifying still sub such sup sure t's take taken tell tends th that that's thats the their theirs them themselves then thence there there's thereafter thereby therefore therein theres thereupon these they they'd they'll they're they've think third this thorough thoroughly those though three through throughout thru thus to together too took toward towards tried tries truly try trying twice two un under unfortunately unless unlikely until unto up upon us use used useful uses using usually value various very via viz vs want wants was wasn't way we we'd we'll we're we've welcome last lately later latter latterly least less lest let lets like liked lul usefully usefulness uses using usually value various 've very via viz vol vols vs want wants was wasnt way we wed welcome we'll went w ere werent we've what whatever what'll whats when whence whenever where whereafter whereas whereby wherein wheres whereupon wherever whether which while whim whither who whod whoever whole who'll whom whomever whos whose why widely willing wish with within without wont words world would wouldnt www yes yet you youd you'll your youre yours yourself yourselves you've zero".split())

    def get_noise_mark_list(self):
        return self.noise_marks

    def is_stop_word(self, w):
        if w in self.swlist or w in self.noise_marks or w in stopwords.words('english'):
            return True
        return False
        #if re.match(u"[0-9]", w) or len(w) == 1:
        #    return True
        #return re.search(self.cite_pattern , w)
        #if match:
        #    return True
        #else:
        #    return False
