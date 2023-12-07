# -*- coding: utf-8 -*-
#!/usr/bin/python

def get_tags():
    general_history = [
    "history", "historyfacts", "oldpicture", "historyteacher", "memorylane", "histoire", "twitterstorians", "historicalcontext", "colorization", "memories", "oldphoto", "earlymodern", "historicalevent", "worldhistory", "twitterstorian", "historynerd", "histedchat", "historyfeed", "historyteacher", "archives", "historymatters"
    ]

    national_history = [
    "canadianhistory", "ushistory", "histoireducanada", "jewishhistory", "nazigermany", "ottoman", "cdnhistory", "dchistory", "cdnhist", "thirdreich", "tohistory", "mdhistory", "bchist", "abhistory", "vthistory", "britishhistory", "ancientchina", "ancientegypt", "ancientgreece", "americanhistory", "thisiscanadashistory", "ottomanempire", "ontariohistory", "earlyamhistory", "japanhistory", "japanesehistory", "chinesehistory", "localhistory"
    ]

    facet_focused_history = [
    "blackfacts", "histoiremiliterre", "wmnshist", "arthistory", "sporthistory", "womenshistory", "navalhistory", "presidentialhistory", "musichistory", "militaryhistory", "blackhistory", "envhist", "histmed", "wmnhist", "todayintennishistory", "todayinblackhistory", "ibhistory", "u2history", "historythroughcoins", "histSTM", "silentfilm", "historyscience", "histsci", "digitalhistory", "foodhistory", "histmonast", "histnursing", "histgender", "histtech"
    ]

    general_commemoration = [
    "onthisday", "otd", "otdh", "ThisDayIn", "thisdayinhistory", "TodayinHistory", "tdih", "onthisdayinhistory", "otdih", "100YearsAgo", "thisday", "lessthan100yearsago", "todayweremember", "titanicremembranceday", "WeRemember", "100yearsago", "remembering", "wewillrememberthem", "rememberthem", "remembranceday", "historyrepeatsitself", "throwbackthursday", "cw150", "botd"
    ]

    historical_events = [
    "1ww", "gulfwar", "ColdWar", "ww2", "ww1", "worldwar", "worldwarII", "VietnamWar", "worldwar2", "worldwartwo", "VEDay", "worldwarone", "greatwar", "battleofmidway", "Holocaust", "FrenchRevolutionaryWar", "wwII", "wwI", "SevenYearsWar", "firstworldwar", "ColdWarhist", "gulfwar", "battleofokinawa", "dday", "berlinwall", "ddayoverlord", "operationoverlord", "fww", "PearlHarbor", "americanrevolution", "6juin44", "sww", "june61944", "victoryineuropeday", "dday72", "neverforget84", "warof1812", "ww1politics", "ww1centenary", "ww1economy"
    ]

    historical_entities = [
    "stalin", "hitler", "abrahamlincoln", "rudolfhess", "napoleon"
    ]

    label_name = ["General History", "National or Regional History", "Facet-focused History", "General Commemoration", "Historical Events", "Historical Entities"]


    rvalue = [general_history, national_history, facet_focused_history, general_commemoration, historical_events, historical_entities, label_name]
    return rvalue
