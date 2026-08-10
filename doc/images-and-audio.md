# Images and Audio

`openai.images` wraps image generation, edits, variations, and stream events.
Binary inputs accept paths, byte arrays, or input streams.

`openai.audio` wraps speech creation, transcription, and translation.

```clojure
(images/generate client {:model "gpt-image-1" :prompt "A map"})
(audio/create-transcription client {:file "speech.wav" :model "whisper-1"})
```
