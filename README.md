# Sentiment
Sentiment is a news sentiment analyser that aims to track the shifting sentiment of specific keywords/topics in the news.

## Getting Started
### Configuration
Each module requires configuration before launching. N.B. Application will not function without configuration.
Configuration files must be located in the `resources` folder of each module.

#### News Crawler / News Analyser / News Linker
These three modules require a JSON configuration which resembles the following:

````
{
  "api": {
    "key"               : [api key],
    "base.url"          : [microsoft cognitive services api url],
    "url.path"          : [microsoft cognitive services path],
    "port"              : [default port]
  }
}
````
At a minimum, the api key must be provided, the remaining can be omitted and default values will be used. To apply for a Microsoft Cognitive Services api key see the following links for the following modules

News Crawler - [Bing News Search API](https://www.microsoft.com/cognitive-services/en-us/bing-news-search-api).

News Analyser - [Text Analytics API](https://www.microsoft.com/cognitive-services/en-us/text-analytics-api).

News Linker - [Entity Linking Intelligence Service](https://www.microsoft.com/cognitive-services/en-us/entity-linking-intelligence-service).

#### Storage
The storage module uses mongo on the backend. The host and database name can be configured as follows:

````
{
  "host": "mongo",
  "db_name": "sentiment"
}
````

#### Sentiment Service
This module has a single value which can be configured at this point, the delay between news crawls:

````
{
  "timer.delay" : 3600000
}
````

This configuration can be omitted and the default value of 1 hour will be used. It is recommended to use a delay of 1 hour or greater to avoid hitting API limits on Microsoft's free tier.

#### API Gateway
Requires no specific configuration at this time.

### Running
To build the source and generate the required jar's, begin by running `gradle build` from the root directory. This will build all sub-modules in the same process.

For a quick and simple live preview a docker compose yaml file has been made availble, so simply `docker-compose up` from the root directory. ELK stack logging is available at localhost:5061 once launched.

Alternatively, you can launch via `gradle run` from the root directory. You may also perform a `gradle run` in each modules folder which will launch only that module. Be aware that some modules have service dependencies and will not launch until their service dependencies can be located. No ELK stack logging is performed via this method.

## More Info
Sentiment consists of three main modules to assist in crawling and analysing the news.

**_News Crawler_**
This module does what it says on the tin, it queries Microsoft's Bing News Search API to retrieve current news headlines for the current day (US news at this point). Once retrieved, it passes the results to the News Analyser and News Linker

**_News Analyser_**
Again, pretty straight forward, utilised Microsofts Text Analytics API to determine a sentiment for the submitted news headline / article. To quote the docs "Sentiment score is generated using classification techniques, and returns a score between 0 and 1. The input features to the classifier include n-grams, features generated from part-of-speech tags, and embedded words."

**_News Linker_**
It's important for us to be able to distinguish the difference in the queries which we are analysing. For instance, we may perform a search for the query 'Apple' which is pretty likely to return results we simply aren't interested in. The News Linker provides a method for developers to inspect Sentiments results and make a determination about which results they would prefer to include/exclude.

Here are two headlines which have vastly different meanings: 'Apple shatters market records' & 'Apple farms struck by blight. Produce markets in shock.'.
The first headline would return a positive result, the second a negative result. If we are only interested in Apple the technology company, its important we exclude the negative result to avoid skewing the sentiment score.

News Linker returns the following result when processing the headline 'Apple shatters market records':
````
{
    "entities":[{
        "matches":[{
            "text":"Apple",
            "entries":[{
                "offset":0
            }]
        }],
        "name":"Apple Inc.",
        "wikipediaId":"Apple Inc.",
        "score":0.311
    }]
}
````
And returns the following when processing the headline 'Apple farms struck by blight. Produce markets in shock.'
````
{
    "entities":[{
        "matches":[{
            "text":"Apple",
            "entries":[{
                "offset":0
            }]
        }],
        "name":"Apple",
        "wikipediaId":"Apple",
        "score":0.566
    }]
}
````

We can now distinguish between the two results based on the 'name' or 'wikipediaId' field. This still requires that we have some knowledge about the query and what results we expect, but we now have a mechanism for filtering results.

##### Sentiment is built upon the Vert.x framework and Microsoft Cognitive Services API's
