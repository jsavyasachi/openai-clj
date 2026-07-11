(ns openai.audio-test
  (:require [clojure.test :refer [deftest is]]
            [openai.audio :as audio])
  (:import (com.openai.models.audio AudioModel AudioResponseFormat)
           (com.openai.models.audio.speech SpeechCreateParams
                                           SpeechCreateParams$ResponseFormat
                                           SpeechCreateParams$Voice
                                           SpeechModel)
           (com.openai.models.audio.transcriptions Transcription
                                                   TranscriptionCreateParams
                                                   TranscriptionCreateParams$TimestampGranularity
                                                   TranscriptionCreateResponse
                                                   TranscriptionVerbose)
           (com.openai.models.audio.translations Translation
                                                TranslationCreateParams
                                                TranslationCreateParams$ResponseFormat
                                                TranslationCreateResponse
                                                TranslationVerbose)))

(set! *warn-on-reflection* true)

(defn- opt [o]
  (when (.isPresent ^java.util.Optional o)
    (.get ^java.util.Optional o)))

(defn- speech-params ^SpeechCreateParams [m]
  (#'audio/->speech-params m))

(defn- transcription-params ^TranscriptionCreateParams [m]
  (#'audio/->transcription-params m))

(defn- translation-params ^TranslationCreateParams [m]
  (#'audio/->translation-params m))

(deftest translates-speech-params
  (let [p (speech-params {:input "Hello"
                          :model "gpt-4o-mini-tts"
                          :voice :alloy
                          :response-format :mp3})]
    (is (= "Hello" (.input p)))
    (is (= "gpt-4o-mini-tts" (.asString ^SpeechModel (.model p))))
    (is (= "alloy" (.asString ^SpeechCreateParams$Voice (.voice p))))
    (is (= "mp3" (.asString ^SpeechCreateParams$ResponseFormat
                             (opt (.responseFormat p)))))))

(deftest coerces-transcription-bytes-with-filename-and-translates-options
  (let [p (transcription-params {:file (.getBytes "audio" "UTF-8")
                                 :filename "sample.wav"
                                 :model "whisper-1"
                                 :language "fr"
                                 :prompt "Names"
                                 :response-format :verbose-json
                                 :temperature 0.2
                                 :timestamp-granularities [:word :segment]})
        field (._file p)]
    (is (= "sample.wav" (opt (.filename field))))
    (is (= [97 117 100 105 111] (vec (.readAllBytes (.file p)))))
    (is (= "whisper-1" (.asString ^AudioModel (.model p))))
    (is (= "fr" (opt (.language p))))
    (is (= "Names" (opt (.prompt p))))
    (is (= "verbose_json" (.asString ^AudioResponseFormat
                                      (opt (.responseFormat p)))))
    (is (= 0.2 (opt (.temperature p))))
    (is (= ["word" "segment"]
           (mapv #(.asString ^TranscriptionCreateParams$TimestampGranularity %)
                 (opt (.timestampGranularities p)))))))

(deftest converts-transcription-response-unions
  (let [plain (-> (Transcription/builder) (.text "plain") (.build))
        verbose (-> (TranscriptionVerbose/builder)
                    (.text "verbose") (.language "en") (.duration 1.5) (.build))]
    (is (= {:text "plain"}
           (#'audio/transcription-response->map
            (TranscriptionCreateResponse/ofTranscription plain))))
    (is (= {:text "verbose" :language "en" :duration 1.5}
           (#'audio/transcription-response->map
            (TranscriptionCreateResponse/ofVerbose verbose))))))

(deftest translates-translation-params
  (let [p (translation-params {:file (.getBytes "audio" "UTF-8")
                               :filename "sample.mp3"
                               :model "whisper-1"
                               :prompt "Names"
                               :response-format :verbose-json
                               :temperature 0.3})]
    (is (= "sample.mp3" (opt (.filename (._file p)))))
    (is (= "whisper-1" (.asString ^AudioModel (.model p))))
    (is (= "Names" (opt (.prompt p))))
    (is (= "verbose_json"
           (.asString ^TranslationCreateParams$ResponseFormat
                      (opt (.responseFormat p)))))
    (is (= 0.3 (opt (.temperature p))))))

(deftest converts-translation-response-unions
  (let [plain (-> (Translation/builder) (.text "plain") (.build))
        verbose (-> (TranslationVerbose/builder)
                    (.text "verbose") (.language "en") (.duration 2.5) (.build))]
    (is (= {:text "plain"}
           (#'audio/translation-response->map
            (TranslationCreateResponse/ofTranslation plain))))
    (is (= {:text "verbose" :language "en" :duration 2.5}
           (#'audio/translation-response->map
            (TranslationCreateResponse/ofVerbose verbose))))))
