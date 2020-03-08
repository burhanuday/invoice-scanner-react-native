# CodeSquad PS1
Solution for Problem Statement 1 for AIDL 2020 conducted by [@unifynd](https://github.com/unifynd) technologies.

## Problem
Given images of bills/invoices, the task was to perform the following 3 operations:
  * Edge detection, cropping, flattening, enhancement of cropped image and compression.
  * Extracting text from the processed image.
  * The confidence score for the image to text conversion.

## Build

1. Make sure you have `react-native cli` & the latest Android SDK installed on your system. To get started with React Native, [follow here](https://reactnative.dev/docs/getting-started)

2. To install **OpenCV for Android**, [see here](https://github.com/davidmigloz/go-bees/wiki/Setup-OpenCV-3.1.0-in-Android-Studio-2.2)

3. Clone the github repository and install the dependencies using `npm`
```bash
$ git clone https://github.com/burhanuday/codesquad-PS1
$ cd codesquad-PS1
$ npm install
$ npx react-native run-android --no-jetifier
```

4. Run the `flask` server from the `flask-server` folder
```bash
$ python app.py
```

## Screens
<a href="https://imgbb.com/"><img width="200" src="https://i.ibb.co/Bft09zp/photo6278161801069832522.jpg" alt="photo6278161801069832522" border="0" /></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/M8GGbP7/photo6278311635298920670.jpg" width="200" alt="photo6278311635298920670" border="0" /></a> <a href="https://imgbb.com/"><img width="200" src="https://i.ibb.co/w0mvNRr/photo6278311635298920671.jpg" alt="photo6278311635298920671" border="0" /></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/vj4PH8m/photo6278311635298920672.jpg" width="200" alt="photo6278311635298920672" border="0" /></a> <a href="https://imgbb.com/"><img width="200" src="https://i.ibb.co/ph8dvfT/photo6278311635298920673.jpg" alt="photo6278311635298920673" border="0" /></a>

## Credits
Special thanks to [react-native-document-scanner](https://github.com/Woonivers/react-native-document-scanner#readme) & [react-native-perspective-image-cropper](https://github.com/Michaelvilleneuve/react-native-perspective-image-cropper)

**NOTE: We are using heavily modified versions of both these libraries to support our usecase. You can find these modified libraries in the `modified_open_source_libs/`**
