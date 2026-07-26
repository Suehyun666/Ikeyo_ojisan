# Documentation Assets

Store validation images for the Korean project report here.

Recommended layout:

```text
docs/assets/cases/
- case-001/
  - original.png
  - detected.png
  - mask.png
  - crop.png
  - summary.txt
- case-002/
  - original.png
  - detected.png
  - mask.png
  - crop.png
```

Use each case folder for one Reel screenshot test.

- `original.png`: original Instagram Reel screenshot
- `detected.png`: original screenshot with detected bounding box
- `mask.png`: HSV yellow mask
- `crop.png`: cropped title ROI used for OCR
- `summary.txt`: detection settings, crop coordinates, OCR text, title similarity, and translation result
