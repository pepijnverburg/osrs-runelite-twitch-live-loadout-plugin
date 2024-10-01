Command to generate a log filter based on a specific tag:
```
git log v2.1.3..HEAD --no-merges --format=%B --grep=feat: --grep=fix:
```

Command to properly tag:
```
git tag -a v2.1.0 17a2e18c5729f3286f95f9e7eedab7053f0272d5 -m "Tag" && git push origin v2.1.0
```