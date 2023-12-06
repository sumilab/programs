This program is a part of implementation of [HistoChatbot](https://mobile.twitter.com/HistoChatbot). 
The main program is `auto_quiz_gen.py`.

# Organization
## Main Programs
* `auto_quiz_gen.py`: posting a quiz
* `mysql.py`: wrapper functions for mysql operation
* `keys.py`: setting the access token keys of Twitter Developer API
* `StopWord_en.py`: removing stop words as preprocess
* `util.py`: functions used in `auto_quiz_gen.py`, but these are usable in other programs
* `dict_models.py`: training models for feature vector creation


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

We use 4 work modes: Calendar-based, current-news-based, trending-words-based, and entity-based quiz modes.

# Experimental Evaluation
## Quiz Generation on Wikipedia
We performed a simple analysis to check whether the [question generation method](https://aclanthology.org/D19-5821/) that we use can indeed correctly generate quizzes from our dataset. For this, we randomly selected 100 generated quizzes and we checked all the obtained quiz sentences for their grammatical correctness and appropriateness in relation to their corresponding answers. As a result, we could confirm that 95 out of 100 quizzes were generated as correct and valid questions. We believe that 95% accuracy is a reasonable level for using the question generation approach for our purposes.

## Determination if the given answer is correct or incorrect
To evaluate the performance of HistoChatbot, we prepared 100 random texts for each of the current-news-based, trending-words-based, and entity-based quiz modes. The [entire evaluation data](https://onl.bz/MKQJeWj) is made available online. We collected current news texts and trending words during February 8-9, 2023. We also randomly selected from Wikipedia 20 entities for each of the 5 basic entity types: countries, cities, persons, organizations, and events.

Based on this data, we evaluated the rate of correct quizzes that the chatbot sends in response to user input (this would actually correspond to P@1). We have checked if the question is correctly formed considering its mode. We found that the chatbot achieved 72.7% success rate over the 300 produced quizzes. It obtained 78.0% and 74.0% for current-news- and trending-words-based quiz modes, respectively. For the entity mode, the chatbot scored 66%. In particular, it presented correct quizzes for approximately 90% countries and cities. However, there were only 40\% correct results for organizations and events. In addition, when 13 U.S. presidents and British prime ministers were entered as an entity, the appropriate quizzes were given for 12 of them. By contrast, in the case where 6 track and field and soccer players were entered, only one output was appropriate.

We found that incorrect quizzes were often chosen when the entity or trending word was not uniquely defined (e.g., Amazon). In the future, to improve the quiz selection accuracy one could use clarification responses asking about the correct named entity, or more complex methods to disambiguate entities could be proposed. 

# Publication
1. Yasunobu Sumikawa and Adam Jatowt, [HistoChatbot: Educating History by Generating Quizzes in Social Network Services](https://sumilab.github.io/web/pdf/2023/icadl_2023.pdf), In Proceedings of the 25th International Conference on Asia-Pacific Digital Libraries, ICADL'23, pp.28-35, Springer, 2023.

