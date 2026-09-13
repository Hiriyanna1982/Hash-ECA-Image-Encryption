# Scalability Image Preparation (Bird, Mountain, Bell_pepper)

Addresses the reviewer request for "exact identifiers and crop coordinates
for the scalability images."

## Source

Three high-resolution RGB images (Bird, Mountain, Bell_pepper) were
obtained from Wikimedia Commons, held out from the primary 10-image
dataset, and used for the scalability, round-count sensitivity, and
Round 2 ablation/attack experiments (scrambling-hierarchy ablation,
ECA-vs-no-ECA baseline, single-bit avalanche test, adaptive control-state
attack):

- **Bell_pepper**: https://commons.wikimedia.org/wiki/File:Colorful_Bell_Peppers.JPG
- **Mountain**: https://commons.wikimedia.org/wiki/File:Mountain_Lake_Sierra_Mountains.jpg
- **Bird**: https://commons.wikimedia.org/wiki/File:Bird_(26576011703).jpg

## Cropping method

Each native-resolution source image was center-cropped to 256x256, 512x512,
and 1024x1024, independently for each target size (not nested/cascaded
crops), with no resizing or interpolation. The exact MATLAB code used:

```matlab
img = imread('Bell_Peppers.bmp');
[H, W, ~] = size(img);
sizes = [1024, 512, 256];
for k = 1:length(sizes)
    S = sizes(k);
    x1 = floor((W - S)/2) + 1;
    y1 = floor((H - S)/2) + 1;
    cropped = img(y1:y1+S-1, x1:x1+S-1, :);
    filename = sprintf('Pepper_%dx%d.bmp', S, S);
    imwrite(cropped, filename, 'bmp');
end
```

(The same procedure was applied to the Bird and Mountain source images,
substituting the appropriate filename.)

In 0-indexed terms, this is a standard symmetric center crop: the crop
window's top-left corner is at `(floor((W-S)/2), floor((H-S)/2))` in the
native image, for each target size `S` independently.

## Files actually used in this repository

`Results/round2/` and the ablation/attack scripts operate on the 512x512
crop only (`Bird_512.bmp`, `Mountain_512.bmp`, `Bell_pepper_512.bmp`), since
none of the Round 2 statistical or cryptanalytic experiments required the
256x256 or 1024x1024 sizes (those were only used for the pure runtime
scalability benchmark, Figure 6, and the peak-memory benchmark, Table 10,
which is documented separately in `Documentation/MEMORY_BENCHMARK.md`).
