(ns clojuretwbot.feed
  (:require  [clj-http.client        :as http]
             [taoensso.timbre        :as timbre]
             [feedparser-clj.core    :as feedparser]
             [clojure.core.async     :as async :refer [chan go-loop >! <! put!]]))

;; channel that store new feed info
(def channel (chan))

;; since we'll send feed url to telegram, some feed doesn't has og:drecription tag
(defn- fetch-html
  "Fetch the HTML source of link, return nil when failed."
  [url]
  (try (-> (http/get url  {:insecure? true})
           :body)
       (catch Exception e
         (timbre/warn (.getMessage e)))))

(defn- parse-description
  "Parse feed's og:description info, set :description to nil if not exist."
  [info]
  (let [html (or (fetch-html (:link info)) "")
        description (-> (re-find #"(<meta\s*property=\"og:description\"\s*content=\")(.*)(\">)" html)
                        (nth 2))]
    (merge info {:description description})))

(defn- parse-feed
  "Parse feed url to [{:link :title :description} array."
  [url]
  (let [feed (feedparser/parse-feed url)
        info (map #(select-keys % [:title :link]) (:entries feed))]
    (map parse-description info)))

(defn- fetch-feed
  "Fetch feeds we need and send to channel."
  [feed-url]
  (doseq [f (parse-feed feed-url)]
    (put! channel f)))

(defn- fetch-mailing-list
  "Fetch mailing-list from google-group."
  [feed-url]
  (doseq [f (->> (parse-feed feed-url)
                 (filter #(or (re-matches #"\[ANN\].*" (:title %))
                              (re-matches #"ANN:.*" (:title %)))))]
    (put! channel f)))

(defn fetch-all
  "Fetch all feeds we need and send to channel."
  []
  ;; planet clojure
  (fetch-feed "http://planet.clojure.in/atom.xml")
  ;; Clojure mailing-lits
  (fetch-mailing-list "https://groups.google.com/forum/feed/clojure/msgs/rss_v2_0.xml"))