(ns openai.audio-test
  (:require [clojure.test :refer [deftest is]]
            [openai.audio :as audio])
  (:import (com.openai.client OpenAIClient)
           (com.openai.models.audio AudioModel AudioResponseFormat)
           (com.openai.core JsonValue)
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
                                                TranslationVerbose)
           (com.openai.models.audio.voices Voice Voice$Type
                                          VoiceCreateParams
                                          VoiceCreateParams$Body$AudioSample$Type)
           (com.openai.services.blocking AudioService)
           (com.openai.services.blocking.audio VoiceService)))

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

(defn- voice-params ^VoiceCreateParams [m]
  (#'audio/->voice-params m))

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

(deftest translates-transcription-languages
  (let [p (transcription-params {:file (.getBytes "audio" "UTF-8")
                                 :model "whisper-1"
                                 :language "fr"
                                 :languages ["fr" "en"]})]
    (is (= "fr" (opt (.language p))))
    (is (= ["fr" "en"] (opt (.languages p))))))

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

(deftest builds-voice-audio-sample-params
  (let [p (voice-params {:type :audio-sample
                         :audio-sample (.getBytes "audio" "UTF-8")
                         :consent "I consent"
                         :name "Ada"})
        body (.asAudioSample (.body p))]
    (is (= "I consent" (.consent body)))
    (is (= "Ada" (.name body)))
    (is (= "audio_sample" (.asString ^VoiceCreateParams$Body$AudioSample$Type
                                       (opt (.type body)))))
    (is (= [97 117 100 105 111] (vec (.readAllBytes (.audioSample body)))))))

(deftest builds-voice-prompt-params
  (let [p (voice-params {:type :prompt :name "Ada" :prompt "Warm and clear"
                         :model "gpt-4o-mini-tts" :script-hint "Welcome"})
        body (.asPrompt (.body p))]
    (is (= "Ada" (.name body)))
    (is (= "Warm and clear" (.prompt body)))
    (is (= "gpt-4o-mini-tts" (.asString (opt (.model body)))))
    (is (= "Welcome" (opt (.scriptHint body))))))

(deftest converts-voice-result
  (let [voice (-> (Voice/builder) (.id "voice_123") (.createdAt 42) (.name "Ada")
                  (.object_ (JsonValue/from "voice")) (.type Voice$Type/PROMPT) (.build))]
    (is (= {:id "voice_123" :created-at 42 :name "Ada" :object "voice" :type :prompt}
           (#'audio/voice->map voice)))))

(deftest rejects-missing-or-invalid-voice-variant
  (doseq [req [{} {:type :unsupported}]]
    (let [error (try (voice-params req) nil (catch clojure.lang.ExceptionInfo e e))]
      (is (= :invalid-voice-variant (:openai/error (ex-data error)))))))

(deftest creates-voice-through-the-audio-service
  (let [voice (-> (Voice/builder) (.id "voice_123") (.createdAt 42) (.name "Ada")
                  (.object_ (JsonValue/from "voice")) (.type Voice$Type/PROMPT) (.build))
        service (proxy [VoiceService] [] (create [_] voice))
        audio-service (proxy [AudioService] [] (voices [] service))
        client (proxy [OpenAIClient] [] (audio [] audio-service))]
    (is (= {:id "voice_123" :created-at 42 :name "Ada" :object "voice" :type :prompt}
           (audio/create-voice client {:type :prompt :name "Ada" :prompt "Warm and clear"})))))
