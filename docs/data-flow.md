Editor on:
```
https://mermaid-js.github.io/mermaid-live-editor/
```

Mermaid code:
```
stateDiagram
    RuneLite --> TwitchApi
    TwitchApi --> ConfigurationService
    TwitchApi --> PubSubService
    ConfigurationService --> TwitchViewer
    PubSubService --> TwitchViewer
```

Mermaid configuration:
```
{
  "theme": "neutral"
}
```
