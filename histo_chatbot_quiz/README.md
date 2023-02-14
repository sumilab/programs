This program is an implementation of [HistoChatbot](https://mobile.twitter.com/HistoChatbot). 
The main program is `auto_quiz_gen.py`.

# Organization
## Main Programs
* `auto_quiz_gen.py`: posting a quiz
* `mysql.py`: wrapper functions for mysql operation
* `keys.py`: setting the access token keys of Twitter Developer API
* `StopWord_en.py`: removing stop words as preprocess
* `util.py`: functions used in `auto_quiz_gen.py`, but these are usable in other programs

## History quiz data
* `twitter_histo_chatbot_quiz_data.sql`: history-related quiz data

## For quick installation
* `insert_data_to_mysql.sh`: inserting quiz data into MySQL database by shell script

# Using Libraries
1. Numpy
2. Gensim
3. MySQL
4. NLTK

# Preparation
1. Installing MySQL
2. Creating a database in MySQL
3. Applying Twitter Developer API

# Quick Installation
## Parameters
This guide presents procedures for ease using quiz post. We assume that parameters are set as follows:
* `db` in `mysql.py`: `histo_quiz`
* `self.user`: `test`
* `self.passwd`: `test`

## Database setting
After creating the database and setting mysql user rights, following command inserts the quiz data to `histo_quiz`

```
sh insert_data_to_mysql.sh
```

