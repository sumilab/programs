This program is an implementation of [HistoChatbot](https://mobile.twitter.com/HistoChatbot). 
The main program is `auto_quiz_gen.py`.

# Organization
## Main Programs
* `auto_quiz_gen.py`: posting a quiz
* `mysql.py`: wrapper functions for mysql operation
* `keys.py`: setting the access token keys of Twitter Developer API
* `StopWord_en.py`: removing stop words as preprocess
* `util.py`: functions used in `auto_quiz_gen.py`, but these are usable in other programs
* `dict_models.py`: train models for feature vector creation


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
* `self.user` in `mysql.py`: `test`
* `self.passwd` in `mysql.py`: `test`
* `list_id` in `auto_quiz_gen.py`: `1256794745512185857` This parameter is used in obtaining current news. This list includes several news agencies' Twitter accounts.


## Database setting
After creating the database and setting mysql user rights, following command inserts the quiz data to `histo_quiz`

```
sh insert_data_to_mysql.sh
```


## Preparation for feature vector creation
To perform cosine similarity, following program produces models for feature vector creation

```
mkdir models
python dict_models.py
```


## Posting quiz
Following command posts a quiz. Quiz generation modes are switched by setting the `modes` list on the line 350.
```
python auto_quiz_gen.py
```